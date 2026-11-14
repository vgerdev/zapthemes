package com.zapthemes.files;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A tree of files that a file browser shows: the local disks ({@link LocalFileSystem}), one folder of
 * another filesystem ({@link RootedFileSystem}), a tree in memory ({@link MemoryFileSystem}), or a
 * remote server an application connects to (SFTP, FTP, S3, ...).
 *
 * <p>Calls block and may be slow; the browser makes them on background threads, never on the UI
 * thread, so implementations need to be safe for use from several threads at once. A browser
 * interrupts a call it no longer needs; an implementation may stop early with
 * {@link java.io.InterruptedIOException}, or finish and have its result dropped.
 *
 * <p>Paths are {@link FilePath}s from the filesystem's top. What users see and type goes through
 * {@link #format} and {@link #parse}, and names through {@link #label}.
 *
 * <p>Operations other than listing are optional: each refuses with
 * {@link UnsupportedOperationException} unless {@link #capabilities()} names it.
 */
public interface FileSystem extends Closeable {

    /** A name for the whole filesystem, such as "Local" or "user@host". */
    String displayName();

    /** The name of the top: "This PC", "Computer", a root folder's name, a host name. */
    String topName();

    /** Where browsing starts by default. */
    FilePath home();

    /** What the filesystem has and can do. */
    Set<Capability> capabilities();

    /** Whether it has {@code capability}. */
    default boolean has(Capability capability) {
        return capabilities().contains(capability);
    }

    /**
     * The entries of {@code folder}, in no particular order, without {@code "."} and {@code ".."}.
     *
     * @throws java.nio.file.NoSuchFileException if it does not exist
     * @throws java.nio.file.NotDirectoryException if it is not a folder
     * @throws java.nio.file.AccessDeniedException if it may not be listed
     */
    List<FileEntry> list(FilePath folder) throws IOException;

    /** The entry at {@code path}, following links, or empty if there is none. At the top, an entry for the top. */
    Optional<FileEntry> stat(FilePath path) throws IOException;

    /**
     * The path a user typed, such as {@code C:\work\..\tmp} or {@code ~/src}: absolute, or relative
     * to {@code base}, with {@code "."} and {@code ".."} resolved. Doesn't check that it exists.
     * With a {@code null} base, only full paths are accepted.
     *
     * @throws IllegalArgumentException if it is not a path of this filesystem
     */
    FilePath parse(String text, FilePath base);

    /** The path as users see and type it: {@code C:\work}, {@code /home/me}, {@code \reports} (rooted). */
    String format(FilePath path);

    /** The separator {@link #format} uses between names. */
    default char separator() {
        return '/';
    }

    /**
     * The name to show for the last segment of {@code path} (a breadcrumb segment): "OS (C:)" for a
     * Windows drive, {@link #topName()} at the top, else the segment itself. May block.
     */
    default String label(FilePath path) throws IOException {
        return path.isTop() ? topName() : path.name();
    }

    /**
     * Creates the folder {@code name} in {@code parent}.
     *
     * @return the new folder's path
     * @throws java.nio.file.FileAlreadyExistsException if something has that name
     */
    default FilePath createDirectory(FilePath parent, String name) throws IOException {
        throw new UnsupportedOperationException(displayName() + " cannot create folders");
    }

    /**
     * Renames {@code path} within its folder.
     *
     * @return the new path
     */
    default FilePath rename(FilePath path, String newName) throws IOException {
        throw new UnsupportedOperationException(displayName() + " cannot rename");
    }

    /**
     * Moves {@code path} into the folder {@code targetFolder}, keeping its name.
     *
     * @return the new path
     * @throws java.nio.file.FileAlreadyExistsException if the folder has something of that name
     */
    default FilePath move(FilePath path, FilePath targetFolder) throws IOException {
        throw new UnsupportedOperationException(displayName() + " cannot move");
    }

    /** Deletes {@code path}; a folder only if empty, unless {@code recursive}. Links are deleted, not followed. */
    default void delete(FilePath path, boolean recursive) throws IOException {
        throw new UnsupportedOperationException(displayName() + " cannot delete");
    }

    /** Opens the file {@code path} for reading. */
    default InputStream openRead(FilePath path) throws IOException {
        throw new UnsupportedOperationException(displayName() + " cannot read files");
    }

    /** Opens the file {@code path} for writing, creating or truncating it. */
    default OutputStream openWrite(FilePath path) throws IOException {
        throw new UnsupportedOperationException(displayName() + " cannot write files");
    }

    /** Releases what the filesystem holds (a connection). The default does nothing. */
    @Override
    default void close() throws IOException {
    }
}
