package com.zapthemes.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads proxy lists and maps original widget class names to their proxy class names. */
public final class ProxyNames {

    private static final Map<String, String> PACKAGE_PREFIXES = Map.of(
            "org.eclipse.swt.", "com.zapthemes.swt.",
            "org.eclipse.nebula.widgets.", "com.zapthemes.nebula.");

    private ProxyNames() {
    }

    /** One fully qualified class name per line; blank lines and {@code #} comments are ignored. */
    public static List<String> readList(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
    }

    /** {@code org.eclipse.swt.widgets.Button} → {@code com.zapthemes.swt.widgets.Button}. */
    public static String proxyOf(String originalClassName) {
        for (var prefix : PACKAGE_PREFIXES.entrySet()) {
            if (originalClassName.startsWith(prefix.getKey())) {
                return prefix.getValue() + originalClassName.substring(prefix.getKey().length());
            }
        }
        throw new IllegalArgumentException("No proxy package for " + originalClassName);
    }

    /** Original class name → proxy class name, for every entry of a proxy list. */
    public static Map<String, String> mapping(Path proxyList) throws IOException {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String original : readList(proxyList)) {
            mapping.put(original, proxyOf(original));
        }
        return mapping;
    }
}
