package com.zapthemes.editor.palette;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Palettes saved for reuse, one JSON file each in a directory: by default
 * {@code ~/.zapthemes/palettes}, per user and outside any application; an application embedding the
 * editor can pass its own. A file holds the swatches in a theme's {@code swatches} shape and the
 * generator settings in the shape of a theme's {@code editor.generator}:
 *
 * <pre>{@code {
 *   "formatVersion": 1,
 *   "name": "Ocean",
 *   "dark": true,
 *   "swatches": { "ground": "#0E1A24", "accent": "#2AACB8" },
 *   "generator": { "rule": "analogous", "base": "#2AACB8", "locked": ["ground"] }
 * }}</pre>
 */
public final class PaletteStore {

    /** The format this class reads and writes. */
    public static final int FORMAT_VERSION = 1;
    private static final String SUFFIX = ".json";
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _.-]+");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path directory;

    public PaletteStore(Path directory) {
        this.directory = directory;
    }

    /** The store in {@code ~/.zapthemes/palettes}. */
    public static PaletteStore user() {
        return new PaletteStore(Path.of(System.getProperty("user.home"), ".zapthemes", "palettes"));
    }

    public Path directory() {
        return directory;
    }

    /** The names of the saved palettes, sorted; empty when the directory does not exist. */
    public List<String> list() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(f -> f.getFileName().toString()).filter(f -> f.endsWith(SUFFIX))
                    .map(f -> f.substring(0, f.length() - SUFFIX.length())).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Saves {@code palette} as {@code name}, replacing a palette of that name. */
    public void save(String name, Palette palette) {
        Path file = file(name);
        try {
            Files.createDirectories(directory);
            Files.writeString(file, GSON.toJson(toJson(name, palette)) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The palette saved as {@code name}. */
    public Palette load(String name) {
        try {
            return fromJson(JsonParser.parseString(Files.readString(file(name), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void delete(String name) {
        try {
            Files.deleteIfExists(file(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String name) {
        if (!NAME.matcher(name).matches() || name.startsWith(".")) {
            throw new IllegalArgumentException("A palette name has letters, digits, spaces, '_', '.' and '-' only: " + name);
        }
        return directory.resolve(name + SUFFIX);
    }

    static JsonObject toJson(String name, Palette palette) {
        JsonObject json = new JsonObject();
        json.addProperty("formatVersion", FORMAT_VERSION);
        json.addProperty("name", name);
        json.addProperty("dark", palette.dark());
        JsonObject swatches = new JsonObject();
        palette.toSwatches().forEach(swatches::addProperty);
        json.add("swatches", swatches);
        JsonObject generator = new JsonObject();
        if (palette.rule() != null) {
            generator.addProperty("rule", palette.rule().id());
        }
        if (palette.base() != null) {
            generator.addProperty("base", Swatch.hex(palette.base()));
        }
        JsonArray locked = new JsonArray();
        palette.swatches().stream().filter(Swatch::locked).forEach(s -> locked.add(s.name()));
        generator.add("locked", locked);
        json.add("generator", generator);
        return json;
    }

    static Palette fromJson(JsonElement element) {
        if (!element.isJsonObject()) {
            throw new JsonParseException("A palette is a JSON object");
        }
        JsonObject json = element.getAsJsonObject();
        int version = json.has("formatVersion") ? json.get("formatVersion").getAsInt() : FORMAT_VERSION;
        if (version > FORMAT_VERSION) {
            throw new JsonParseException("Palette format " + version + " is newer than this library's (" + FORMAT_VERSION
                    + "); update ZapThemes to open it");
        }
        boolean dark = json.has("dark") && json.get("dark").getAsBoolean();
        JsonObject generator = json.has("generator") ? json.getAsJsonObject("generator") : new JsonObject();
        List<String> locked = new ArrayList<>();
        if (generator.has("locked")) {
            generator.getAsJsonArray("locked").forEach(e -> locked.add(e.getAsString()));
        }
        List<Swatch> swatches = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("swatches").entrySet()) {
            swatches.add(new Swatch(entry.getKey(), Swatch.parse(entry.getValue().getAsString()), locked.contains(entry.getKey())));
        }
        Harmony rule = generator.has("rule") ? Harmony.byId(generator.get("rule").getAsString()) : null;
        return new Palette(swatches, dark, rule, generator.has("base") ? Swatch.parse(generator.get("base").getAsString()) : null);
    }
}
