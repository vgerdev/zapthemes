package com.zapthemes.editor;

import java.util.List;

import org.eclipse.swt.SWT;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * A font the user gave a common name. Empty {@code families} and a zero {@code height} take the
 * inherited font's (or the system font's, with nothing to inherit).
 *
 * @param families the families in order of preference; the first one installed wins
 * @param height points, or 0 to inherit
 * @param style {@link SWT#NORMAL}, {@link SWT#BOLD} and/or {@link SWT#ITALIC}
 * @param inherit the common name whose font this one starts from, or null
 */
public record FontChoice(List<String> families, int height, int style, String inherit) {

    public FontChoice {
        families = families == null ? List.of() : families.stream().map(String::strip).filter(f -> !f.isEmpty()).toList();
        if (height < 0) {
            throw new IllegalArgumentException("Negative font height: " + height);
        }
    }

    /** {@code name}'s font, changed only in what is set later. */
    public static FontChoice inheriting(String name) {
        return new FontChoice(List.of(), 0, SWT.NORMAL, name);
    }

    public FontChoice withFamilies(List<String> newFamilies) {
        return new FontChoice(newFamilies, height, style, inherit);
    }

    public FontChoice withHeight(int newHeight) {
        return new FontChoice(families, newHeight, style, inherit);
    }

    public FontChoice withStyle(int newStyle) {
        return new FontChoice(families, height, newStyle, inherit);
    }

    public FontChoice withInherit(String name) {
        return new FontChoice(families, height, style, name);
    }

    /** As a theme writes it: {@code { "inherit": "$foreground", "name": [...], "height": 9, "style": "bold" }}. */
    public JsonObject toJson() {
        JsonObject font = new JsonObject();
        if (inherit != null) {
            font.addProperty("inherit", "$" + inherit);
        }
        if (!families.isEmpty()) {
            if (families.size() == 1) {
                font.addProperty("name", families.getFirst());
            } else {
                JsonArray names = new JsonArray();
                families.forEach(names::add);
                font.add("name", names);
            }
        }
        if (height > 0) {
            font.addProperty("height", height);
        }
        // With nothing to inherit, normal is written too, so that the font is complete.
        if (style != SWT.NORMAL || inherit == null) {
            font.addProperty("style", styleName(style));
        }
        return font;
    }

    static String styleName(int style) {
        boolean bold = (style & SWT.BOLD) != 0;
        boolean italic = (style & SWT.ITALIC) != 0;
        return bold && italic ? "bold|italic" : bold ? "bold" : italic ? "italic" : "normal";
    }
}
