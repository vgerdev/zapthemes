package com.zapthemes.samples;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.style.PropertyStyler;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StylerProvider;
import com.zapthemes.core.style.StylerRegistry;
import com.zapthemes.core.style.ThemeContext;

/**
 * Registers the {@link Gauge} styler, found through {@code META-INF/services}: a
 * {@link PropertyStyler} calls {@code setNeedleColor(...)} for the theme key {@code needleColor} and
 * so on, restoring each original value when a theme leaves a key out, then repaints.
 */
public final class GaugeStylers implements StylerProvider {

    @Override
    public void register(StylerRegistry registry) {
        registry.register("Gauge", new PropertyStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                ((Control) widget).redraw();
            }
        });
    }
}
