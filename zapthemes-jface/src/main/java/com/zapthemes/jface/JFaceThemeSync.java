package com.zapthemes.jface;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;

import com.zapthemes.core.ThemeExtension;
import com.zapthemes.core.ThemeManager;
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
 */
public final class JFaceThemeSync implements ThemeExtension {

    private final Map<String, RGB> originalColors = new HashMap<>();
    private final Map<String, FontData[]> originalFonts = new HashMap<>();
    private Set<String> themedColors = Set.of();
    private Set<String> themedFonts = Set.of();

    @Override
    public void install(ThemeManager manager) {
        manager.addThemeChangeListener((previous, current) -> sync(manager));
        sync(manager);
    }

    void sync(ThemeManager manager) {
        themedColors = syncColors(manager.registry("colors"), JFaceResources.getColorRegistry());
        themedFonts = syncFonts(manager.registry("fonts"), JFaceResources.getFontRegistry());
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

    private Set<String> syncFonts(ResolvedStyle fonts, FontRegistry registry) {
        Set<String> themed = new HashSet<>();
        for (String key : fonts.keys()) {
            fonts.font(key).ifPresent(spec -> {
                if (!originalFonts.containsKey(key)) {
                    originalFonts.put(key, registry.hasValueFor(key) ? registry.getFontData(key) : null);
                }
                registry.put(key, new FontData[] { fontData(spec, registry.defaultFont().getFontData()[0]) });
                themed.add(key);
            });
        }
        for (String key : themedFonts) {
            FontData[] original = originalFonts.get(key);
            if (!themed.contains(key) && original != null) {
                registry.put(key, original);
            }
        }
        return themed;
    }

    /** A theme font; a missing name or height falls back to JFace's default font. */
    static FontData fontData(FontSpec spec, FontData fallback) {
        String name = spec.name() != null ? spec.name() : fallback.getName();
        int height = spec.height() > 0 ? spec.height() : fallback.getHeight();
        return new FontData(name, height, spec.style());
    }
}
