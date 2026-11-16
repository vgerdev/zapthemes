package com.zapthemes.files.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/**
 * The colors the browser's bars paint with: the theme rule of the bar's type, and for keys the theme
 * leaves out, colors derived from the bar's background and foreground (so a theme that knows nothing
 * of the file browser still gives a consistent look). In the OS high-contrast mode, system colors.
 */
record BarColors(Color background, Color foreground, Color hover, Color pressed, Color field, Color fieldHover, Color border,
        Color focus, Color disabled, Color muted, Color error) {

    static BarColors of(Control control) {
        return of(control, true);
    }

    /** The colors of {@code control}; without {@code field}, the address bar keys are not read. */
    static BarColors of(Control control, boolean withField) {
        if (control.getDisplay().getHighContrast()) {
            var d = control.getDisplay();
            Color bg = d.getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
            Color fg = d.getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
            Color sel = d.getSystemColor(SWT.COLOR_LIST_SELECTION);
            return new BarColors(bg, fg, sel, sel, d.getSystemColor(SWT.COLOR_LIST_BACKGROUND), sel, fg, sel, d.getSystemColor(SWT.COLOR_GRAY),
                    fg, fg);
        }
        ThemeManager manager = ThemeManager.get(control.getDisplay());
        ResolvedStyle style = manager.styleOf(control);
        ThemeContext context = new ThemeContext(manager.resources());
        Color background = control.getBackground();
        Color foreground = control.getForeground();
        RGB bg = background.getRGB();
        RGB fg = foreground.getRGB();
        Color hover = color(context, style, "hoverBackground", blend(fg, bg, 0.10));
        Color pressed = color(context, style, "pressedBackground", blend(fg, bg, 0.18));
        Color field = withField ? color(context, style, "fieldBackground", blend(fg, bg, luminance(bg) > 0.5 ? 0.0 : 0.06)) : background;
        Color fieldHover = withField ? color(context, style, "fieldHoverBackground", new Color(blend(fg, field.getRGB(), 0.06))) : hover;
        Color border = color(context, style, "borderColor", blend(fg, bg, 0.22));
        Color focus = withField ? color(context, style, "focusColor", control.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION).getRGB())
                : border;
        Color disabled = color(context, style, "disabledForeground", blend(fg, bg, 0.40));
        Color muted = color(context, style, "mutedForeground", blend(fg, bg, 0.70));
        Color error = color(context, style, "errorForeground", luminance(bg) > 0.5 ? new RGB(0xC4, 0x2B, 0x1C) : new RGB(0xFF, 0x99, 0xA4));
        return new BarColors(background, foreground, hover, pressed, field, fieldHover, border, focus, disabled, muted, error);
    }

    private static Color color(ThemeContext context, ResolvedStyle style, String key, RGB fallback) {
        Color color = context.color(style, key);
        return color != null ? color : new Color(fallback);
    }

    private static Color color(ThemeContext context, ResolvedStyle style, String key, Color fallback) {
        Color color = context.color(style, key);
        return color != null ? color : fallback;
    }

    /** {@code amount} of {@code a} over {@code b}. */
    static RGB blend(RGB a, RGB b, double amount) {
        return new RGB((int) Math.round(a.red * amount + b.red * (1 - amount)), (int) Math.round(a.green * amount + b.green * (1 - amount)),
                (int) Math.round(a.blue * amount + b.blue * (1 - amount)));
    }

    static double luminance(RGB c) {
        return (0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue) / 255;
    }
}
