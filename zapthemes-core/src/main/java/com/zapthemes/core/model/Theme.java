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
 *   "controls": { "Button": { "background": "#2B2D30" }, "Button[CHECK]": { ... } }
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
        Map<String, Map<String, JsonElement>> controls) {

    /** A theme with no rules: every control keeps its platform look. */
    public static final Theme EMPTY = new Theme("empty", "No theme", null, null, null, null);

    public Theme {
        palette = frozen(palette);
        defaults = frozen(defaults);
        Map<String, Map<String, JsonElement>> rules = new LinkedHashMap<>();
        if (controls != null) {
            controls.forEach((type, props) -> rules.put(type, frozen(props)));
        }
        controls = Collections.unmodifiableMap(rules);
    }

    /** Returns this theme layered on top of {@code base}: every value here wins. */
    public Theme over(Theme base) {
        Map<String, Map<String, JsonElement>> rules = new LinkedHashMap<>(base.controls);
        controls.forEach((type, props) -> {
            Map<String, JsonElement> merged = new LinkedHashMap<>(rules.getOrDefault(type, Map.of()));
            merged.putAll(props);
            rules.put(type, merged);
        });
        return new Theme(id, name, base.parent, merge(base.palette, palette), merge(base.defaults, defaults), rules);
    }

    private static Map<String, JsonElement> merge(Map<String, JsonElement> base, Map<String, JsonElement> top) {
        Map<String, JsonElement> merged = new LinkedHashMap<>(base);
        merged.putAll(top);
        return merged;
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
