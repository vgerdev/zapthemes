package com.zapthemes.files;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A filter of names such as {@code *.log; *.txt; src/}, as zapscp's panes have: masks separated by
 * {@code ;} or {@code ,}, with {@code *} and {@code ?} as wildcards, matched against names (not
 * paths) without regard to case. A mask ending in {@code /} is for folders.
 *
 * <p>{@link #shows(FileEntry)} is what a browser shows: files that one of the file masks matches,
 * and every folder, unless there are folder masks: then only the folders one of them matches. Masks
 * of one kind leave the other kind alone: {@code *.log} shows all folders, {@code src/} all files.
 */
public final class FileMask {

    /** The mask that shows everything. */
    public static final FileMask ALL = new FileMask("");

    private record Mask(Pattern pattern, boolean foldersOnly) {
    }

    private final String text;
    private final List<Mask> files = new ArrayList<>();
    private final List<Mask> folders = new ArrayList<>();

    /** The masks {@code text} gives; blank gives {@link #ALL}'s behavior. */
    public FileMask(String text) {
        this.text = text == null ? "" : text.strip();
        for (String raw : this.text.split("[;,]")) {
            String mask = raw.strip();
            boolean foldersOnly = mask.endsWith("/");
            if (foldersOnly) {
                mask = mask.substring(0, mask.length() - 1).strip();
            }
            if (mask.isEmpty()) {
                continue;
            }
            Mask compiled = new Mask(Pattern.compile(regex(mask), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), foldersOnly);
            (foldersOnly ? folders : files).add(compiled);
        }
    }

    /** The masks as given, trimmed. */
    public String text() {
        return text;
    }

    /** Whether there is no mask, so everything is shown. */
    public boolean isEmpty() {
        return files.isEmpty() && folders.isEmpty();
    }

    /**
     * Whether a mask matches {@code name}: for a folder, any mask; for a file, one not ending in
     * {@code /} (zapscp's {@code FileMask.matches}).
     */
    public boolean matches(String name, boolean folder) {
        return any(files, name) || folder && any(folders, name);
    }

    /** Whether a browser filtered by this mask shows {@code entry}. */
    public boolean shows(FileEntry entry) {
        if (entry.isContainer()) {
            return folders.isEmpty() || any(folders, entry.name());
        }
        return files.isEmpty() || any(files, entry.name());
    }

    private static boolean any(List<Mask> masks, String name) {
        for (Mask mask : masks) {
            if (mask.pattern().matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String regex(String mask) {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (char c : mask.toCharArray()) {
            if (c == '*' || c == '?') {
                if (!literal.isEmpty()) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(c == '*' ? ".*" : ".");
            } else {
                literal.append(c);
            }
        }
        if (!literal.isEmpty()) {
            regex.append(Pattern.quote(literal.toString()));
        }
        return regex.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FileMask other && text.equals(other.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
