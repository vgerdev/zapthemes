package com.zapthemes.nebula;

import org.eclipse.nebula.widgets.pgroup.PGroup;
import org.eclipse.nebula.widgets.pgroup.RectangleGroupStrategy;
import org.eclipse.swt.graphics.Color;
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
 * Nebula widgets expose their look through setters ({@code setLineColor}, {@code setPickerTodayColor},
 * ...), so every Nebula type gets a {@link PropertyStyler}: a theme key {@code pickerTodayColor} calls
 * {@code setPickerTodayColor}. What Nebula paints with system colors follows the theme's
 * {@code registries.systemColors}, through the fork.
 *
 * <p>PGroup also takes {@code titleBackground} / {@code titleBackgroundGradient}: its title gradient
 * is a color array, which its strategy otherwise copies from the system colors once at creation.
 */
public final class NebulaStylers implements StylerProvider, CatalogProvider {

    private static final String GROUP = "Nebula";
    private static final java.util.List<Class<? extends Widget>> PROXIES = NebulaProxies.ALL;

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

    /** Default names by type, as in zap-dark: { type, { "key=name", ... } }. */
    private static final Object[] DEFAULTS = {
            "BadgedLabel", new String[] { "badgeBackground=accent", "badgeForeground=onAccent", "textColor=foreground", "backgroundColor=raised", "borderColor=border" },
            "Carousel", new String[] { "arrowColor=foreground", "circleBackground=raised", "circleForeground=accent", "circleHoverColor=hover" },
            "CDateTime", new String[] { "background=surface", "pickerBackgroundColor=surface", "pickerForegroundColor=foreground", "pickerTodayColor=accent", "pickerActiveDayColor=foreground", "pickerInactiveDayColor=disabled", "buttonHoverBackgroundColor=hover", "buttonHoverBorderColor=border", "buttonSelectedBackgroundColor=accent", "buttonSelectedBorderColor=accent" },
            "Chips", new String[] { "chipsBackground=raised", "borderColor=border", "hoverBackground=hover", "hoverForeground=foreground", "hoverBorderColor=border", "pushedStateBackground=accent", "pushedStateForeground=onAccent", "pushedStateBorderColor=accent", "closeButtonForeground=foreground", "closeButtonBackground=raised", "closeButtonHoverForeground=onAccent", "closeButtonHoverBackground=accent" },
            "CTreeCombo", new String[] { "background=surface" },
            "CTreeComboItem", new String[] { "background=surface", "foreground=foreground" },
            "Gallery", new String[] { "background=surface", "groupRenderer.titleBackground=raised", "groupRenderer.titleForeground=foreground", "itemRenderer.selectionBackgroundColor=accent", "itemRenderer.selectionForegroundColor=onAccent", "itemRenderer.foregroundColor=foreground", "itemRenderer.backgroundColor=surface" },
            "GalleryItem", new String[] { "background=surface", "foreground=foreground" },
            "Grid", new String[] { "background=surface", "lineColor=gridLine", "cellHeaderSelectionBackground=selection" },
            "GridItem", new String[] { "background=surface", "foreground=foreground" },
            "BreadcrumbItem", new String[] { "textColor=foreground", "textColorSelected=accent" },
            "Header", new String[] { "titleColor=foreground", "gradientStart=surface", "gradientEnd=background", "separatorColor=border" },
            "RangeSlider", new String[] { "foreground=border" },
            "RoundedToolItem", new String[] { "textColor=foreground", "textColorSelected=onAccent" },
            "RoundedToolbar", new String[] { "borderColor=border" },
            "SwitchButton", new String[] { "selectedBackgroundColor=accent", "selectedForegroundColor=onAccent", "unselectedBackgroundColor=raised", "unselectedForegroundColor=foreground", "borderColor=border", "buttonBorderColor=border", "buttonBackgroundColor1=surface", "buttonBackgroundColor2=surface", "focusColor=focus" },
            "TextAssist", new String[] { "background=surface" },
            "PasswordRevealer", new String[] { "background=surface" },
            "PGroup", new String[] { "strategy.borderColor=border", "titleBackground=surface", "titleBackgroundGradient=raised", "foreground=foreground" },
            "ProgressCircle", new String[] { "highlightColor=accent", "background=background", "foreground=raised" },
            "PShelf", new String[] { "renderer.shadeColor=raised" },
            "TableCombo", new String[] { "background=surface", "borderColor=border", "oddLinesBackgroundColor=surface", "evenLinesBackgroundColor=stripe", "oddLinesForegroundColor=foreground", "evenLinesForegroundColor=foreground" },
            "Tiles", new String[] { "background=surface" },
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
        // Parts of Nebula widgets that have no proxy: themed by name, with the default properties.
        catalog.type(TypeSpec.of("ImageContainer", GROUP, TypeSpec.Base.COMPOSITE).described("Part of Carousel"));
        catalog.type(TypeSpec.of("ImageSelector", GROUP, TypeSpec.Base.COMPOSITE).described("Part of Carousel"));
        catalog.type(TypeSpec.of("EyeButton", GROUP, TypeSpec.Base.COMPOSITE).described("Part of PasswordRevealer"));
        // A Color[] gradient, set by the PGroup styler itself (no single-value setter).
        catalog.type(TypeSpec.of("PGroup", GROUP, TypeSpec.Base.COMPOSITE)
                .with(PropertySpec.color("titleBackground", "surface", "Title, bottom of its gradient"),
                        PropertySpec.color("titleBackgroundGradient", "raised", "Title, top of its gradient")));
    }
}
