package com.zapthemes.widgets;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.ClassSpec;
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
        for (String type : new String[] { "Ribbon", "RibbonPopup", "RibbonToolTip", "RibbonBackstage", "RibbonMessageBar" }) {
            registry.register(type, repaint);
        }
    }

    private static void describeRibbon(PropertyCatalog.Builder catalog) {
        String group = "Ribbon";
        catalog.type(TypeSpec.of("Ribbon", group, TypeSpec.Base.COMPOSITE).defaults("background=raised")
                .with(PropertySpec.color("tabStripBackground", "chrome", "The tab row"),
                        PropertySpec.color("tabForeground", "foreground", "Tab labels"),
                        PropertySpec.color("tabHoverBackground", "hover", "A tab under the mouse"),
                        PropertySpec.color("selectedTabBackground", "chrome", "The selected tab; the tab row's color draws only its underline"),
                        PropertySpec.color("selectedTabForeground", "accent", "The selected tab's label"),
                        PropertySpec.color("tabIndicatorColor", "accent", "The selected tab's underline; the accent of items"),
                        PropertySpec.font("tabFont", "foreground", "Tab labels"),
                        PropertySpec.color("groupLabelForeground", "muted", "Group labels"),
                        PropertySpec.font("groupLabelFont", "caption", "Group labels and KeyTips"),
                        PropertySpec.color("separatorColor", "border", "Lines between groups"),
                        PropertySpec.color("borderColor", "border", "The ribbon's bottom line, fields and right commands"),
                        PropertySpec.color("itemHoverBackground", "hover", "An item under the mouse"),
                        PropertySpec.color("itemPressedBackground", "selection", "A pressed item, or one whose menu is open"),
                        PropertySpec.color("itemCheckedBackground", "selection", "A checked toggle button"),
                        PropertySpec.color("itemCheckedBorderColor", "accent", "A checked toggle button's outline; hovered fields"),
                        PropertySpec.color("disabledForeground", "disabled", "Disabled items"),
                        PropertySpec.color("fieldBackground", "surface", "Combo box fields"),
                        PropertySpec.color("galleryBorderColor", "border", "In-ribbon galleries' outline"),
                        PropertySpec.color("galleryBackground", "surface", "In-ribbon galleries"),
                        PropertySpec.color("gallerySelectionColor", "accent", "The selected gallery item's outline"),
                        PropertySpec.color("keyTipBackground", "badge", "KeyTip badges"),
                        PropertySpec.color("keyTipForeground", "onBadge", "KeyTip letters"),
                        PropertySpec.color("keyTipBorderColor", "border", "KeyTip badges' outline"),
                        PropertySpec.color("fileTabBackground", "accent", "The File tab"),
                        PropertySpec.color("fileTabForeground", "onAccent", "The File tab's label"),
                        PropertySpec.color("emphasisBackground", "accent", "Emphasized right commands (Share)"),
                        PropertySpec.color("emphasisForeground", "onAccent", "Emphasized right commands' label"),
                        PropertySpec.color("contextualAccent1", "accent", "Contextual tab sets with accent 1"),
                        PropertySpec.color("contextualAccent2", "info", "Contextual tab sets with accent 2"),
                        PropertySpec.color("contextualAccent3", "success", "Contextual tab sets with accent 3"),
                        PropertySpec.color("contextualAccent4", "warning", "Contextual tab sets with accent 4"),
                        PropertySpec.color("focusColor", "focus", "The keyboard focus ring"),
                        PropertySpec.bool("useAppAccent", true, "Whether the app's brand color replaces the accent keys"),
                        PropertySpec.integer("arc", 4, "Corner radius, in pixels"))
                .withStates("hover", "pressed", "checked", "disabled", "focused")
                .described("An Office-style ribbon: tabs, groups, galleries, KeyTips"));
        catalog.type(TypeSpec.of("RibbonPopup", group, TypeSpec.Base.COMPOSITE).defaults("background=raised")
                .with(PropertySpec.color("borderColor", "border", "Outline"))
                .described("Ribbon popups: collapsed groups, the collapsed ribbon's overlay, galleries, color palettes"));
        catalog.type(TypeSpec.of("RibbonToolTip", group, TypeSpec.Base.COMPOSITE).defaults("background=surface")
                .with(PropertySpec.color("borderColor", "border", "Outline"),
                        PropertySpec.font("titleFont", "strong", "The title line: label and shortcut"))
                .described("The ribbon's super tooltips"));
        catalog.type(TypeSpec.of("RibbonBackstage", group, TypeSpec.Base.COMPOSITE)
                .with(PropertySpec.color("navBackground", "accent", "The navigation column"),
                        PropertySpec.color("navForeground", "onAccent", "Navigation entries"),
                        PropertySpec.color("navHoverBackground", null, "An entry under the mouse; derived from the column when not set"),
                        PropertySpec.color("navSelectedBackground", null, "The selected page's entry; derived when not set"),
                        PropertySpec.color("navSelectedForeground", "onAccent", "The selected page's label"),
                        PropertySpec.font("navFont", "foreground", "Navigation entries"),
                        PropertySpec.font("titleFont", "heading1", "Page titles"),
                        PropertySpec.bool("useAppAccent", true, "Whether the app's brand color colors the navigation column"))
                .described("The File tab's full-window page"));
        catalog.type(TypeSpec.of("RibbonMessageBar", group, TypeSpec.Base.COMPOSITE)
                .with(PropertySpec.color("borderColor", "border", "Outline"),
                        PropertySpec.color("iconColor", "foreground", "The icon"),
                        PropertySpec.font("titleFont", "strong", "The title"))
                .withStates("info", "warning", "error", "success")
                .described("A message bar under the ribbon"));
        catalog.styleClass(ClassSpec.of("backstage", "The backstage's page area and pages", "Composite"));
        catalog.styleClass(ClassSpec.of("backstageTitle", "Backstage page titles", "Label"));
    }

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        PropertySpec border = PropertySpec.color("borderColor", "disabled", "Outline");
        PropertySpec accent = PropertySpec.color("accentColor", "accent", "Accent (focus, check marks, default button)");
        PropertySpec arc = PropertySpec.size("arc", 6, "Corner radius, in pixels");
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
        describeRibbon(catalog);
    }
}
