package com.zapthemes.files.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileTypes;

/**
 * The file browser's own icons, drawn in code on a 16-unit grid at any size, so they stay sharp at
 * every zoom and on high-DPI screens: folders, drives, the computer, file types, the standard places
 * and the glyphs of the navigation and status bars. No artwork from any product.
 *
 * <p>Images are cached per icon and size and belong to this object; {@link #dispose} frees them.
 */
public final class FileIcons {

    /** The colored icons. */
    public enum Icon {
        FOLDER, DRIVE, COMPUTER, FILE, TEXT, DOCUMENT, SPREADSHEET, PRESENTATION, PDF, IMAGE, AUDIO, VIDEO, ARCHIVE, CODE,
        EXECUTABLE, HOME, DESKTOP, DOWNLOADS, DOCUMENTS, PICTURES, MUSIC, VIDEOS, NETWORK
    }

    /** One-color glyphs, drawn in the foreground color. */
    public enum Glyph {
        BACK, FORWARD, UP, REFRESH, CHEVRON_RIGHT, CHEVRON_DOWN, OVERFLOW, CLOSE, PIN, DETAILS_VIEW, LARGE_ICONS_VIEW, NEW, CUT, COPY, PASTE,
        RENAME, SHARE, DELETE, SORT, MORE, DETAILS_PANE
    }

    private final Display display;
    private final Map<String, Image> images = new HashMap<>();

    public FileIcons(Display display) {
        this.display = display;
    }

    /** {@code icon} at {@code size} pixels (at 100 % of the screen; high-DPI screens get more pixels). */
    public Image get(Icon icon, int size) {
        return images.computeIfAbsent(icon + "@" + size,
                key -> new Image(display, (ImageDataProvider) zoom -> render(Math.max(1, size * zoom / 100), gc -> paint(gc, icon))));
    }

    /** The icon of a file browser entry; faded for a hidden one, as Explorer dims hidden and temporary files. */
    public Image get(FileEntry entry, int size) {
        return get(entry, size, false);
    }

    /** The icon of an entry, faded if it is hidden or {@code faded} (cut, waiting for a paste). */
    public Image get(FileEntry entry, int size, boolean faded) {
        Icon icon = iconOf(FileTypes.category(entry));
        if (!entry.hidden() && !faded) {
            return get(icon, size);
        }
        return images.computeIfAbsent(icon + "@" + size + "/faded", key -> new Image(display, (ImageDataProvider) zoom -> {
            ImageData data = render(Math.max(1, size * zoom / 100), gc -> paint(gc, icon));
            for (int i = 0; i < data.alphaData.length; i++) {
                data.alphaData[i] = (byte) ((data.alphaData[i] & 0xFF) / 2);
            }
            return data;
        }));
    }

    /** The icon of a standard place kind ({@link com.zapthemes.files.Place#kind()}). */
    public Image place(String kind, int size) {
        Icon icon = switch (kind) {
            case "home" -> Icon.HOME;
            case "desktop" -> Icon.DESKTOP;
            case "downloads" -> Icon.DOWNLOADS;
            case "documents" -> Icon.DOCUMENTS;
            case "pictures" -> Icon.PICTURES;
            case "music" -> Icon.MUSIC;
            case "videos" -> Icon.VIDEOS;
            case "computer" -> Icon.COMPUTER;
            case "drive" -> Icon.DRIVE;
            case "network" -> Icon.NETWORK;
            default -> Icon.FOLDER;
        };
        return get(icon, size);
    }

    static Icon iconOf(FileTypes.Category category) {
        return switch (category) {
            case FOLDER -> Icon.FOLDER;
            case DRIVE -> Icon.DRIVE;
            case FILE -> Icon.FILE;
            case TEXT -> Icon.TEXT;
            case DOCUMENT -> Icon.DOCUMENT;
            case SPREADSHEET -> Icon.SPREADSHEET;
            case PRESENTATION -> Icon.PRESENTATION;
            case PDF -> Icon.PDF;
            case IMAGE -> Icon.IMAGE;
            case AUDIO -> Icon.AUDIO;
            case VIDEO -> Icon.VIDEO;
            case ARCHIVE -> Icon.ARCHIVE;
            case CODE -> Icon.CODE;
            case EXECUTABLE -> Icon.EXECUTABLE;
        };
    }

    /** The number of images made so far (tests). */
    int imageCount() {
        return images.size();
    }

    public void dispose() {
        images.values().forEach(Image::dispose);
        images.clear();
    }

    // ------------------------------------------------------------------ glyphs

    /** Draws {@code glyph} in {@code color} in the square at {@code (x, y)} of {@code size} pixels. */
    public static void draw(GC gc, Glyph glyph, int x, int y, int size, Color color) {
        Transform saved = new Transform(gc.getDevice());
        gc.getTransform(saved);
        Transform transform = new Transform(gc.getDevice());
        gc.getTransform(transform);
        transform.translate(x, y);
        transform.scale(size / 16f, size / 16f);
        gc.setTransform(transform);
        gc.setAntialias(SWT.ON);
        gc.setForeground(color);
        gc.setBackground(color);
        // About 1.25 px at 16 px, never thinner than one pixel.
        gc.setLineAttributes(new LineAttributes(Math.max(1.25f, 16f / size), SWT.CAP_ROUND, SWT.JOIN_ROUND));
        Strokes d = new Strokes(gc);
        switch (glyph) {
            case BACK -> d.line(3, 8, 13, 8).line(7.5f, 3.5f, 3, 8, 7.5f, 12.5f);
            case FORWARD -> d.line(3, 8, 13, 8).line(8.5f, 3.5f, 13, 8, 8.5f, 12.5f);
            case UP -> d.line(8, 3, 8, 13).line(3.5f, 7.5f, 8, 3, 12.5f, 7.5f);
            case REFRESH -> {
                Path arc = new Path(gc.getDevice());
                arc.addArc(3, 3, 10, 10, 60, 280);
                gc.drawPath(arc);
                arc.dispose();
                d.line(10.2f, 2.2f, 12.6f, 3.8f, 10.9f, 6.1f);
            }
            case CHEVRON_RIGHT -> d.line(6, 4, 10, 8, 6, 12);
            case CHEVRON_DOWN -> d.line(4, 6, 8, 10, 12, 6);
            case OVERFLOW -> d.line(8, 4.5f, 4.5f, 8, 8, 11.5f).line(12, 4.5f, 8.5f, 8, 12, 11.5f);
            case CLOSE -> d.line(4.5f, 4.5f, 11.5f, 11.5f).line(11.5f, 4.5f, 4.5f, 11.5f);
            case PIN -> d.closed(6, 2.5f, 10, 2.5f, 9.5f, 6.5f, 11.5f, 9, 4.5f, 9, 6.5f, 6.5f).line(8, 9, 8, 13.5f);
            case DETAILS_VIEW -> d.line(2.5f, 4, 13.5f, 4).line(2.5f, 8, 13.5f, 8).line(2.5f, 12, 13.5f, 12);
            case LARGE_ICONS_VIEW -> d.closed(2.5f, 2.5f, 13.5f, 2.5f, 13.5f, 10, 2.5f, 10).line(2.5f, 13, 13.5f, 13);
            case NEW -> {
                d.ring(2, 2, 12, 12);
                d.line(8, 5, 8, 11).line(5, 8, 11, 8);
            }
            case CUT -> {
                d.ring(2.5f, 10, 3.5f, 3.5f);
                d.ring(10, 10, 3.5f, 3.5f);
                d.line(5.4f, 10.4f, 11, 2.5f).line(10.6f, 10.4f, 5, 2.5f);
            }
            case COPY -> d.line(5, 4.5f, 5, 2.5f, 13.5f, 2.5f, 13.5f, 11, 11.5f, 11).closed(2.5f, 5, 11, 5, 11, 13.5f, 2.5f, 13.5f);
            case PASTE -> d.closed(3, 3.5f, 13, 3.5f, 13, 14.5f, 3, 14.5f).closed(6, 2, 10, 2, 10, 5, 6, 5);
            case RENAME -> d.line(7.5f, 5, 1.5f, 5, 1.5f, 11, 7.5f, 11).line(12.5f, 5, 14.5f, 5, 14.5f, 11, 12.5f, 11).line(10, 3, 10, 13)
                    .line(8.5f, 3, 11.5f, 3).line(8.5f, 13, 11.5f, 13);
            case SHARE -> d.line(5, 6, 2.5f, 6, 2.5f, 14, 13.5f, 14, 13.5f, 6, 11, 6).line(8, 1.5f, 8, 10).line(5, 4.5f, 8, 1.5f, 11, 4.5f);
            case DELETE -> d.line(2, 4, 14, 4).line(6, 4, 6, 2, 10, 2, 10, 4).line(3.5f, 4, 4.5f, 14.5f, 11.5f, 14.5f, 12.5f, 4)
                    .line(6.5f, 7, 6.5f, 12).line(9.5f, 7, 9.5f, 12);
            case SORT -> d.line(5, 2, 5, 14).line(2.5f, 11.5f, 5, 14, 7.5f, 11.5f).line(11, 14, 11, 2).line(8.5f, 4.5f, 11, 2, 13.5f, 4.5f);
            case MORE -> d.dot(3.5f, 8, 1.2f).dot(8, 8, 1.2f).dot(12.5f, 8, 1.2f);
            case DETAILS_PANE -> d.closed(1.5f, 2.5f, 14.5f, 2.5f, 14.5f, 13.5f, 1.5f, 13.5f).line(10, 2.5f, 10, 13.5f);
        }
        gc.setTransform(saved);
        transform.dispose();
        saved.dispose();
        gc.setLineAttributes(new LineAttributes(1));
    }

    /** Strokes polylines in the glyph grid. */
    private record Strokes(GC gc) {
        Strokes line(float... points) {
            Path path = new Path(gc.getDevice());
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) {
                path.lineTo(points[i], points[i + 1]);
            }
            gc.drawPath(path);
            path.dispose();
            return this;
        }

        Strokes ring(float x, float y, float w, float h) {
            Path path = new Path(gc.getDevice());
            path.addArc(x, y, w, h, 0, 360);
            gc.drawPath(path);
            path.dispose();
            return this;
        }

        Strokes dot(float cx, float cy, float r) {
            Path path = new Path(gc.getDevice());
            path.addArc(cx - r, cy - r, 2 * r, 2 * r, 0, 360);
            gc.fillPath(path);
            path.dispose();
            return this;
        }

        Strokes closed(float... points) {
            Path path = new Path(gc.getDevice());
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) {
                path.lineTo(points[i], points[i + 1]);
            }
            path.close();
            gc.drawPath(path);
            path.dispose();
            return this;
        }
    }

    // ------------------------------------------------------------------ colored icons

    /**
     * Renders {@code painter} at {@code pixels}: drawn once on black and once on white, which gives
     * each pixel's alpha and color, so shapes keep their antialiased edges on any background.
     */
    private ImageData render(int pixels, Consumer<GC> painter) {
        ImageData onBlack = draw(pixels, new RGB(0, 0, 0), painter);
        ImageData onWhite = draw(pixels, new RGB(255, 255, 255), painter);
        ImageData out = new ImageData(pixels, pixels, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        out.alphaData = new byte[pixels * pixels];
        for (int y = 0; y < pixels; y++) {
            for (int x = 0; x < pixels; x++) {
                RGB b = onBlack.palette.getRGB(onBlack.getPixel(x, y));
                RGB w = onWhite.palette.getRGB(onWhite.getPixel(x, y));
                int alpha = 255 - ((w.red - b.red) + (w.green - b.green) + (w.blue - b.blue)) / 3;
                alpha = Math.clamp(alpha, 0, 255);
                out.alphaData[y * pixels + x] = (byte) alpha;
                if (alpha > 0) {
                    int r = Math.min(255, b.red * 255 / alpha);
                    int g = Math.min(255, b.green * 255 / alpha);
                    int bl = Math.min(255, b.blue * 255 / alpha);
                    out.setPixel(x, y, (r << 16) | (g << 8) | bl);
                }
            }
        }
        return out;
    }

    private ImageData draw(int pixels, RGB background, Consumer<GC> painter) {
        Image image = new Image(display, pixels, pixels);
        GC gc = new GC(image);
        gc.setBackground(new Color(background));
        gc.fillRectangle(0, 0, pixels, pixels);
        gc.setAntialias(SWT.ON);
        Transform transform = new Transform(display);
        transform.scale(pixels / 16f, pixels / 16f);
        gc.setTransform(transform);
        painter.accept(gc);
        transform.dispose();
        gc.dispose();
        ImageData data = image.getImageData();
        image.dispose();
        return data;
    }

    private static final int WHITE = 0xFFFFFF;
    private static final int PAGE_LINE = 0x8A9096;

    private static void paint(GC gc, Icon icon) {
        Shapes s = new Shapes(gc);
        switch (icon) {
            case FOLDER -> folder(s);
            case DRIVE -> {
                s.round(1.5f, 5, 13, 7, 1.5f, 0x7D8790).round(2.5f, 6, 11, 5, 1, 0xB9C2CB).rect(11, 8.2f, 1.6f, 1.2f, 0x3FC04F);
                s.rect(3.5f, 8.4f, 5, 0.8f, 0x7D8790);
            }
            case COMPUTER -> s.round(1, 2, 14, 9.5f, 1, 0x4A5560).rect(2, 3, 12, 7.5f, 0x3AA0F0).rect(6.5f, 11.5f, 3, 2, 0x6B7680)
                    .round(4, 13.2f, 8, 1.6f, 0.8f, 0x6B7680);
            case FILE -> page(s);
            case TEXT -> page(s).rect(5, 6, 6, 0.9f, 0x9AA0A6).rect(5, 8, 6, 0.9f, 0x9AA0A6).rect(5, 10, 6, 0.9f, 0x9AA0A6)
                    .rect(5, 12, 4, 0.9f, 0x9AA0A6);
            case DOCUMENT -> badge(page(s).rect(9, 6, 2.5f, 0.9f, 0x2B6FD6).rect(9, 8, 2.5f, 0.9f, 0x2B6FD6), 0x2B6FD6)
                    .rect(2.5f, 10, 5, 0.9f, WHITE).rect(2.5f, 12, 5, 0.9f, WHITE);
            case SPREADSHEET -> badge(page(s), 0x1F8A4C).rect(2.3f, 11.4f, 5.4f, 0.7f, WHITE).rect(4.6f, 9.3f, 0.7f, 4.6f, WHITE);
            case PRESENTATION -> badge(page(s), 0xD0582A).oval(3, 9.6f, 4, 3.6f, WHITE).oval(4, 10.5f, 2, 1.8f, 0xD0582A);
            case PDF -> badge(page(s), 0xD93025).rect(2.5f, 10.2f, 5, 0.9f, WHITE).rect(2.5f, 12.2f, 3.5f, 0.9f, WHITE);
            case IMAGE -> page(s).rect(4.5f, 6, 7, 6, 0x6EB6F0).poly(0x3D9A50, 4.5f, 12, 7, 8.5f, 9, 10.5f, 10, 9.5f, 11.5f, 12)
                    .oval(8.6f, 6.6f, 1.8f, 1.8f, 0xFFD34D);
            case AUDIO -> page(s).oval(4.8f, 10.4f, 3.2f, 2.6f, 0xE8742B).rect(7.2f, 5.5f, 1, 6.2f, 0xE8742B)
                    .poly(0xE8742B, 7.2f, 5.5f, 11, 6.6f, 11, 8.2f, 8.2f, 7.3f);
            case VIDEO -> page(s).round(4, 6.5f, 8, 6, 1, 0x7B4FD6).poly(WHITE, 7, 8, 10, 9.5f, 7, 11);
            case ARCHIVE -> page(s).rect(7, 1.6f, 2, 8, 0xB88A2E).rect(7, 2.5f, 1, 1, WHITE).rect(8, 4, 1, 1, WHITE)
                    .rect(7, 5.5f, 1, 1, WHITE).rect(8, 7, 1, 1, WHITE).round(6.2f, 9.6f, 3.6f, 3.4f, 0.6f, 0x8A6A20);
            case CODE -> {
                page(s);
                gc.setForeground(new Color(rgb(0x6B5FD6)));
                gc.setLineAttributes(new LineAttributes(1.2f, SWT.CAP_ROUND, SWT.JOIN_ROUND));
                new Strokes(gc).line(6.6f, 7, 4.8f, 9, 6.6f, 11).line(9.4f, 7, 11.2f, 9, 9.4f, 11);
            }
            case EXECUTABLE -> s.round(1.5f, 2.5f, 13, 11, 1, 0x4A5560).rect(2.5f, 3.5f, 11, 2, 0x2B6FD6).rect(2.5f, 5.5f, 11, 7, WHITE);
            case HOME -> s.poly(0xD9822B, 8, 1.5f, 15, 8, 13, 8, 13, 14.5f, 3, 14.5f, 3, 8, 1, 8).rect(6.8f, 10, 2.4f, 4.5f, WHITE);
            case DESKTOP -> s.round(1, 2.5f, 14, 9, 1, 0x1E90D6).rect(2, 3.5f, 12, 7, 0x6CC4F5).rect(7, 11.5f, 2, 1.6f, 0x1E90D6)
                    .round(4.5f, 13, 7, 1.5f, 0.7f, 0x1E90D6);
            case DOWNLOADS -> s.oval(1, 1, 14, 14, 0x2EA44F).rect(7.1f, 3.8f, 1.8f, 5.2f, WHITE).poly(WHITE, 4.6f, 8.2f, 11.4f, 8.2f, 8, 11.6f)
                    .rect(4.6f, 12.2f, 6.8f, 1, WHITE);
            case DOCUMENTS -> page(s).rect(5, 6, 6, 0.9f, 0x2B6FD6).rect(5, 8, 6, 0.9f, 0x2B6FD6).rect(5, 10, 6, 0.9f, 0x2B6FD6)
                    .rect(5, 12, 4, 0.9f, 0x2B6FD6);
            case PICTURES -> s.round(1, 2.5f, 14, 11, 1.5f, 0x3B8EEA).poly(0x2C9A55, 1, 12, 5.5f, 7, 9, 10.5f, 11, 8.5f, 15, 12.5f, 15, 13.5f, 1, 13.5f)
                    .oval(10, 4.2f, 2.6f, 2.6f, 0xFFD34D);
            case MUSIC -> s.round(1, 1, 14, 14, 3, 0xE8742B).oval(4, 9.6f, 3.6f, 3, WHITE).rect(6.6f, 3.8f, 1.1f, 7.2f, WHITE)
                    .poly(WHITE, 6.6f, 3.8f, 11.5f, 5.2f, 11.5f, 7, 7.7f, 6);
            case VIDEOS -> s.round(1, 2.5f, 14, 11, 2, 0x7B4FD6).poly(WHITE, 6.2f, 5.2f, 11, 8, 6.2f, 10.8f);
            case NETWORK -> {
                s.oval(1.5f, 1.5f, 13, 13, 0x3B8EEA);
                gc.setForeground(new Color(rgb(WHITE)));
                gc.setLineAttributes(new LineAttributes(0.9f));
                new Strokes(gc).line(1.5f, 8, 14.5f, 8).line(8, 1.5f, 8, 14.5f);
                Path ring = new Path(gc.getDevice());
                ring.addArc(4.6f, 1.6f, 6.8f, 12.8f, 0, 360);
                gc.drawPath(ring);
                ring.dispose();
            }
        }
    }

    private static Shapes folder(Shapes s) {
        return s.poly(0xD9962B, 1.5f, 2.5f, 6, 2.5f, 7.5f, 4, 14.5f, 4, 14.5f, 13.5f, 1.5f, 13.5f).round(1.5f, 5.5f, 13, 8, 0.8f, 0xFFC83D)
                .rect(1.9f, 5.5f, 12.2f, 0.8f, 0xFFDB75);
    }

    private static Shapes page(Shapes s) {
        s.poly(PAGE_LINE, 2.5f, 0.5f, 10.4f, 0.5f, 13.5f, 3.6f, 13.5f, 15.5f, 2.5f, 15.5f);
        s.poly(WHITE, 3.4f, 1.4f, 10, 1.4f, 12.6f, 4, 12.6f, 14.6f, 3.4f, 14.6f);
        return s.poly(0xC9CED3, 10, 1.4f, 10, 4, 12.6f, 4);
    }

    private static Shapes badge(Shapes s, int color) {
        return s.round(1, 8.5f, 8, 6.5f, 1, color);
    }

    private static RGB rgb(int value) {
        return new RGB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF);
    }

    /** Fills shapes in the 16-unit grid. */
    private record Shapes(GC gc) {
        Shapes rect(float x, float y, float w, float h, int color) {
            Path path = new Path(gc.getDevice());
            path.addRectangle(x, y, w, h);
            return fill(path, color);
        }

        Shapes round(float x, float y, float w, float h, float r, int color) {
            Path path = new Path(gc.getDevice());
            path.moveTo(x + r, y);
            path.lineTo(x + w - r, y);
            path.addArc(x + w - 2 * r, y, 2 * r, 2 * r, 90, -90);
            path.lineTo(x + w, y + h - r);
            path.addArc(x + w - 2 * r, y + h - 2 * r, 2 * r, 2 * r, 0, -90);
            path.lineTo(x + r, y + h);
            path.addArc(x, y + h - 2 * r, 2 * r, 2 * r, -90, -90);
            path.lineTo(x, y + r);
            path.addArc(x, y, 2 * r, 2 * r, 180, -90);
            path.close();
            return fill(path, color);
        }

        Shapes oval(float x, float y, float w, float h, int color) {
            Path path = new Path(gc.getDevice());
            path.addArc(x, y, w, h, 0, 360);
            return fill(path, color);
        }

        Shapes poly(int color, float... points) {
            Path path = new Path(gc.getDevice());
            path.moveTo(points[0], points[1]);
            for (int i = 2; i < points.length; i += 2) {
                path.lineTo(points[i], points[i + 1]);
            }
            path.close();
            return fill(path, color);
        }

        private Shapes fill(Path path, int color) {
            gc.setBackground(new Color(rgb(color)));
            gc.fillPath(path);
            path.dispose();
            return this;
        }
    }
}
