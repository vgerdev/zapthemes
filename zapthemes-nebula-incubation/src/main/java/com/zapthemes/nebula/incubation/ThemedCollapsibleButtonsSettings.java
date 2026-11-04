package com.zapthemes.nebula.incubation;

import org.eclipse.nebula.widgets.collapsiblebuttons.DefaultSettings;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ThemeContext;

/**
 * CollapsibleButtons takes its button text color from its {@code ISettings}; this one reads the
 * widget's theme key {@code buttonTextColor}, falling back to upstream's default. Bound to its widget
 * right after construction by the CollapsibleButtons proxy.
 */
public class ThemedCollapsibleButtonsSettings extends DefaultSettings {

    private Widget widget;

    public void bind(Widget buttons) {
        this.widget = buttons;
    }

    @Override
    public Color getButtonTextColor() {
        if (widget != null && !widget.isDisposed()) {
            ThemeManager manager = ThemeManager.get(widget.getDisplay());
            Color color = new ThemeContext(manager.resources()).color(manager.styleOf(widget), "buttonTextColor");
            if (color != null) {
                return color;
            }
        }
        return super.getButtonTextColor();
    }
}
