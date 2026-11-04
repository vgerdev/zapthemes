package com.zapthemes.themes;

import java.util.List;

/**
 * Classpath locations of the themes shipped with ZapThemes, base themes first, e.g.
 * {@code registry.loadResource(BundledThemes.class.getClassLoader(), path)} for each entry.
 */
public final class BundledThemes {

    public static final String BASE = "zapthemes/themes/zap-base.json";
    public static final String LIGHT = "zapthemes/themes/zap-light.json";
    public static final String DARK = "zapthemes/themes/zap-dark.json";
    public static final String HIGH_CONTRAST = "zapthemes/themes/zap-high-contrast.json";

    public static final List<String> RESOURCES = List.of(BASE, LIGHT, DARK, HIGH_CONTRAST);

    /** Ids of the themes an application can select, in menu order. */
    public static final List<String> IDS = List.of("zap-light", "zap-dark", "zap-high-contrast");

    private BundledThemes() {
    }

    /**
     * The bundled theme that matches the desktop, e.g.
     * {@code idFor(display.getHighContrast(), Display.isSystemDarkTheme())}.
     */
    public static String idFor(boolean highContrast, boolean dark) {
        return highContrast ? "zap-high-contrast" : dark ? "zap-dark" : "zap-light";
    }
}
