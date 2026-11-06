package com.zapthemes.core;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zapthemes.core.model.Theme;

/**
 * Upgrades theme JSON of older formats to the current one, a step per version, so that themes
 * written for an older library keep loading. Each step takes the JSON of its version and leaves
 * that of the next.
 */
final class ThemeUpgrades {

    /** Step {@code i} upgrades version {@code i + 1} to {@code i + 2}. */
    private static final List<Step> STEPS = List.of(ThemeUpgrades::v1ToV2);

    private ThemeUpgrades() {
    }

    @FunctionalInterface
    private interface Step {
        void apply(JsonObject theme);
    }

    /** Upgrades {@code theme}, of format {@code version}, in place to the current format. */
    static void upgrade(JsonObject theme, int version) {
        for (int v = version; v < Theme.FORMAT_VERSION; v++) {
            STEPS.get(v - 1).apply(theme);
        }
        theme.addProperty(ThemeJson.FORMAT_VERSION, Theme.FORMAT_VERSION);
    }

    /**
     * Version 1 had one {@code palette} of colors and fonts. Each entry becomes a common name with
     * that one part: {@code "bg": "#1E1F22"} becomes {@code "bg": { "color": "#1E1F22" }}, and
     * {@code "font.default": { ... }} becomes {@code "font.default": { "font": { ... } }}. References
     * in rules keep their names, and resolve to the part their key asks for. An entry that refers to
     * another entry gets that entry's part; one referring to an entry of a theme it extends (not in
     * this file) is a font if its name starts with {@code font}, else a color. {@code dark} is taken
     * from the platform settings' {@code darkThemePreferred}.
     */
    private static void v1ToV2(JsonObject theme) {
        JsonElement palette = theme.remove("palette");
        if (palette != null && palette.isJsonObject()) {
            Map<String, JsonElement> entries = palette.getAsJsonObject().asMap();
            JsonObject names = new JsonObject();
            entries.forEach((key, value) -> {
                JsonObject name = new JsonObject();
                name.add(isFont(key, entries) ? "font" : "color", value);
                names.add(key, name);
            });
            theme.add("names", names);
        }
        if (!theme.has("dark")) {
            Boolean dark = darkThemePreferred(theme.get("platform"));
            if (dark != null) {
                theme.addProperty("dark", dark);
            }
        }
    }

    private static boolean isFont(String key, Map<String, JsonElement> palette) {
        Set<String> seen = new HashSet<>();
        String at = key;
        JsonElement value = palette.get(key);
        while (value != null && value.isJsonPrimitive() && value.getAsString().startsWith("$") && seen.add(at)) {
            at = value.getAsString().substring(1);
            value = palette.get(at);
        }
        if (value != null && !(value.isJsonPrimitive() && value.getAsString().startsWith("$"))) {
            return value.isJsonObject();
        }
        return at.startsWith("font");
    }

    private static Boolean darkThemePreferred(JsonElement platform) {
        if (platform == null || !platform.isJsonObject()) {
            return null;
        }
        for (String ws : List.of("*", "win32", "gtk", "cocoa")) {
            JsonElement settings = platform.getAsJsonObject().get(ws);
            if (settings != null && settings.isJsonObject()
                    && settings.getAsJsonObject().get("darkThemePreferred") instanceof JsonPrimitive dark && dark.isBoolean()) {
                return dark.getAsBoolean();
            }
        }
        return null;
    }
}
