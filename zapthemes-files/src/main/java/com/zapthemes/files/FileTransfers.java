package com.zapthemes.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Copies and moves between any two {@link FileSystem}s through their API: folders recursively,
 * files as streams. A name already taken in the target gets Explorer's names instead
 * ({@code report - Copy.docx} in the same folder, {@code report (2).docx} elsewhere), so nothing is
 * overwritten.
 */
public final class FileTransfers {

    private static final int MAX_DEPTH = 64;

    private FileTransfers() {
    }

    /**
     * Runs {@code request}, blocking.
     *
     * @return the names created in the target folder, in the order of the sources
     */
    public static List<String> run(TransferRequest request) throws IOException {
        FileSystem source = request.source();
        FileSystem target = request.target();
        Set<String> taken = names(target, request.targetFolder());
        List<String> created = new ArrayList<>();
        for (FileEntry entry : request.sources()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException("Transfer interrupted");
            }
            FilePath from = entry.path();
            if (request.withinOneFileSystem() && request.targetFolder().startsWith(from)) {
                throw new IOException("\"" + entry.displayName() + "\" can't be put inside itself");
            }
            boolean sameFolder = request.withinOneFileSystem() && request.targetFolder().equals(from.parent());
            if (request.move() && sameFolder) {
                created.add(entry.name()); // Moving into its own folder changes nothing.
                continue;
            }
            String name = freeName(entry.name(), entry.isContainer(), taken, sameFolder);
            if (request.move() && request.withinOneFileSystem() && source.has(Capability.MOVE) && name.equals(entry.name())) {
                source.move(from, request.targetFolder());
            } else {
                copy(source, entry, target, request.targetFolder(), name, 0);
                if (request.move()) {
                    source.delete(from, true);
                }
            }
            taken.add(name.toLowerCase(Locale.ROOT));
            created.add(name);
        }
        return created;
    }

    private static Set<String> names(FileSystem fs, FilePath folder) throws IOException {
        Set<String> names = new HashSet<>();
        for (FileEntry entry : fs.list(folder)) {
            names.add(entry.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    /**
     * {@code name}, or the first free one of Explorer's alternatives: {@code "a - Copy.txt"},
     * {@code "a - Copy (2).txt"} when copying within a folder, {@code "a (2).txt"} otherwise. Names
     * are compared ignoring case.
     */
    public static String freeName(String name, boolean folder, Set<String> takenLowerCase, boolean sameFolder) {
        if (!takenLowerCase.contains(name.toLowerCase(Locale.ROOT))) {
            return name;
        }
        String extension = folder ? "" : FileTypes.extension(name);
        String stem = extension.isEmpty() ? name : name.substring(0, name.length() - extension.length() - 1);
        String dot = extension.isEmpty() ? "" : "." + name.substring(name.length() - extension.length());
        for (int n = 1; ; n++) {
            String candidate = sameFolder ? stem + " - Copy" + (n == 1 ? "" : " (" + n + ")") + dot : stem + " (" + (n + 1) + ")" + dot;
            if (!takenLowerCase.contains(candidate.toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
    }

    /** {@code base} itself, or {@code base (2)}, {@code base (3)}, ... until one is free in {@code folder}. */
    public static String newName(FileSystem fs, FilePath folder, String base, boolean isFolder) throws IOException {
        Set<String> taken = names(fs, folder);
        if (!taken.contains(base.toLowerCase(Locale.ROOT))) {
            return base;
        }
        return freeName(base, isFolder, taken, false);
    }

    private static void copy(FileSystem source, FileEntry entry, FileSystem target, FilePath folder, String name, int depth)
            throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("Folders nested too deeply at " + source.format(entry.path()));
        }
        if (entry.isContainer()) {
            FilePath created = target.createDirectory(folder, name);
            if (entry.link()) {
                return; // A link to a folder is copied as an empty folder, never followed (no loops).
            }
            for (FileEntry child : source.list(entry.path())) {
                copy(source, child, target, created, child.name(), depth + 1);
            }
            return;
        }
        try (InputStream in = source.openRead(entry.path()); OutputStream out = target.openWrite(folder.resolve(name))) {
            in.transferTo(out);
        }
    }
}
