package com.zapthemes.files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The local disks, through {@code java.nio.file}.
 *
 * <p>On Windows the top is "This PC" and lists the drives, labeled with their volume names
 * ("OS (C:)"); a path's first segment is its drive ({@code "C:\"}) or UNC share
 * ({@code "\\server\share\"}). Elsewhere the top is {@code /}. Hidden files are those with the
 * DOS hidden attribute on Windows, and those whose name starts with a dot elsewhere; POSIX
 * permissions, owners and groups are read where the platform has them.
 */
public final class LocalFileSystem implements FileSystem {

    private static final LocalFileSystem INSTANCE = new LocalFileSystem();

    private final boolean windows = File.separatorChar == '\\';
    private final boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    private final boolean caseInsensitive;
    private final Map<String, String> driveLabels = new ConcurrentHashMap<>();

    private LocalFileSystem() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        caseInsensitive = windows || os.contains("mac");
    }

    /** The local filesystem. */
    public static LocalFileSystem get() {
        return INSTANCE;
    }

    @Override
    public String displayName() {
        return "Local";
    }

    @Override
    public String topName() {
        return windows ? "This PC" : "Computer";
    }

    @Override
    public FilePath home() {
        return fromPath(Path.of(System.getProperty("user.home")));
    }

    @Override
    public Set<Capability> capabilities() {
        Set<Capability> set = EnumSet.of(Capability.CREATE_DIRECTORY, Capability.RENAME, Capability.DELETE, Capability.READ,
                Capability.WRITE);
        if (posix) {
            set.add(Capability.PERMISSIONS);
            set.add(Capability.OWNERS);
        }
        if (caseInsensitive) {
            set.add(Capability.CASE_INSENSITIVE);
        }
        return set;
    }

    @Override
    public char separator() {
        return File.separatorChar;
    }

    /** The NIO path of {@code path}; on Windows the top has none. */
    public Path toPath(FilePath path) {
        if (path.isTop()) {
            if (windows) {
                throw new IllegalArgumentException("\"" + topName() + "\" is not a folder on disk");
            }
            return Path.of("/");
        }
        List<String> segments = path.segments();
        if (windows) {
            return Path.of(segments.getFirst(), segments.subList(1, segments.size()).toArray(String[]::new));
        }
        return Path.of("/", segments.toArray(String[]::new));
    }

    /** The {@link FilePath} of an NIO path, made absolute and normalized. */
    public FilePath fromPath(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        List<String> segments = new ArrayList<>();
        if (windows) {
            segments.add(absolute.getRoot().toString());
        }
        for (Path name : absolute) {
            segments.add(name.toString());
        }
        return FilePath.of(segments);
    }

    @Override
    public List<FileEntry> list(FilePath folder) throws IOException {
        if (windows && folder.isTop()) {
            List<FileEntry> drives = new ArrayList<>();
            for (Path root : FileSystems.getDefault().getRootDirectories()) {
                FilePath path = FilePath.of(root.toString());
                drives.add(FileEntry.builder(path, EntryKind.DRIVE).label(label(path)).build());
            }
            return drives;
        }
        Path dir = toPath(folder);
        List<FileEntry> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.io.InterruptedIOException("Listing " + dir + " was interrupted");
                }
                FileEntry entry = entry(folder.resolve(child.getFileName().toString()), child);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    @Override
    public Optional<FileEntry> stat(FilePath path) throws IOException {
        if (path.isTop()) {
            return Optional.of(FileEntry.builder(path, EntryKind.DIRECTORY).label(topName()).build());
        }
        Path file = toPath(path);
        if (windows && path.depth() == 1) {
            return Files.exists(file) ? Optional.of(FileEntry.builder(path, EntryKind.DRIVE).label(label(path)).build())
                    : Optional.empty();
        }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        return Optional.ofNullable(entry(path, file));
    }

    /** The entry of {@code file}, or {@code null} if it vanished while being read. */
    private FileEntry entry(FilePath path, Path file) throws IOException {
        BasicFileAttributes own;
        try {
            own = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException e) {
            return null;
        }
        boolean link = own.isSymbolicLink();
        BasicFileAttributes attributes = own;
        String target = null;
        if (link) {
            try {
                target = Files.readSymbolicLink(file).toString();
            } catch (IOException | UnsupportedOperationException e) {
                // Shown without a target.
            }
            try {
                attributes = Files.readAttributes(file, BasicFileAttributes.class);
            } catch (IOException e) {
                attributes = null; // Dangling.
            }
        }
        EntryKind kind = attributes == null ? EntryKind.OTHER
                : attributes.isDirectory() ? EntryKind.DIRECTORY : attributes.isRegularFile() ? EntryKind.FILE : EntryKind.OTHER;
        FileEntry.Builder builder = FileEntry.builder(path, kind).link(link).linkTarget(target);
        BasicFileAttributes times = attributes != null ? attributes : own;
        builder.modified(times.lastModifiedTime().toInstant());
        if (kind == EntryKind.FILE) {
            builder.size(times.size());
        }
        String name = path.name();
        if (windows) {
            try {
                builder.hidden(Files.readAttributes(file, DosFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isHidden());
            } catch (IOException | UnsupportedOperationException e) {
                builder.hidden(name.startsWith("."));
            }
        } else {
            builder.hidden(name.startsWith("."));
        }
        if (posix) {
            try {
                PosixFileAttributes p = Files.readAttributes(file, PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                builder.permissions(PosixFilePermissions.toString(p.permissions())).owner(p.owner().getName())
                        .group(p.group().getName());
            } catch (IOException | UnsupportedOperationException e) {
                // Left out.
            }
        }
        return builder.build();
    }

    @Override
    public String label(FilePath path) {
        if (path.isTop()) {
            return topName();
        }
        if (!windows || path.depth() != 1) {
            return path.name();
        }
        return driveLabels.computeIfAbsent(path.segments().getFirst(), LocalFileSystem::driveLabel);
    }

    /** "OS (C:)", "Local Disk (C:)" without a volume name, or the share for a UNC root. */
    private static String driveLabel(String root) {
        String letter = root.endsWith("\\") ? root.substring(0, root.length() - 1) : root;
        if (letter.startsWith("\\\\")) {
            return letter;
        }
        try {
            FileStore store = Files.getFileStore(Path.of(root));
            String name = store.name();
            return (name == null || name.isBlank() ? "Local Disk" : name) + " (" + letter + ")";
        } catch (IOException | SecurityException e) {
            return "Drive (" + letter + ")"; // Not ready: an empty card reader or disc drive.
        }
    }

    @Override
    public FilePath parse(String text, FilePath base) {
        String t = text.strip();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1);
        }
        if (windows && (t.isEmpty() || t.equalsIgnoreCase(topName()))) {
            return FilePath.TOP;
        }
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Type a path");
        }
        if (t.equals("~") || t.startsWith("~/") || t.startsWith("~" + File.separator)) {
            t = System.getProperty("user.home") + t.substring(1);
        }
        if (windows && t.length() == 2 && t.charAt(1) == ':') {
            t = t + "\\";
        }
        try {
            Path path = Path.of(t);
            if (!path.isAbsolute()) {
                if (base == null || (windows && base.isTop())) {
                    throw new IllegalArgumentException("\"" + text + "\" is not a full path");
                }
                path = toPath(base).resolve(path);
            }
            return fromPath(path);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("\"" + text + "\" is not a valid path", e);
        }
    }

    @Override
    public String format(FilePath path) {
        if (path.isTop()) {
            return windows ? topName() : "/";
        }
        return toPath(path).toString();
    }

    @Override
    public FilePath createDirectory(FilePath parent, String name) throws IOException {
        FilePath path = parent.resolve(checkLocalName(name));
        Files.createDirectory(toPath(path));
        return path;
    }

    @Override
    public FilePath rename(FilePath path, String newName) throws IOException {
        FilePath target = path.parent().resolve(checkLocalName(newName));
        Path to = toPath(target);
        Path from = toPath(path);
        // A change of case only is a rename on a case-insensitive disk, not a clash.
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS) && !(caseInsensitive && path.name().equalsIgnoreCase(newName))) {
            throw new FileAlreadyExistsException(to.toString());
        }
        Files.move(from, to);
        return target;
    }

    @Override
    public void delete(FilePath path, boolean recursive) throws IOException {
        Path file = toPath(path);
        if (!recursive || !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
            Files.delete(file);
            return;
        }
        Files.walkFileTree(file, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) throws IOException {
                Files.delete(f);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public InputStream openRead(FilePath path) throws IOException {
        return Files.newInputStream(toPath(path));
    }

    @Override
    public OutputStream openWrite(FilePath path) throws IOException {
        return Files.newOutputStream(toPath(path));
    }

    /**
     * The standard folders under the user's home that exist: Desktop, Downloads, Documents,
     * Pictures, Music, Videos (Movies on macOS). Checks the disk, so call it off the UI thread.
     */
    public List<Place> standardPlaces() {
        Path home = Path.of(System.getProperty("user.home"));
        List<Place> places = new ArrayList<>();
        String[][] folders = { { "Desktop", "desktop" }, { "Downloads", "downloads" }, { "Documents", "documents" },
                { "Pictures", "pictures" }, { "Music", "music" }, { "Videos", "videos" }, { "Movies", "videos" } };
        for (String[] folder : folders) {
            Path dir = home.resolve(folder[0]);
            if (Files.isDirectory(dir)) {
                places.add(new Place(folder[0], fromPath(dir), folder[1]));
            }
        }
        return places;
    }

    private String checkLocalName(String name) {
        FilePath.checkName(name);
        if (name.indexOf(File.separatorChar) >= 0 || (windows && name.matches(".*[<>:\"|?*].*"))) {
            throw new IllegalArgumentException("A file name can't contain " + (windows ? "\\ / : * ? \" < > |" : "/"));
        }
        return name;
    }
}
