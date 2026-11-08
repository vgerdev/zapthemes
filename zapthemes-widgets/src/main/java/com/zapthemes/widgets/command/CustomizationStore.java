package com.zapthemes.widgets.command;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * End-user customizations of a window's bars, in one JSON file the app chooses, one section per
 * bar: {@code "ribbon"}, {@code "quickAccessToolbar"}. Sections a bar does not know are kept, so
 * bars and app versions share the file.
 *
 * <pre>{@code
 * { "formatVersion": 1,
 *   "quickAccessToolbar": { "commands": ["file.save", "edit.undo", "|"] },
 *   "ribbon": { "tabs": [ ... ] } }
 * }</pre>
 *
 * A missing file is an empty store; an unreadable one is too, and the error is logged.
 */
public final class CustomizationStore {

    public static final int FORMAT_VERSION = 1;
    private static final System.Logger LOG = System.getLogger(CustomizationStore.class.getName());
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path file;
    private JsonObject root;

    private CustomizationStore(Path file, JsonObject root) {
        this.file = file;
        this.root = root;
    }

    /** Reads {@code file} if it exists. */
    public static CustomizationStore open(Path file) {
        JsonObject root = new JsonObject();
        if (file != null && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) {
                    root = parsed.getAsJsonObject();
                }
            } catch (IOException | JsonParseException e) {
                LOG.log(Level.WARNING, "Ignoring unreadable customization file " + file, e);
            }
        }
        return new CustomizationStore(file, root);
    }

    /** A store kept in memory only ({@link #save()} does nothing): for tests and previews. */
    public static CustomizationStore inMemory() {
        return new CustomizationStore(null, new JsonObject());
    }

    public Path file() {
        return file;
    }

    /** A copy of the section {@code name}, or {@code null}. */
    public JsonElement section(String name) {
        JsonElement element = root.get(name);
        return element == null ? null : element.deepCopy();
    }

    /** Replaces the section {@code name}; {@code null} removes it. Call {@link #save()} after. */
    public void put(String name, JsonElement element) {
        if (element == null) {
            root.remove(name);
        } else {
            root.add(name, element.deepCopy());
        }
    }

    /** Writes the file: a temporary file next to it, then a move over it. */
    public void save() {
        if (file == null) {
            return;
        }
        JsonObject out = new JsonObject();
        out.addProperty("formatVersion", FORMAT_VERSION);
        root.entrySet().stream().filter(e -> !e.getKey().equals("formatVersion")).forEach(e -> out.add(e.getKey(), e.getValue()));
        root = out;
        try {
            Path dir = file.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(out, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file, e);
        }
    }
}
