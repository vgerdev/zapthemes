package com.zapthemes.editor.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Random;

import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.Test;

/** OKLCH, HSL, WCAG contrast, and tints and shades. */
class ColorModelTest {

    @Test
    void oklchMatchesPublishedValues() {
        Oklch white = Oklch.of(new RGB(255, 255, 255));
        assertThat(white.l()).isCloseTo(1.0, within(1e-3));
        assertThat(white.c()).isCloseTo(0.0, within(1e-3));
        assertThat(Oklch.of(new RGB(0, 0, 0)).l()).isCloseTo(0.0, within(1e-6));
        // sRGB red is oklch(62.8% 0.2577 29.23).
        Oklch red = Oklch.of(new RGB(255, 0, 0));
        assertThat(red.l()).isCloseTo(0.628, within(1e-3));
        assertThat(red.c()).isCloseTo(0.2577, within(1e-3));
        assertThat(red.h()).isCloseTo(29.23, within(0.1));
    }

    @Test
    void oklchRoundTripsEverySrgbColor() {
        Random random = new Random(1);
        for (int i = 0; i < 5000; i++) {
            RGB rgb = new RGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            assertThat(Oklch.of(rgb).toRgb()).as(rgb.toString()).isEqualTo(rgb);
        }
    }

    @Test
    void outOfGamutColorsLoseChromaNotLightnessOrHue() {
        Oklch vivid = new Oklch(0.7, 0.4, 150);
        assertThat(vivid.inGamut()).isFalse();
        Oklch mapped = Oklch.of(vivid.toRgb());
        assertThat(mapped.l()).isCloseTo(0.7, within(0.01));
        assertThat(mapped.h()).isCloseTo(150, within(2.0));
        assertThat(mapped.c()).isLessThan(0.4).isGreaterThan(0.1);
    }

    @Test
    void hslRoundTripsAndMatchesKnownValues() {
        Hsl red = Hsl.of(new RGB(255, 0, 0));
        assertThat(red.h()).isCloseTo(0, within(1e-9));
        assertThat(red.s()).isCloseTo(1, within(1e-9));
        assertThat(red.l()).isCloseTo(0.5, within(1e-9));
        assertThat(new Hsl(210, 0.5, 0.4).toRgb()).isEqualTo(new RGB(51, 102, 153));
        Random random = new Random(2);
        for (int i = 0; i < 5000; i++) {
            RGB rgb = new RGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            assertThat(Hsl.of(rgb).toRgb()).as(rgb.toString()).isEqualTo(rgb);
        }
    }

    @Test
    void contrastRatiosAndLevelsFollowWcag() {
        RGB white = new RGB(255, 255, 255);
        RGB black = new RGB(0, 0, 0);
        assertThat(Contrast.ratio(white, black)).isCloseTo(21, within(1e-9));
        assertThat(Contrast.ratio(black, white)).isCloseTo(21, within(1e-9));
        assertThat(Contrast.ratio(white, white)).isCloseTo(1, within(1e-9));
        // #767676 is the lightest gray that reaches 4.5:1 on white.
        assertThat(Contrast.ratio(new RGB(0x76, 0x76, 0x76), white)).isGreaterThanOrEqualTo(4.5);
        assertThat(Contrast.ratio(new RGB(0x77, 0x77, 0x77), white)).isLessThan(4.5);
        assertThat(Contrast.Level.of(7.2)).isEqualTo(Contrast.Level.AAA);
        assertThat(Contrast.Level.of(4.6)).isEqualTo(Contrast.Level.AA);
        assertThat(Contrast.Level.of(3.2)).isEqualTo(Contrast.Level.AA_LARGE);
        assertThat(Contrast.Level.of(2.0)).isEqualTo(Contrast.Level.FAIL);
    }

    @Test
    void atLeastMovesLightnessJustEnoughAndKeepsTheHue() {
        RGB white = new RGB(255, 255, 255);
        RGB paleBlue = new Oklch(0.8, 0.1, 250).toRgb();
        RGB readable = Contrast.atLeast(paleBlue, Contrast.TEXT, white);

        assertThat(Contrast.ratio(readable, white)).isGreaterThanOrEqualTo(Contrast.TEXT).isLessThan(5.2);
        assertThat(Oklch.of(readable).h()).isCloseTo(250, within(3.0));
        assertThat(Contrast.atLeast(readable, Contrast.TEXT, white)).as("already enough").isEqualTo(readable);
        // Against a mid gray and white at once, darker is the only way.
        RGB both = Contrast.atLeast(paleBlue, Contrast.LARGE, white, new RGB(0xE0, 0xE0, 0xE0));
        assertThat(Contrast.worst(both, white, new RGB(0xE0, 0xE0, 0xE0))).isGreaterThanOrEqualTo(Contrast.LARGE);
    }

    @Test
    void tintsAndShadesStepEvenlyInLightnessLightestFirst() {
        RGB teal = new RGB(0x2A, 0xAC, 0xB8);
        List<RGB> row = TintsAndShades.of(teal, 9);

        assertThat(row).hasSize(9);
        double previous = 2;
        double step = (TintsAndShades.LIGHTEST - TintsAndShades.DARKEST) / 8;
        for (int i = 0; i < row.size(); i++) {
            Oklch c = Oklch.of(row.get(i));
            assertThat(c.l()).as("step " + i).isCloseTo(TintsAndShades.LIGHTEST - step * i, within(0.006));
            assertThat(c.l()).isLessThan(previous);
            previous = c.l();
            if (c.c() > 0.02) {
                assertThat(c.h()).as("hue of step " + i).isCloseTo(Oklch.of(teal).h(), within(4.0));
            }
        }
    }
}
