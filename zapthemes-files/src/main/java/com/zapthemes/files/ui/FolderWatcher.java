package com.zapthemes.files.ui;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.eclipse.swt.widgets.Display;

import com.zapthemes.files.Capability;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileSystem;

/**
 * Watches the folder a {@link FileBrowser} shows ({@link FileSystem#watch}) and lists it again
 * when it changes: a moment after the last change of a burst (a copy of many files is one
 * refresh), and at least every {@link #MAX_WAIT} ms while changes keep coming. Not while a listing
 * loads or a rename field is open; then right after.
 *
 * <p>The watch starts in the background (a remote filesystem may take a moment), is replaced when
 * the browser shows another folder, and closed with the browser.
 */
final class FolderWatcher {

    /** Quiet time after a change before listing again. */
    static final int DELAY = 300;
    /** Longest wait while changes keep coming (a file being written). */
    static final int MAX_WAIT = 2000;

    private final FileBrowser browser;
    private final Display display;
    private final AtomicInteger generation = new AtomicInteger();
    private final AtomicBoolean posted = new AtomicBoolean();
    private boolean enabled = true;
    private FileSystem fileSystem;
    private FilePath folder;
    private FileSystem.Watch watch;
    private boolean scheduled;
    private long firstChange;
    private long lastChange;
    private int refreshes;

    FolderWatcher(FileBrowser browser) {
        this.browser = browser;
        this.display = browser.getDisplay();
    }

    /** Whether folders are watched; on by default. Turned off, the watch stops. */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            stop();
        } else if (browser.getFileSystem() != null && browser.getCurrentPath() != null) {
            watch(browser.getFileSystem(), browser.getCurrentPath());
        }
    }

    boolean isEnabled() {
        return enabled;
    }

    /** Watches {@code path} of {@code fs} instead of what was watched; nothing if it already is or it can't be. */
    void watch(FileSystem fs, FilePath path) {
        if (fs == fileSystem && Objects.equals(path, folder)) {
            return;
        }
        stop();
        if (!enabled || !fs.has(Capability.WATCH)) {
            return;
        }
        fileSystem = fs;
        folder = path;
        int gen = generation.get();
        Consumer<FilePath> listener = changed -> {
            // On the filesystem's thread: one post at a time to the UI thread.
            if (gen == generation.get() && posted.compareAndSet(false, true) && !display.isDisposed()) {
                display.asyncExec(() -> {
                    posted.set(false);
                    changed(gen);
                });
            }
        };
        Thread.ofVirtual().name("zapthemes-files-watch").start(() -> {
            FileSystem.Watch started;
            try {
                started = fs.watch(path, listener);
            } catch (Exception e) {
                return; // Can't be watched (gone already, refused): F5 still lists it again.
            }
            boolean keep;
            synchronized (this) {
                keep = gen == generation.get();
                if (keep) {
                    watch = started;
                }
            }
            if (!keep) {
                started.close();
            }
        });
    }

    /** Stops watching; changes already heard of are dropped. */
    void stop() {
        FileSystem.Watch closing;
        synchronized (this) {
            generation.incrementAndGet();
            closing = watch;
            watch = null;
        }
        fileSystem = null;
        folder = null;
        scheduled = false;
        if (closing != null) {
            closing.close();
        }
    }

    /** The folder watched, or null (tests). */
    FilePath folder() {
        return folder;
    }

    /** Whether the watch has started (tests). */
    synchronized boolean started() {
        return watch != null;
    }

    /** How many times a change listed the folder again (tests). */
    int refreshes() {
        return refreshes;
    }

    private void changed(int gen) {
        if (gen != generation.get() || browser.isDisposed()) {
            return;
        }
        long now = System.currentTimeMillis();
        lastChange = now;
        if (!scheduled) {
            scheduled = true;
            firstChange = now;
            display.timerExec(DELAY, () -> fire(gen));
        }
    }

    private void fire(int gen) {
        if (gen != generation.get() || browser.isDisposed() || !scheduled) {
            return;
        }
        long now = System.currentTimeMillis();
        long quiet = lastChange + DELAY - now;
        if (quiet > 10 && now - firstChange < MAX_WAIT) {
            display.timerExec((int) Math.min(quiet, MAX_WAIT - (now - firstChange)), () -> fire(gen));
            return;
        }
        if (browser.isBusy()) {
            display.timerExec(DELAY, () -> fire(gen));
            return;
        }
        scheduled = false;
        refreshes++;
        browser.relistChanged(folder);
    }
}
