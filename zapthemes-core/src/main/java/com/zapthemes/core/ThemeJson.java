package com.zapthemes.core;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.zapthemes.core.model.Theme;

/** Reads and writes {@link Theme}s as JSON with GSON. */
public final class ThemeJson {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private ThemeJson() {
    }

    /**
     * @throws ThemeFormatException if the JSON is malformed (the message includes line and column)
     *         or the theme has no {@code id}
     */
    public static Theme read(Reader reader, String source) {
        Theme theme;
        try {
            theme = GSON.fromJson(reader, Theme.class);
        } catch (JsonParseException e) {
            throw new ThemeFormatException(source + ": " + e.getMessage(), e);
        }
        if (theme == null) {
            throw new ThemeFormatException(source + ": empty theme file", null);
        }
        if (theme.id() == null || theme.id().isBlank()) {
            throw new ThemeFormatException(source + ": theme has no \"id\"", null);
        }
        return theme;
    }

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
