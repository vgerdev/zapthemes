package com.zapthemes.nebula.incubation;

import org.eclipse.nebula.widgets.timeline.TimelineComposite;
import org.eclipse.nebula.widgets.timeline.figures.RootFigure;
import org.eclipse.nebula.widgets.timeline.jface.DefaultTimelineStyleProvider;
import org.eclipse.nebula.widgets.timeline.jface.ITimelineStyleProvider;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.TypeSpec;
import com.zapthemes.core.style.PropertyStyler;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StylerProvider;
import com.zapthemes.core.style.StylerRegistry;
import com.zapthemes.core.style.ThemeContext;

/**
 * Every Nebula incubation type gets a {@link PropertyStyler}, as the released widgets do: theme keys
 * call the widget's setters, and what it paints with system colors follows the theme's
 * {@code registries.systemColors}, through the fork. Timeline gets a {@link ThemedTimelineStyleProvider}.
 */
public final class NebulaIncubationStylers implements StylerProvider, CatalogProvider {

    private static final String GROUP = "Nebula incubation";
    private static final java.util.List<Class<? extends Widget>> PROXIES = NebulaIncubationProxies.ALL;

    @Override
    public void register(StylerRegistry registry) {
        PropertyStyler styler = new PropertyStyler();
        for (Class<? extends Widget> proxy : NebulaIncubationProxies.ALL) {
            registry.register(proxy.getSimpleName(), styler);
        }
        registry.register("TimelineComposite", new PropertyStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                RootFigure root = ((TimelineComposite) widget).getRootFigure();
                ITimelineStyleProvider current = root.getStyleProvider();
                // Replace only upstream's default; an application's own style provider stays.
                if (current == null || current.getClass() == DefaultTimelineStyleProvider.class
                        || current instanceof ThemedTimelineStyleProvider) {
                    root.setStyleProvider(new ThemedTimelineStyleProvider(root.getResourceManager(), widget));
                }
            }
        });
    }

    /** Default names by type, as in zap-dark: { type, { "key=name", ... } }. */
    private static final Object[] DEFAULTS = {
            "CollapsibleButtons", new String[] { "background=background", "foreground=foreground", "borderColor=border", "darkResizeColor=border", "lightResizeColor=raised", "dotDarkColor=disabled", "dotLightColor=raised", "dotMiddleColor=border", "buttonBackgroundColorTop=raised", "buttonBackgroundColorMiddle=raised", "buttonBackgroundColorBottom=raised", "selectedButtonBackgroundColorTop=accent", "selectedButtonBackgroundColorMiddle=accent", "selectedButtonBackgroundColorBottom=accent", "hoverButtonBackgroundColorTop=hover", "hoverButtonBackgroundColorMiddle=hover", "hoverButtonBackgroundColorBottom=hover", "hoverSelectedButtonBackgroundColorTop=accent", "hoverSelectedButtonBackgroundColorMiddle=accent", "hoverSelectedButtonBackgroundColorBottom=accent", "buttonTextColor=foreground" },
            "DateChooser", new String[] { "background=surface" },
            "TimelineComposite", new String[] { "background=surface", "gridColor=gridLine", "overviewSelectionBackgroundColor=accent", "cursorColor=accent", "selectedCursorColor=error" },
    };

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        java.util.Map<String, String[]> defaults = new java.util.HashMap<>();
        for (int i = 0; i < DEFAULTS.length; i += 2) {
            defaults.put((String) DEFAULTS[i], (String[]) DEFAULTS[i + 1]);
        }
        for (Class<? extends Widget> proxy : PROXIES) {
            String type = proxy.getSimpleName();
            catalog.type(TypeSpec.reflect(type, GROUP, TypeSpec.Base.bySetters(proxy), proxy)
                    .defaults(defaults.getOrDefault(type, new String[0])));
        }
    }
}
