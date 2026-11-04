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

    public static final List<String> RESOURCES = List.of(BASE, LIGHT, DARK);

    private BundledThemes() {
    }
}
