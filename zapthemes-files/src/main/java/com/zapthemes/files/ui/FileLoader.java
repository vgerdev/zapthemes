package com.zapthemes.files.ui;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;

/**
 * Runs filesystem calls on virtual threads and hands their results to the UI thread, for one
 * widget: nothing is delivered once it is disposed, and {@link #close} interrupts what still runs.
 */
final class FileLoader {

    private final Widget owner;
    private final Thread.Builder threads = Thread.ofVirtual().name("zapthemes-files-", 0);
    private final Set<Thread> live = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private final AtomicInteger running = new AtomicInteger();

    FileLoader(Widget owner) {
        this.owner = owner;
    }

    /**
     * Runs {@code work} in the background, then {@code done} on the UI thread with its result or its
     * failure (one of the two is null). The returned thread may be interrupted to stop the work;
     * its result (or failure) is still delivered, so callers check that it is still wanted.
     */
    <T> Thread run(Callable<T> work, BiConsumer<T, Throwable> done) {
        Display display = owner.getDisplay();
        if (closed) {
            throw new IllegalStateException("The loader is closed");
        }
        running.incrementAndGet();
        Thread thread = threads.unstarted(() -> {
            T result = null;
            Throwable failure = null;
            try {
                result = work.call();
            } catch (Throwable t) {
                failure = t;
            }
            live.remove(Thread.currentThread());
            T r = result;
            Throwable f = failure;
            if (!display.isDisposed()) {
                display.asyncExec(() -> {
                    running.decrementAndGet();
                    if (!owner.isDisposed()) {
                        done.accept(r, f);
                    }
                });
            }
        });
        live.add(thread);
        thread.start();
        return thread;
    }

    /** How many calls have not yet been delivered. */
    int pending() {
        return running.get();
    }

    void close() {
        closed = true;
        live.forEach(Thread::interrupt);
    }
}
