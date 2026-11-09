package com.zapthemes.core;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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

    /** The bundled themes, whose names changed when they were rewritten in format 2. */
    static final Set<String> BUNDLED = Set.of("zap-base", "zap-light", "zap-dark", "zap-high-contrast");

    /**
     * The bundled themes' version 1 palette entries whose common name is different in version 2.
     * {@code muted} was the raised surface color; the secondary text color is now {@code muted}.
     */
    static final Map<String, String> BUNDLED_V1_NAMES = Map.of(
            "bg", "background", "fg", "foreground", "muted", "raised", "qualifier", "muted", "line", "gridLine",
            "font.default", "foreground", "font.strong", "strong", "font.header", "heading1");

    /**
     * The bundled version 1 fonts that are now derived from the default font ({@code inherit}). A
     * version 1 theme that changed the default font did not change these, so its upgrade keeps them.
     */
    private static final Map<String, String> BUNDLED_V1_FONTS = Map.of(
            "font.strong", "{ \"height\": 9, \"style\": \"bold\" }", "font.header", "{ \"height\": 12, \"style\": \"bold\" }");

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
     * <p>
     * The bundled themes were rewritten on the common names in format 2, so a version 1 theme that
     * extends one of them has its entries and references renamed to match ({@link #BUNDLED_V1_NAMES}):
     * an entry {@code "bg"} that overrides zap-dark's background becomes the name {@code background}
     * that zap-dark's rules now use, and {@code "$qualifier"} becomes {@code "$muted"}. Such a theme
     * that changes {@code font.default} keeps the bold and header fonts it had, which are now derived
     * from the default font.
     */
    private static void v1ToV2(JsonObject theme) {
        boolean bundledParent = theme.get("extends") instanceof JsonPrimitive parent && BUNDLED.contains(parent.getAsString());
        JsonElement palette = theme.remove("palette");
        if (palette != null && palette.isJsonObject()) {
            Map<String, JsonElement> entries = palette.getAsJsonObject().asMap();
            JsonObject names = new JsonObject();
            entries.forEach((key, value) -> {
                String part = isFont(key, entries) ? "font" : "color";
                String renamed = bundledParent ? BUNDLED_V1_NAMES.getOrDefault(key, key) : key;
                JsonObject name = names.has(renamed) ? names.getAsJsonObject(renamed) : new JsonObject();
                if (!name.has(part)) {
                    name.add(part, value);
                }
                names.add(renamed, name);
            });
            if (bundledParent && entries.containsKey("font.default")) {
                BUNDLED_V1_FONTS.forEach((v1, font) -> {
                    if (!entries.containsKey(v1)) {
                        JsonObject name = new JsonObject();
                        name.add("font", JsonParser.parseString(font));
                        names.add(BUNDLED_V1_NAMES.get(v1), name);
                    }
                });
            }
            theme.add("names", names);
        }
        if (bundledParent) {
            renameReferences(theme);
        }
        if (!theme.has("dark")) {
            Boolean dark = darkThemePreferred(theme.get("platform"));
            if (dark != null) {
                theme.addProperty("dark", dark);
            }
        }
    }

    /** Renames every reference to a bundled theme's version 1 entry, anywhere in {@code element}. */
    private static void renameReferences(JsonElement element) {
        if (element instanceof JsonObject object) {
            for (Entry<String, JsonElement> e : object.entrySet()) {
                JsonElement renamed = renamed(e.getValue());
                if (renamed != null) {
                    e.setValue(renamed);
                } else {
                    renameReferences(e.getValue());
                }
            }
        } else if (element instanceof JsonArray array) {
            for (int i = 0; i < array.size(); i++) {
                JsonElement renamed = renamed(array.get(i));
                if (renamed != null) {
                    array.set(i, renamed);
                } else {
                    renameReferences(array.get(i));
                }
            }
        }
    }

    private static JsonElement renamed(JsonElement value) {
        if (value instanceof JsonPrimitive p && p.isString() && p.getAsString().startsWith("$")) {
            String name = BUNDLED_V1_NAMES.get(p.getAsString().substring(1));
            return name == null ? null : new JsonPrimitive("$" + name);
        }
        return null;
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
