package com.zapthemes.core.style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.zapthemes.core.SystemColors;
import com.zapthemes.core.model.Theme;

/**
 * Checks a theme (with its {@code extends} chain already merged) for mistakes that would otherwise
 * surface only when a matching widget is themed: bad colors and fonts, unknown palette entries,
 * unknown style bits, broken {@code inherit} chains and malformed class or state rules.
 *
 * <p>Keys are typed by name: {@code background}, {@code foreground} and keys ending in
 * {@code Background}, {@code Foreground} or {@code Color} are colors; {@code font} and keys ending in
 * {@code Font} are fonts. Other keys are left to their stylers.
 */
public final class ThemeValidator {

    private ThemeValidator() {
    }

    /** @return one message per problem, empty if the theme is valid */
    public static List<String> validate(Theme theme) {
        List<String> problems = new ArrayList<>();
        checkProps("defaults", theme.defaults(), theme, problems);
        theme.controls().forEach((key, props) -> {
            int open = key.indexOf('[');
            if (open > 0 && key.endsWith("]")) {
                try {
                    StyleResolver.parseBits(key, key.substring(open + 1, key.length() - 1));
                } catch (IllegalArgumentException e) {
                    problems.add("controls." + key + ": " + e.getMessage());
                }
            } else if (key.contains("[") || key.contains("]")) {
                problems.add("controls." + key + ": style-bit rules are written Type[BIT|BIT]");
            }
            checkProps("controls." + key, props, theme, problems);
            JsonElement inherit = props.get(StyleResolver.INHERIT);
            if (inherit != null && !theme.controls().containsKey(inherit.getAsString())) {
                problems.add("controls." + key + ".inherit: no control rule \"" + inherit.getAsString() + "\"");
            }
        });
        theme.classes().forEach((key, props) -> {
            int dot = key.lastIndexOf('.');
            if (dot <= 0 || dot == key.length() - 1) {
                problems.add("classes." + key + ": class rules are written Type.class or *.class");
            }
            checkProps("classes." + key, props, theme, problems);
        });
        theme.platform().forEach((ws, settings) -> {
            if (!Set.of("*", "win32", "gtk", "cocoa").contains(ws)) {
                problems.add("platform." + ws + ": unknown platform (use *, win32, gtk or cocoa)");
            }
            checkProps("platform." + ws, settings, theme, problems);
        });
        theme.registries().forEach((name, entries) -> {
            ResolvedStyle registry = new ResolvedStyle(entries, theme.palette());
            for (String key : entries.keySet()) {
                if (name.equals(SystemColors.REGISTRY) && !SystemColors.isConstant(key)) {
                    problems.add("registries." + name + "." + key + ": not an SWT system color constant (COLOR_...)");
                    continue;
                }
                try {
                    switch (name) {
                        case "colors", SystemColors.REGISTRY -> registry.color(key);
                        case "fonts" -> registry.font(key);
                        default -> registry.value(key);
                    }
                } catch (RuntimeException e) {
                    problems.add("registries." + name + "." + key + ": " + e.getMessage());
                }
            }
        });
        StyleResolver resolver = new StyleResolver(Theme.EMPTY);
        try {
            resolver = new StyleResolver(theme);
        } catch (IllegalArgumentException e) {
            // Already reported per rule above.
        }
        for (String type : theme.controls().keySet()) {
            try {
                resolver.resolve(type, 0);
            } catch (IllegalStateException e) {
                problems.add("controls." + type + ": " + e.getMessage());
            }
        }
        return problems;
    }

    private static void checkProps(String path, Map<String, JsonElement> props, Theme theme, List<String> problems) {
        ResolvedStyle style = new ResolvedStyle(props, theme.palette());
        for (String key : props.keySet()) {
            try {
                if (key.equals(ResolvedStyle.STATES)) {
                    checkStates(path, props.get(key), theme, problems);
                } else if (isColor(key)) {
                    style.color(key);
                } else if (isFont(key)) {
                    style.font(key);
                } else {
                    style.value(key);
                }
            } catch (RuntimeException e) {
                problems.add(path + "." + key + ": " + e.getMessage());
            }
        }
    }

    private static void checkStates(String path, JsonElement states, Theme theme, List<String> problems) {
        if (!states.isJsonObject()) {
            problems.add(path + ".states: must be an object of state name → properties");
            return;
        }
        states.getAsJsonObject().entrySet().forEach(state -> {
            if (!state.getValue().isJsonObject()) {
                problems.add(path + ".states." + state.getKey() + ": must be an object");
                return;
            }
            Map<String, JsonElement> props = new LinkedHashMap<>();
            state.getValue().getAsJsonObject().entrySet().forEach(e -> props.put(e.getKey(), e.getValue()));
            checkProps(path + ".states." + state.getKey(), props, theme, problems);
        });
    }

    static boolean isColor(String key) {
        return key.equals("background") || key.equals("foreground")
                || key.endsWith("Background") || key.endsWith("Foreground") || key.endsWith("Color");
    }

    static boolean isFont(String key) {
        return key.equals("font") || key.endsWith("Font");
    }
}
