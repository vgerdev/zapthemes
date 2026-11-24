package com.zapthemes.files.ui;

import org.eclipse.swt.graphics.ImageData;

import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileSystem;

/**
 * Icons and type names for a {@link FileBrowser} to show instead of its own
 * ({@link FileBrowser#setIconProvider}): the platform's, through {@link #platform()}, or an
 * application's. Whatever it gives no answer for keeps the browser's own icon and type name.
 *
 * <p>The browser asks on the UI thread, for every item it paints, so {@link #iconKey} and
 * {@link #typeName} must be quick (answer from a cache). Images are made once per key and size
 * and kept by the browser.
 */
public interface IconProvider {

    /**
     * A key naming the icon of {@code entry}, the same for entries that share an icon (a file name
     * extension, {@code ".txt"}), or {@code null} for the browser's own icon.
     */
    String iconKey(FileSystem fs, FileEntry entry);

    /**
     * The icon named {@code key}, {@code pixels} wide and high, with transparency, or {@code null}
     * if there is none that size (the browser then draws its own). Called once per key and size.
     */
    ImageData iconData(String key, int pixels);

    /** The type name of {@code entry} ("Text Document"), or {@code null} for the browser's own. */
    default String typeName(FileSystem fs, FileEntry entry) {
        return null;
    }

    /**
     * The platform's icons for local files, by extension, through SWT's
     * {@link org.eclipse.swt.program.Program} (cached per extension); on Windows its type names too
     * (elsewhere a program's name is an application's, so the browser's own type names stay).
     * Folders, drives and files of other filesystems keep the browser's icons.
     */
    static IconProvider platform() {
        return new PlatformIcons(false);
    }

    /** {@link #platform()}, for the files of every filesystem, by their extensions (a remote server's too). */
    static IconProvider platformForAllFileSystems() {
        return new PlatformIcons(true);
    }
}
