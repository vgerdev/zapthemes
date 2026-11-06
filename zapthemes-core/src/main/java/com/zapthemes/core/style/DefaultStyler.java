package com.zapthemes.core.style;

import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeKeys;

/**
 * Sets {@code background}, {@code foreground} and {@code font} on controls and items, and
 * {@code backgroundMode} ({@code "none"}, {@code "default"}, {@code "force"}) on composites when the
 * theme sets it. A background mode the theme does not mention is left as the application set it.
 *
 * <p>A font the application set on a proxy control itself ({@code setFont}, which the proxy reports
 * through {@link com.zapthemes.core.ThemeManager#fontSetByApplication}) is kept on later theme
 * switches and zooms, scaled by the zoom, instead of the theme's: see {@link ThemeKeys#APP_FONT}.
 * A font like the theme's counts as the theme's.
 */
public class DefaultStyler implements ControlStyler {

    public static final String BACKGROUND = "background";
    public static final String FOREGROUND = "foreground";
    public static final String FONT = "font";
    public static final String BACKGROUND_MODE = "backgroundMode";

    @Override
    public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
        // Read only what is set, so that the keys read are the keys a type has (KeyRecorder).
        switch (widget) {
            case Control control -> {
                control.setBackground(context.color(style, BACKGROUND));
                control.setForeground(context.color(style, FOREGROUND));
                // Without a theme font a control still follows the zoom; items inherit their parent's.
                Font font = context.font(style, FONT);
                applyFont(control, font != null ? font : context.defaultFont(), context);
                if (control instanceof Composite composite) {
                    style.string(BACKGROUND_MODE).ifPresent(mode -> composite.setBackgroundMode(backgroundMode(mode)));
                }
            }
            case TableItem item -> {
                item.setBackground(context.color(style, BACKGROUND));
                item.setForeground(context.color(style, FOREGROUND));
                item.setFont(context.font(style, FONT));
            }
            case TreeItem item -> {
                item.setBackground(context.color(style, BACKGROUND));
                item.setForeground(context.color(style, FOREGROUND));
                item.setFont(context.font(style, FONT));
            }
            case CTabItem item -> {
                item.setForeground(context.color(style, FOREGROUND));
                item.setFont(context.font(style, FONT));
            }
            default -> {
                // Other items (TableColumn, ToolItem, MenuItem, ...) are styled through their parent.
            }
        }
    }

    /**
     * A font the application set itself: its family and style, its height at 100 % (a fraction, so
     * zooming never drifts) and the font itself, used again at 100 % while it is not disposed.
     */
    public record AppFont(String name, int style, double baseHeight, Font original) {

        Font at(ThemeContext context) {
            double zoom = context.resources().zoom();
            if (zoom == 1 && !original.isDisposed() && original.getFontData()[0].getHeight() == Math.round(baseHeight)) {
                return original;
            }
            return context.resources().font(name, style, baseHeight);
        }
    }

    /** The control whose font this styler is setting right now, so its proxy does not take it as the application's. */
    private static Control theming;

    /** {@code control}'s font: the application's if it set one ({@link ThemeKeys#APP_FONT}), else {@code themed}. */
    private static void applyFont(Control control, Font themed, ThemeContext context) {
        AppFont app = control.getData(ThemeKeys.APP_FONT) instanceof AppFont a && !a.original().isDisposed() ? a : null;
        theming = control;
        try {
            control.setFont(app != null ? app.at(context) : themed);
        } finally {
            theming = null;
        }
    }

    /**
     * Called by a proxy's {@code setFont} before SWT's: remembers {@code font} as the font the
     * application chose for {@code control}, taken at the current zoom. {@code null}, or a font like
     * {@code themeFont} (the one the theme gives the control now), makes it follow the theme again.
     * The styler's own {@code setFont} calls are ignored.
     */
    public static void fontSetByApplication(Control control, Font font, Font themeFont, ThemeContext context) {
        if (theming == control) {
            return;
        }
        if (font == null || font.isDisposed() || (themeFont != null && sameFont(font, themeFont))) {
            control.setData(ThemeKeys.APP_FONT, null);
            return;
        }
        double base = context.resources().baseHeight(font).orElse(font.getFontData()[0].getHeight() / context.resources().zoom());
        control.setData(ThemeKeys.APP_FONT, appFont(font, base));
    }

    /**
     * Makes {@code control} follow the theme's font again, dropping a font the application set,
     * e.g. when it gets a style class ({@link com.zapthemes.core.ThemeManager#setThemeClass}).
     */
    public static void followThemeFont(Control control) {
        control.setData(ThemeKeys.APP_FONT, null);
    }

    private static AppFont appFont(Font font, double baseHeight) {
        FontData data = font.getFontData()[0];
        return new AppFont(data.getName(), data.getStyle(), baseHeight, font);
    }

    private static boolean sameFont(Font a, Font b) {
        FontData x = a.getFontData()[0];
        FontData y = b.getFontData()[0];
        return x.getName().equalsIgnoreCase(y.getName()) && x.getHeight() == y.getHeight() && x.getStyle() == y.getStyle();
    }

    private static int backgroundMode(String mode) {
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "none" -> SWT.INHERIT_NONE;
            case "default" -> SWT.INHERIT_DEFAULT;
            case "force" -> SWT.INHERIT_FORCE;
            default -> throw new IllegalArgumentException("backgroundMode must be none, default or force: " + mode);
        };
    }
}
