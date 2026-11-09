package com.zapthemes.editor.palette;

import org.eclipse.swt.graphics.RGB;

/**
 * A color in OKLCH, the polar form of Björn Ottosson's OKLab: perceived lightness {@code l}
 * (0 to 1), chroma {@code c} (0 for grays, up to about 0.37) and hue {@code h} in degrees. Equal
 * steps in {@code l} look equal, which is why tints, shades and the generator work in it.
 */
public record Oklch(double l, double c, double h) {

    private static final double GAMUT_EPSILON = 1e-4;

    public Oklch {
        h = ((h % 360) + 360) % 360;
    }

    public static Oklch of(RGB rgb) {
        double[] lab = toOklab(linear(rgb.red), linear(rgb.green), linear(rgb.blue));
        double c = Math.hypot(lab[1], lab[2]);
        double h = c < 1e-6 ? 0 : Math.toDegrees(Math.atan2(lab[2], lab[1]));
        return new Oklch(lab[0], c, h);
    }

    public Oklch withL(double lightness) {
        return new Oklch(clamp01(lightness), c, h);
    }

    public Oklch withC(double chroma) {
        return new Oklch(l, Math.max(0, chroma), h);
    }

    public Oklch withH(double hue) {
        return new Oklch(l, c, hue);
    }

    /**
     * The nearest sRGB color: chroma is reduced, keeping lightness and hue, until the color fits
     * the sRGB gamut.
     */
    public RGB toRgb() {
        double[] linear = toLinear(l, c, h);
        if (inGamut(linear)) {
            return rgb(linear);
        }
        double low = 0;
        double high = c;
        for (int i = 0; i < 30; i++) {
            double mid = (low + high) / 2;
            if (inGamut(toLinear(l, mid, h))) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return rgb(toLinear(l, low, h));
    }

    /** Whether this color is in the sRGB gamut as it is, without reducing chroma. */
    public boolean inGamut() {
        return inGamut(toLinear(l, c, h));
    }

    /** OKLab coordinates {@code [L, a, b]} of {@code rgb}. */
    static double[] oklab(RGB rgb) {
        return toOklab(linear(rgb.red), linear(rgb.green), linear(rgb.blue));
    }

    /** The color at OKLab {@code [L, a, b]}, brought into the gamut. */
    static RGB fromOklab(double[] lab) {
        double c = Math.hypot(lab[1], lab[2]);
        return new Oklch(lab[0], c, Math.toDegrees(Math.atan2(lab[2], lab[1]))).toRgb();
    }

    /** Euclidean distance in OKLab: about 0.02 is a just-noticeable difference. */
    public static double distance(RGB a, RGB b) {
        double[] x = oklab(a);
        double[] y = oklab(b);
        return Math.sqrt(square(x[0] - y[0]) + square(x[1] - y[1]) + square(x[2] - y[2]));
    }

    private static double[] toLinear(double l, double c, double h) {
        double a = c * Math.cos(Math.toRadians(h));
        double b = c * Math.sin(Math.toRadians(h));
        double l1 = cube(l + 0.3963377774 * a + 0.2158037573 * b);
        double m1 = cube(l - 0.1055613458 * a - 0.0638541728 * b);
        double s1 = cube(l - 0.0894841775 * a - 1.2914855480 * b);
        return new double[] {
                4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1,
                -1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1,
                -0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1 };
    }

    private static double[] toOklab(double r, double g, double b) {
        double l1 = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m1 = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s1 = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[] {
                0.2104542553 * l1 + 0.7936177850 * m1 - 0.0040720468 * s1,
                1.9779984951 * l1 - 2.4285922050 * m1 + 0.4505937099 * s1,
                0.0259040371 * l1 + 0.7827717662 * m1 - 0.8086757660 * s1 };
    }

    private static boolean inGamut(double[] linear) {
        for (double v : linear) {
            if (v < -GAMUT_EPSILON || v > 1 + GAMUT_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static RGB rgb(double[] linear) {
        return new RGB(channel(linear[0]), channel(linear[1]), channel(linear[2]));
    }

    /** sRGB transfer: a 0..255 channel to linear light. */
    static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int channel(double linear) {
        double v = clamp01(linear);
        double c = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(clamp01(c) * 255);
    }

    private static double clamp01(double v) {
        return Math.clamp(v, 0, 1);
    }

    private static double cube(double v) {
        return v * v * v;
    }

    private static double square(double v) {
        return v * v;
    }
}
