package com.zapthemes.files.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;

import com.zapthemes.files.Capability;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.FileTypes;

/**
 * The pictures of image files, for the icon views and the panes: read in the background one at a
 * time (a remote server isn't flooded), scaled down there, and kept for the most recent few.
 */
final class Thumbnails {

    /** Bigger files aren't read for a thumbnail. */
    static final long MAX_BYTES = 20L * 1024 * 1024;
    private static final int KEEP = 300;

    private record Key(FileSystem fs, com.zapthemes.files.FilePath path, java.time.Instant modified, long size, int pixels) {
    }

    private final FileBrowser browser;
    private final Map<Key, Image> images = new LinkedHashMap<>(16, 0.75f, true);
    private final Set<Key> failed = new HashSet<>();
    private final Deque<Key> queue = new ArrayDeque<>();
    private final Map<Key, Runnable> waiting = new LinkedHashMap<>();
    private Key loading;

    Thumbnails(FileBrowser browser) {
        this.browser = browser;
        browser.addListener(SWT.Dispose, e -> clear());
    }

    /** Whether {@code entry} of {@code fs} can have a thumbnail. */
    static boolean applies(FileSystem fs, FileEntry entry) {
        return fs != null && fs.has(Capability.READ) && !entry.isContainer() && FileTypes.category(entry) == FileTypes.Category.IMAGE
                && entry.size() >= 0 && entry.size() <= MAX_BYTES;
    }

    /**
     * The picture of {@code entry} at most {@code pixels} wide and high (at 100 %), or null while
     * it is read (then {@code ready} runs once it is) or when it can't be.
     */
    Image get(FileEntry entry, int pixels, Runnable ready) {
        FileSystem fs = browser.getFileSystem();
        if (!applies(fs, entry)) {
            return null;
        }
        // A few sizes, so the cache serves the views and the panes alike.
        int bucket = pixels <= 64 ? 64 : pixels <= 128 ? 128 : pixels <= 256 ? 256 : 512;
        Key key = new Key(fs, entry.path(), entry.modified(), entry.size(), bucket);
        Image image = images.get(key);
        if (image != null || failed.contains(key)) {
            return image;
        }
        waiting.merge(key, ready, (a, b) -> () -> {
            a.run();
            b.run();
        });
        if (!key.equals(loading) && !queue.contains(key)) {
            // The newest request first: what was painted last is what is in view.
            queue.addFirst(key);
        }
        next();
        return null;
    }

    /** Forgets what is waiting (the folder changed); the pictures stay cached. */
    void cancelWaiting() {
        queue.clear();
        waiting.clear();
    }

    private void next() {
        if (loading != null || queue.isEmpty()) {
            return;
        }
        Key key = queue.pollFirst();
        loading = key;
        int zoom = Math.max(100, browser.getDisplay().getPrimaryMonitor().getZoom());
        int pixels = key.pixels() * zoom / 100;
        browser.loader().run(() -> read(key.fs(), key.path(), pixels), (data, failure) -> {
            loading = null;
            Runnable ready = waiting.remove(key);
            if (data == null) {
                failed.add(key);
            } else {
                images.put(key, new Image(browser.getDisplay(), (ImageDataProvider) z -> data));
                trim();
                if (ready != null) {
                    ready.run();
                }
            }
            if (!browser.isDisposed()) {
                next();
            }
        });
    }

    private void trim() {
        while (images.size() > KEEP) {
            var eldest = images.entrySet().iterator().next();
            eldest.getValue().dispose();
            images.remove(eldest.getKey());
        }
    }

    private void clear() {
        images.values().forEach(Image::dispose);
        images.clear();
        failed.clear();
        cancelWaiting();
    }

    /** Reads {@code path} as an image no bigger than {@code pixels} each way; null if it isn't one. */
    static ImageData read(FileSystem fs, com.zapthemes.files.FilePath path, int pixels) throws IOException {
        ImageData data;
        try (InputStream in = fs.openRead(path)) {
            ImageData[] frames = new ImageLoader().load(in);
            data = frames.length == 0 ? null : frames[0];
        } catch (SWTException e) {
            return null;
        }
        return data == null ? null : shrink(data, pixels);
    }

    /** {@code data} averaged down to fit {@code pixels} each way, its transparency kept; as it is if it fits. */
    static ImageData shrink(ImageData data, int pixels) {
        Objects.requireNonNull(data, "data");
        if (data.width <= pixels && data.height <= pixels) {
            return data;
        }
        double scale = Math.min((double) pixels / data.width, (double) pixels / data.height);
        int w = Math.max(1, (int) Math.round(data.width * scale));
        int h = Math.max(1, (int) Math.round(data.height * scale));
        ImageData out = new ImageData(w, h, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
        byte[] alpha = new byte[w * h];
        boolean anyAlpha = data.alphaData != null || data.alpha != -1 || data.transparentPixel != -1 || data.maskData != null;
        ImageData mask = data.maskData != null ? data.getTransparencyMask() : null;
        PaletteData palette = data.palette;
        int[] row = new int[data.width];
        long[] r = new long[w];
        long[] g = new long[w];
        long[] b = new long[w];
        long[] a = new long[w];
        long[] n = new long[w];
        int outY = 0;
        int sourceRowsEnd = rowEnd(0, h, data.height);
        for (int y = 0; y < data.height; y++) {
            data.getPixels(0, y, data.width, row, 0);
            for (int x = 0; x < data.width; x++) {
                int ox = Math.min(w - 1, (int) ((long) x * w / data.width));
                RGB rgb = palette.getRGB(row[x]);
                int alphaValue = alphaAt(data, mask, x, y, row[x]);
                // Weighed by opacity, so transparent pixels don't darken the edges.
                r[ox] += (long) rgb.red * alphaValue;
                g[ox] += (long) rgb.green * alphaValue;
                b[ox] += (long) rgb.blue * alphaValue;
                a[ox] += alphaValue;
                n[ox]++;
            }
            if (y + 1 == sourceRowsEnd || y + 1 == data.height) {
                for (int x = 0; x < w; x++) {
                    int opacity = n[x] == 0 ? 0 : (int) (a[x] / n[x]);
                    int red = a[x] == 0 ? 0 : (int) (r[x] / a[x]);
                    int green = a[x] == 0 ? 0 : (int) (g[x] / a[x]);
                    int blue = a[x] == 0 ? 0 : (int) (b[x] / a[x]);
                    out.setPixel(x, outY, red << 16 | green << 8 | blue);
                    alpha[outY * w + x] = (byte) opacity;
                    r[x] = g[x] = b[x] = a[x] = n[x] = 0;
                }
                outY = Math.min(h - 1, outY + 1);
                sourceRowsEnd = rowEnd(outY, h, data.height);
            }
        }
        if (anyAlpha) {
            out.alphaData = alpha;
        }
        return out;
    }

    /** The source row after the last one that goes into output row {@code outY}. */
    private static int rowEnd(int outY, int outHeight, int height) {
        return (int) Math.min(height, ((long) (outY + 1) * height + outHeight - 1) / outHeight);
    }

    private static int alphaAt(ImageData data, ImageData mask, int x, int y, int pixel) {
        if (data.alphaData != null) {
            return data.getAlpha(x, y);
        }
        if (data.alpha != -1) {
            return data.alpha;
        }
        if (data.transparentPixel != -1) {
            return pixel == data.transparentPixel ? 0 : 255;
        }
        if (mask != null) {
            return mask.getPixel(x, y) == 0 ? 0 : 255;
        }
        return 255;
    }

    /** Images cached (tests). */
    int size() {
        return images.size();
    }
}
