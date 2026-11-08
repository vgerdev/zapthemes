package com.zapthemes.jface;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;

import com.zapthemes.core.ThemeExtension;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeResources;
import com.zapthemes.core.model.FontSpec;
import com.zapthemes.core.style.ResolvedStyle;

/**
 * Keeps JFace's {@link JFaceResources#getColorRegistry() ColorRegistry} and
 * {@link JFaceResources#getFontRegistry() FontRegistry} in step with the theme's
 * {@code registries.colors} and {@code registries.fonts}, keyed by JFace's symbolic names
 * (e.g. {@code HYPERLINK_COLOR}, {@code org.eclipse.jface.dialogfont}). JFace and viewers that
 * listen to the registries update themselves.
 *
 * <p>Values JFace had before the first theme are remembered; when a later theme no longer sets a
 * key, its original value is restored. Installed automatically through {@link ThemeExtension}.
 *
 * <p>Every font of the registry follows the {@linkplain ThemeManager#setZoom(double) zoom}, the
 * theme's and the ones JFace or the application put there: at 150 % each key holds its theme (or
 * original) font at 1.5 times its height. A value the application puts for a key the theme does
 * not set becomes that key's new original, and is zoomed right away.
 */
public final class JFaceThemeSync implements ThemeExtension {

    private final Map<String, RGB> originalColors = new HashMap<>();
    private final Map<String, FontData[]> originalFonts = new HashMap<>();
    private FontData originalDefault;
    private Set<String> themedColors = Set.of();
    private Set<String> themedFonts = Set.of();
    /** Set while this class puts fonts, to tell its own changes from the application's. */
    private boolean puttingFonts;

    @Override
    public void install(ThemeManager manager) {
        manager.addThemeChangeListener((previous, current) -> sync(manager));
        manager.addZoomListener((previous, current) -> syncFonts(manager));
        JFaceResources.getFontRegistry().addListener(event -> {
            if (!puttingFonts && event.getNewValue() instanceof FontData[] data) {
                fontPutByApplication(manager, event.getProperty(), data);
            }
        });
        sync(manager);
    }

    void sync(ThemeManager manager) {
        themedColors = syncColors(manager.registry("colors"), JFaceResources.getColorRegistry());
        syncFonts(manager);
    }

    private Set<String> syncColors(ResolvedStyle colors, ColorRegistry registry) {
        Set<String> themed = new HashSet<>();
        for (String key : colors.keys()) {
            colors.color(key).ifPresent(rgba -> {
                if (!originalColors.containsKey(key)) {
                    originalColors.put(key, registry.hasValueFor(key) ? registry.getRGB(key) : null);
                }
                registry.put(key, rgba.rgb);
                themed.add(key);
            });
        }
        for (String key : themedColors) {
            RGB original = originalColors.get(key);
            if (!themed.contains(key) && original != null) {
                registry.put(key, original);
            }
        }
        return themed;
    }

    private void fontPutByApplication(ThemeManager manager, String key, FontData[] data) {
        originalFonts.put(key, data);
        if (manager.resources().zoom() != 1 && !themedFonts.contains(key)) {
            put(JFaceResources.getFontRegistry(), key, zoomed(data, manager.resources()));
        }
    }

    private void put(FontRegistry registry, String key, FontData[] data) {
        puttingFonts = true;
        try {
            registry.put(key, data);
        } finally {
            puttingFonts = false;
        }
    }

    private void syncFonts(ThemeManager manager) {
        FontRegistry registry = JFaceResources.getFontRegistry();
        ThemeResources resources = manager.resources();
        ResolvedStyle fonts = manager.registry("fonts");
        if (originalDefault == null) {
            originalDefault = registry.defaultFont().getFontData()[0];
        }
        // Missing names and heights fall back to the theme's (unzoomed) default font, else JFace's.
        FontData fallback = fonts.font(JFaceResources.DEFAULT_FONT)
                .map(spec -> fontData(spec, originalDefault)).orElse(originalDefault);

        Map<String, FontData[]> wanted = new HashMap<>();
        Set<String> themed = new HashSet<>();
        for (String key : fonts.keys()) {
            fonts.font(key).ifPresent(spec -> {
                remember(registry, key);
                themed.add(key);
                wanted.put(key, new FontData[] { fontData(spec, fallback) });
            });
        }
        // Keys set before (to restore or rescale), and at a zoom every key, so JFace's own fonts scale.
        Set<String> others = new LinkedHashSet<>(originalFonts.keySet());
        if (resources.zoom() != 1) {
            others.addAll(registry.getKeySet());
        }
        for (String key : others) {
            if (!wanted.containsKey(key)) {
                remember(registry, key);
                FontData[] original = originalFonts.get(key);
                if (original != null) {
                    wanted.put(key, original);
                }
            }
        }
        themedFonts = themed;
        wanted.forEach((key, data) -> put(registry, key, zoomed(data, resources)));
    }

    /** Remembers the value of {@code key} before this class first sets it. */
    private void remember(FontRegistry registry, String key) {
        if (!originalFonts.containsKey(key)) {
            originalFonts.put(key, registry.hasValueFor(key) ? registry.getFontData(key) : null);
        }
    }

    private static FontData[] zoomed(FontData[] data, ThemeResources resources) {
        if (resources.zoom() == 1) {
            return data;
        }
        return Arrays.stream(data).filter(Objects::nonNull)
                .map(font -> new FontData(font.getName(), resources.zoomed(font.getHeight()), font.getStyle()))
                .toArray(FontData[]::new);
    }

    /** A theme font; a missing name or height falls back to JFace's default font. */
    static FontData fontData(FontSpec spec, FontData fallback) {
        String name = spec.name() != null ? spec.name() : fallback.getName();
        int height = spec.height() > 0 ? spec.height() : fallback.getHeight();
        return new FontData(name, height, spec.style());
    }
}
