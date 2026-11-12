package com.zapthemes.editor;

import java.util.Objects;

import org.eclipse.swt.graphics.RGB;

/**
 * A color the user gave a common name: a literal color, or a reference to a swatch or another name
 * (written {@code "$name"} in the theme, so it follows when the swatch changes).
 */
public sealed interface ColorChoice {

    /** A color of its own: a tint, a shade or a custom color. */
    record Literal(RGB rgb) implements ColorChoice {
        public Literal {
            Objects.requireNonNull(rgb, "rgb");
            rgb = new RGB(rgb.red, rgb.green, rgb.blue);
        }
    }

    /** The color of swatch or common name {@code name}. */
    record Ref(String name) implements ColorChoice {
        public Ref {
            Objects.requireNonNull(name, "name");
        }
    }

    static ColorChoice of(RGB rgb) {
        return new Literal(rgb);
    }

    static ColorChoice ref(String name) {
        return new Ref(name);
    }
}
