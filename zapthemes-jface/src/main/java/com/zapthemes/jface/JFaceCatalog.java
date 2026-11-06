package com.zapthemes.jface;

import static com.zapthemes.core.catalog.PropertySpec.color;
import static com.zapthemes.core.catalog.PropertySpec.font;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.ClassSpec;
import com.zapthemes.core.catalog.PropertyCatalog;

/**
 * What the JFace fork themes, for the {@link PropertyCatalog}: the style classes it sets
 * ({@link JFaceTheming}) and the {@code registries.colors} and {@code registries.fonts} entries
 * {@link JFaceThemeSync} puts into JFace's registries, by JFace's symbolic names.
 */
public final class JFaceCatalog implements CatalogProvider {

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        catalog.styleClass(ClassSpec.of(JFaceTheming.BANNER, "Title areas of title-area dialogs and wizards"));
        catalog.styleClass(ClassSpec.of(JFaceTheming.TITLE, "Titles of dialogs, popups and notifications"));
        catalog.styleClass(ClassSpec.of(JFaceTheming.INFO, "Tooltips and hovers"));
        catalog.styleClass(ClassSpec.of(JFaceTheming.POPUP, "Popup dialogs, hovers and content assist"));
        catalog.styleClass(ClassSpec.of(JFaceTheming.MUTED, "Secondary text: a popup's info text, status lines"));
        catalog.styleClass(ClassSpec.of(JFaceTheming.ERROR, "Error messages in status lines and dialogs"));
        catalog.styleClass(ClassSpec.of(JFaceTheming.PLAIN, "Text fields drawn flat on the background", "Text"));
        catalog.styleClass(ClassSpec.of(JFaceTheming.PANE, "Panes set off from the background"));

        catalog.registry("colors",
                color("ERROR_COLOR", "error", "JFacePreferences.ERROR_COLOR"),
                color("HYPERLINK_COLOR", "link", "JFacePreferences.HYPERLINK_COLOR"),
                color("ACTIVE_HYPERLINK_COLOR", "link", "JFacePreferences.ACTIVE_HYPERLINK_COLOR"),
                color("QUALIFIER_COLOR", "muted", "Qualifiers in styled viewer labels"),
                color("DECORATIONS_COLOR", "muted", "Decorations in styled viewer labels"),
                color("COUNTER_COLOR", "accent", "Counters in styled viewer labels"),
                color("CONTENT_ASSIST_BACKGROUND_COLOR", "surface", "Content assist popups"),
                color("CONTENT_ASSIST_FOREGROUND_COLOR", "foreground", "Content assist text"),
                color("org.eclipse.ui.workbench.INFORMATION_BACKGROUND", "surface", "Information popups"),
                color("org.eclipse.ui.workbench.INFORMATION_FOREGROUND", "foreground", "Information popup text"));
        catalog.registry("fonts",
                font("org.eclipse.jface.defaultfont", "foreground", "JFaceResources default font"),
                font("org.eclipse.jface.dialogfont", "foreground", "Dialogs"),
                font("org.eclipse.jface.viewerfont", "foreground", "Viewers"),
                font("org.eclipse.jface.windowfont", "foreground", "Windows"),
                font("org.eclipse.jface.bannerfont", "strong", "Title-area banners"),
                font("org.eclipse.jface.headerfont", "heading1", "Headers"),
                font("org.eclipse.jface.textfont", "code", "Text editors"));
    }
}
