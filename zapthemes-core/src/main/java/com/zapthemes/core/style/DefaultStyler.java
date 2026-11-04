package com.zapthemes.core.style;

import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/** Sets {@code background}, {@code foreground} and {@code font} on controls and items. */
public class DefaultStyler implements ControlStyler {

    public static final String BACKGROUND = "background";
    public static final String FOREGROUND = "foreground";
    public static final String FONT = "font";

    @Override
    public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
        var background = context.color(style, BACKGROUND);
        var foreground = context.color(style, FOREGROUND);
        var font = context.font(style, FONT);
        switch (widget) {
            case Control control -> {
                control.setBackground(background);
                control.setForeground(foreground);
                control.setFont(font);
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
}
