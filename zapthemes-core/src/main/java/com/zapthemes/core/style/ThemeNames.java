package com.zapthemes.core.style;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.swt.graphics.RGBA;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zapthemes.core.model.FontSpec;
import com.zapthemes.core.model.Theme;

/**
 * Resolves {@code "$name"} references against a theme's {@code swatches} and {@code names}.
 *
 * <p>A swatch is a color. A common name holds a {@code color}, a {@code font} or both, and a
 * reference takes the part its use asks for: a color key ({@code background}, {@code ...Color})
 * takes the color, a font key ({@code font}, {@code ...Font}) the font, and a key of any other type
 * the color if the name has one, else its font. A part may itself be a reference, to a swatch or to
 * another name, so names can build on each other; cycles are errors.
 *
 * <p>A font is an object ({@code name}, {@code height}, {@code style}); {@code name} may be a list
 * of families, and {@code "inherit": "$name"} starts from another font, overriding only what it
 * gives.
 */
public final class ThemeNames {

    /** What a reference is asked for. */
    public enum Part {
        COLOR("color"), FONT("font"),
        /** A key of a type the theme does not know: the color, else the font. */
        ANY(null);

        private final String key;

        Part(String key) {
            this.key = key;
        }

        /** The name entry's key for this part, {@code "color"} or {@code "font"}; null for ANY. */
        public String key() {
            return key;
        }
    }

    /** The parts a common name may have. */
    public static final Set<String> PARTS = Set.of("color", "font");
    static final String INHERIT = "inherit";

    private static final ThemeNames NONE = new ThemeNames(Map.of(), Map.of());

    private final Map<String, JsonElement> swatches;
    private final Map<String, JsonElement> names;

    public ThemeNames(Map<String, JsonElement> swatches, Map<String, JsonElement> names) {
        this.swatches = swatches;
        this.names = names;
    }

    public static ThemeNames of(Theme theme) {
        return new ThemeNames(theme.swatches(), theme.names());
    }

    static ThemeNames none() {
        return NONE;
    }

    /** The color of common name or swatch {@code name}, if it has one. */
    public Optional<RGBA> color(String name) {
        JsonElement value = colorPartOf(name);
        return value == null ? Optional.empty() : Optional.of(color(value, "$" + name));
    }

    /** The font of common name {@code name}, if it has one. */
    public Optional<FontSpec> font(String name) {
        if (!names.containsKey(name) || !partOf(name, Part.FONT).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(font(reference(name), "$" + name));
    }

    private JsonElement colorPartOf(String name) {
        if (swatches.containsKey(name)) {
            return reference(name);
        }
        return names.containsKey(name) && partOf(name, Part.COLOR).isPresent() ? reference(name) : null;
    }

    private static JsonElement reference(String name) {
        return new com.google.gson.JsonPrimitive("$" + name);
    }

    /**
     * {@code value} with references followed until a plain value, for {@code part}.
     *
     * @param where the key being read, for messages
     * @throws IllegalArgumentException for an unknown name or a name without the part
     * @throws IllegalStateException for a reference cycle
     */
    public JsonElement resolve(JsonElement value, Part part, String where) {
        Set<String> seen = new LinkedHashSet<>();
        while (isReference(value)) {
            String ref = value.getAsString().substring(1);
            if (!seen.add(ref)) {
                throw new IllegalStateException("Reference cycle at \"" + where + "\": " + chain(seen, ref));
            }
            JsonElement swatch = swatches.get(ref);
            if (swatch != null) {
                if (part == Part.FONT) {
                    throw new IllegalArgumentException("\"$" + ref + "\" in \"" + where + "\" is a swatch, a color, not a font");
                }
                value = swatch;
                part = Part.COLOR;
                continue;
            }
            if (!names.containsKey(ref)) {
                throw new IllegalArgumentException("Unknown name \"$" + ref + "\" in \"" + where + "\"");
            }
            Part asked = part;
            Map.Entry<Part, JsonElement> found = partOf(ref, part).orElseThrow(() -> new IllegalArgumentException(
                    "Name \"$" + ref + "\" in \"" + where + "\" has no " + (asked == Part.ANY ? "color or font" : asked.key())));
            part = found.getKey();
            value = found.getValue();
        }
        return value;
    }

    private Optional<Map.Entry<Part, JsonElement>> partOf(String name, Part part) {
        JsonElement entry = names.get(name);
        if (entry == null || !entry.isJsonObject()) {
            throw new IllegalArgumentException("Name \"" + name + "\" must be an object of \"color\" and/or \"font\", got: " + entry);
        }
        JsonObject parts = entry.getAsJsonObject();
        for (Part candidate : part == Part.ANY ? List.of(Part.COLOR, Part.FONT) : List.of(part)) {
            JsonElement value = parts.get(candidate.key());
            if (value != null && !value.isJsonNull()) {
                return Optional.of(Map.entry(candidate, value));
            }
        }
        return Optional.empty();
    }

    private static String chain(Set<String> seen, String again) {
        List<String> path = new ArrayList<>();
        seen.forEach(s -> path.add("$" + s));
        path.add("$" + again);
        return String.join(" -> ", path);
    }

    static boolean isReference(JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && value.getAsString().startsWith("$");
    }

    /** {@code "#RRGGBB"}, {@code "#RRGGBBAA"} or a reference to one. */
    RGBA color(JsonElement value, String where) {
        JsonElement resolved = resolve(value, Part.COLOR, where);
        if (!resolved.isJsonPrimitive() || !resolved.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Color \"" + where + "\" must be #RRGGBB or #RRGGBBAA, got: " + resolved);
        }
        return parseColor(where, resolved.getAsString());
    }

    /** A font object or a reference to one, with {@code inherit} chains merged. */
    FontSpec font(JsonElement value, String where) {
        return font(value, where, new LinkedHashSet<>());
    }

    private FontSpec font(JsonElement value, String where, Set<JsonElement> seen) {
        JsonElement resolved = resolve(value, Part.FONT, where);
        if (!resolved.isJsonObject()) {
            throw new IllegalArgumentException("Font \"" + where + "\" must be an object, got: " + resolved);
        }
        if (!seen.add(resolved)) {
            throw new IllegalStateException("Font \"inherit\" cycle at \"" + where + "\"");
        }
        JsonObject font = resolved.getAsJsonObject();
        FontSpec base = font.has(INHERIT) ? font(font.get(INHERIT), where, seen) : new FontSpec(List.of(), 0, 0);
        List<String> families = base.names();
        if (font.has("name")) {
            JsonElement name = font.get("name");
            families = new ArrayList<>();
            if (name.isJsonArray()) {
                for (JsonElement family : name.getAsJsonArray()) {
                    families.add(family.getAsString());
                }
            } else {
                families.add(name.getAsString());
            }
        }
        int height = font.has("height") ? font.get("height").getAsInt() : base.height();
        int style = font.has("style") ? FontSpec.parseStyle(font.get("style").getAsString()) : base.style();
        return new FontSpec(families, height, style);
    }

    static RGBA parseColor(String key, String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (hex.length() != 6 && hex.length() != 8) {
            throw new IllegalArgumentException("Color \"" + key + "\" must be #RRGGBB or #RRGGBBAA, got: " + text);
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int a = hex.length() == 8 ? Integer.parseInt(hex.substring(6, 8), 16) : 255;
            return new RGBA(r, g, b, a);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Color \"" + key + "\" is not hex: " + text, e);
        }
    }

    Map<String, JsonElement> swatches() {
        return swatches;
    }

    Map<String, JsonElement> names() {
        return names;
    }
}
