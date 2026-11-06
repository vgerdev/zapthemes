package com.zapthemes.widgets.titlebar;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/**
 * The colors a {@link TitleBar} paints with, for one state (active or not): from the theme's
 * {@code TitleBar} rule, from the app's accent when the theme allows it ({@code useAccent}), or from
 * the system title colors in the OS high-contrast mode. Keys the theme leaves out are derived from
 * the background and foreground.
 */
record TitleBarColors(RGB background, RGB foreground, RGB hover, RGB pressed, RGB closeHover, RGB closeHoverForeground,
        RGB closePressed, RGB searchBackground, RGB searchForeground, RGB searchHint, RGB searchBorder,
        RGB avatarBackground, RGB avatarForeground, RGB focus, RGB itemBorder) {

    static final RGB CLOSE_RED = new RGB(0xC4, 0x2B, 0x1C);
    static final RGB CLOSE_RED_PRESSED = new RGB(0xB2, 0x2A, 0x1B);
    static final RGB WHITE = new RGB(255, 255, 255);
    static final RGB BLACK = new RGB(0, 0, 0);

    /** The colors of {@code bar} now: its theme rule, its accent and whether its shell is active. */
    static TitleBarColors of(TitleBar bar, RGB accent, boolean active) {
        Display display = bar.getDisplay();
        if (display.getHighContrast()) {
            return highContrast(display, active);
        }
        ThemeManager manager = ThemeManager.get(display);
        ResolvedStyle style = manager.styleOf(bar);
        ThemeContext context = new ThemeContext(manager.resources());
        boolean useAccent = accent != null && style.bool("useAccent").orElse(true);

        RGB background;
        RGB foreground;
        if (useAccent) {
            RGB window = rgb(bar.getShell().getBackground());
            background = active ? accent : blend(accent, window, 0.7);
            RGB onAccent = contrasting(accent);
            foreground = active ? onAccent : blend(onAccent, background, 0.7);
        } else if (active) {
            background = color(context, style, "background", display.getSystemColor(SWT.COLOR_TITLE_BACKGROUND).getRGB());
            foreground = color(context, style, "foreground", display.getSystemColor(SWT.COLOR_TITLE_FOREGROUND).getRGB());
        } else {
            RGB activeBackground = color(context, style, "background", display.getSystemColor(SWT.COLOR_TITLE_BACKGROUND).getRGB());
            RGB activeForeground = color(context, style, "foreground", display.getSystemColor(SWT.COLOR_TITLE_FOREGROUND).getRGB());
            background = color(context, style, "inactiveBackground", activeBackground);
            foreground = color(context, style, "inactiveForeground", blend(activeForeground, background, 0.35));
        }

        // With an accent, the theme's hover and search colors were made for another background.
        RGB hover = useAccent ? blend(foreground, background, 0.16) : color(context, style, "hoverBackground", blend(foreground, background, 0.12));
        RGB pressed = useAccent ? blend(foreground, background, 0.26) : color(context, style, "pressedBackground", blend(foreground, background, 0.22));
        RGB closeHover = color(context, style, "closeHoverBackground", CLOSE_RED);
        RGB closeHoverForeground = color(context, style, "closeHoverForeground", WHITE);
        RGB closePressed = color(context, style, "closePressedBackground", CLOSE_RED_PRESSED);
        RGB searchBackground = useAccent ? blend(WHITE, background, luminance(background) > 0.6 ? 0.55 : 0.22)
                : color(context, style, "searchBackground", blend(foreground, background, 0.10));
        RGB searchForeground = useAccent ? foreground : color(context, style, "searchForeground", foreground);
        RGB searchHint = blend(searchForeground, searchBackground, 0.70);
        RGB searchBorder = useAccent ? blend(foreground, background, 0.35) : color(context, style, "searchBorderColor", blend(foreground, background, 0.30));
        RGB avatarBackground = color(context, style, "avatarBackground", blend(foreground, background, 0.30));
        RGB avatarForeground = color(context, style, "avatarForeground", contrasting(avatarBackground));
        RGB focus = useAccent ? foreground : color(context, style, "focusColor", foreground);
        RGB itemBorder = blend(foreground, background, 0.55);
        return new TitleBarColors(background, foreground, hover, pressed, closeHover, closeHoverForeground, closePressed,
                searchBackground, searchForeground, searchHint, searchBorder, avatarBackground, avatarForeground, focus, itemBorder);
    }

    private static TitleBarColors highContrast(Display display, boolean active) {
        RGB background = display.getSystemColor(active ? SWT.COLOR_TITLE_BACKGROUND : SWT.COLOR_TITLE_INACTIVE_BACKGROUND).getRGB();
        RGB foreground = display.getSystemColor(active ? SWT.COLOR_TITLE_FOREGROUND : SWT.COLOR_TITLE_INACTIVE_FOREGROUND).getRGB();
        RGB selection = display.getSystemColor(SWT.COLOR_LIST_SELECTION).getRGB();
        RGB selectionText = display.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT).getRGB();
        RGB field = display.getSystemColor(SWT.COLOR_LIST_BACKGROUND).getRGB();
        RGB fieldText = display.getSystemColor(SWT.COLOR_LIST_FOREGROUND).getRGB();
        return new TitleBarColors(background, foreground, selection, selection, selection, selectionText, selection,
                field, fieldText, fieldText, foreground, foreground, background, foreground, foreground);
    }

    private static RGB color(ThemeContext context, ResolvedStyle style, String key, RGB fallback) {
        Color color = context.color(style, key);
        return color != null ? color.getRGB() : fallback;
    }

    private static RGB rgb(Color color) {
        return color != null ? color.getRGB() : WHITE;
    }

    /** {@code t} of {@code top} over {@code bottom}. */
    static RGB blend(RGB top, RGB bottom, double t) {
        return new RGB(mix(top.red, bottom.red, t), mix(top.green, bottom.green, t), mix(top.blue, bottom.blue, t));
    }

    private static int mix(int a, int b, double t) {
        return (int) Math.round(a * t + b * (1 - t));
    }

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    static double luminance(RGB rgb) {
        return 0.2126 * channel(rgb.red) + 0.7152 * channel(rgb.green) + 0.0722 * channel(rgb.blue);
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** White or black, whichever contrasts more with {@code background}. */
    static RGB contrasting(RGB background) {
        double l = luminance(background);
        double onWhite = 1.05 / (l + 0.05);
        double onBlack = (l + 0.05) / 0.05;
        return onWhite >= onBlack ? WHITE : BLACK;
    }
}
