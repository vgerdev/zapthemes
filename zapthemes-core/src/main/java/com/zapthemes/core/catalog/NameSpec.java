package com.zapthemes.core.catalog;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A common name a theme defines in {@code names}, such as {@code foreground} or {@code border}.
 *
 * @param name the name, referenced as {@code "$name"}
 * @param group for listing: {@code "Surfaces"}, {@code "Text"}, ...
 * @param parts which of {@code "color"} and {@code "font"} it holds
 * @param contrastWith names it is drawn on, whose contrast with it a tool should check: text
 *        colors list their backgrounds
 * @param description one line
 */
public record NameSpec(String name, String group, Set<String> parts, List<String> contrastWith, String description) {

    public NameSpec {
        Objects.requireNonNull(name, "name");
        parts = Set.copyOf(parts);
        contrastWith = contrastWith == null ? List.of() : List.copyOf(contrastWith);
        description = description == null ? "" : description;
    }

    public boolean hasColor() {
        return parts.contains("color");
    }

    public boolean hasFont() {
        return parts.contains("font");
    }
}
