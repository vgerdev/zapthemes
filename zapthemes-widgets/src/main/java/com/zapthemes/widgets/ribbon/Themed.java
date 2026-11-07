package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Control;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/** Reads one key of a control's theme rule, with a fallback. */
final class Themed {

    private final ResolvedStyle style;
    private final ThemeContext context;

    private Themed(Control control, String state) {
        ThemeManager manager = ThemeManager.get(control.getDisplay());
        ResolvedStyle resolved = manager.styleOf(control);
        style = state == null ? resolved : resolved.state(state);
        context = new ThemeContext(manager.resources());
    }

    static Themed of(Control control) {
        return new Themed(control, null);
    }

    /** The rule in {@code state} ({@code "warning"}, ...): keys the state does not set come from the rule. */
    static Themed of(Control control, String state) {
        return new Themed(control, state);
    }

    Color color(String key, Color fallback) {
        Color color = context.color(style, key);
        return color != null ? color : fallback;
    }

    Font font(String key, Font fallback) {
        Font font = context.font(style, key);
        return font != null ? font : fallback;
    }

    boolean bool(String key, boolean fallback) {
        return style.bool(key).orElse(fallback);
    }

    /** {@code at100} pixels at the display's zoom ({@link ThemeManager#getZoom}). */
    static int px(Control control, int at100) {
        return (int) Math.round(at100 * ThemeManager.get(control.getDisplay()).getZoom());
    }

    int integer(String key, int fallback) {
        return style.integer(key).orElse(fallback);
    }
}
