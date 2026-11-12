package com.zapthemes.files;

/** What a {@link FileSystem} can do or has, beyond listing. */
public enum Capability {
    /** {@link FileSystem#createDirectory} works. */
    CREATE_DIRECTORY,
    /** {@link FileSystem#rename} works. */
    RENAME,
    /** {@link FileSystem#delete} works. */
    DELETE,
    /** {@link FileSystem#openRead} works. */
    READ,
    /** {@link FileSystem#openWrite} works. */
    WRITE,
    /** Entries carry {@link FileEntry#permissions()}. */
    PERMISSIONS,
    /** Entries carry {@link FileEntry#owner()} and {@link FileEntry#group()}. */
    OWNERS,
    /** Names that differ only in case are the same file (Windows, macOS by default). */
    CASE_INSENSITIVE,
    /**
     * Calls may take long (a remote server): the browser shows that it is loading and lists every
     * folder in the background. (It never lists on the UI thread anyway; this only changes what it
     * shows meanwhile and how eagerly it looks ahead.)
     */
    SLOW
}
