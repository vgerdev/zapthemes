package com.zapthemes.widgets.command;

import org.eclipse.swt.graphics.Image;

/**
 * The images of a command, by size in pixels: bars ask for the size they draw (16 for small
 * buttons, 32 for large ones at 100 %, more when zoomed or on HiDPI) and draw what they get,
 * scaled if it is not that size. The app owns the images and disposes them.
 */
@FunctionalInterface
public interface Images {

    /** No images. */
    Images NONE = size -> null;

    /** The image for {@code size} pixels, or {@code null} if there is none. */
    Image image(int size);

    /**
     * A small (16 px) and a large (32 px) image: sizes up to 24 return the small one, unless it is
     * missing; larger ones the large one, unless it is missing.
     */
    static Images of(Image small, Image large) {
        return size -> {
            if (size <= 24) {
                return small != null ? small : large;
            }
            return large != null ? large : small;
        };
    }

    /** One image for every size. */
    static Images of(Image image) {
        return size -> image;
    }
}
