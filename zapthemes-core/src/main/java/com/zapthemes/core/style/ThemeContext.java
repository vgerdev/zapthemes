package com.zapthemes.core.style;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

import com.zapthemes.core.ThemeResources;

/** What a {@link ControlStyler} needs besides the style: the current theme's resources. */
public record ThemeContext(ThemeResources resources) {

    /** The color for {@code key}, or {@code null} (platform default) if the theme has none. */
    public Color color(ResolvedStyle style, String key) {
        return style.color(key).map(resources::color).orElse(null);
    }

    /** The font for {@code key}, or {@code null} (platform default) if the theme has none. */
    public Font font(ResolvedStyle style, String key) {
        return style.font(key).map(resources::font).orElse(null);
    }
}
