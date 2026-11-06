package com.zapthemes.core.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The common names every theme is expected to define, which the default map
 * ({@link PropertySpec#defaultName()}) refers to. A theme may define more; the theme editor lets
 * users add their own.
 */
public final class BaseNames {

    private static final Set<String> COLOR = Set.of("color");
    private static final Set<String> FONT = Set.of("font");
    private static final Set<String> BOTH = Set.of("color", "font");

    /** In display order, by group. */
    public static final List<NameSpec> ALL = List.of(
            name("background", "Surfaces", COLOR, "Window and panel background"),
            name("surface", "Surfaces", COLOR, "Fields, lists and tables: content areas set off from the background"),
            name("raised", "Surfaces", COLOR, "Buttons, headers and bars: raised above the surface"),
            name("stripe", "Surfaces", COLOR, "Alternate table rows"),
            name("hover", "Surfaces", COLOR, "Under the mouse"),
            name("selection", "Surfaces", COLOR, "Selected rows and text"),

            name("foreground", "Text", BOTH, "Text; its font is the default font", "background", "surface", "raised"),
            name("muted", "Text", COLOR, "Secondary text: qualifiers, hints, line numbers", "background", "surface"),
            name("disabled", "Text", COLOR, "Disabled text and outlines", "background", "surface"),
            name("onAccent", "Text", COLOR, "Text on the accent color", "accent"),
            name("link", "Text", COLOR, "Links", "background", "surface"),
            name("selectionForeground", "Text", COLOR, "Text on the selection", "selection"),

            name("heading1", "Headings", BOTH, "Largest heading", "background"),
            name("heading2", "Headings", BOTH, "Second-level heading", "background"),
            name("heading3", "Headings", BOTH, "Third-level heading", "background"),
            name("strong", "Headings", BOTH, "Emphasized text: titles, group labels", "background"),
            name("caption", "Headings", BOTH, "Small text: status lines, captions", "background"),

            name("border", "Lines", COLOR, "Borders and separators"),
            name("gridLine", "Lines", COLOR, "Table grid lines and chart grids"),
            name("focus", "Lines", COLOR, "Focus rings"),

            name("accent", "Accent and status", COLOR, "The accent: default buttons, current values, highlights"),
            name("error", "Accent and status", COLOR, "Errors", "background"),
            name("warning", "Accent and status", COLOR, "Warnings", "background"),
            name("success", "Accent and status", COLOR, "Success", "background"),
            name("info", "Accent and status", COLOR, "Information", "background"),

            name("code", "Code", FONT, "Source code"),
            name("keyword", "Code", COLOR, "Keywords in source code", "surface"),
            name("string", "Code", COLOR, "Strings in source code", "surface"),
            name("comment", "Code", COLOR, "Comments in source code", "surface"),
            name("number", "Code", COLOR, "Numbers in source code", "surface"));

    private static final Map<String, NameSpec> BY_NAME = new LinkedHashMap<>();

    static {
        ALL.forEach(n -> BY_NAME.put(n.name(), n));
    }

    private BaseNames() {
    }

    public static Optional<NameSpec> get(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    private static NameSpec name(String name, String group, Set<String> parts, String description, String... contrastWith) {
        return new NameSpec(name, group, parts, List.of(contrastWith), description);
    }
}
