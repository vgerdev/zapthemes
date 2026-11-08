package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/**
 * The colors and fonts the ribbon paints with, read from its {@code Ribbon} theme rule, with
 * fallbacks derived from its background and foreground for keys a theme does not set. The app's
 * accent ({@link Ribbon#setAccentColor}) replaces the accent keys unless the theme sets
 * {@code useAppAccent: false}.
 */
final class RibbonColors {

    final Color background;
    final Color foreground;
    final Color tabStrip;
    final Color tabForeground;
    final Color tabHover;
    final Color selectedTab;
    final Color selectedTabForeground;
    final Color tabIndicator;
    final Color groupLabel;
    final Color separator;
    final Color border;
    final Color hover;
    final Color pressed;
    final Color checked;
    final Color checkedBorder;
    final Color disabled;
    final Color fieldBackground;
    final Color galleryBorder;
    final Color galleryBackground;
    final Color gallerySelection;
    final Color keyTipBackground;
    final Color keyTipForeground;
    final Color keyTipBorder;
    final Color fileTab;
    final Color fileTabForeground;
    final Color emphasis;
    final Color emphasisForeground;
    final Color[] contextual = new Color[4];
    final Color focus;
    /** What an item in each state paints: the theme's {@code states} block over the keys above. */
    final Look hoverLook;
    final Look pressedLook;
    final Look checkedLook;
    final Look disabledLook;
    final Look focusedLook;
    final Font tabFont;
    final Font groupLabelFont;
    final int arc;

    RibbonColors(Control ribbon, RGB appAccent) {
        ThemeManager manager = ThemeManager.get(ribbon.getDisplay());
        ResolvedStyle style = manager.styleOf(ribbon);
        ThemeContext context = new ThemeContext(manager.resources());
        boolean useAppAccent = appAccent != null && style.bool("useAppAccent").orElse(true);
        Color accentOverride = useAppAccent ? new Color(appAccent) : null;

        background = or(context.color(style, "background"), ribbon.getBackground());
        foreground = or(context.color(style, "foreground"), ribbon.getForeground());
        tabStrip = or(context.color(style, "tabStripBackground"), background);
        tabForeground = or(context.color(style, "tabForeground"), foreground);
        Color itemHover = or(context.color(style, "itemHoverBackground"), mix(background, foreground, 0.12));
        tabHover = or(context.color(style, "tabHoverBackground"), itemHover);
        selectedTab = or(context.color(style, "selectedTabBackground"), background);
        Color accent = or(context.color(style, "tabIndicatorColor"), ribbon.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION));
        tabIndicator = accentOverride != null ? accentOverride : accent;
        selectedTabForeground = accentOverride != null ? accentOverride : or(context.color(style, "selectedTabForeground"), foreground);
        groupLabel = or(context.color(style, "groupLabelForeground"), foreground);
        border = or(context.color(style, "borderColor"), mix(background, foreground, 0.25));
        separator = or(context.color(style, "separatorColor"), border);
        Color itemPressed = or(context.color(style, "itemPressedBackground"), mix(background, foreground, 0.22));
        Color itemChecked = or(context.color(style, "itemCheckedBackground"), mix(background, tabIndicator, 0.25));
        Color itemCheckedBorder = or(context.color(style, "itemCheckedBorderColor"), tabIndicator);
        Color itemDisabled = or(context.color(style, "disabledForeground"), mix(background, foreground, 0.45));
        fieldBackground = or(context.color(style, "fieldBackground"), background);
        galleryBorder = or(context.color(style, "galleryBorderColor"), separator);
        galleryBackground = or(context.color(style, "galleryBackground"), fieldBackground);
        gallerySelection = accentOverride != null ? accentOverride : or(context.color(style, "gallerySelectionColor"), tabIndicator);
        keyTipBackground = or(context.color(style, "keyTipBackground"), foreground);
        keyTipForeground = or(context.color(style, "keyTipForeground"), background);
        keyTipBorder = or(context.color(style, "keyTipBorderColor"), separator);
        fileTab = accentOverride != null ? accentOverride : or(context.color(style, "fileTabBackground"), tabIndicator);
        fileTabForeground = or(context.color(style, "fileTabForeground"), ribbon.getDisplay().getSystemColor(SWT.COLOR_WHITE));
        emphasis = accentOverride != null ? accentOverride : or(context.color(style, "emphasisBackground"), tabIndicator);
        emphasisForeground = or(context.color(style, "emphasisForeground"), fileTabForeground);
        Color[] fallbacks = { tabIndicator, tabIndicator, tabIndicator, tabIndicator };
        for (int i = 0; i < 4; i++) {
            contextual[i] = or(context.color(style, "contextualAccent" + (i + 1)), fallbacks[i]);
        }
        Color focusColor = or(context.color(style, "focusColor"), tabIndicator);
        hoverLook = look(context, style, "hover", new Look(itemHover, foreground, null));
        pressedLook = look(context, style, "pressed", new Look(itemPressed, foreground, null));
        checkedLook = look(context, style, "checked", new Look(itemChecked, foreground, itemCheckedBorder));
        disabledLook = look(context, style, "disabled", new Look(null, itemDisabled, null));
        focusedLook = look(context, style, "focused", new Look(null, null, focusColor));
        hover = hoverLook.background();
        pressed = pressedLook.background();
        checked = checkedLook.background();
        checkedBorder = accentOverride != null ? accentOverride : checkedLook.border();
        disabled = disabledLook.foreground();
        focus = focusedLook.border();
        Font font = ribbon.getFont();
        tabFont = or(context.font(style, "tabFont"), font);
        groupLabelFont = or(context.font(style, "groupLabelFont"), font);
        arc = context.size(style, "arc").orElse(context.resources().zoomedPixels(4));
    }

    /**
     * An item state's colors: {@code background}, {@code foreground} and {@code borderColor} of
     * the state's overrides; {@code null} where the state paints nothing of its own.
     */
    record Look(Color background, Color foreground, Color border) {
    }

    private static Look look(ThemeContext context, ResolvedStyle style, String state, Look fallback) {
        ResolvedStyle own = style.overridesOf(state);
        if (own == null) {
            return fallback;
        }
        return new Look(or(context.color(own, "background"), fallback.background()), or(context.color(own, "foreground"), fallback.foreground()),
                or(context.color(own, "borderColor"), fallback.border()));
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }

    /** {@code a} moved {@code amount} of the way to {@code b}. */
    static Color mix(Color a, Color b, double amount) {
        return new Color(mix(a.getRGB(), b.getRGB(), amount));
    }

    static RGB mix(RGB a, RGB b, double amount) {
        return new RGB((int) Math.round(a.red + (b.red - a.red) * amount), (int) Math.round(a.green + (b.green - a.green) * amount),
                (int) Math.round(a.blue + (b.blue - a.blue) * amount));
    }
}
