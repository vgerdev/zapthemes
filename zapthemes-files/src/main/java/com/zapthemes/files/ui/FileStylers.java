package com.zapthemes.files.ui;

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
 * Registers the file browser's stylers (the default colors and font, then a repaint: the bars read
 * the rest of their style while painting) and describes what they read for the property catalog.
 */
public final class FileStylers implements StylerProvider, CatalogProvider {

    private static final String GROUP = "File browser";

    @Override
    public void register(StylerRegistry registry) {
        DefaultStyler repaint = new DefaultStyler() {
            @Override
            public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
                super.apply(widget, style, context);
                ((Control) widget).redraw();
            }
        };
        registry.register("FileBrowser", new DefaultStyler());
        registry.register("FileNavigationBar", repaint);
        registry.register("FileStatusBar", repaint);
    }

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        PropertySpec hover = PropertySpec.color("hoverBackground", "hover", "A button or segment under the mouse; derived from the colors when not set");
        PropertySpec pressed = PropertySpec.color("pressedBackground", "selection",
                "A pressed button, an open drop-down's chevron, the current view's toggle; derived when not set");
        PropertySpec disabled = PropertySpec.color("disabledForeground", "disabled", "Disabled buttons; derived when not set");
        PropertySpec border = PropertySpec.color("borderColor", "border", "The address bar's outline and the drop-downs' outline; derived when not set");
        catalog.type(TypeSpec.of("FileBrowser", GROUP, TypeSpec.Base.COMPOSITE)
                .described("An Explorer-style file browser: its frame, around the bars, the folder tree (a Tree) and the Details view (a Table)"));
        catalog.type(TypeSpec.of("FileNavigationBar", GROUP, TypeSpec.Base.COMPOSITE)
                .with(hover, pressed, disabled, border,
                        PropertySpec.color("fieldBackground", "surface", "The address bar; derived when not set"),
                        PropertySpec.color("fieldHoverBackground", null, "The address bar under the mouse; derived when not set"),
                        PropertySpec.color("focusColor", "accent", "The address bar's outline while the path is edited"),
                        PropertySpec.color("mutedForeground", "muted", "Secondary text; derived when not set"),
                        PropertySpec.color("errorForeground", "error", "Error text; derived when not set"),
                        PropertySpec.size("arc", 6, "The address bar's corner radius, in pixels"))
                .withStates("hover", "pressed", "disabled", "focused")
                .described("The file browser's navigation row: Back, Forward, Up, Refresh and the breadcrumb address bar"));
        catalog.type(TypeSpec.of("FileStatusBar", GROUP, TypeSpec.Base.COMPOSITE)
                .with(hover, pressed, disabled, PropertySpec.color("borderColor", "border", "The line between the item count and the selection; derived when not set"),
                        PropertySpec.color("mutedForeground", "muted", "Secondary text; derived when not set"),
                        PropertySpec.color("errorForeground", "error", "An error shown in the bar"))
                .described("The file browser's status bar: item count, selection, view toggles"));
    }
}
