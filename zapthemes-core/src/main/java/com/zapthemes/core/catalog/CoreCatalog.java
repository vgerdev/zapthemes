package com.zapthemes.core.catalog;

import static com.zapthemes.core.catalog.PropertySpec.bool;
import static com.zapthemes.core.catalog.PropertySpec.color;

import java.util.List;

import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.CBanner;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.TableCursor;
import org.eclipse.swt.custom.TreeCursor;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.opengl.GLCanvas;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.CoolItem;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Decorations;
import org.eclipse.swt.widgets.ExpandBar;
import org.eclipse.swt.widgets.ExpandItem;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Sash;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

import com.zapthemes.core.SystemColors;
import com.zapthemes.core.catalog.TypeSpec.Base;

/**
 * What core themes: every SWT widget type with a proxy (background, foreground and font, set by
 * {@code DefaultStyler}), the extra properties of the built-in stylers and {@code ItemPainter},
 * the {@code systemColors} registry, and the platform settings.
 */
final class CoreCatalog implements CatalogProvider {

    static final String SWT = "SWT";

    private static final List<Class<?>> WIDGETS = List.of(
            Browser.class, CBanner.class, CCombo.class, CLabel.class, CTabFolder.class, CTabItem.class, SashForm.class,
            ScrolledComposite.class, StyledText.class, TableCursor.class, TreeCursor.class, ViewForm.class, GLCanvas.class,
            Button.class, Canvas.class, Combo.class, Composite.class, CoolBar.class, CoolItem.class, DateTime.class,
            Decorations.class, ExpandBar.class, ExpandItem.class, Group.class, Label.class, Link.class,
            org.eclipse.swt.widgets.List.class, Menu.class, MenuItem.class, ProgressBar.class, Sash.class, Scale.class,
            Shell.class, Slider.class, Spinner.class, TabFolder.class, TabItem.class, Table.class, TableColumn.class,
            TableItem.class, Text.class, ToolBar.class, ToolItem.class, ToolTip.class, Tree.class, TreeColumn.class,
            TreeItem.class);

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        for (Class<?> widget : WIDGETS) {
            catalog.type(TypeSpec.of(widget.getSimpleName(), SWT, base(widget)));
        }
        catalog.type(TypeSpec.of("Text", SWT, Base.CONTROL).defaults("background=surface").withVariants("MULTI", "READ_ONLY"));
        catalog.type(TypeSpec.of("Combo", SWT, Base.COMPOSITE).defaults("background=surface").withVariants("READ_ONLY"));
        catalog.type(TypeSpec.of("Button", SWT, Base.CONTROL).defaults("background=raised")
                .withVariants("PUSH", "CHECK", "RADIO", "TOGGLE", "FLAT"));
        catalog.type(TypeSpec.of("Label", SWT, Base.CONTROL).withVariants("WRAP", "SEPARATOR"));
        catalog.type(TypeSpec.of("ProgressBar", SWT, Base.CONTROL).defaults("background=raised", "foreground=accent")
                .described("Native: some platforms ignore its colors"));
        for (String rows : List.of("Table", "Tree")) {
            catalog.type(TypeSpec.of(rows, SWT, Base.COMPOSITE).defaults("background=surface")
                    .with(color("headerBackground", "raised", "Column headers"),
                            color("headerForeground", "foreground", "Column header text"),
                            color("selectionBackground", "selection", "Selected rows (owner-drawn)"),
                            color("selectionForeground", "selectionForeground", "Text of selected rows (owner-drawn)"),
                            color("focusCellBackground", "accent", "The focus cell of JFace's FocusCellOwnerDrawHighlighter"),
                            color("focusCellForeground", "onAccent", "Text of the focus cell"),
                            color("gridLineColor", "gridLine", "Grid lines, when lines are visible (owner-drawn)"))
                    .withStates("hover"));
        }
        catalog.type(TypeSpec.of("Table", SWT, Base.COMPOSITE)
                .with(color("alternateRowBackground", "stripe", "Every second row (owner-drawn)")));
        catalog.type(TypeSpec.of("TableItem", SWT, Base.ITEM).inheriting("Table"));
        catalog.type(TypeSpec.of("TreeItem", SWT, Base.ITEM).inheriting("Tree"));
        catalog.type(TypeSpec.of("CTabFolder", SWT, Base.COMPOSITE)
                .with(color("selectionBackground", "surface", "The selected tab"),
                        color("selectionForeground", "accent", "The selected tab's text"),
                        PropertySpec.size("selectionBarThickness", 2, "Height of the bar over the selected tab, in pixels"),
                        bool("highlight", true, "Highlight the selected tab"),
                        PropertySpec.size("tabHeight", null, "Tab height, in pixels")));
        catalog.type(TypeSpec.of("StyledText", SWT, Base.COMPOSITE).defaults("background=surface")
                .with(color("selectionBackground", "selection", "Selected text"),
                        color("selectionForeground", "selectionForeground", "Selected text's color"),
                        color("marginColor", "surface", "The margins")));
        catalog.type(TypeSpec.of("Link", SWT, Base.CONTROL).with(color("linkForeground", "link", "Links")));
        for (String inField : List.of("CCombo", "Spinner", "DateTime", "List")) {
            catalog.type(TypeSpec.of(inField, SWT, Base.CONTROL).defaults("background=surface"));
        }

        catalog.styleClass(ClassSpec.of("primary", "The default action", "Button", "FlatButton"));
        catalog.styleClass(ClassSpec.of("muted", "Secondary text", "Label"));
        catalog.styleClass(ClassSpec.of("strong", "Emphasized text"));
        catalog.styleClass(ClassSpec.of("title", "Titles"));

        systemColors(catalog);
        platform(catalog);
    }

    private static Base base(Class<?> widget) {
        if (widget == CTabItem.class) {
            return Base.TEXT_ITEM;
        }
        if (widget == TableItem.class || widget == TreeItem.class) {
            return Base.ITEM;
        }
        if (Composite.class.isAssignableFrom(widget)) {
            return Base.COMPOSITE;
        }
        return Control.class.isAssignableFrom(widget) ? Base.CONTROL : Base.NONE;
    }

    /** {@code Display.getSystemColor} constants inside the forks that paint with them. */
    private static void systemColors(PropertyCatalog.Builder catalog) {
        String[][] entries = {
                { "COLOR_WIDGET_BACKGROUND", "background" }, { "COLOR_WIDGET_FOREGROUND", "foreground" },
                { "COLOR_LIST_BACKGROUND", "surface" }, { "COLOR_LIST_FOREGROUND", "foreground" },
                { "COLOR_LIST_SELECTION", "accent" }, { "COLOR_LIST_SELECTION_TEXT", "onAccent" },
                { "COLOR_WIDGET_NORMAL_SHADOW", "border" }, { "COLOR_WIDGET_LIGHT_SHADOW", "raised" },
                { "COLOR_WIDGET_HIGHLIGHT_SHADOW", "surface" }, { "COLOR_WIDGET_DARK_SHADOW", "disabled" },
                { "COLOR_WIDGET_BORDER", "border" }, { "COLOR_TITLE_BACKGROUND", "surface" },
                { "COLOR_TITLE_FOREGROUND", "foreground" }, { "COLOR_TITLE_BACKGROUND_GRADIENT", "raised" },
                { "COLOR_TITLE_INACTIVE_BACKGROUND", "raised" }, { "COLOR_TITLE_INACTIVE_FOREGROUND", "disabled" },
                { "COLOR_TITLE_INACTIVE_BACKGROUND_GRADIENT", "raised" }, { "COLOR_INFO_BACKGROUND", "raised" },
                { "COLOR_INFO_FOREGROUND", "foreground" }, { "COLOR_TEXT_DISABLED_BACKGROUND", "raised" },
                { "COLOR_WIDGET_DISABLED_FOREGROUND", "disabled" }, { "COLOR_LINK_FOREGROUND", "link" } };
        for (String[] entry : entries) {
            catalog.registry(SystemColors.REGISTRY, color(entry[0], entry[1], "SWT." + entry[0] + " in the Nebula and JFace Text forks"));
        }
        // The plain colors (COLOR_BLACK, COLOR_DARK_GRAY, ...) are looked up too; a theme may override them.
        for (java.lang.reflect.Field field : org.eclipse.swt.SWT.class.getFields()) {
            if (SystemColors.isConstant(field.getName())) {
                catalog.registry(SystemColors.REGISTRY, color(field.getName(), null, "SWT." + field.getName()));
            }
        }
    }

    private static void platform(PropertyCatalog.Builder catalog) {
        catalog.platform("*", bool("darkThemePreferred", null, "Dark title bars and native dialogs"));
        catalog.platform("win32",
                bool("darkScrollbars", null, "Dark scroll bars"), bool("darkCombos", null, "Dark combo boxes"),
                bool("darkTextIcons", null, "Dark icons in text fields"), bool("progressBarColors", null, "Theme progress bar colors"),
                color("menuBarBackground", "background", "Menu bar"), color("menuBarForeground", "foreground", "Menu bar text"),
                color("menuBarBorderColor", "raised", "Line under the menu bar"),
                color("tableHeaderLineColor", "raised", "Lines between table header cells"),
                color("disabledLabelForeground", "disabled", "Disabled labels"));
        catalog.platform("gtk",
                color("menuBarBackground", "background", "Menu bar"), color("menuBarForeground", "foreground", "Menu bar text"),
                color("menuBackground", "surface", "Menus"), color("menuForeground", "foreground", "Menu text"),
                color("menuSelectionBackground", "selection", "The hovered menu item"),
                color("menuSelectionForeground", "selectionForeground", "The hovered menu item's text"));
        catalog.platform("cocoa");
    }
}
