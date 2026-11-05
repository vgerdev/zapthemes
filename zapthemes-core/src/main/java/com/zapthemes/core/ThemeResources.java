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
 * Colors and fonts created for one theme on one display. The {@link ThemeManager} creates a new
 * instance per theme and disposes the old one only after every control has been re-themed.
 */
public final class ThemeResources {

    private final Display display;
    private final Map<RGBA, Color> colors = new HashMap<>();
    private final Map<FontSpec, Font> fonts = new HashMap<>();

    ThemeResources(Display display) {
        this.display = display;
    }

    public Color color(RGBA rgba) {
        return colors.computeIfAbsent(rgba, key -> new Color(display, key));
    }

    public Font font(FontSpec spec) {
        return fonts.computeIfAbsent(spec, key -> {
            FontData system = display.getSystemFont().getFontData()[0];
            String name = key.name() != null ? key.name() : system.getName();
            int height = key.height() > 0 ? key.height() : system.getHeight();
            return new Font(display, name, height, key.style());
        });
    }

    /** Number of fonts currently held; used by leak tests. */
    public int fontCount() {
        return fonts.size();
    }

    void dispose() {
        fonts.values().forEach(Font::dispose);
        fonts.clear();
        // Since SWT 3.115 colors hold no OS handle; disposing is harmless and future-proof.
        colors.values().forEach(Color::dispose);
        colors.clear();
    }
}
