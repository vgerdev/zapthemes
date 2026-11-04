package com.zapthemes.core.style;

import org.eclipse.swt.widgets.Widget;

/**
 * Applies a resolved style to one kind of widget. A styler must set <em>every</em> property it
 * owns, using {@code null} (the platform default) when the theme has no value, so that switching
 * themes never leaves values or disposed resources from the previous theme behind.
 */
@FunctionalInterface
public interface ControlStyler {

    void apply(Widget widget, ResolvedStyle style, ThemeContext context);
}
