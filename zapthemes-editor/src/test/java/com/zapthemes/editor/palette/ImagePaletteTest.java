package com.zapthemes.editor.palette;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.Test;

/** k-means on an image's pixels, with no display needed. */
class ImagePaletteTest {

    private static final RGB NAVY = new RGB(0x10, 0x20, 0x50);
    private static final RGB SAND = new RGB(0xE8, 0xD8, 0xB0);
    private static final RGB CORAL = new RGB(0xF0, 0x71, 0x78);

    @Test
    void theDominantColorsComeMostCommonFirst() {
        ImageData image = stripes(200, 100, new RGB[] { NAVY, SAND, CORAL }, new int[] { 50, 30, 20 });

        List<RGB> colors = ImagePalette.extract(image, 3, 1);

        assertThat(colors).hasSize(3);
        assertThat(Oklch.distance(colors.get(0), NAVY)).isLessThan(0.01);
        assertThat(Oklch.distance(colors.get(1), SAND)).isLessThan(0.01);
        assertThat(Oklch.distance(colors.get(2), CORAL)).isLessThan(0.01);
    }

    @Test
    void noisyClustersAverageToTheirCenter() {
        ImageData image = stripes(300, 300, new RGB[] { NAVY, SAND }, new int[] { 60, 40 });
        java.util.Random random = new java.util.Random(4);
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                RGB rgb = image.palette.getRGB(image.getPixel(x, y));
                int n = random.nextInt(9) - 4;
                image.setPixel(x, y, image.palette.getPixel(new RGB(clamp(rgb.red + n), clamp(rgb.green + n), clamp(rgb.blue + n))));
            }
        }

        List<RGB> colors = ImagePalette.extract(image, 2, 3);

        assertThat(Oklch.distance(colors.get(0), NAVY)).isLessThan(0.02);
        assertThat(Oklch.distance(colors.get(1), SAND)).isLessThan(0.02);
    }

    @Test
    void transparentPixelsAreLeftOut() {
        ImageData image = stripes(100, 10, new RGB[] { NAVY, CORAL }, new int[] { 70, 30 });
        byte[] alpha = new byte[image.width * image.height];
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < 70; x++) {
                alpha[y * image.width + x] = 0;
            }
            for (int x = 70; x < image.width; x++) {
                alpha[y * image.width + x] = (byte) 255;
            }
        }
        image.alphaData = alpha;

        assertThat(ImagePalette.extract(image, 3, 1)).containsExactly(CORAL);
    }

    @Test
    void theSameSeedGivesTheSameColorsAndFewerColorsThanKIsFine() {
        ImageData image = stripes(120, 40, new RGB[] { NAVY, SAND, CORAL }, new int[] { 40, 40, 20 });
        assertThat(ImagePalette.extract(image, 5, 9)).isEqualTo(ImagePalette.extract(image, 5, 9));
        assertThat(ImagePalette.extract(image, 8, 2)).hasSizeLessThanOrEqualTo(3).contains(NAVY, SAND, CORAL);
    }

    @Test
    void largeImagesAreSampled() {
        ImageData image = stripes(1000, 1000, new RGB[] { SAND, NAVY }, new int[] { 75, 25 });
        long start = System.nanoTime();
        List<RGB> colors = ImagePalette.extract(image, 2, 1);
        assertThat(colors.getFirst()).isEqualTo(SAND);
        assertThat((System.nanoTime() - start) / 1_000_000).as("ms").isLessThan(5000);
    }

    /** Vertical stripes of {@code colors}, each {@code percent} of the width. */
    private static ImageData stripes(int width, int height, RGB[] colors, int[] percent) {
        ImageData image = new ImageData(width, height, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        int x = 0;
        for (int i = 0; i < colors.length; i++) {
            int end = i == colors.length - 1 ? width : x + width * percent[i] / 100;
            int pixel = image.palette.getPixel(colors[i]);
            for (; x < end; x++) {
                for (int y = 0; y < height; y++) {
                    image.setPixel(x, y, pixel);
                }
            }
        }
        return image;
    }

    private static int clamp(int v) {
        return Math.clamp(v, 0, 255);
    }
}
