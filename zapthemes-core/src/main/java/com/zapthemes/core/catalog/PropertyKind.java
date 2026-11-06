package com.zapthemes.core.catalog;

import java.util.Locale;

/** The type of a theme property's value. */
public enum PropertyKind {
    COLOR, FONT, INTEGER, BOOLEAN, STRING,
    /** One of a fixed set of strings ({@link PropertySpec#choices()}). */
    CHOICE;

    /**
     * The kind a key's name implies, as the theme format types keys: {@code background},
     * {@code foreground} and keys ending in {@code Background}, {@code Foreground} or {@code Color}
     * are colors; {@code font} and keys ending in {@code Font} are fonts. Null for any other key.
     * The last part of a dotted key counts ({@code itemRenderer.selectionBackgroundColor}).
     */
    public static PropertyKind ofKey(String key) {
        String last = key.substring(key.lastIndexOf('.') + 1);
        if (last.equals("background") || last.equals("foreground") || last.endsWith("Background")
                || last.endsWith("Foreground") || last.endsWith("Color")) {
            return COLOR;
        }
        if (last.equals("font") || last.endsWith("Font")) {
            return FONT;
        }
        return null;
    }

    /** {@code "color"}, {@code "font"}, ... for messages. */
    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
