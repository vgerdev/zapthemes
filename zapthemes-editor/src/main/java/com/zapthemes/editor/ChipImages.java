package com.zapthemes.editor;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

/** Small square images of colors, with a gray outline, for table and tree cells; cached until disposed. */
final class ChipImages {

    static final int SIZE = 14;
    private static final RGB OUTLINE = new RGB(0x80, 0x80, 0x80);

    private final Map<RGB, Image> images = new HashMap<>();

    Image of(RGB rgb) {
        return images.computeIfAbsent(rgb, color -> new Image(Display.getCurrent(), data(color)));
    }

    static ImageData data(RGB color) {
        ImageData data = new ImageData(SIZE, SIZE, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        int fill = data.palette.getPixel(color);
        int line = data.palette.getPixel(OUTLINE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean edge = x == 0 || y == 0 || x == SIZE - 1 || y == SIZE - 1;
                data.setPixel(x, y, edge ? line : fill);
            }
        }
        return data;
    }

    void dispose() {
        images.values().forEach(Image::dispose);
        images.clear();
    }
}
