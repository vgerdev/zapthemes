package com.zapthemes.editor.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The seeded generator: harmony rules, light and dark, lock and regenerate. */
class PaletteGeneratorTest {

    private static final RGB TEAL = new RGB(0x2A, 0xAC, 0xB8);

    @Test
    void theSameSeedGivesTheSamePalettes() {
        PaletteGenerator one = new PaletteGenerator(42);
        PaletteGenerator two = new PaletteGenerator(42);

        Palette a = one.generate(Harmony.TRIADIC, TEAL, 6, true);
        assertThat(two.generate(Harmony.TRIADIC, TEAL, 6, true)).isEqualTo(a);
        assertThat(one.regenerate(a)).isEqualTo(two.regenerate(a));
        assertThat(new PaletteGenerator(43).generate(Harmony.TRIADIC, TEAL, 6, true)).isNotEqualTo(a);
    }

    @Test
    void swatchesAreThreeNeutralsAndAccents() {
        Palette palette = new PaletteGenerator(1).generate(Harmony.TETRADIC, TEAL, 8, false);

        assertThat(palette.swatches()).extracting(Swatch::name)
                .containsExactly("ground", "panel", "ink", "accent1", "accent2", "accent3", "accent4", "accent5");
        assertThat(palette.rule()).isEqualTo(Harmony.TETRADIC);
        assertThat(palette.base()).isEqualTo(TEAL);
        assertThat(palette.dark()).isFalse();
        for (String neutral : List.of("ground", "panel", "ink")) {
            assertThat(Oklch.of(palette.swatch(neutral).orElseThrow().color()).c()).as(neutral).isLessThan(0.03);
        }
    }

    @Test
    void darkAndLightPickTheBackgroundEnd() {
        PaletteGenerator generator = new PaletteGenerator(7);
        Palette dark = generator.generate(Harmony.ANALOGOUS, TEAL, 5, true);
        Palette light = generator.generate(Harmony.ANALOGOUS, TEAL, 5, false);

        assertThat(l(dark, "ground")).isLessThan(0.25);
        assertThat(l(dark, "ink")).isGreaterThan(0.88);
        assertThat(l(light, "ground")).isGreaterThan(0.95);
        assertThat(l(light, "ink")).isLessThan(0.3);
    }

    @ParameterizedTest
    @EnumSource(Harmony.class)
    void accentsSitOnTheRulesHues(Harmony rule) {
        Palette palette = new PaletteGenerator(3).generate(rule, TEAL, Math.max(Palette.MIN_SIZE, 3 + rule.offsets().size()), true);
        double base = Oklch.of(TEAL).h();

        for (int i = 0; i < rule.offsets().size(); i++) {
            Oklch accent = Oklch.of(palette.swatches().get(3 + i).color());
            assertThat(hueDistance(accent.h(), base + rule.offsets().get(i))).as(rule + " accent " + i).isLessThan(12);
            assertThat(accent.c()).as("vivid enough for an accent").isGreaterThan(0.06);
        }
    }

    @Test
    void regenerateKeepsLockedSwatchesAndRerollsTheRest() {
        PaletteGenerator generator = new PaletteGenerator(11);
        Palette first = generator.generate(Harmony.COMPLEMENTARY, TEAL, 6, true).withLocked("accent1", true).withLocked("ground", true);

        Palette second = generator.regenerate(first);

        assertThat(second.swatch("accent1")).isEqualTo(first.swatch("accent1"));
        assertThat(second.swatch("ground")).isEqualTo(first.swatch("ground"));
        assertThat(second.swatch("accent1").orElseThrow().locked()).isTrue();
        assertThat(second.swatch("accent2")).isNotEqualTo(first.swatch("accent2"));
        assertThat(second.swatch("ink")).isNotEqualTo(first.swatch("ink"));
    }

    @Test
    void lockingASwatchDoesNotChangeWhatTheOthersGet() {
        Palette start = new PaletteGenerator(5).generate(Harmony.TRIADIC, TEAL, 7, false);

        Palette unlocked = new PaletteGenerator(9).regenerate(start);
        Palette locked = new PaletteGenerator(9).regenerate(start.withLocked("panel", true));

        for (int i = 0; i < start.swatches().size(); i++) {
            String name = start.swatches().get(i).name();
            if (!name.equals("panel")) {
                assertThat(locked.swatch(name).orElseThrow().color()).as(name).isEqualTo(unlocked.swatch(name).orElseThrow().color());
            }
        }
    }

    @Test
    void aChangedRuleOrBaseIsUsedByRegenerate() {
        PaletteGenerator generator = new PaletteGenerator(13);
        Palette palette = generator.generate(Harmony.ANALOGOUS, TEAL, 5, true);
        RGB coral = new RGB(0xF0, 0x71, 0x78);

        Palette changed = generator.regenerate(palette.withRule(Harmony.MONOCHROMATIC).withBase(coral));

        assertThat(changed.rule()).isEqualTo(Harmony.MONOCHROMATIC);
        assertThat(hueDistance(Oklch.of(changed.swatch("accent1").orElseThrow().color()).h(), Oklch.of(coral).h())).isLessThan(12);
        assertThat(changed.withDark(false).dark()).isFalse();
    }

    @Test
    void aPaletteThatWasNotGeneratedRegeneratesAroundItsFirstColor() {
        Palette plain = Palette.of(true, List.of(new RGB(10, 10, 10), new RGB(200, 60, 60), new RGB(240, 240, 240),
                new RGB(60, 60, 200), new RGB(60, 200, 60)));

        Palette rolled = new PaletteGenerator(1).regenerate(plain);

        assertThat(rolled.swatches()).extracting(Swatch::name).containsExactly("color1", "color2", "color3", "color4", "color5");
        assertThat(rolled.rule()).isEqualTo(Harmony.ANALOGOUS);
    }

    @Test
    void sizesOutsideFiveToEightAreRefused() {
        PaletteGenerator generator = new PaletteGenerator(1);
        assertThatThrownBy(() -> generator.generate(Harmony.TRIADIC, TEAL, 4, true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> generator.generate(Harmony.TRIADIC, TEAL, 9, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Harmony.byId("tetradic")).isEqualTo(Harmony.TETRADIC);
    }

    private static double l(Palette palette, String name) {
        return Oklch.of(palette.swatch(name).orElseThrow().color()).l();
    }

    private static double hueDistance(double a, double b) {
        double d = Math.abs(((a - b) % 360 + 360) % 360);
        return Math.min(d, 360 - d);
    }
}
