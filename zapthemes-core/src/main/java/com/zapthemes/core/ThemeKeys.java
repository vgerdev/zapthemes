package com.zapthemes.core;

/** Keys used with {@link org.eclipse.swt.widgets.Widget#setData(String, Object)}. */
public final class ThemeKeys {

    /**
     * The theme control type of a widget, e.g. {@code "Button"} or {@code "Grid"}. Set by every
     * proxy on creation and read by the {@link ThemeManager} to pick the theme rule and styler.
     */
    public static final String THEME_CONTROL_TYPE = "THEME_CONTROL_TYPE";

    /** Optional style class of a widget, e.g. {@code "primary"}; selects {@code classes} rules. */
    public static final String THEME_CLASS = "THEME_CLASS";

    private ThemeKeys() {
    }
}
