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
        registry.register("FileCommandBar", repaint);
        registry.register("FileItemView", repaint);
    }

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        PropertySpec hover = PropertySpec.color("hoverBackground", "hover", "A button or segment under the mouse; derived from the colors when not set");
        PropertySpec pressed = PropertySpec.color("pressedBackground", "selection",
                "A pressed button, an open drop-down's chevron, the current view's toggle; derived when not set");
        PropertySpec disabled = PropertySpec.color("disabledForeground", "disabled", "Disabled buttons; derived when not set");
        PropertySpec border = PropertySpec.color("borderColor", "border", "The address bar's outline and the drop-downs' outline; derived when not set");
        catalog.type(TypeSpec.of("FileBrowser", GROUP, TypeSpec.Base.COMPOSITE)
                .described("An Explorer-style file browser: its frame, around the bars, the folder tree (a Tree), the Details view (a Table) and the other layouts (FileItemView)"));
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
        catalog.type(TypeSpec.of("FileCommandBar", GROUP, TypeSpec.Base.COMPOSITE)
                .with(hover, pressed, disabled,
                        PropertySpec.color("borderColor", "border", "The separators between groups of commands; derived when not set"),
                        PropertySpec.color("mutedForeground", "muted", "Secondary text; derived when not set"),
                        PropertySpec.color("errorForeground", "error", "Read with the bar's other colors; derived when not set"))
                .withStates("hover", "pressed", "checked", "disabled")
                .described("The file browser's command bar: New, Cut, Copy, Paste, Rename, Share, Delete, Sort, View, ... and the Details toggle"));
        catalog.type(TypeSpec.of("FileStatusBar", GROUP, TypeSpec.Base.COMPOSITE)
                .with(hover, pressed, disabled, PropertySpec.color("borderColor", "border", "The line between the item count and the selection; derived when not set"),
                        PropertySpec.color("mutedForeground", "muted", "Secondary text; derived when not set"),
                        PropertySpec.color("errorForeground", "error", "An error shown in the bar"))
                .described("The file browser's status bar: item count, selection, view toggles"));
        catalog.type(TypeSpec.of("FileItemView", GROUP, TypeSpec.Base.COMPOSITE)
                .with(PropertySpec.color("selectionBackground", "selection", "Selected items; the Details view's Table rule when not set"),
                        PropertySpec.color("selectionForeground", null, "The text of selected items; the Table rule's when not set"),
                        PropertySpec.color("hoverBackground", "hover", "An item under the mouse, and a folder a drop goes into; the Table rule's when not set"),
                        PropertySpec.color("mutedForeground", "muted", "Secondary text (types, sizes, labels); derived when not set"))
                .described("The file browser's icon, List, Tiles and Content layouts, and its details and preview panes; "
                        + "background and foreground come from the Details view's Table rule when not set"));
    }
}
