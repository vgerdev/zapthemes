package com.zapthemes.core.style;

import java.util.List;
import java.util.Optional;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

import com.zapthemes.core.ThemeResources;
import com.zapthemes.core.model.FontSpec;

/** What a {@link ControlStyler} needs besides the style: the current theme's resources. */
public record ThemeContext(ThemeResources resources) {

    private static final FontSpec SYSTEM_FONT = new FontSpec(List.of(), 0, SWT.NORMAL);

    /** The color for {@code key}, or {@code null} (platform default) if the theme has none. */
    public Color color(ResolvedStyle style, String key) {
        return style.color(key).map(resources::color).orElse(null);
    }

    /** The font for {@code key}, or {@code null} (platform default) if the theme has none. */
    public Font font(ResolvedStyle style, String key) {
        return style.font(key).map(resources::font).orElse(null);
    }

    /**
     * The size in pixels for {@code key} at the current zoom (a corner radius, a thickness), or
     * empty if the theme has none. Only for keys the catalog describes as sizes.
     */
    public Optional<Integer> size(ResolvedStyle style, String key) {
        return style.integer(key).map(resources::zoomedPixels);
    }

    /**
     * {@code key}'s integer for a widget of theme type {@code type}: zoomed if the
     * {@link com.zapthemes.core.catalog.PropertyCatalog} describes it as a size, as is otherwise.
     */
    public Optional<Integer> integer(ResolvedStyle style, String type, String key) {
        return Sizes.isSize(type, key) ? size(style, key) : style.integer(key);
    }

    /**
     * The font for a control whose theme sets none: {@code null} (the platform default) at 100 %
     * zoom, otherwise the system font at the current zoom.
     */
    public Font defaultFont() {
        return resources.zoom() == 1 ? null : resources.font(SYSTEM_FONT);
    }
}
