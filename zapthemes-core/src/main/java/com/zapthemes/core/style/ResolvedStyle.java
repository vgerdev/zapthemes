package com.zapthemes.core.style;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.swt.graphics.RGBA;

import com.google.gson.JsonElement;
import com.zapthemes.core.model.FontSpec;

/**
 * The merged properties that apply to one widget. Values may refer to the theme's swatches and
 * common names with {@code "$name"}; references are resolved when a value is read, for the part
 * the read asks for ({@link ThemeNames}).
 */
public final class ResolvedStyle {

    private final Map<String, JsonElement> props;
    private final ThemeNames names;
    /** What the style is for, for {@link KeyRecorder}: a control type, registries.x or platform. */
    private final String owner;

    ResolvedStyle(Map<String, JsonElement> props, ThemeNames names) {
        this(props, names, null);
    }

    ResolvedStyle(Map<String, JsonElement> props, ThemeNames names, String owner) {
        this.props = props;
        this.names = names;
        this.owner = owner;
    }
    /** Key of the per-state overrides: {@code "states": { "hover": { ... }, "disabled": { ... } }}. */
    public static final String STATES = "states";

    public boolean has(String key) {
        KeyRecorder.record(owner, key);
        return props.containsKey(key);
    }

    /** The property keys, including {@value #STATES}. */
    public Set<String> keys() {
        return props.keySet();
    }

    /** The names of the states this style defines overrides for. */
    public Set<String> states() {
        JsonElement states = props.get(STATES);
        return states != null && states.isJsonObject() ? states.getAsJsonObject().keySet() : Set.of();
    }

    /**
     * This style with the overrides of {@code state} (e.g. {@code "hover"}) applied, or this style if
     * it has none. Used by owner-draw renderers.
     */
    public ResolvedStyle state(String state) {
        JsonElement states = props.get(STATES);
        if (states == null || !states.isJsonObject() || !states.getAsJsonObject().has(state)) {
            return this;
        }
        JsonElement overrides = states.getAsJsonObject().get(state);
        if (!overrides.isJsonObject()) {
            throw new IllegalArgumentException("State \"" + state + "\" must be an object, got: " + overrides);
        }
        Map<String, JsonElement> merged = new LinkedHashMap<>(props);
        merged.remove(STATES);
        overrides.getAsJsonObject().entrySet().forEach(e -> merged.put(e.getKey(), e.getValue()));
        return new ResolvedStyle(merged, names, owner);
    }

    /** {@code "#RRGGBB"} or {@code "#RRGGBBAA"}, directly or via a swatch or a name's color. */
    public Optional<RGBA> color(String key) {
        KeyRecorder.record(owner, key);
        JsonElement value = props.get(key);
        return value == null ? Optional.empty() : Optional.of(names.color(value, key));
    }

    /** A font object, directly or via a name's font. */
    public Optional<FontSpec> font(String key) {
        KeyRecorder.record(owner, key);
        JsonElement value = props.get(key);
        return value == null ? Optional.empty() : Optional.of(names.font(value, key));
    }

    public Optional<Boolean> bool(String key) {
        return value(key).map(JsonElement::getAsBoolean);
    }

    public Optional<Integer> integer(String key) {
        return value(key).map(JsonElement::getAsInt);
    }

    public Optional<String> string(String key) {
        return value(key).map(JsonElement::getAsString);
    }

    /** The raw value with references resolved: a name's color if it has one, else its font. */
    public Optional<JsonElement> value(String key) {
        KeyRecorder.record(owner, key);
        JsonElement value = props.get(key);
        return value == null ? Optional.empty() : Optional.of(names.resolve(value, ThemeNames.Part.ANY, key));
    }

    @Override
    public String toString() {
        return props.toString();
    }
}
