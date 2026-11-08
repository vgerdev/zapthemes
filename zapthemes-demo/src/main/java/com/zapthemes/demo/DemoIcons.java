package com.zapthemes.demo;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.widgets.command.Images;

/**
 * Placeholder icons drawn in code for the ribbon demo: a rounded colored tile with one or two
 * white letters, at 16, 32 and 48 px. No artwork from any product.
 */
final class DemoIcons {

    private final Display display;
    private final List<Image> images = new ArrayList<>();

    DemoIcons(Display display) {
        this.display = display;
    }

    /** {@code text} (one or two characters) on a tile of {@code color}, at the sizes bars ask for. */
    Images of(String text, RGB color) {
        Image small = tile(text, color, 16);
        Image large = tile(text, color, 32);
        Image larger = tile(text, color, 48);
        return size -> size <= 20 ? small : size <= 36 ? large : larger;
    }

    private Image tile(String text, RGB color, int size) {
        Image glyph = new Image(display, size, size);
        GC gc = new GC(glyph);
        gc.setBackground(display.getSystemColor(SWT.COLOR_BLACK));
        gc.fillRectangle(0, 0, size, size);
        gc.setForeground(display.getSystemColor(SWT.COLOR_WHITE));
        gc.setTextAntialias(SWT.ON);
        FontData data = display.getSystemFont().getFontData()[0];
        Font font = new Font(display, data.getName(), 1, SWT.BOLD);
        int height = Math.max(4, size * (text.length() > 1 ? 9 : 11) / 16);
        for (int points = 1; points < 60; points++) {
            Font next = new Font(display, data.getName(), points, SWT.BOLD);
            gc.setFont(next);
            if (gc.getFontMetrics().getAscent() > height) {
                next.dispose();
                break;
            }
            font.dispose();
            font = next;
        }
        gc.setFont(font);
        Point extent = gc.textExtent(text);
        gc.drawText(text, (size - extent.x) / 2, (size - extent.y) / 2, true);
        gc.dispose();
        font.dispose();
        ImageData coverage = glyph.getImageData();
        glyph.dispose();

        ImageData out = new ImageData(size, size, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        double radius = size / 5.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                RGB pixel = coverage.palette.getRGB(coverage.getPixel(x, y));
                double ink = Math.max(pixel.red, Math.max(pixel.green, pixel.blue)) / 255.0;
                int r = (int) Math.round(color.red + (255 - color.red) * ink);
                int g = (int) Math.round(color.green + (255 - color.green) * ink);
                int b = (int) Math.round(color.blue + (255 - color.blue) * ink);
                out.setPixel(x, y, out.palette.getPixel(new RGB(r, g, b)));
                out.setAlpha(x, y, (int) Math.round(255 * cornerCoverage(x + 0.5, y + 0.5, size, radius)));
            }
        }
        Image image = new Image(display, out);
        images.add(image);
        return image;
    }

    /** How much of the pixel at {@code (x, y)} the rounded tile covers, 0 to 1. */
    private static double cornerCoverage(double x, double y, int size, double radius) {
        double cx = Math.max(radius, Math.min(size - radius, x));
        double cy = Math.max(radius, Math.min(size - radius, y));
        double distance = Math.hypot(x - cx, y - cy);
        return Math.max(0, Math.min(1, radius - distance + 0.5));
    }

    void dispose() {
        images.forEach(Image::dispose);
        images.clear();
    }
}
