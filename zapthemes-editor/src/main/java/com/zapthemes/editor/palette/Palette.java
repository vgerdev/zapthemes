package com.zapthemes.editor.palette;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.eclipse.swt.graphics.RGB;

import com.zapthemes.core.catalog.BaseNames;
import com.zapthemes.core.catalog.NameSpec;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ThemeNames;

/**
 * Step 1 of the theme editor: raw colors (swatches) with no meaning yet, plus how they were
 * generated. {@code rule} and {@code base} are null for a palette that was not generated, such as
 * one taken from a theme or an image.
 */
public record Palette(List<Swatch> swatches, boolean dark, Harmony rule, RGB base) {

    /** Fewest and most swatches the generator makes. */
    public static final int MIN_SIZE = 5;
    public static final int MAX_SIZE = 8;

    public Palette {
        swatches = List.copyOf(swatches);
        base = base == null ? null : new RGB(base.red, base.green, base.blue);
        if (swatches.stream().map(Swatch::name).distinct().count() != swatches.size()) {
            throw new IllegalArgumentException("Swatch names must be unique: " + swatches);
        }
    }

    /** A palette of {@code colors}, named {@code color1}, {@code color2}, ..., not generated. */
    public static Palette of(boolean dark, List<RGB> colors) {
        List<Swatch> swatches = new ArrayList<>();
        for (int i = 0; i < colors.size(); i++) {
            swatches.add(new Swatch("color" + (i + 1), colors.get(i), false));
        }
        return new Palette(swatches, dark, null, null);
    }

    /**
     * A theme's palette: its {@code swatches} if it has any; otherwise up to {@link #MAX_SIZE} of
     * the colors its base names resolve to, the most different from each other, in name order.
     */
    public static Palette fromTheme(Theme theme) {
        boolean dark = Boolean.TRUE.equals(theme.dark());
        ThemeNames names = ThemeNames.of(theme);
        if (!theme.swatches().isEmpty()) {
            List<Swatch> swatches = new ArrayList<>();
            for (String name : theme.swatches().keySet()) {
                names.color(name).ifPresent(rgba -> swatches.add(new Swatch(name, rgba.rgb, false)));
            }
            return new Palette(swatches, dark, null, null);
        }
        Map<String, RGB> colors = new LinkedHashMap<>();
        for (NameSpec spec : BaseNames.ALL) {
            if (spec.parts().contains("color")) {
                names.color(spec.name()).ifPresent(rgba -> {
                    if (!colors.containsValue(rgba.rgb)) {
                        colors.put(spec.name(), rgba.rgb);
                    }
                });
            }
        }
        // Greedy farthest-point choice, starting from the background.
        List<String> chosen = new ArrayList<>();
        List<String> candidates = new ArrayList<>(colors.keySet());
        if (!candidates.isEmpty()) {
            chosen.add(candidates.removeFirst());
        }
        while (chosen.size() < MAX_SIZE && !candidates.isEmpty()) {
            String farthest = null;
            double distance = -1;
            for (String candidate : candidates) {
                double nearest = chosen.stream().mapToDouble(c -> Oklch.distance(colors.get(c), colors.get(candidate))).min().orElse(0);
                if (nearest > distance) {
                    distance = nearest;
                    farthest = candidate;
                }
            }
            if (distance < 0.02) {
                break;
            }
            chosen.add(farthest);
            candidates.remove(farthest);
        }
        List<Swatch> swatches = new ArrayList<>();
        for (String name : colors.keySet()) {
            if (chosen.contains(name)) {
                swatches.add(new Swatch(name, colors.get(name), false));
            }
        }
        return new Palette(swatches, dark, null, null);
    }

    public Optional<Swatch> swatch(String name) {
        return swatches.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    public List<RGB> colors() {
        return swatches.stream().map(Swatch::color).toList();
    }

    /** The swatch {@code name} replaced by {@code swatch}. */
    public Palette with(String name, Swatch swatch) {
        List<Swatch> changed = new ArrayList<>(swatches);
        for (int i = 0; i < changed.size(); i++) {
            if (changed.get(i).name().equals(name)) {
                changed.set(i, swatch);
                return new Palette(changed, dark, rule, base);
            }
        }
        throw new IllegalArgumentException("No swatch " + name);
    }

    public Palette withLocked(String name, boolean locked) {
        return with(name, swatch(name).orElseThrow(() -> new IllegalArgumentException("No swatch " + name)).withLocked(locked));
    }

    public Palette withDark(boolean isDark) {
        return new Palette(swatches, isDark, rule, base);
    }

    public Palette withRule(Harmony harmony) {
        return new Palette(swatches, dark, harmony, base);
    }

    public Palette withBase(RGB color) {
        return new Palette(swatches, dark, rule, color);
    }

    /** The colors as a theme's {@code swatches} section: name to {@code #RRGGBB}. */
    public Map<String, String> toSwatches() {
        Map<String, String> section = new LinkedHashMap<>();
        swatches.forEach(s -> section.put(s.name(), s.hex()));
        return section;
    }
}
