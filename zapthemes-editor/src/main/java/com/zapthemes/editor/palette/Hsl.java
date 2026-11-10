package com.zapthemes.editor.palette;

import org.eclipse.swt.graphics.RGB;

/**
 * A color as hue (degrees), saturation and lightness (0 to 1), the model of the palette page's
 * per-swatch sliders.
 */
public record Hsl(double h, double s, double l) {

    public Hsl {
        h = ((h % 360) + 360) % 360;
        s = Math.clamp(s, 0, 1);
        l = Math.clamp(l, 0, 1);
    }

    public static Hsl of(RGB rgb) {
        double r = rgb.red / 255.0;
        double g = rgb.green / 255.0;
        double b = rgb.blue / 255.0;
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        double l = (max + min) / 2;
        double d = max - min;
        if (d == 0) {
            return new Hsl(0, 0, l);
        }
        double s = d / (1 - Math.abs(2 * l - 1));
        double h;
        if (max == r) {
            h = 60 * (((g - b) / d) % 6);
        } else if (max == g) {
            h = 60 * ((b - r) / d + 2);
        } else {
            h = 60 * ((r - g) / d + 4);
        }
        return new Hsl(h, s, l);
    }

    public RGB toRgb() {
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60) % 2 - 1));
        double m = l - c / 2;
        double r;
        double g;
        double b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return new RGB(round(r + m), round(g + m), round(b + m));
    }

    public Hsl withH(double hue) {
        return new Hsl(hue, s, l);
    }

    public Hsl withS(double saturation) {
        return new Hsl(h, saturation, l);
    }

    public Hsl withL(double lightness) {
        return new Hsl(h, s, lightness);
    }

    private static int round(double v) {
        return (int) Math.round(Math.clamp(v, 0, 1) * 255);
    }
}
