package com.zapthemes.files;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Carries out copies and moves for a file browser (paste, and later drops). An application with a
 * transfer queue of its own (progress, conflicts, resume) implements it; {@link #direct()} copies
 * through the {@link FileSystem} API in the background, renaming on clashes.
 */
@FunctionalInterface
public interface FileTransferHandler {

    /**
     * Starts {@code request}. Called on the UI thread; must not block it.
     *
     * @return completes with the names created in the target folder (the browser selects them),
     *         or exceptionally if it failed
     */
    CompletionStage<List<String>> transfer(TransferRequest request);

    /** Copies and moves with {@link FileTransfers}, on a virtual thread. */
    static FileTransferHandler direct() {
        return request -> {
            CompletableFuture<List<String>> result = new CompletableFuture<>();
            Thread.ofVirtual().name("zapthemes-files-transfer").start(() -> {
                try {
                    result.complete(FileTransfers.run(request));
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            });
            return result;
        };
    }
}
