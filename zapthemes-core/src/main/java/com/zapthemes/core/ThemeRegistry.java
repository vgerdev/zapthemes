package com.zapthemes.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.zapthemes.core.model.Theme;

/**
 * Holds the known themes by id and resolves {@code "extends"} chains.
 * Not thread-safe; use it from the UI thread or synchronize externally.
 */
public final class ThemeRegistry {

    private final Map<String, Theme> themes = new LinkedHashMap<>();

    public Theme register(Theme theme) {
        themes.put(theme.id(), theme);
        return theme;
    }

    /** Loads a theme from the classpath, e.g. {@code "/zapthemes/themes/zap-dark.json"}. */
    public Theme loadResource(ClassLoader loader, String resource) {
        String name = resource.startsWith("/") ? resource.substring(1) : resource;
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalArgumentException("Theme resource not found: " + resource);
            }
            return register(ThemeJson.read(new InputStreamReader(in, StandardCharsets.UTF_8), resource));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Theme loadFile(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return register(ThemeJson.read(reader, file.toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public List<Theme> themes() {
        return new ArrayList<>(themes.values());
    }

    /** Returns the theme with its whole {@code extends} chain merged in. */
    public Theme resolve(String id) {
        Theme theme = themes.get(id);
        if (theme == null) {
            throw new IllegalArgumentException("Unknown theme: " + id);
        }
        Set<String> seen = new LinkedHashSet<>();
        seen.add(id);
        while (theme.parent() != null) {
            if (!seen.add(theme.parent())) {
                throw new IllegalStateException("Cyclic theme inheritance: " + seen + " -> " + theme.parent());
            }
            Theme base = themes.get(theme.parent());
            if (base == null) {
                throw new IllegalStateException("Theme " + id + " extends unknown theme " + theme.parent());
            }
            theme = theme.over(base);
        }
        return theme;
    }
}
