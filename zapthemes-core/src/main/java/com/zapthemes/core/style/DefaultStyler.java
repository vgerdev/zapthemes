package com.zapthemes.core.style;

import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/**
 * Sets {@code background}, {@code foreground} and {@code font} on controls and items, and
 * {@code backgroundMode} ({@code "none"}, {@code "default"}, {@code "force"}) on composites when the
 * theme sets it. A background mode the theme does not mention is left as the application set it.
 */
public class DefaultStyler implements ControlStyler {

    public static final String BACKGROUND = "background";
    public static final String FOREGROUND = "foreground";
    public static final String FONT = "font";
    public static final String BACKGROUND_MODE = "backgroundMode";

    @Override
    public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
        var background = context.color(style, BACKGROUND);
        var foreground = context.color(style, FOREGROUND);
        var font = context.font(style, FONT);
        switch (widget) {
            case Control control -> {
                control.setBackground(background);
                control.setForeground(foreground);
                // Without a theme font a control still follows the zoom; items inherit their parent's.
                control.setFont(font != null ? font : context.defaultFont());
                if (control instanceof Composite composite) {
                    style.string(BACKGROUND_MODE).ifPresent(mode -> composite.setBackgroundMode(backgroundMode(mode)));
                }
            }
            case TableItem item -> {
                item.setBackground(background);
                item.setForeground(foreground);
                item.setFont(font);
            }
            case TreeItem item -> {
                item.setBackground(background);
                item.setForeground(foreground);
                item.setFont(font);
            }
            case CTabItem item -> {
                item.setForeground(foreground);
                item.setFont(font);
            }
            default -> {
                // Other items (TableColumn, ToolItem, MenuItem, ...) are styled through their parent.
            }
        }
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
