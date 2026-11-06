package com.zapthemes.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.zapthemes.core.model.Theme;

/**
 * Reads and writes {@link Theme}s as JSON with GSON. Files of an older format (no
 * {@code formatVersion} is version 1) are upgraded in steps as they are read
 * ({@link ThemeUpgrades}); themes are always written in the current format,
 * {@value Theme#FORMAT_VERSION}.
 */
public final class ThemeJson {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final String FORMAT_VERSION = "formatVersion";

    private ThemeJson() {
    }

    /**
     * @throws ThemeFormatException if the JSON is malformed (the message includes line and column),
     *         is of a newer format than this library knows, or the theme has no {@code id}
     */
    public static Theme read(Reader reader, String source) {
        Theme theme;
        try {
            JsonElement json = JsonParser.parseReader(reader);
            if (json == null || json.isJsonNull()) {
                throw new ThemeFormatException(source + ": empty theme file", null);
            }
            if (!json.isJsonObject()) {
                throw new ThemeFormatException(source + ": a theme must be a JSON object", null);
            }
            JsonObject object = json.getAsJsonObject();
            int version = version(object, source);
            if (version > Theme.FORMAT_VERSION) {
                throw new ThemeFormatException(source + ": format version " + version + " is newer than this library's ("
                        + Theme.FORMAT_VERSION + "); update ZapThemes to read it", null);
            }
            ThemeUpgrades.upgrade(object, version);
            theme = GSON.fromJson(object, Theme.class);
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException | NumberFormatException e) {
            throw new ThemeFormatException(source + ": " + e.getMessage(), e);
        }
        if (theme.id() == null || theme.id().isBlank()) {
            throw new ThemeFormatException(source + ": theme has no \"id\"", null);
        }
        return theme;
    }

    private static int version(JsonObject object, String source) {
        JsonElement version = object.get(FORMAT_VERSION);
        if (version == null) {
            return 1;
        }
        if (!(version instanceof JsonPrimitive primitive) || !primitive.isNumber() || primitive.getAsInt() < 1) {
            throw new ThemeFormatException(source + ": \"" + FORMAT_VERSION + "\" must be a positive integer, got: " + version, null);
        }
        return primitive.getAsInt();
    }

    /** Writes {@code theme} in the current format. */
    public static void write(Theme theme, Writer writer) throws IOException {
        GSON.toJson(theme, writer);
        writer.flush();
    }

    /** A theme file that cannot be used. */
    public static final class ThemeFormatException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ThemeFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
