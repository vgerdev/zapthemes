package com.zapthemes.jface;

import java.util.Collection;
import java.util.function.Supplier;

import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.swt.widgets.Composite;
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
 *   <tr><td>{@value #INFO}</td><td>the clipped-message tooltip of TitleAreaDialog, {@code DefaultToolTip},
 *       {@code ControlDecoration} hovers</td></tr>
 *   <tr><td>{@value #POPUP}</td><td>{@code PopupDialog} (info and content-assist popups); its info text is
 *       also {@value #MUTED}</td></tr>
 *   <tr><td>{@value #ERROR}</td><td>the status line message while it shows an error</td></tr>
 *   <tr><td>{@value #PLAIN}</td><td>read-only message texts that should look like labels (InputDialog)</td></tr>
 *   <tr><td>{@value #PANE}</td><td>PreferenceDialog's page area and sash</td></tr>
 * </table>
 *
 * {@code FocusCellOwnerDrawHighlighter} registers its focus cell instead ({@link #focusCell}), so
 * the theme's {@code focusCellBackground} reaches the cell the highlighter would have painted.
 */
public final class JFaceTheming {

    public static final String BANNER = "banner";
    public static final String TITLE = "title";
    public static final String INFO = "info";
    public static final String PLAIN = "plain";
    public static final String PANE = "pane";
    public static final String POPUP = "popup";
    public static final String MUTED = "muted";
    public static final String ERROR = "error";

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

    /** {@link #classed} for {@code root} and its descendants, except {@code skip} (and theirs). */
    public static void classedTree(String themeClass, Control root, Collection<? extends Control> skip) {
        if (root == null || skip.contains(root)) {
            return;
        }
        classed(themeClass, root);
        if (root instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                classedTree(themeClass, child, skip);
            }
        }
    }

    /** Removes the style class {@link #classed} gave {@code controls}, re-applying the theme. */
    public static void unclassed(Control... controls) {
        for (Control control : controls) {
            if (control != null && !control.isDisposed() && control.getData(ThemeKeys.THEME_CLASS) != null) {
                classed(null, control);
            }
        }
    }

    /** Lets the theme paint {@code focusCell}'s cell of {@code control} (a Table or Tree). */
    public static void focusCell(Control control, Supplier<ViewerCell> focusCell) {
        control.setData(ThemeKeys.FOCUS_CELL, (ThemeKeys.FocusCell) (item, column) -> {
            ViewerCell cell = focusCell.get();
            return cell != null && cell.getItem() == item && cell.getColumnIndex() == column;
        });
    }
}
