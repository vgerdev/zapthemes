package com.zapthemes.files.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.program.Program;

import com.zapthemes.files.EntryKind;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.FileTypes;
import com.zapthemes.files.LocalFileSystem;
import com.zapthemes.files.RootedFileSystem;

/**
 * {@link IconProvider#platform()}: the icon and (on Windows) the name of the program
 * {@link Program#findProgram} finds for a file's extension. Programs, names and missing ones are
 * cached per extension; images are made by the browser, once per extension and size.
 *
 * <p>Windows extracts a file type's icon at any size ({@code SHDefExtractIcon}), so icons are
 * sharp in every layout and at every zoom. Where a platform has only smaller ones, they are scaled
 * up to twice their size; beyond that the browser draws its own, which stay sharp.
 */
final class PlatformIcons implements IconProvider {

    private static final boolean WINDOWS = SWT.getPlatform().equals("win32");

    private final boolean allFileSystems;
    private final Map<String, Optional<Program>> programs = new HashMap<>();
    private final Map<String, Optional<String>> names = new HashMap<>();

    PlatformIcons(boolean allFileSystems) {
        this.allFileSystems = allFileSystems;
    }

    @Override
    public String iconKey(FileSystem fs, FileEntry entry) {
        String extension = extensionOf(fs, entry);
        return extension != null && program(extension) != null ? "." + extension : null;
    }

    @Override
    public ImageData iconData(String key, int pixels) {
        Program program = program(key.substring(1));
        if (program == null) {
            return null;
        }
        // Program sizes its icon as a small icon (16 at 100 %) at the zoom asked for.
        ImageData data;
        try {
            data = program.getImageData(Math.max(100, pixels * 100 / 16));
        } catch (RuntimeException e) {
            return null;
        }
        if (data == null || data.width <= 0 || data.height <= 0 || Math.max(data.width, data.height) * 2 < pixels) {
            return null;
        }
        return fit(data, pixels);
    }

    @Override
    public String typeName(FileSystem fs, FileEntry entry) {
        if (!WINDOWS) {
            return null; // A program's name elsewhere is the application's ("Text Editor"), not the type's.
        }
        String extension = extensionOf(fs, entry);
        if (extension == null) {
            return null;
        }
        return names.computeIfAbsent(extension, e -> {
            Program program = program(e);
            String name = program != null ? program.getName() : null;
            return Optional.ofNullable(name == null || name.isBlank() ? null : name.strip());
        }).orElse(null);
    }

    /** The extension of a file this provider covers, or null. */
    private String extensionOf(FileSystem fs, FileEntry entry) {
        if (entry.kind() != EntryKind.FILE || !allFileSystems && !isLocal(fs)) {
            return null;
        }
        String extension = FileTypes.extension(entry.name());
        return extension.isEmpty() ? null : extension.toLowerCase(Locale.ROOT);
    }

    private static boolean isLocal(FileSystem fs) {
        return fs instanceof LocalFileSystem || fs instanceof RootedFileSystem rooted && isLocal(rooted.base());
    }

    private Program program(String extension) {
        return programs.computeIfAbsent(extension, e -> {
            try {
                return Optional.ofNullable(Program.findProgram("." + e));
            } catch (RuntimeException | LinkageError failure) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    // ------------------------------------------------------------------ image data

    /**
     * {@code source} as {@code pixels} square, 24-bit with an alpha channel, centered if it isn't
     * square: averaged down, or interpolated up, with premultiplied alpha so edges don't darken.
     */
    static ImageData fit(ImageData source, int pixels) {
        int w = source.width;
        int h = source.height;
        // Premultiplied ARGB of the source.
        float[] a = new float[w * h];
        float[] r = new float[w * h];
        float[] g = new float[w * h];
        float[] b = new float[w * h];
        ImageData mask = source.getTransparencyType() == SWT.TRANSPARENCY_MASK || source.getTransparencyType() == SWT.TRANSPARENCY_PIXEL
                ? source.getTransparencyMask()
                : null;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                RGB rgb = source.palette.getRGB(source.getPixel(x, y));
                int alpha;
                if (source.alphaData != null || source.alpha != -1) {
                    alpha = source.getAlpha(x, y);
                } else if (mask != null) {
                    alpha = mask.getPixel(x, y) == 0 ? 0 : 255;
                } else {
                    alpha = 255;
                }
                int i = y * w + x;
                a[i] = alpha / 255f;
                r[i] = rgb.red * a[i];
                g[i] = rgb.green * a[i];
                b[i] = rgb.blue * a[i];
            }
        }
        // The largest box of the source's shape in the square.
        double scale = (double) pixels / Math.max(w, h);
        int outW = Math.max(1, (int) Math.round(w * scale));
        int outH = Math.max(1, (int) Math.round(h * scale));
        int offX = (pixels - outW) / 2;
        int offY = (pixels - outH) / 2;
        ImageData out = new ImageData(pixels, pixels, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        out.alphaData = new byte[pixels * pixels];
        float[] px = new float[4];
        for (int y = 0; y < outH; y++) {
            for (int x = 0; x < outW; x++) {
                if (scale < 1) {
                    average(a, r, g, b, w, h, x / scale, y / scale, (x + 1) / scale, (y + 1) / scale, px);
                } else {
                    interpolate(a, r, g, b, w, h, (x + 0.5) / scale - 0.5, (y + 0.5) / scale - 0.5, px);
                }
                int alpha = Math.clamp(Math.round(px[0] * 255), 0, 255);
                int i = (y + offY) * pixels + x + offX;
                out.alphaData[i] = (byte) alpha;
                if (px[0] > 0) {
                    int red = Math.clamp(Math.round(px[1] / px[0]), 0, 255);
                    int green = Math.clamp(Math.round(px[2] / px[0]), 0, 255);
                    int blue = Math.clamp(Math.round(px[3] / px[0]), 0, 255);
                    out.setPixel(x + offX, y + offY, (red << 16) | (green << 8) | blue);
                }
            }
        }
        return out;
    }

    private static void average(float[] a, float[] r, float[] g, float[] b, int w, int h, double x0, double y0, double x1, double y1,
            float[] out) {
        float sa = 0;
        float sr = 0;
        float sg = 0;
        float sb = 0;
        float total = 0;
        for (int y = (int) y0; y < Math.min(h, Math.ceil(y1)); y++) {
            double hy = Math.min(y1, y + 1) - Math.max(y0, y);
            for (int x = (int) x0; x < Math.min(w, Math.ceil(x1)); x++) {
                float weight = (float) (hy * (Math.min(x1, x + 1) - Math.max(x0, x)));
                int i = y * w + x;
                sa += a[i] * weight;
                sr += r[i] * weight;
                sg += g[i] * weight;
                sb += b[i] * weight;
                total += weight;
            }
        }
        if (total == 0) {
            total = 1;
        }
        out[0] = sa / total;
        out[1] = sr / total;
        out[2] = sg / total;
        out[3] = sb / total;
    }

    private static void interpolate(float[] a, float[] r, float[] g, float[] b, int w, int h, double sx, double sy, float[] out) {
        int x0 = (int) Math.floor(sx);
        int y0 = (int) Math.floor(sy);
        float fx = (float) (sx - x0);
        float fy = (float) (sy - y0);
        float[][] channels = { a, r, g, b };
        for (int c = 0; c < 4; c++) {
            float[] v = channels[c];
            float top = lerp(at(v, w, h, x0, y0), at(v, w, h, x0 + 1, y0), fx);
            float bottom = lerp(at(v, w, h, x0, y0 + 1), at(v, w, h, x0 + 1, y0 + 1), fx);
            out[c] = lerp(top, bottom, fy);
        }
    }

    private static float at(float[] v, int w, int h, int x, int y) {
        return v[Math.clamp(y, 0, h - 1) * w + Math.clamp(x, 0, w - 1)];
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
