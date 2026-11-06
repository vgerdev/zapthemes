package com.zapthemes.core;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.model.FontSpec;

/**
 * Colors and fonts of the themes used on one display.
 *
 * <p>Every {@link #color} call returns a new {@link Color}: colors hold no OS resource (SWT 3.115+),
 * and some widget libraries dispose the colors they were given, which must not break other widgets.
 * Fonts are cached by spec and zoomed height for the display's lifetime (bounded by the specs the
 * themes use and the zoom levels) and are not disposed on a theme switch or zoom, since widgets the
 * re-theme walk cannot reach may still use them.
 *
 * <p>Every font is scaled by the display's {@linkplain #zoom() zoom} (see
 * {@link ThemeManager#setZoom(double)}).
 */
public final class ThemeResources {

    private final Display display;
    private final Map<FontKey, Font> fonts = new HashMap<>();
    private final Map<String, Boolean> installed = new HashMap<>();
    private double zoom = 1;

    /** A spec at one effective height: zoom levels that round to the same height share a font. */
    private record FontKey(FontSpec spec, int height) {
    }

    ThemeResources(Display display) {
        this.display = display;
    }

    public Color color(RGBA rgba) {
        return new Color(display, rgba);
    }

    /** The font of {@code spec} at the current zoom. */
    public Font font(FontSpec spec) {
        int height = zoomed(spec.height() > 0 ? spec.height() : systemFontData().getHeight());
        return fonts.computeIfAbsent(new FontKey(spec, height), key -> {
            String name = family(spec, systemFontData().getName());
            return new Font(display, name, key.height(), spec.style());
        });
    }

    /**
     * The first of {@code spec}'s families that is installed. A single family is used as given (the
     * platform substitutes a missing one, as it always has); when none of several is installed, the
     * first.
     */
    private String family(FontSpec spec, String system) {
        if (spec.names().isEmpty()) {
            return system;
        }
        if (spec.names().size() > 1) {
            for (String candidate : spec.names()) {
                if (installed.computeIfAbsent(candidate,
                        n -> display.getFontList(n, true).length > 0 || display.getFontList(n, false).length > 0)) {
                    return candidate;
                }
            }
        }
        return spec.name();
    }

    /** The current zoom factor, {@code 1.0} for 100 %. */
    public double zoom() {
        return zoom;
    }

    /** {@code height} (in points) at the current zoom, rounded, at least 1. */
    public int zoomed(int height) {
        return zoom == 1 ? height : Math.max(1, (int) Math.round(height * zoom));
    }

    /** {@code pixels} at the current zoom, rounded; 0 stays 0. For sizes such as corner radii. */
    public int zoomedPixels(int pixels) {
        return zoom == 1 ? pixels : (int) Math.round(pixels * zoom);
    }

    /**
     * A font of {@code name} and {@code style} whose height at 100 % is {@code baseHeight}, at the
     * current zoom; cached like theme fonts. Used for fonts an application set itself.
     */
    public Font font(String name, int style, double baseHeight) {
        int height = Math.max(1, (int) Math.round(baseHeight * zoom));
        FontSpec spec = new FontSpec(name, 0, style);
        return fonts.computeIfAbsent(new FontKey(spec, height),
                key -> new Font(display, family(spec, systemFontData().getName()), key.height(), style));
    }

    void setZoom(double zoom) {
        this.zoom = zoom;
    }

    /** Number of fonts held; used by leak tests. */
    public int fontCount() {
        return fonts.size();
    }

    private FontData systemFontData() {
        return display.getSystemFont().getFontData()[0];
    }

    /** Called when the display is disposed. */
    void dispose() {
        fonts.values().forEach(Font::dispose);
        fonts.clear();
    }
}
