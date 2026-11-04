package com.zapthemes.nebula.incubation;

import java.util.function.Supplier;

import org.eclipse.nebula.widgets.collapsiblebuttons.AbstractColorManager;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/**
 * CollapsibleButtons paints with an {@code IColorManager} fixed at construction (Office-style blue
 * by default). This one reads the widget's theme rule on every call, keyed by the getter name
 * without {@code get} ({@code buttonBackgroundColorTop}, {@code selectedButtonBackgroundColorMiddle},
 * {@code borderColor}, {@code dotDarkColor}, ...), falling back to upstream's colors. Bound to its
 * widget right after construction by the CollapsibleButtons proxy.
 */
public class ThemedCollapsibleButtonsColors extends AbstractColorManager {

    private Widget widget;

    public void bind(Widget buttons) {
        this.widget = buttons;
    }

    @Override public Color getBorderColor() { return themed("borderColor", super::getBorderColor); }
    @Override public Color getDarkResizeColor() { return themed("darkResizeColor", super::getDarkResizeColor); }
    @Override public Color getLightResizeColor() { return themed("lightResizeColor", super::getLightResizeColor); }
    @Override public Color getDotDarkColor() { return themed("dotDarkColor", super::getDotDarkColor); }
    @Override public Color getDotLightColor() { return themed("dotLightColor", super::getDotLightColor); }
    @Override public Color getDotMiddleColor() { return themed("dotMiddleColor", super::getDotMiddleColor); }
    @Override public Color getButtonBackgroundColorTop() { return themed("buttonBackgroundColorTop", super::getButtonBackgroundColorTop); }
    @Override public Color getButtonBackgroundColorMiddle() { return themed("buttonBackgroundColorMiddle", super::getButtonBackgroundColorMiddle); }
    @Override public Color getButtonBackgroundColorBottom() { return themed("buttonBackgroundColorBottom", super::getButtonBackgroundColorBottom); }
    @Override public Color getSelectedButtonBackgroundColorTop() { return themed("selectedButtonBackgroundColorTop", super::getSelectedButtonBackgroundColorTop); }
    @Override public Color getSelectedButtonBackgroundColorMiddle() { return themed("selectedButtonBackgroundColorMiddle", super::getSelectedButtonBackgroundColorMiddle); }
    @Override public Color getSelectedButtonBackgroundColorBottom() { return themed("selectedButtonBackgroundColorBottom", super::getSelectedButtonBackgroundColorBottom); }
    @Override public Color getHoverButtonBackgroundColorTop() { return themed("hoverButtonBackgroundColorTop", super::getHoverButtonBackgroundColorTop); }
    @Override public Color getHoverButtonBackgroundColorMiddle() { return themed("hoverButtonBackgroundColorMiddle", super::getHoverButtonBackgroundColorMiddle); }
    @Override public Color getHoverButtonBackgroundColorBottom() { return themed("hoverButtonBackgroundColorBottom", super::getHoverButtonBackgroundColorBottom); }
    @Override public Color getHoverSelectedButtonBackgroundColorTop() { return themed("hoverSelectedButtonBackgroundColorTop", super::getHoverSelectedButtonBackgroundColorTop); }
    @Override public Color getHoverSelectedButtonBackgroundColorMiddle() { return themed("hoverSelectedButtonBackgroundColorMiddle", super::getHoverSelectedButtonBackgroundColorMiddle); }
    @Override public Color getHoverSelectedButtonBackgroundColorBottom() { return themed("hoverSelectedButtonBackgroundColorBottom", super::getHoverSelectedButtonBackgroundColorBottom); }

    private Color themed(String key, Supplier<Color> fallback) {
        if (widget == null || widget.isDisposed()) {
            return fallback.get();
        }
        ThemeManager manager = ThemeManager.get(widget.getDisplay());
        ResolvedStyle style = manager.styleOf(widget);
        Color color = new ThemeContext(manager.resources()).color(style, key);
        return color != null ? color : fallback.get();
    }
}
