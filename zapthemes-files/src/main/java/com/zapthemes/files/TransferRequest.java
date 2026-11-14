package com.zapthemes.files;

import java.util.List;
import java.util.Objects;

/**
 * Copy or move {@code sources} of {@code source} into the folder {@code targetFolder} of
 * {@code target}: what a paste or a drop asks a {@link FileTransferHandler} to do. The two
 * filesystems may differ (local to remote is an upload).
 */
public record TransferRequest(FileSystem source, List<FileEntry> sources, FileSystem target, FilePath targetFolder, boolean move) {

    public TransferRequest {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(targetFolder, "targetFolder");
        sources = List.copyOf(sources);
    }

    /** Whether source and target are the same filesystem. */
    public boolean withinOneFileSystem() {
        return source == target;
    }
}
