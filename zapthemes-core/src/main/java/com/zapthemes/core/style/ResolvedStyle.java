package com.zapthemes.core.style;

import java.util.Map;
import java.util.Optional;

import org.eclipse.swt.graphics.RGBA;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zapthemes.core.model.FontSpec;

/**
 * The merged properties that apply to one widget. Values may reference the theme palette with
 * {@code "$name"}; references are resolved when a value is read.
 */
public final class ResolvedStyle {

    private static final int MAX_REFERENCE_DEPTH = 16;

    private final Map<String, JsonElement> props;
    private final Map<String, JsonElement> palette;

    ResolvedStyle(Map<String, JsonElement> props, Map<String, JsonElement> palette) {
        this.props = props;
        this.palette = palette;
    }

    public boolean has(String key) {
        return props.containsKey(key);
    }

    /** {@code "#RRGGBB"} or {@code "#RRGGBBAA"}, directly or via a palette reference. */
    public Optional<RGBA> color(String key) {
        return value(key).map(value -> parseColor(key, value.getAsString()));
    }

    /** A font object, directly or via a palette reference. */
    public Optional<FontSpec> font(String key) {
        return value(key).map(value -> {
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException("Font \"" + key + "\" must be an object, got: " + value);
            }
            JsonObject font = value.getAsJsonObject();
            String name = font.has("name") ? font.get("name").getAsString() : null;
            int height = font.has("height") ? font.get("height").getAsInt() : 0;
            String style = font.has("style") ? font.get("style").getAsString() : null;
            return new FontSpec(name, height, FontSpec.parseStyle(style));
        });
    }

    public Optional<Integer> integer(String key) {
        return value(key).map(JsonElement::getAsInt);
    }

    public Optional<String> string(String key) {
        return value(key).map(JsonElement::getAsString);
    }

    /** The raw value with palette references resolved. */
    public Optional<JsonElement> value(String key) {
        JsonElement value = props.get(key);
        for (int depth = 0; value != null && isReference(value); depth++) {
            if (depth == MAX_REFERENCE_DEPTH) {
                throw new IllegalStateException("Palette reference cycle at \"" + key + "\"");
            }
            String ref = value.getAsString().substring(1);
            value = palette.get(ref);
            if (value == null) {
                throw new IllegalArgumentException("Unknown palette entry \"$" + ref + "\" in \"" + key + "\"");
            }
        }
        return Optional.ofNullable(value);
    }

    private static boolean isReference(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().startsWith("$");
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

    @Override
    public String toString() {
        return props.toString();
    }
}
