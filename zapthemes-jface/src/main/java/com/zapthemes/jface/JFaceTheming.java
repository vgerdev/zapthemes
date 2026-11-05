package com.zapthemes.jface;

import org.eclipse.swt.widgets.Control;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;

/**
 * Called from the JFace fork's patches ({@code zapthemes-jface/src/fork/jface-patches}) where
 * upstream JFace hard-codes colors (banner white, info yellow, list white). Instead of a fixed color,
 * the areas get a style class, so every theme can style them and theme switches keep them distinct.
 *
 * <table>
 *   <caption>Style classes set by the fork</caption>
 *   <tr><th>Class</th><th>Where</th></tr>
 *   <tr><td>{@value #BANNER}</td><td>TitleAreaDialog / WizardDialog title area</td></tr>
 *   <tr><td>{@value #TITLE}</td><td>the title label in that area (with {@value #BANNER})</td></tr>
 *   <tr><td>{@value #INFO}</td><td>the clipped-message tooltip of TitleAreaDialog</td></tr>
 *   <tr><td>{@value #PLAIN}</td><td>read-only message texts that should look like labels (InputDialog)</td></tr>
 *   <tr><td>{@value #PANE}</td><td>PreferenceDialog's page area and sash</td></tr>
 * </table>
 */
public final class JFaceTheming {

    public static final String BANNER = "banner";
    public static final String TITLE = "title";
    public static final String INFO = "info";
    public static final String PLAIN = "plain";
    public static final String PANE = "pane";

    private JFaceTheming() {
    }

    /**
     * Gives {@code controls} the style class {@code themeClass} and re-applies the current theme,
     * replacing the colors JFace set. Without a theme the controls are only tagged, so JFace looks
     * exactly as upstream.
     */
    public static void classed(String themeClass, Control... controls) {
        for (Control control : controls) {
            if (control == null || control.isDisposed()) {
                continue;
            }
            ThemeManager manager = ThemeManager.get(control.getDisplay());
            if (manager.getTheme() == Theme.EMPTY) {
                control.setData(ThemeKeys.THEME_CLASS, themeClass);
            } else {
                manager.setThemeClass(control, themeClass);
            }
        }
    }
}
