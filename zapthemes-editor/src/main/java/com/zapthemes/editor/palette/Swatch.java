package com.zapthemes.editor.palette;

import java.util.Objects;

import org.eclipse.swt.graphics.RGB;

/** A named raw color of a palette; a locked swatch survives {@link PaletteGenerator#regenerate}. */
public record Swatch(String name, RGB color, boolean locked) {

    public Swatch {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(color, "color");
        color = new RGB(color.red, color.green, color.blue);
    }

    public Swatch withColor(RGB newColor) {
        return new Swatch(name, newColor, locked);
    }

    public Swatch withLocked(boolean lock) {
        return new Swatch(name, color, lock);
    }

    public Swatch withName(String newName) {
        return new Swatch(newName, color, locked);
    }

    /** {@code #RRGGBB}. */
    public String hex() {
        return hex(color);
    }

    static String hex(RGB rgb) {
        return String.format("#%02X%02X%02X", rgb.red, rgb.green, rgb.blue);
    }

    static RGB parse(String hex) {
        String digits = hex.startsWith("#") ? hex.substring(1) : hex;
        if (digits.length() != 6) {
            throw new IllegalArgumentException("Not a #RRGGBB color: " + hex);
        }
        int value = Integer.parseInt(digits, 16);
        return new RGB(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF);
    }
}
