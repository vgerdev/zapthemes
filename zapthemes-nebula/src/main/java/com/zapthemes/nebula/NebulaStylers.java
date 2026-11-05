package com.zapthemes.nebula;

import org.eclipse.nebula.widgets.pgroup.PGroup;
import org.eclipse.nebula.widgets.pgroup.RectangleGroupStrategy;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.style.PropertyStyler;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StylerProvider;
import com.zapthemes.core.style.StylerRegistry;
import com.zapthemes.core.style.ThemeContext;

/**
 * Nebula widgets expose their look through setters ({@code setLineColor}, {@code setPickerTodayColor},
 * ...), so every Nebula type gets a {@link PropertyStyler}: a theme key {@code pickerTodayColor} calls
 * {@code setPickerTodayColor}. What Nebula paints with system colors follows the theme's
 * {@code registries.systemColors}, through the fork.
 *
 * <p>PGroup also takes {@code titleBackground} / {@code titleBackgroundGradient}: its title gradient
 * is a color array, which its strategy otherwise copies from the system colors once at creation.
 */
public final class NebulaStylers implements StylerProvider {

    @Override
    public void register(StylerRegistry registry) {
        PropertyStyler styler = new PropertyStyler();
        for (Class<? extends Widget> proxy : NebulaProxies.ALL) {
            registry.register(proxy.getSimpleName(), styler);
        }
        registry.register("PGroup", new PropertyStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                // The title gradient is a Color[] (no single-value setter), and the strategy copies the
                // system title colors only once; set it when the theme defines it.
                Color top = context.color(style, "titleBackgroundGradient");
                Color bottom = context.color(style, "titleBackground");
                if ((top != null || bottom != null) && ((PGroup) widget).getStrategy() instanceof RectangleGroupStrategy strategy) {
                    strategy.setBackground(new Color[] { top != null ? top : bottom, bottom != null ? bottom : top },
                            new int[] { 100 }, true);
                    ((PGroup) widget).redraw();
                }
            }
        });
    }
}
