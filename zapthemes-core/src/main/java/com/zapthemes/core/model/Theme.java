package com.zapthemes.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

/**
 * A theme as stored in JSON.
 *
 * <pre>{@code
 * {
 *   "id": "zap-dark", "name": "Zap Dark", "extends": "zap-base",
 *   "palette":  { "bg": "#1E1F22", "font.default": { "name": "Segoe UI", "height": 9 } },
 *   "defaults": { "background": "$bg" },
 *   "controls": { "Button": { "background": "#2B2D30", "states": { "hover": { ... } } },
 *                 "Button[CHECK]": { ... } },
 *   "classes":  { "Button.primary": { ... }, "*.danger": { ... } },
 *   "platform": { "win32": { "darkScrollbars": true } }
 * }
 * }</pre>
 *
 * Property values are kept as raw {@link JsonElement}s so stylers can define their own keys;
 * {@link com.zapthemes.core.style.ResolvedStyle} interprets them.
 */
public record Theme(
        String id,
        String name,
        @SerializedName("extends") String parent,
        Map<String, JsonElement> palette,
        Map<String, JsonElement> defaults,
        Map<String, Map<String, JsonElement>> controls,
        Map<String, Map<String, JsonElement>> classes,
        Map<String, Map<String, JsonElement>> platform) {

    /** A theme with no rules: every control keeps its platform look. */
    public static final Theme EMPTY = new Theme("empty", "No theme", null, null, null, null, null, null);

    public Theme {
        palette = frozen(palette);
        defaults = frozen(defaults);
        controls = frozenRules(controls);
        classes = frozenRules(classes);
        platform = frozenRules(platform);
    }

    /** Settings for one SWT windowing system ({@code "win32"}, {@code "gtk"}, {@code "cocoa"}). */
    public Map<String, JsonElement> platformSettings(String ws) {
        return platform.getOrDefault(ws, Map.of());
    }

    /** Returns this theme layered on top of {@code base}: every value here wins. */
    public Theme over(Theme base) {
        return new Theme(id, name, base.parent, merge(base.palette, palette), merge(base.defaults, defaults),
                mergeRules(base.controls, controls), mergeRules(base.classes, classes),
                mergeRules(base.platform, platform));
    }

    private static Map<String, Map<String, JsonElement>> mergeRules(
            Map<String, Map<String, JsonElement>> base, Map<String, Map<String, JsonElement>> top) {
        Map<String, Map<String, JsonElement>> rules = new LinkedHashMap<>(base);
        top.forEach((key, props) -> rules.put(key, merge(rules.getOrDefault(key, Map.of()), props)));
        return rules;
    }

    private static Map<String, JsonElement> merge(Map<String, JsonElement> base, Map<String, JsonElement> top) {
        Map<String, JsonElement> merged = new LinkedHashMap<>(base);
        merged.putAll(top);
        return merged;
    }

    private static Map<String, Map<String, JsonElement>> frozenRules(Map<String, Map<String, JsonElement>> rules) {
        Map<String, Map<String, JsonElement>> copy = new LinkedHashMap<>();
        if (rules != null) {
            rules.forEach((key, props) -> copy.put(key, frozen(props)));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, JsonElement> frozen(Map<String, JsonElement> map) {
        Map<String, JsonElement> copy = new LinkedHashMap<>();
        if (map != null) {
            map.forEach((key, value) -> {
                if (value != null && !value.isJsonNull()) {
                    copy.put(key, value);
                }
            });
        }
        return Collections.unmodifiableMap(copy);
    }
}
