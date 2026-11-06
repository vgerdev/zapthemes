package com.zapthemes.samples;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.PropertySpec;
import com.zapthemes.core.catalog.TypeSpec;
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
public final class GaugeStylers implements StylerProvider, CatalogProvider {

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

    /**
     * Describes the gauge for tools such as the theme editor: a color property per color setter, plus
     * the thickness, with the common names they take by default.
     */
    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        catalog.type(TypeSpec.reflect("Gauge", "Samples", TypeSpec.Base.bySetters(Gauge.class), Gauge.class)
                .defaults("trackColor=raised", "needleColor=accent", "labelColor=foreground")
                .with(PropertySpec.size("thickness", null, "Track thickness, in pixels"))
                .described("The custom widget of CustomWidgetSample"));
    }
}
