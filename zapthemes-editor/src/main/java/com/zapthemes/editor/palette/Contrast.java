package com.zapthemes.editor.palette;

import org.eclipse.swt.graphics.RGB;

/** WCAG 2 contrast: relative luminance, contrast ratios and their levels. */
public final class Contrast {

    /** The minimum for text, WCAG AA. */
    public static final double TEXT = 4.5;
    /** The minimum for large text and for parts of controls, WCAG AA. */
    public static final double LARGE = 3.0;
    /** WCAG AAA for text. */
    public static final double ENHANCED = 7.0;

    /** A ratio's level for text. */
    public enum Level {
        /** 7:1 and up. */
        AAA,
        /** 4.5:1 and up. */
        AA,
        /** 3:1 and up: enough for large text only. */
        AA_LARGE,
        /** Below 3:1. */
        FAIL;

        public static Level of(double ratio) {
            if (ratio >= ENHANCED) {
                return AAA;
            }
            if (ratio >= TEXT) {
                return AA;
            }
            return ratio >= LARGE ? AA_LARGE : FAIL;
        }
    }

    private Contrast() {
    }

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    public static double luminance(RGB rgb) {
        return 0.2126 * Oklch.linear(rgb.red) + 0.7152 * Oklch.linear(rgb.green) + 0.0722 * Oklch.linear(rgb.blue);
    }

    /** The contrast ratio of two colors, 1 to 21, whichever is in front. */
    public static double ratio(RGB a, RGB b) {
        double x = luminance(a);
        double y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    /**
     * {@code color} with its OKLCH lightness moved away from every color of {@code against} just
     * enough to reach {@code minimum} on each, keeping hue and as much chroma as fits; the side
     * (lighter or darker) is the one where it can get there. When no lightness gets there, the one
     * with the best worst ratio.
     */
    public static RGB atLeast(RGB color, double minimum, RGB... against) {
        if (worst(color, against) >= minimum) {
            return color;
        }
        Oklch start = Oklch.of(color);
        RGB best = color;
        double bestRatio = worst(color, against);
        for (int sign : new int[] { 1, -1 }) {
            // Step outward from the color's own lightness; the first that is enough is the closest.
            for (int step = 1; step <= 100; step++) {
                double lightness = start.l() + sign * step * 0.01;
                if (lightness < 0 || lightness > 1) {
                    break;
                }
                RGB candidate = start.withL(lightness).toRgb();
                double ratio = worst(candidate, against);
                if (ratio >= minimum) {
                    if (bestRatio < minimum || Math.abs(lightness - start.l()) < Math.abs(Oklch.of(best).l() - start.l())) {
                        best = candidate;
                        bestRatio = ratio;
                    }
                    break;
                }
                if (bestRatio < minimum && ratio > bestRatio) {
                    best = candidate;
                    bestRatio = ratio;
                }
            }
        }
        return best;
    }

    /** The lowest ratio of {@code color} on the colors of {@code against}; 21 when there are none. */
    static double worst(RGB color, RGB... against) {
        double worst = 21;
        for (RGB other : against) {
            worst = Math.min(worst, ratio(color, other));
        }
        return worst;
    }
}
