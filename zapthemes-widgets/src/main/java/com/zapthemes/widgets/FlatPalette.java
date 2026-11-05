package com.zapthemes.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Control;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/**
 * The colors a canvas widget paints with: its resolved theme style, optionally in a state
 * ({@code "hover"}, {@code "pressed"}, ...), with platform fallbacks for keys the theme does not set.
 */
final class FlatPalette {

    private final Control control;
    private final ResolvedStyle style;
    private final ThemeContext context;

    private FlatPalette(Control control, ResolvedStyle style, ThemeContext context) {
        this.control = control;
        this.style = style;
        this.context = context;
    }

    static FlatPalette of(Control control, String state) {
        ThemeManager manager = ThemeManager.get(control.getDisplay());
        ResolvedStyle style = manager.styleOf(control);
        return new FlatPalette(control, state != null ? style.state(state) : style, new ThemeContext(manager.resources()));
    }

    Color background() {
        return color("background", control.getBackground());
    }

    Color foreground() {
        return color("foreground", control.getForeground());
    }

    Color border() {
        return color("borderColor", control.getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW));
    }

    Color accent() {
        return color("accentColor", control.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION));
    }

    Color color(String key, Color fallback) {
        Color color = context.color(style, key);
        return color != null ? color : fallback;
    }

    Font font(String key, Font fallback) {
        Font font = context.font(style, key);
        return font != null ? font : fallback;
    }

    int integer(String key, int fallback) {
        return style.integer(key).orElse(fallback);
    }
}
