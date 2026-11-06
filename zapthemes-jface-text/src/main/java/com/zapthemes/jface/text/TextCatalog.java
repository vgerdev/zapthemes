package com.zapthemes.jface.text;

import static com.zapthemes.core.catalog.PropertySpec.color;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.ClassSpec;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.jface.JFaceTheming;

/**
 * What the JFace Text fork themes, for the {@link PropertyCatalog}: the style classes
 * {@link TextTheming} sets and the syntax colors it reads from {@code registries.colors}.
 */
public final class TextCatalog implements CatalogProvider {

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        catalog.styleClass(ClassSpec.of(TextTheming.RULER, "Line number and annotation rulers"));
        catalog.styleClass(ClassSpec.of(TextTheming.FRAME, "Frames around context information"));
        catalog.styleClass(ClassSpec.of(JFaceTheming.POPUP, "Hovers and content assist"));
        catalog.registry("colors",
                color(TextTheming.KEYWORD, "keyword", "Keywords, for syntax coloring"),
                color(TextTheming.STRING, "string", "Strings, for syntax coloring"),
                color(TextTheming.COMMENT, "comment", "Comments, for syntax coloring"),
                color(TextTheming.NUMBER, "number", "Numbers, for syntax coloring"));
    }
}
