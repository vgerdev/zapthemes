package com.zapthemes.core.model;

import java.util.Locale;

import org.eclipse.swt.SWT;

/**
 * A font as written in a theme: {@code { "name": "Segoe UI", "height": 9, "style": "bold|italic" }}.
 * A missing name or height falls back to the platform's system font.
 */
public record FontSpec(String name, int height, int style) {

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
