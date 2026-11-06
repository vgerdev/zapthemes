package com.zapthemes.core.style;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records which keys stylers read, and of which type, when {@code -Dzapthemes.recordKeys=<dir>} is
 * set: at exit, one line {@code owner<TAB>key} per key read goes to {@code <dir>/keys-<pid>.txt}.
 * The owner is a control type, {@code registries.<name>} or {@code platform}. A check compares the
 * keys the demos read with the {@link com.zapthemes.core.catalog.PropertyCatalog}, so that every key
 * a styler reads is described. Off, it costs one null check per read.
 */
public final class KeyRecorder {

    public static final String PROPERTY = "zapthemes.recordKeys";

    private static final Set<String> KEYS = init();

    private KeyRecorder() {
    }

    private static Set<String> init() {
        String dir = System.getProperty(PROPERTY);
        if (dir == null || dir.isBlank()) {
            return null;
        }
        Set<String> keys = ConcurrentHashMap.newKeySet();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Path out = Path.of(dir);
                Files.createDirectories(out);
                Files.write(out.resolve("keys-" + ProcessHandle.current().pid() + ".txt"), new TreeSet<>(keys),
                        StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, "zapthemes-key-recorder"));
        return keys;
    }

    static void record(String owner, String key) {
        if (KEYS != null && owner != null) {
            KEYS.add(owner + "\t" + key);
        }
    }
}
