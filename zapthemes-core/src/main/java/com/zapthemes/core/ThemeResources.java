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
 * Fonts are cached by spec for the display's lifetime (bounded by the specs the themes use) and are
 * not disposed on a theme switch, since widgets the re-theme walk cannot reach may still use them.
 */
public final class ThemeResources {

    private final Display display;
    private final Map<FontSpec, Font> fonts = new HashMap<>();

    ThemeResources(Display display) {
        this.display = display;
    }

    public Color color(RGBA rgba) {
        return new Color(display, rgba);
    }

    public Font font(FontSpec spec) {
        return fonts.computeIfAbsent(spec, key -> {
            FontData system = display.getSystemFont().getFontData()[0];
            String name = key.name() != null ? key.name() : system.getName();
            int height = key.height() > 0 ? key.height() : system.getHeight();
            return new Font(display, name, height, key.style());
        });
    }

    /** Number of fonts held; used by leak tests. */
    public int fontCount() {
        return fonts.size();
    }

    /** Called when the display is disposed. */
    void dispose() {
        fonts.values().forEach(Font::dispose);
        fonts.clear();
    }
}
