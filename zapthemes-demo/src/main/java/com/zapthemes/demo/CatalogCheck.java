package com.zapthemes.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.style.KeyRecorder;

/**
 * Checks that every key the demos' stylers read is described by the {@link PropertyCatalog}: reads
 * the files {@link KeyRecorder} wrote while the smoke tests ran ({@code -Dzapthemes.recordKeys}) and
 * exits non-zero, listing them, if any key is not in the catalog.
 *
 * <p>{@code CatalogCheck <dir>}
 */
public final class CatalogCheck {

    private CatalogCheck() {
    }

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args[0]);
        Set<String> reads = new TreeSet<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.filter(f -> f.getFileName().toString().startsWith("keys-")).toList()) {
                    reads.addAll(Files.readAllLines(file, StandardCharsets.UTF_8));
                }
            }
        }
        if (reads.isEmpty()) {
            System.out.println("CATALOG FAIL: no keys recorded in " + dir + " (run the smoke tests with -D" + KeyRecorder.PROPERTY + ")");
            System.exit(1);
        }
        PropertyCatalog catalog = PropertyCatalog.load();
        List<String> missing = reads.stream().filter(line -> {
            String[] parts = line.split("\t", 2);
            return parts.length == 2 && !catalog.describesRead(parts[0], parts[1]);
        }).toList();
        if (missing.isEmpty()) {
            System.out.println("CATALOG OK: the " + reads.size() + " keys the demos read are all described ("
                    + catalog.types().size() + " types)");
            return;
        }
        missing.forEach(line -> System.out.println("CATALOG FAIL: read but not described: " + line.replace('\t', '.')));
        System.exit(1);
    }
}
