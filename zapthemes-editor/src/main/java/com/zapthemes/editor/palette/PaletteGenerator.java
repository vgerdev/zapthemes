package com.zapthemes.editor.palette;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import org.eclipse.swt.graphics.RGB;

/**
 * Generates UI palettes from a base color and a {@link Harmony} rule. A palette of {@code n}
 * swatches has three neutrals tinted with the base hue, which set the background end for light or
 * dark ({@code ground}, {@code panel}, {@code ink}), and {@code n - 3} accents on the rule's hues
 * ({@code accent}, {@code accent2}, ...). Lightness and chroma vary within ranges that suit
 * interfaces, so no rule gives neon or muddy accents.
 *
 * <p>The generator is seeded: the same seed and the same calls give the same palettes, which keeps
 * tests and a saved theme's {@code editor.generator} settings repeatable. Each call draws new
 * values, so {@link #regenerate} re-rolls.
 */
public final class PaletteGenerator {

    /** Swatch names, in order. */
    public static final String GROUND = "ground";
    public static final String PANEL = "panel";
    public static final String INK = "ink";
    public static final String ACCENT = "accent";

    private static final int NEUTRALS = 3;

    private final Random random;

    public PaletteGenerator(long seed) {
        this.random = new Random(seed);
    }

    /** A new palette of {@code size} swatches ({@link Palette#MIN_SIZE} to {@link Palette#MAX_SIZE}). */
    public Palette generate(Harmony rule, RGB base, int size, boolean dark) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(base, "base");
        if (size < Palette.MIN_SIZE || size > Palette.MAX_SIZE) {
            throw new IllegalArgumentException("A palette has " + Palette.MIN_SIZE + " to " + Palette.MAX_SIZE + " swatches: " + size);
        }
        List<Swatch> swatches = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            swatches.add(new Swatch(nameAt(i), roll(i, rule, base, dark), false));
        }
        return new Palette(swatches, dark, rule, base);
    }

    /**
     * {@code palette} with every unlocked swatch rolled again, from its rule and base. Locked
     * swatches keep their color. A palette that was not generated takes the analogous rule and its
     * first accent (or first swatch) as the base; its swatches keep their names.
     */
    public Palette regenerate(Palette palette) {
        Harmony rule = palette.rule() != null ? palette.rule() : Harmony.ANALOGOUS;
        RGB base = palette.base() != null ? palette.base() : defaultBase(palette);
        List<Swatch> swatches = new ArrayList<>();
        for (int i = 0; i < palette.swatches().size(); i++) {
            Swatch swatch = palette.swatches().get(i);
            RGB color = roll(i, rule, base, palette.dark());
            swatches.add(swatch.locked() ? swatch : swatch.withColor(color));
        }
        return new Palette(swatches, palette.dark(), rule, base);
    }

    /** The name of the swatch at {@code index} in a generated palette. */
    public static String nameAt(int index) {
        return switch (index) {
            case 0 -> GROUND;
            case 1 -> PANEL;
            case 2 -> INK;
            case 3 -> ACCENT;
            default -> ACCENT + (index - NEUTRALS + 1);
        };
    }

    private static RGB defaultBase(Palette palette) {
        return palette.swatch(ACCENT).map(Swatch::color).orElse(palette.swatches().isEmpty()
                ? new RGB(0x35, 0x74, 0xF0) : palette.swatches().getFirst().color());
    }

    /**
     * The color at {@code index}. Every swatch draws the same number of values whether or not it is
     * kept, so locking one does not change what the others get.
     */
    private RGB roll(int index, Harmony rule, RGB base, boolean dark) {
        Oklch seed = Oklch.of(base);
        double hue = seed.h();
        double r1 = random.nextDouble();
        double r2 = random.nextDouble();
        double r3 = random.nextDouble();
        return switch (index) {
            case 0 -> neutral(hue, dark ? between(0.17, 0.23, r1) : between(0.965, 0.985, r1), r2);
            case 1 -> neutral(hue, dark ? between(0.25, 0.30, r1) : between(0.90, 0.935, r1), r2);
            case 2 -> neutral(hue, dark ? between(0.90, 0.95, r1) : between(0.20, 0.27, r1), r2);
            default -> accent(index - NEUTRALS, rule, seed, dark, r1, r2, r3);
        };
    }

    private static RGB neutral(double hue, double lightness, double r) {
        return new Oklch(lightness, between(0.004, 0.018, r), hue).toRgb();
    }

    private static RGB accent(int accentIndex, Harmony rule, Oklch base, boolean dark, double r1, double r2, double r3) {
        List<Double> offsets = rule.offsets();
        double hue = base.h() + offsets.get(accentIndex % offsets.size()) + between(-8, 8, r3);
        double lightness = dark ? between(0.66, 0.78, r1) : between(0.48, 0.62, r1);
        double chroma = between(0.09, 0.17, r2);
        if (rule == Harmony.MONOCHROMATIC || accentIndex >= offsets.size()) {
            // One hue, or the rule's hues used up: vary lightness and chroma instead.
            int round = rule == Harmony.MONOCHROMATIC ? accentIndex : accentIndex / offsets.size();
            lightness += (round % 2 == 0 ? 1 : -1) * 0.08 * ((round + 1) / 2);
            chroma *= 1 - 0.2 * Math.min(round, 3);
        }
        return new Oklch(Math.clamp(lightness, 0.3, 0.9), chroma, hue).toRgb();
    }

    private static double between(double low, double high, double r) {
        return low + (high - low) * r;
    }
}
