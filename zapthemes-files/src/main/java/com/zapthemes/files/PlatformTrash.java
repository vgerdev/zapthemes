package com.zapthemes.files;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Moves local files to the platform's trash with one native call through the JDK's foreign
 * function API (no AWT, no extra library): {@code SHFileOperationW} on Windows (the Recycle Bin of
 * a fixed drive), GIO's {@code g_file_trash} on Linux and the BSDs (the freedesktop.org trash the
 * desktop's file manager shows), and {@code NSFileManager trashItemAtURL:} on macOS.
 *
 * <p>Where none of these loads, {@link #isAvailable()} is false and local deletes stay permanent.
 */
@SuppressWarnings("restricted")
final class PlatformTrash {

    private interface Backend {
        boolean canTrash(Path path);

        void trash(Path path) throws Throwable;
    }

    private static final Backend BACKEND = load();

    private PlatformTrash() {
    }

    /** Whether this platform's trash could be loaded. */
    static boolean isAvailable() {
        return BACKEND != null;
    }

    /** Whether {@code path} can go to the trash (on Windows, only files of a fixed drive: others have no Recycle Bin). */
    static boolean canTrash(Path path) {
        return BACKEND != null && BACKEND.canTrash(path);
    }

    /** Moves {@code path} to the trash; if it can't, nothing is deleted and an {@link IOException} says why. */
    static void trash(Path path) throws IOException {
        if (BACKEND == null) {
            throw new UnsupportedOperationException("This platform has no trash");
        }
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.exists(absolute, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(absolute.toString());
        }
        if (!BACKEND.canTrash(absolute)) {
            throw new IOException(absolute.getRoot() + " has no Recycle Bin");
        }
        try {
            BACKEND.trash(absolute);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IOException("Can't move " + absolute + " to the trash", e);
        }
    }

    private static Backend load() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.startsWith("windows")) {
                return new Windows();
            }
            if (os.contains("mac")) {
                return new MacOs();
            }
            return new Gio();
        } catch (Throwable e) {
            // No such library, or native access refused: deletes stay permanent.
            return null;
        }
    }

    private static MethodHandle function(SymbolLookup library, String name, FunctionDescriptor descriptor) {
        return Linker.nativeLinker().downcallHandle(library.find(name).orElseThrow(() -> new UnsatisfiedLinkError(name)), descriptor);
    }

    // ------------------------------------------------------------------ Windows

    private static final class Windows implements Backend {
        private static final int FO_DELETE = 3;
        private static final int FOF_SILENT = 0x4;
        private static final int FOF_NOCONFIRMATION = 0x10;
        private static final int FOF_ALLOWUNDO = 0x40;
        private static final int FOF_NOCONFIRMMKDIR = 0x200;
        private static final int FOF_NOERRORUI = 0x400;
        /** Asks before deleting for good something the Recycle Bin can't take (too big), rather than doing it silently. */
        private static final int FOF_WANTNUKEWARNING = 0x4000;
        private static final int DRIVE_FIXED = 3;

        private final MethodHandle shFileOperation;
        private final MethodHandle getDriveType;

        Windows() {
            SymbolLookup shell32 = SymbolLookup.libraryLookup("shell32", Arena.global());
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            shFileOperation = function(shell32, "SHFileOperationW", FunctionDescriptor.of(JAVA_INT, ADDRESS));
            getDriveType = function(kernel32, "GetDriveTypeW", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        }

        @Override
        public boolean canTrash(Path path) {
            Path root = path.getRoot();
            if (root == null) {
                return false;
            }
            try (Arena arena = Arena.ofConfined()) {
                return (int) getDriveType.invokeExact(wide(arena, root.toString(), 1)) == DRIVE_FIXED;
            } catch (Throwable e) {
                return false;
            }
        }

        @Override
        public void trash(Path path) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                // SHFILEOPSTRUCTW on 64-bit Windows: natural alignment, 56 bytes.
                MemorySegment op = arena.allocate(56, 8);
                op.set(JAVA_INT, 8, FO_DELETE);
                op.set(ADDRESS, 16, wide(arena, path.toString(), 2)); // A list of names: two NULs at the end.
                op.set(JAVA_SHORT, 32, (short) (FOF_ALLOWUNDO | FOF_NOCONFIRMATION | FOF_SILENT | FOF_NOERRORUI | FOF_NOCONFIRMMKDIR
                        | FOF_WANTNUKEWARNING));
                int result = (int) shFileOperation.invokeExact(op);
                if (op.get(JAVA_INT, 36) != 0) {
                    throw new IOException("Moving " + path + " to the Recycle Bin was cancelled");
                }
                if (result != 0) {
                    throw new IOException("Can't move " + path + " to the Recycle Bin (error 0x" + Integer.toHexString(result) + ")");
                }
            }
        }

        /** {@code text} in UTF-16 with {@code nuls} NUL characters after it. */
        private static MemorySegment wide(Arena arena, String text, int nuls) {
            byte[] bytes = (text + "\0".repeat(nuls)).getBytes(StandardCharsets.UTF_16LE);
            MemorySegment segment = arena.allocate(bytes.length, 2);
            MemorySegment.copy(MemorySegment.ofArray(bytes), 0, segment, 0, bytes.length);
            return segment;
        }
    }

    // ------------------------------------------------------------------ Linux, BSD (GIO)

    private static final class Gio implements Backend {
        private final MethodHandle newForPath;
        private final MethodHandle trash;
        private final MethodHandle unref;
        private final MethodHandle errorFree;

        Gio() {
            SymbolLookup gio = SymbolLookup.libraryLookup("libgio-2.0.so.0", Arena.global());
            SymbolLookup gobject = SymbolLookup.libraryLookup("libgobject-2.0.so.0", Arena.global());
            SymbolLookup glib = SymbolLookup.libraryLookup("libglib-2.0.so.0", Arena.global());
            newForPath = function(gio, "g_file_new_for_path", FunctionDescriptor.of(ADDRESS, ADDRESS));
            trash = function(gio, "g_file_trash", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
            unref = function(gobject, "g_object_unref", FunctionDescriptor.ofVoid(ADDRESS));
            errorFree = function(glib, "g_error_free", FunctionDescriptor.ofVoid(ADDRESS));
        }

        @Override
        public boolean canTrash(Path path) {
            return true;
        }

        @Override
        public void trash(Path path) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment file = (MemorySegment) newForPath.invokeExact(arena.allocateFrom(path.toString()));
                MemorySegment error = arena.allocate(ADDRESS);
                int ok;
                try {
                    ok = (int) trash.invokeExact(file, MemorySegment.NULL, error);
                } finally {
                    unref.invokeExact(file);
                }
                if (ok == 0) {
                    String message = "Can't move " + path + " to the trash";
                    MemorySegment gerror = error.get(ADDRESS, 0);
                    if (!gerror.equals(MemorySegment.NULL)) {
                        // GError: a quark and a code (two 32-bit ints), then the message.
                        MemorySegment text = gerror.reinterpret(16).get(ADDRESS, 8);
                        if (!text.equals(MemorySegment.NULL)) {
                            message = text.reinterpret(Integer.MAX_VALUE).getString(0);
                        }
                        errorFree.invokeExact(gerror);
                    }
                    throw new IOException(message);
                }
            }
        }
    }

    // ------------------------------------------------------------------ macOS

    private static final class MacOs implements Backend {
        private final MethodHandle getClass;
        private final MethodHandle selector;
        private final MethodHandle send0;
        private final MethodHandle send1;
        private final MethodHandle sendTrash;
        private final MethodHandle poolPush;
        private final MethodHandle poolPop;

        MacOs() {
            SymbolLookup objc = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global());
            // Loaded for NSFileManager and NSURL; the symbols come from objc.
            SymbolLookup.libraryLookup("/System/Library/Frameworks/Foundation.framework/Foundation", Arena.global());
            getClass = function(objc, "objc_getClass", FunctionDescriptor.of(ADDRESS, ADDRESS));
            selector = function(objc, "sel_registerName", FunctionDescriptor.of(ADDRESS, ADDRESS));
            MemorySegment send = objc.find("objc_msgSend").orElseThrow();
            Linker linker = Linker.nativeLinker();
            send0 = linker.downcallHandle(send, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            send1 = linker.downcallHandle(send, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            sendTrash = linker.downcallHandle(send, FunctionDescriptor.of(JAVA_BYTE, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            poolPush = function(objc, "objc_autoreleasePoolPush", FunctionDescriptor.of(ADDRESS));
            poolPop = function(objc, "objc_autoreleasePoolPop", FunctionDescriptor.ofVoid(ADDRESS));
        }

        @Override
        public boolean canTrash(Path path) {
            return true;
        }

        @Override
        public void trash(Path path) throws Throwable {
            MemorySegment pool = (MemorySegment) poolPush.invokeExact();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment string = (MemorySegment) send1.invokeExact(cls(arena, "NSString"), sel(arena, "stringWithUTF8String:"),
                        arena.allocateFrom(path.toString()));
                MemorySegment url = (MemorySegment) send1.invokeExact(cls(arena, "NSURL"), sel(arena, "fileURLWithPath:"), string);
                MemorySegment manager = (MemorySegment) send0.invokeExact(cls(arena, "NSFileManager"), sel(arena, "defaultManager"));
                MemorySegment error = arena.allocate(ADDRESS);
                byte ok = (byte) sendTrash.invokeExact(manager, sel(arena, "trashItemAtURL:resultingItemURL:error:"), url,
                        MemorySegment.NULL, error);
                if (ok == 0) {
                    String message = "Can't move " + path + " to the Trash";
                    MemorySegment nsError = error.get(ADDRESS, 0);
                    if (!nsError.equals(MemorySegment.NULL)) {
                        MemorySegment description = (MemorySegment) send0.invokeExact(nsError, sel(arena, "localizedDescription"));
                        MemorySegment text = (MemorySegment) send0.invokeExact(description, sel(arena, "UTF8String"));
                        if (!text.equals(MemorySegment.NULL)) {
                            message = text.reinterpret(Integer.MAX_VALUE).getString(0);
                        }
                    }
                    throw new IOException(message);
                }
            } finally {
                poolPop.invokeExact(pool);
            }
        }

        private MemorySegment cls(Arena arena, String name) throws Throwable {
            return (MemorySegment) getClass.invokeExact(arena.allocateFrom(name));
        }

        private MemorySegment sel(Arena arena, String name) throws Throwable {
            return (MemorySegment) selector.invokeExact(arena.allocateFrom(name));
        }
    }
}
