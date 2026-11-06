package com.zapthemes.widgets;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.PropertySpec;
import com.zapthemes.core.catalog.TypeSpec;
import com.zapthemes.core.style.DefaultStyler;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StylerProvider;
import com.zapthemes.core.style.StylerRegistry;
import com.zapthemes.core.style.ThemeContext;

/**
 * Registers the canvas widgets' stylers: the default colors and font, then a repaint, since the
 * widgets read the rest of their style (borders, states, accents) while painting. Describes what
 * they read for the {@link PropertyCatalog}.
 */
public final class FlatStylers implements StylerProvider, CatalogProvider {

    private static final String GROUP = "Flat widgets";

    @Override
    public void register(StylerRegistry registry) {
        DefaultStyler repaint = new DefaultStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                ((Control) widget).redraw();
            }
        };
        registry.register("FlatButton", repaint);
        registry.register("FlatGroup", repaint);
        registry.register("FlatProgressBar", repaint);
    }

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        PropertySpec border = PropertySpec.color("borderColor", "disabled", "Outline");
        PropertySpec accent = PropertySpec.color("accentColor", "accent", "Accent (focus, check marks, default button)");
        PropertySpec arc = PropertySpec.integer("arc", 6, "Corner radius, in pixels");
        catalog.type(TypeSpec.of("FlatButton", GROUP, TypeSpec.Base.COMPOSITE).defaults("background=raised")
                .with(border, accent, arc,
                        PropertySpec.color("boxBackground", "surface", "Check box and radio button box"),
                        PropertySpec.color("checkColor", null, "Check mark; the accent color when not set"))
                .withVariants("PUSH", "CHECK", "RADIO", "TOGGLE")
                .withStates("hover", "pressed", "focused", "disabled", "selected")
                .described("A canvas button that paints every state from the theme"));
        catalog.type(TypeSpec.of("FlatGroup", GROUP, TypeSpec.Base.COMPOSITE)
                .with(border.withDefaultName("raised"), accent, arc,
                        PropertySpec.color("titleForeground", "foreground", "Title text"),
                        PropertySpec.font("titleFont", "strong", "Title font"))
                .withStates("disabled").described("A canvas group box"));
        catalog.type(TypeSpec.of("FlatProgressBar", GROUP, TypeSpec.Base.COMPOSITE).defaults("background=raised")
                .with(border.withDefaultName(null), accent, arc,
                        PropertySpec.color("barColor", "accent", "The bar"),
                        PropertySpec.color("errorColor", "error", "The bar in the error state"),
                        PropertySpec.color("pausedColor", "warning", "The bar in the paused state"))
                .withStates("disabled").described("A canvas progress bar"));
    }
}
