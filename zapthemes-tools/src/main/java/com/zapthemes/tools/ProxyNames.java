package com.zapthemes.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads proxy lists and maps original widget class names to their proxy class names.
 *
 * <p>A proxy list has one directive per line; blank lines and {@code #} comments are ignored:
 * <pre>
 * package org.eclipse.swt.widgets       # every proxiable widget of the package
 * org.eclipse.nebula.widgets.grid.Grid  # one class
 * index com.zapthemes.swt.SwtProxies    # generate a class listing all proxies
 * </pre>
 */
public final class ProxyNames {

    private static final Map<String, String> PACKAGE_PREFIXES = Map.of(
            "org.eclipse.swt.", "com.zapthemes.swt.",
            "org.eclipse.nebula.widgets.", "com.zapthemes.nebula.");

    private static final String PACKAGE = "package ";
    private static final String INDEX = "index ";

    private ProxyNames() {
    }

    /** The original classes a proxy list selects, in list order. */
    public static List<String> readList(Path file) throws IOException {
        Set<String> classes = new LinkedHashSet<>();
        for (String line : lines(file)) {
            if (line.startsWith(PACKAGE)) {
                classes.addAll(WidgetCatalog.proxiable(line.substring(PACKAGE.length()).strip()));
            } else if (!line.startsWith(INDEX)) {
                classes.add(line);
            }
        }
        return List.copyOf(classes);
    }

    /** The {@code index} class name of a proxy list, if any. */
    public static Optional<String> indexClass(Path file) throws IOException {
        return lines(file).stream()
                .filter(line -> line.startsWith(INDEX))
                .map(line -> line.substring(INDEX.length()).strip())
                .findFirst();
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

    /** Original class name → proxy class name, for every class a proxy list selects. */
    public static Map<String, String> mapping(Path proxyList) throws IOException {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (String original : readList(proxyList)) {
            mapping.put(original, proxyOf(original));
        }
        return mapping;
    }

    private static List<String> lines(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .map(line -> {
                    int comment = line.indexOf('#');
                    return (comment >= 0 ? line.substring(0, comment) : line).strip();
                })
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
