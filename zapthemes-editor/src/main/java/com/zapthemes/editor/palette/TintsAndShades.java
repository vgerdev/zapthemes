package com.zapthemes.editor.palette;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.graphics.RGB;

/**
 * A row of lighter and darker versions of a color, in even OKLCH lightness steps, so that the
 * steps look even. Hue is kept; chroma is reduced where the color would leave the sRGB gamut.
 */
public final class TintsAndShades {

    /** The lightness of the lightest and darkest step. */
    public static final double LIGHTEST = 0.97;
    public static final double DARKEST = 0.18;

    private TintsAndShades() {
    }

    /** {@code steps} colors (at least 2), lightest first, from {@link #LIGHTEST} to {@link #DARKEST}. */
    public static List<RGB> of(RGB color, int steps) {
        if (steps < 2) {
            throw new IllegalArgumentException("At least 2 steps: " + steps);
        }
        Oklch base = Oklch.of(color);
        List<RGB> row = new ArrayList<>();
        for (int i = 0; i < steps; i++) {
            double lightness = LIGHTEST - (LIGHTEST - DARKEST) * i / (steps - 1);
            row.add(base.withL(lightness).toRgb());
        }
        return row;
    }
}
