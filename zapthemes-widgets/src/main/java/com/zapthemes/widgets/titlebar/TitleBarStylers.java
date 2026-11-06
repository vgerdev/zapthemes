package com.zapthemes.widgets.titlebar;

import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.PropertySpec;
import com.zapthemes.core.catalog.TypeSpec;
import com.zapthemes.core.style.ControlStyler;
import com.zapthemes.core.style.DefaultStyler;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StylerProvider;
import com.zapthemes.core.style.StylerRegistry;
import com.zapthemes.core.style.ThemeContext;

/**
 * Themes the {@link TitleBar}: its font from the theme, then its colors, which it works out itself
 * (theme, accent, active state). Its search field and slot take the bar's colors. Describes the
 * {@code TitleBar} type for the {@link PropertyCatalog}.
 */
public final class TitleBarStylers implements StylerProvider, CatalogProvider {

    static final String SLOT_TYPE = "TitleBarSlot";
    static final String SEARCH_TYPE = "TitleBarSearch";
    private static final String GROUP = "Flat widgets";

    @Override
    public void register(StylerRegistry registry) {
        registry.register(TitleBar.TYPE, new DefaultStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                ((TitleBar) widget).themeChanged();
            }
        });
        ControlStyler child = (widget, style, context) -> {
            if (((org.eclipse.swt.widgets.Control) widget).getParent() instanceof TitleBar bar) {
                bar.applyColors();
            }
        };
        registry.register(SLOT_TYPE, child);
        registry.register(SEARCH_TYPE, child);
    }

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        catalog.type(TypeSpec.of(TitleBar.TYPE, GROUP, TypeSpec.Base.COMPOSITE)
                .defaults("background=titleBar", "foreground=titleBarForeground")
                .with(PropertySpec.color("inactiveBackground", "titleBarInactive", "Background while the window is inactive"),
                        PropertySpec.color("inactiveForeground", "titleBarInactiveForeground", "Text and glyphs while the window is inactive"),
                        PropertySpec.font("titleFont", "foreground", "The document title"),
                        PropertySpec.color("hoverBackground", null, "Buttons under the mouse; derived from the foreground when not set"),
                        PropertySpec.color("pressedBackground", null, "Pressed buttons; derived from the foreground when not set"),
                        PropertySpec.color("closeHoverBackground", null, "The close button under the mouse (#C42B1C when not set)"),
                        PropertySpec.color("closeHoverForeground", null, "The close button's cross under the mouse (white when not set)"),
                        PropertySpec.color("closePressedBackground", null, "The pressed close button (#B22A1B when not set)"),
                        PropertySpec.color("searchBackground", null, "The search box; derived when not set"),
                        PropertySpec.color("searchForeground", null, "Search text; the foreground when not set"),
                        PropertySpec.color("searchBorderColor", null, "The search box outline; derived when not set"),
                        PropertySpec.color("avatarBackground", "accent", "The account circle behind the initials"),
                        PropertySpec.color("avatarForeground", "onAccent", "The initials"),
                        PropertySpec.color("focusColor", "focus", "The keyboard focus ring"),
                        PropertySpec.bool("useAccent", true, "Whether the app's accent (TitleBar.setAccent) colors the bar in this theme"))
                .described("An Office-style window title bar: icon, quick access toolbar, title, search, account and window buttons"));
        catalog.type(TypeSpec.of(SLOT_TYPE, GROUP, TypeSpec.Base.NONE)
                .described("The title bar's slot for app controls; takes the bar's background"));
        catalog.type(TypeSpec.of(SEARCH_TYPE, GROUP, TypeSpec.Base.NONE)
                .described("The title bar's search field; takes the bar's search colors"));
    }
}
