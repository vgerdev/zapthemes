package com.zapthemes.files;

/** What a {@link FileEntry} is. A symbolic link has the kind of its target (see {@link FileEntry#link()}). */
public enum EntryKind {
    /** A regular file, or a link to one. */
    FILE,
    /** A folder, or a link to one. */
    DIRECTORY,
    /** A drive or volume at the top of a filesystem (Windows drives); browsed like a folder. */
    DRIVE,
    /** Anything else: a device, a pipe, a socket, a dangling link. */
    OTHER;

    /** Whether it can be opened like a folder. */
    public boolean isContainer() {
        return this == DIRECTORY || this == DRIVE;
    }
}
