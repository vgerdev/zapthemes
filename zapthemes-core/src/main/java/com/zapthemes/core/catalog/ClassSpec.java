package com.zapthemes.core.catalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A style class the library or an application sets on widgets ({@code THEME_CLASS}), such as
 * {@code popup} on JFace's popup dialogs, which a theme gives a rule in {@code classes}.
 *
 * @param name the class name, as in {@code "*.popup"}
 * @param types the types it is set on, empty for any
 * @param description one line; may be empty
 */
public record ClassSpec(String name, List<String> types, String description) {

    public ClassSpec {
        Objects.requireNonNull(name, "name");
        types = types == null ? List.of() : List.copyOf(types);
        description = description == null ? "" : description;
    }

    public static ClassSpec of(String name, String description, String... types) {
        return new ClassSpec(name, List.of(types), description);
    }

    ClassSpec mergedWith(ClassSpec other) {
        List<String> all = new ArrayList<>();
        if (!types.isEmpty() && !other.types.isEmpty()) {
            all.addAll(types);
            other.types.stream().filter(t -> !all.contains(t)).forEach(all::add);
        }
        return new ClassSpec(name, all, description.isEmpty() ? other.description : description);
    }
}
