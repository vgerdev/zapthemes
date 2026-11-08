package com.zapthemes.core.catalog;

import java.util.List;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/**
 * One property a theme can set on a control type, such as {@code Table.headerBackground}.
 *
 * @param key the key in the type's rule; may be dotted ({@code itemRenderer.foregroundColor})
 * @param kind the type of its value
 * @param defaultName for a color or font, the common name ({@link BaseNames}) it takes unless a
 *        theme says otherwise: the default map. Null when the property has none, so a theme that
 *        does not set it leaves the widget's own look.
 * @param defaultValue for other kinds, the value it takes by default, or null for none
 * @param choices the allowed values of a {@link PropertyKind#CHOICE}; empty otherwise
 * @param description one line for tools such as the theme editor; may be empty
 * @param size for an {@link PropertyKind#INTEGER}, whether it is a size in pixels (a corner radius,
 *        a thickness): sizes follow the {@linkplain com.zapthemes.core.ThemeManager#setZoom zoom}
 */
public record PropertySpec(String key, PropertyKind kind, String defaultName, JsonElement defaultValue,
        List<String> choices, String description, boolean size) {

    public PropertySpec {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(kind, "kind");
        choices = choices == null ? List.of() : List.copyOf(choices);
        description = description == null ? "" : description;
        if (defaultName != null && kind != PropertyKind.COLOR && kind != PropertyKind.FONT) {
            throw new IllegalArgumentException(key + ": only colors and fonts have a default name");
        }
        if (size && kind != PropertyKind.INTEGER) {
            throw new IllegalArgumentException(key + ": only integers are sizes");
        }
    }

    /** A property that is not a size. */
    public PropertySpec(String key, PropertyKind kind, String defaultName, JsonElement defaultValue,
            List<String> choices, String description) {
        this(key, kind, defaultName, defaultValue, choices, description, false);
    }

    public static PropertySpec color(String key, String defaultName, String description) {
        return new PropertySpec(key, PropertyKind.COLOR, defaultName, null, null, description);
    }

    public static PropertySpec font(String key, String defaultName, String description) {
        return new PropertySpec(key, PropertyKind.FONT, defaultName, null, null, description);
    }

    public static PropertySpec integer(String key, Integer defaultValue, String description) {
        return new PropertySpec(key, PropertyKind.INTEGER, null, defaultValue == null ? null : new JsonPrimitive(defaultValue),
                null, description);
    }

    /** An integer that is a size in pixels, scaled by the zoom (see {@link #size()}). */
    public static PropertySpec size(String key, Integer defaultValue, String description) {
        return new PropertySpec(key, PropertyKind.INTEGER, null, defaultValue == null ? null : new JsonPrimitive(defaultValue),
                null, description, true);
    }

    public static PropertySpec bool(String key, Boolean defaultValue, String description) {
        return new PropertySpec(key, PropertyKind.BOOLEAN, null, defaultValue == null ? null : new JsonPrimitive(defaultValue),
                null, description);
    }

    public static PropertySpec string(String key, String description) {
        return new PropertySpec(key, PropertyKind.STRING, null, null, null, description);
    }

    public static PropertySpec choice(String key, String defaultValue, String description, String... choices) {
        return new PropertySpec(key, PropertyKind.CHOICE, null, defaultValue == null ? null : new JsonPrimitive(defaultValue),
                List.of(choices), description);
    }

    /**
     * A color or font property typed by its key's name ({@link PropertyKind#ofKey}).
     *
     * @throws IllegalArgumentException if the name implies neither
     */
    public static PropertySpec of(String key, String defaultName, String description) {
        PropertyKind kind = PropertyKind.ofKey(key);
        if (kind == null) {
            throw new IllegalArgumentException("\"" + key + "\" is not named as a color or a font; give its kind");
        }
        return new PropertySpec(key, kind, defaultName, null, null, description);
    }

    /** This spec with another default name. */
    public PropertySpec withDefaultName(String name) {
        return new PropertySpec(key, kind, name, defaultValue, choices, description, size);
    }

    /** This spec with {@code other}'s default and description where this one has none. */
    PropertySpec mergedWith(PropertySpec other) {
        return new PropertySpec(key, kind, defaultName != null ? defaultName : other.defaultName,
                defaultValue != null ? defaultValue : other.defaultValue, choices.isEmpty() ? other.choices : choices,
                description.isEmpty() ? other.description : description, size || other.size);
    }
}
