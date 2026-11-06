package com.zapthemes.widgets.titlebar;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * What a user made of a {@link TitleBar}'s quick access toolbar: the commands shown, in order, the
 * commands known when it was saved, and whether the toolbar is below the bar. Immutable; kept in
 * {@link Properties} (on its own in a file the app chooses, or among the app's own preferences):
 *
 * <pre>
 * quickAccess.shown=autosave,save,undo,redo
 * quickAccess.known=autosave,save,undo,redo,new,open,print,email
 * quickAccess.below=false
 * </pre>
 *
 * A command the app added after the file was saved isn't in {@code known}, so it keeps the app's
 * default; an id the app no longer has is ignored.
 */
public record TitleBarCustomization(List<String> shown, Set<String> known, boolean below) {

    public static final String SHOWN = "quickAccess.shown";
    public static final String KNOWN = "quickAccess.known";
    public static final String BELOW = "quickAccess.below";

    public TitleBarCustomization {
        shown = List.copyOf(new LinkedHashSet<>(Objects.requireNonNull(shown, "shown")));
        known = Set.copyOf(Objects.requireNonNull(known, "known"));
    }

    /** The current state of {@code bar}. */
    public static TitleBarCustomization of(TitleBar bar) {
        List<String> shown = new ArrayList<>();
        Set<String> known = new LinkedHashSet<>();
        for (TitleBarItem item : bar.getQuickAccessItems()) {
            known.add(item.getId());
            if (item.isShown()) {
                shown.add(item.getId());
            }
        }
        return new TitleBarCustomization(shown, known, bar.isQuickAccessBelow());
    }

    /** Shows the {@link #shown} commands in this order, hides the other known ones, sets the position. */
    void applyTo(TitleBar bar) {
        List<String> order = new ArrayList<>(shown);
        // Commands the file doesn't know keep their place relative to the app's order, at the end.
        for (TitleBarItem item : bar.getQuickAccessItems()) {
            String id = item.getId();
            if (shown.contains(id)) {
                item.setShown(true);
            } else if (known.contains(id)) {
                item.setShown(false);
            }
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        bar.setQuickAccessOrder(order);
        bar.setQuickAccessBelow(below);
    }

    public void store(Properties properties) {
        properties.setProperty(SHOWN, String.join(",", shown));
        properties.setProperty(KNOWN, String.join(",", known.stream().sorted().toList()));
        properties.setProperty(BELOW, Boolean.toString(below));
    }

    /** Reads what {@link #store} wrote; missing keys mean nothing was customized. */
    public static TitleBarCustomization load(Properties properties) {
        List<String> shown = ids(properties.getProperty(SHOWN, ""));
        Set<String> known = new LinkedHashSet<>(ids(properties.getProperty(KNOWN, "")));
        known.addAll(shown);
        return new TitleBarCustomization(shown, known, Boolean.parseBoolean(properties.getProperty(BELOW, "false")));
    }

    public static TitleBarCustomization load(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return load(properties);
    }

    /** Writes the file through a temporary file, so a crash never leaves half of it. */
    public void save(Path file) throws IOException {
        Properties properties = new Properties();
        if (Files.isRegularFile(file)) {
            // Keep what else the app put in the same file.
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        store(properties);
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            properties.store(writer, "ZapThemes title bar");
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<String> ids(String value) {
        return Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }
}
