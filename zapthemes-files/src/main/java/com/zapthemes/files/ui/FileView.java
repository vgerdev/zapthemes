package com.zapthemes.files.ui;

/**
 * The layouts of the file view, as in Explorer's View menu ({@link FileBrowser#setView}). Their
 * shortcuts are Ctrl+Shift+1 to 8, in this order.
 */
public enum FileView {
    EXTRA_LARGE_ICONS("Extra large icons", 256),
    LARGE_ICONS("Large icons", 96),
    MEDIUM_ICONS("Medium icons", 48),
    SMALL_ICONS("Small icons", 16),
    LIST("List", 16),
    DETAILS("Details", 16),
    TILES("Tiles", 48),
    CONTENT("Content", 32);

    private final String label;
    private final int iconSize;

    FileView(String label, int iconSize) {
        this.label = label;
        this.iconSize = iconSize;
    }

    /** "Large icons", as the View menu names it. */
    public String label() {
        return label;
    }

    /** The size of its icons, in pixels at 100 %. */
    public int iconSize() {
        return iconSize;
    }

    /** Whether it puts the name below a large icon (Extra large, Large and Medium icons). */
    boolean namesBelow() {
        return this == EXTRA_LARGE_ICONS || this == LARGE_ICONS || this == MEDIUM_ICONS;
    }

    /** Whether image files show their picture instead of the icon. */
    boolean thumbnails() {
        return iconSize >= 32;
    }
}
