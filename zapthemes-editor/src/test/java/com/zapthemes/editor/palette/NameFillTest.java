package com.zapthemes.editor.palette;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.catalog.BaseNames;
import com.zapthemes.core.catalog.NameSpec;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ThemeNames;
import com.zapthemes.themes.BundledThemes;

/** Auto-fill from a palette to the base names, and the contrast checks it has to pass. */
class NameFillTest {

    private static List<Theme> bundled;

    @BeforeAll
    static void loadThemes() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        bundled = BundledThemes.IDS.stream().map(registry::resolve).toList();
    }

    @Test
    void everyBaseColorIsFilledInBaseNameOrder() {
        Palette palette = new PaletteGenerator(1).generate(Harmony.ANALOGOUS, new RGB(0x2A, 0xAC, 0xB8), 6, true);

        Map<String, RGB> names = NameFill.fill(palette);

        List<String> colorNames = BaseNames.ALL.stream().filter(NameSpec::hasColor).map(NameSpec::name).toList();
        assertThat(names.keySet()).containsExactlyElementsOf(colorNames);
    }

    @Test
    void everyGeneratedPaletteFillsNamesThatPassTheContrastChecks() {
        int palettes = 0;
        for (long seed = 0; seed < 40; seed++) {
            PaletteGenerator generator = new PaletteGenerator(seed);
            for (Harmony rule : Harmony.values()) {
                for (boolean dark : new boolean[] { true, false }) {
                    RGB base = new Oklch(0.6, 0.15, seed * 37 % 360).toRgb();
                    Palette palette = generator.generate(rule, base, Palette.MIN_SIZE + (int) (seed % 4), dark);
                    Map<String, RGB> names = NameFill.fill(palette);
                    assertThat(ContrastCheck.failures(names)).as(palette.toString()).isEmpty();
                    palettes++;
                }
            }
        }
        assertThat(palettes).isEqualTo(400);
    }

    @Test
    void theBackgroundIsTheDarkOrLightEndAndSurfacesStepFromIt() {
        Palette palette = Palette.of(true, List.of(new RGB(0x20, 0x22, 0x28), new RGB(0x10, 0x12, 0x18),
                new RGB(0xE0, 0xE4, 0xEA), new RGB(0x35, 0x74, 0xF0), new RGB(0xF0, 0x71, 0x78)));

        Map<String, RGB> dark = NameFill.fill(palette);
        Map<String, RGB> light = NameFill.fill(palette.withDark(false));

        assertThat(dark.get("background")).isEqualTo(new RGB(0x10, 0x12, 0x18));
        assertThat(light.get("background")).isEqualTo(new RGB(0xE0, 0xE4, 0xEA));
        double bg = Oklch.of(dark.get("background")).l();
        assertThat(Oklch.of(dark.get("surface")).l() - bg).isBetween(0.03, 0.06);
        assertThat(Oklch.of(dark.get("raised")).l()).isGreaterThan(Oklch.of(dark.get("surface")).l());
        assertThat(Contrast.ratio(dark.get("foreground"), dark.get("background"))).isGreaterThanOrEqualTo(Contrast.ENHANCED);
        assertThat(dark.get("accent")).as("the most saturated swatch").isEqualTo(new RGB(0x35, 0x74, 0xF0));
    }

    @Test
    void statusColorsAreRedAmberGreenAndBlue() {
        Map<String, RGB> names = NameFill.fill(new PaletteGenerator(2).generate(Harmony.TRIADIC, new RGB(0x80, 0x40, 0xC0), 6, false));

        assertThat(hue(names.get("error"))).isBetween(10.0, 45.0);
        assertThat(hue(names.get("warning"))).isBetween(55.0, 95.0);
        assertThat(hue(names.get("success"))).isBetween(125.0, 170.0);
        assertThat(hue(names.get("info"))).isBetween(230.0, 270.0);
    }

    @Test
    void colorsTheUserSetAreKeptAndWhatDerivesFromThemFollows() {
        Palette palette = new PaletteGenerator(3).generate(Harmony.ANALOGOUS, new RGB(0x2A, 0xAC, 0xB8), 5, true);
        RGB plum = new RGB(0x24, 0x1B, 0x2E);
        RGB pink = new RGB(0xD9, 0x4F, 0x9A);

        Map<String, RGB> names = NameFill.fill(palette, Map.of("background", plum, "accent", pink, "badgeBackground", pink));

        assertThat(names.get("background")).isEqualTo(plum);
        assertThat(names.get("accent")).isEqualTo(pink);
        assertThat(names.get("badgeBackground")).as("a user name passes through").isEqualTo(pink);
        assertThat(Oklch.distance(names.get("surface"), plum)).isLessThan(0.08);
        assertThat(Math.abs(Oklch.of(names.get("selection")).h() - Oklch.of(pink).h())).isLessThan(6);
        assertThat(ContrastCheck.failures(names)).isEmpty();
    }

    @Test
    void aBundledThemesPaletteFillsReadableNames() {
        for (Theme theme : bundled) {
            Palette palette = Palette.fromTheme(theme);
            assertThat(palette.swatches()).as(theme.id()).hasSizeBetween(3, Palette.MAX_SIZE);
            assertThat(palette.dark()).isEqualTo(Boolean.TRUE.equals(theme.dark()));
            assertThat(palette.colors()).contains(ThemeNames.of(theme).color("background").orElseThrow().rgb);
            assertThat(ContrastCheck.failures(NameFill.fill(palette))).as(theme.id()).isEmpty();
        }
    }

    /** Every bundled theme passes every pair its base names declare. */
    @Test
    void theBundledThemesPassEveryContrastCheck() {
        for (Theme theme : bundled) {
            ThemeNames names = ThemeNames.of(theme);
            Map<String, RGB> colors = new java.util.LinkedHashMap<>();
            BaseNames.ALL.stream().filter(NameSpec::hasColor)
                    .forEach(spec -> names.color(spec.name()).ifPresent(c -> colors.put(spec.name(), c.rgb)));
            assertThat(ContrastCheck.check(colors)).as(theme.id()).hasSizeGreaterThan(20);
            assertThat(ContrastCheck.failures(colors)).as(theme.id()).extracting(p -> p.name() + "/" + p.against())
                    .isEmpty();
        }
    }

    private static double hue(RGB rgb) {
        return Oklch.of(rgb).h();
    }
}
