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

    /**
     * Optional {@link FocusCell} of a Table or Tree, e.g. set by JFace's focus-cell highlighter. A
     * themed selection paints that cell in the theme's {@code focusCellBackground} and
     * {@code focusCellForeground}.
     */
    public static final String FOCUS_CELL = "zapthemes.focusCell";

    /**
     * {@link Boolean#FALSE} on a control (or one of its parents) leaves Ctrl + mouse wheel and
     * Ctrl+= / Ctrl+- / Ctrl+0 (Cmd on macOS) over it to the control itself instead of zooming the
     * app, e.g. for a chart that zooms its own plot. See {@link ThemeManager#setZoomShortcutsEnabled}.
     */
    public static final String ZOOM_SHORTCUTS = "zapthemes.zoomShortcuts";

    /**
     * A font the application set on a control with {@code setFont}, a
     * {@link com.zapthemes.core.style.DefaultStyler.AppFont}: kept through theme switches and scaled
     * by the zoom instead of the theme's font. Set by the styler when it finds a font it did not set
     * there. {@link ThemeManager#setThemeClass} and {@link ThemeManager#followThemeFont} clear it.
     */
    public static final String APP_FONT = "zapthemes.appFont";

    /** Which cell of a Table or Tree has the keyboard focus; see {@link #FOCUS_CELL}. */
    @FunctionalInterface
    public interface FocusCell {
        boolean isFocusCell(org.eclipse.swt.widgets.Item item, int column);
    }

    private ThemeKeys() {
    }
}
