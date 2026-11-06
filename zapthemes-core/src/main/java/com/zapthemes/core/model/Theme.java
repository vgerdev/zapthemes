package com.zapthemes.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * A theme as stored in JSON, format version {@value #FORMAT_VERSION}.
 *
 * <pre>{@code
 * {
 *   "formatVersion": 2, "id": "ocean-dark", "name": "Ocean Dark", "dark": true,
 *   "swatches": { "ink": "#0E1A24", "sea": "#2AACB8", "foam": "#DDE7EE" },
 *   "names": {
 *     "background": { "color": "$ink" },
 *     "foreground": { "color": "$foam", "font": { "name": ["Segoe UI", "Cantarell"], "height": 9 } },
 *     "heading1":   { "color": "$foam", "font": { "inherit": "$foreground", "height": 14, "style": "bold" } },
 *     "border":     { "color": "#2B4256" }
 *   },
 *   "defaults": { "background": "$background", "foreground": "$foreground", "font": "$foreground" },
 *   "controls": { "Button": { "borderColor": "$border", "states": { "hover": { ... } } },
 *                 "Button[CHECK]": { ... } },
 *   "classes":  { "Button.primary": { ... }, "*.danger": { ... } },
 *   "platform": { "win32": { "darkScrollbars": true } },
 *   "registries": { "colors": { "HYPERLINK_COLOR": "$accent" }, "fonts": { "org.eclipse.jface.dialogfont": "$foreground" } },
 *   "editor": { ... }
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code swatches} are raw colors (the theme editor's palette); {@code names} are the common
 *       names (foreground, border, heading1, ...), each holding a {@code color}, a {@code font} or
 *       both. Rules refer to either with {@code "$name"}; a color key takes the name's color and a
 *       font key its font ({@link com.zapthemes.core.style.ThemeNames}).</li>
 *   <li>{@code registries} holds named resources for toolkits that keep their own registries
 *       (JFace's {@code ColorRegistry} and {@code FontRegistry}); an extension such as
 *       zapthemes-jface applies them.</li>
 *   <li>{@code dark} says whether the theme is dark, for tools such as the theme editor; theming
 *       does not read it.</li>
 *   <li>{@code editor} is the theme editor's own state (generator settings, locked swatches, the
 *       library version it wrote the theme with); theming ignores it, and it is kept as read.</li>
 * </ul>
 *
 * Files of older formats are upgraded when read ({@link com.zapthemes.core.ThemeJson}), so a
 * {@code Theme} is always of the current format.
 *
 * <p>Property values are kept as raw {@link JsonElement}s so stylers can define their own keys;
 * {@link com.zapthemes.core.style.ResolvedStyle} interprets them.
 */
public record Theme(
        int formatVersion,
        String id,
        String name,
        @SerializedName("extends") String parent,
        Boolean dark,
        Map<String, JsonElement> swatches,
        Map<String, JsonElement> names,
        Map<String, JsonElement> defaults,
        Map<String, Map<String, JsonElement>> controls,
        Map<String, Map<String, JsonElement>> classes,
        Map<String, Map<String, JsonElement>> platform,
        Map<String, Map<String, JsonElement>> registries,
        Map<String, JsonElement> editor) {

    /** The format this library reads and writes; older files are upgraded on load. */
    public static final int FORMAT_VERSION = 2;

    /** A theme with no rules: every control keeps its platform look. */
    public static final Theme EMPTY = new Theme(FORMAT_VERSION, "empty", "No theme", null, null, null, null, null, null,
            null, null, null, null);

    public Theme {
        formatVersion = FORMAT_VERSION;
        swatches = frozen(swatches);
        names = frozen(names);
        defaults = frozen(defaults);
        controls = frozenRules(controls);
        classes = frozenRules(classes);
        platform = frozenRules(platform);
        registries = frozenRules(registries);
        editor = frozen(editor);
    }

    /** Settings for one SWT windowing system ({@code "win32"}, {@code "gtk"}, {@code "cocoa"}). */
    public Map<String, JsonElement> platformSettings(String ws) {
        return platform.getOrDefault(ws, Map.of());
    }

    /**
     * Returns this theme layered on top of {@code base}: every value here wins. A common name merges
     * by part, so a theme that gives a name only a color keeps the base's font for it.
     */
    public Theme over(Theme base) {
        return new Theme(FORMAT_VERSION, id, name, base.parent, dark != null ? dark : base.dark,
                merge(base.swatches, swatches), mergeNames(base.names, names), merge(base.defaults, defaults),
                mergeRules(base.controls, controls), mergeRules(base.classes, classes),
                mergeRules(base.platform, platform), mergeRules(base.registries, registries), merge(base.editor, editor));
    }

    private static Map<String, JsonElement> mergeNames(Map<String, JsonElement> base, Map<String, JsonElement> top) {
        Map<String, JsonElement> merged = new LinkedHashMap<>(base);
        top.forEach((key, entry) -> {
            JsonElement under = merged.get(key);
            if (under != null && under.isJsonObject() && entry.isJsonObject()) {
                JsonObject parts = under.getAsJsonObject().deepCopy();
                entry.getAsJsonObject().entrySet().forEach(part -> parts.add(part.getKey(), part.getValue()));
                merged.put(key, parts);
            } else {
                merged.put(key, entry);
            }
        });
        return merged;
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
