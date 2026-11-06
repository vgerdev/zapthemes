package com.zapthemes.core.style;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

import com.zapthemes.core.ThemeResources;
import com.zapthemes.core.model.FontSpec;

/** What a {@link ControlStyler} needs besides the style: the current theme's resources. */
public record ThemeContext(ThemeResources resources) {

    private static final FontSpec SYSTEM_FONT = new FontSpec(null, 0, SWT.NORMAL);

    /** The color for {@code key}, or {@code null} (platform default) if the theme has none. */
    public Color color(ResolvedStyle style, String key) {
        return style.color(key).map(resources::color).orElse(null);
    }

    /** The font for {@code key}, or {@code null} (platform default) if the theme has none. */
    public Font font(ResolvedStyle style, String key) {
        return style.font(key).map(resources::font).orElse(null);
    }

    /**
     * The font for a control whose theme sets none: {@code null} (the platform default) at 100 %
     * zoom, otherwise the system font at the current zoom.
     */
    public Font defaultFont() {
        return resources.zoom() == 1 ? null : resources.font(SYSTEM_FONT);
    }
}
