package com.zapthemes.editor.palette;

import java.util.List;

/**
 * A harmony rule: the hues, as offsets from the base color's OKLCH hue, that a generated palette's
 * accents take.
 */
public enum Harmony {
    /** The base and its opposite. */
    COMPLEMENTARY("complementary", 0, 180),
    /** The base and its neighbors, 30° either side. */
    ANALOGOUS("analogous", 0, 30, -30),
    /** Three hues 120° apart. */
    TRIADIC("triadic", 0, 120, 240),
    /** Four hues 90° apart. */
    TETRADIC("tetradic", 0, 90, 180, 270),
    /** The base hue only, at different lightness and chroma. */
    MONOCHROMATIC("monochromatic", 0);

    private final String id;
    private final List<Double> offsets;

    Harmony(String id, double... offsets) {
        this.id = id;
        this.offsets = java.util.Arrays.stream(offsets).boxed().toList();
    }

    /** The rule's name in JSON ({@code editor.generator.rule}), e.g. {@code "analogous"}. */
    public String id() {
        return id;
    }

    /** Hue offsets in degrees, the base's (0) first. */
    public List<Double> offsets() {
        return offsets;
    }

    public static Harmony byId(String id) {
        for (Harmony rule : values()) {
            if (rule.id.equals(id)) {
                return rule;
            }
        }
        throw new IllegalArgumentException("Unknown harmony rule: " + id);
    }
}
