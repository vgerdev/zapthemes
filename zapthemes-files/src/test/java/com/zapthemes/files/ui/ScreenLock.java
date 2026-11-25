package com.zapthemes.files.ui;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Held by tests that read their windows back from the screen: Gradle runs the modules' test tasks
 * in parallel, and another test's window on top would be read instead. One lock file for every
 * module, in the temporary directory.
 */
final class ScreenLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;

    private ScreenLock(FileChannel channel, FileLock lock) {
        this.channel = channel;
        this.lock = lock;
    }

    static ScreenLock acquire() throws IOException {
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "zapthemes-screen-tests.lock");
        FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        return new ScreenLock(channel, channel.lock());
    }

    @Override
    public void close() throws IOException {
        lock.release();
        channel.close();
    }
}
