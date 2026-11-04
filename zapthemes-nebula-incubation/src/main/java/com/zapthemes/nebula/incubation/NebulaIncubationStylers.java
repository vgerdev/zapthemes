package com.zapthemes.nebula.incubation;

import org.eclipse.nebula.widgets.timeline.TimelineComposite;
import org.eclipse.nebula.widgets.timeline.figures.RootFigure;
import org.eclipse.nebula.widgets.timeline.jface.DefaultTimelineStyleProvider;
import org.eclipse.nebula.widgets.timeline.jface.ITimelineStyleProvider;
import org.eclipse.swt.widgets.Widget;

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
public final class NebulaIncubationStylers implements StylerProvider {

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
}
