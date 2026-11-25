package com.zapthemes.files;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The watches of {@link LocalFileSystem}: one {@link WatchService} and one daemon thread for all
 * watched folders, started with the first watch and stopped after the last.
 *
 * <p>On Windows a watched folder holds a handle that keeps its parents from being renamed or
 * moved, so the filesystem's own renames, moves and deletes {@link #pausing pause} the watches
 * below what they change. Other programs can't rename such a parent while a browser shows the
 * folder.
 */
final class LocalWatcher {

    private static final class Folder {
        final Path dir;
        final FilePath path;
        final List<Consumer<FilePath>> listeners = new CopyOnWriteArrayList<>();
        WatchKey key;

        Folder(Path dir, FilePath path) {
            this.dir = dir;
            this.path = path;
        }
    }

    /** Something to run while watches are paused. */
    interface Operation<T> {
        T run() throws IOException;
    }

    private final Map<Path, Folder> byDir = new HashMap<>();
    private final Map<WatchKey, Folder> byKey = new HashMap<>();
    private WatchService service;

    synchronized FileSystem.Watch watch(Path dir, FilePath path, Consumer<FilePath> listener) throws IOException {
        Folder folder = byDir.get(dir);
        if (folder == null) {
            if (service == null) {
                service = FileSystems.getDefault().newWatchService();
                WatchService started = service;
                Thread thread = new Thread(() -> loop(started), "zapthemes-file-watcher");
                thread.setDaemon(true);
                thread.start();
            }
            folder = new Folder(dir, path);
            register(folder);
            byDir.put(dir, folder);
        } else if (folder.key == null) {
            register(folder); // It went away and is back.
        }
        folder.listeners.add(listener);
        Folder watched = folder;
        return new FileSystem.Watch() {
            private boolean closed;

            @Override
            public void close() {
                synchronized (LocalWatcher.this) {
                    if (!closed) {
                        closed = true;
                        stop(watched, listener);
                    }
                }
            }
        };
    }

    /** The number of folders watched (tests). */
    synchronized int watchedCount() {
        return byDir.size();
    }

    /**
     * Runs {@code operation} on {@code path} with the watches of folders at or below it paused (on
     * Windows only: elsewhere a watch doesn't hold its parents). Afterwards the folders still there
     * are watched again; the listeners of those gone hear of it.
     */
    <T> T pausing(Path path, boolean windows, Operation<T> operation) throws IOException {
        if (!windows) {
            return operation.run();
        }
        List<Folder> paused = new ArrayList<>();
        synchronized (this) {
            for (Folder folder : byDir.values()) {
                if (folder.dir.startsWith(path) && folder.key != null) {
                    byKey.remove(folder.key);
                    folder.key.cancel();
                    folder.key = null;
                    paused.add(folder);
                }
            }
        }
        try {
            return operation.run();
        } finally {
            List<Folder> gone = new ArrayList<>();
            synchronized (this) {
                for (Folder folder : paused) {
                    if (byDir.get(folder.dir) != folder) {
                        continue; // Closed meanwhile.
                    }
                    try {
                        if (Files.isDirectory(folder.dir)) {
                            register(folder);
                            continue;
                        }
                    } catch (IOException e) {
                        // Can't watch it again: as if gone.
                    }
                    gone.add(folder);
                }
            }
            for (Folder folder : gone) {
                notify(folder, folder.path);
            }
        }
    }

    private void register(Folder folder) throws IOException {
        folder.key = folder.dir.register(service, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        byKey.put(folder.key, folder);
    }

    private void stop(Folder folder, Consumer<FilePath> listener) {
        folder.listeners.remove(listener);
        if (!folder.listeners.isEmpty() || byDir.get(folder.dir) != folder) {
            return;
        }
        byDir.remove(folder.dir);
        if (folder.key != null) {
            byKey.remove(folder.key);
            folder.key.cancel();
        }
        if (byDir.isEmpty() && service != null) {
            try {
                service.close();
            } catch (IOException e) {
                // Nothing to do: it is dropped anyway.
            }
            service = null;
        }
    }

    private void loop(WatchService watching) {
        while (true) {
            WatchKey key;
            try {
                key = watching.take();
            } catch (ClosedWatchServiceException | InterruptedException e) {
                return;
            }
            Folder folder;
            synchronized (this) {
                folder = byKey.get(key);
            }
            List<FilePath> changed = new ArrayList<>();
            for (WatchEvent<?> event : key.pollEvents()) {
                if (folder != null) {
                    changed.add(event.kind() == StandardWatchEventKinds.OVERFLOW || !(event.context() instanceof Path name) ? folder.path
                            : folder.path.resolve(name.toString()));
                }
            }
            boolean valid = key.reset();
            if (folder == null) {
                continue; // Paused or closed meanwhile.
            }
            if (!valid) {
                synchronized (this) {
                    if (byKey.get(key) == folder) {
                        byKey.remove(key);
                        folder.key = null;
                    }
                }
                changed.add(folder.path); // The folder itself went away.
            }
            for (FilePath path : changed.stream().distinct().toList()) {
                notify(folder, path);
            }
        }
    }

    private static void notify(Folder folder, FilePath path) {
        for (Consumer<FilePath> listener : folder.listeners) {
            try {
                listener.accept(path);
            } catch (RuntimeException e) {
                // One listener's failure doesn't stop the others.
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            }
        }
    }
}
