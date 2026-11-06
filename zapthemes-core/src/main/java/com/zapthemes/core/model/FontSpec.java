package com.zapthemes.core.model;

import java.util.List;
import java.util.Locale;

import org.eclipse.swt.SWT;

/**
 * A font as written in a theme: {@code { "name": "Segoe UI", "height": 9, "style": "bold|italic" }}.
 * {@code "name"} may also be a list of families, the first one installed winning
 * ({@code ["Segoe UI", "Cantarell", ".AppleSystemUIFont"]}), so one theme fits every platform. No
 * name or no height falls back to the platform's system font.
 *
 * @param names the families in order of preference; empty for the system font's
 */
public record FontSpec(List<String> names, int height, int style) {

    public FontSpec {
        names = names == null ? List.of() : List.copyOf(names);
    }

    /** A font with a single family, or the system font's when {@code name} is null. */
    public FontSpec(String name, int height, int style) {
        this(name == null ? List.of() : List.of(name), height, style);
    }

    /** The first family, or {@code null} for the system font's. */
    public String name() {
        return names.isEmpty() ? null : names.getFirst();
    }

    /** Parses {@code "normal"}, {@code "bold"}, {@code "italic"} or {@code "bold|italic"}. */
    public static int parseStyle(String style) {
        if (style == null || style.isBlank()) {
            return SWT.NORMAL;
        }
        int bits = SWT.NORMAL;
        for (String part : style.toLowerCase(Locale.ROOT).split("[|,\\s]+")) {
            bits |= switch (part) {
                case "bold" -> SWT.BOLD;
                case "italic" -> SWT.ITALIC;
                case "normal", "" -> SWT.NORMAL;
                default -> throw new IllegalArgumentException("Unknown font style: " + part);
            };
        }
        return bits;
    }
}
