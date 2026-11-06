package com.zapthemes.jface.text;

import java.util.List;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Control;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.jface.JFaceTheming;

/**
 * Theme keys for source viewers on the JFace Text fork, and the hooks its patches
 * ({@code zapthemes-jface-text/src/fork/text-patches}) call where upstream hard-codes colors.
 *
 * <table>
 *   <caption>Style classes set by the fork</caption>
 *   <tr><th>Class</th><th>Where</th></tr>
 *   <tr><td>{@value JFaceTheming#POPUP}</td><td>hovers and other information controls, the content assist
 *       proposal list, context information and its selector; their status line is also
 *       {@value JFaceTheming#MUTED}</td></tr>
 *   <tr><td>{@value #FRAME}</td><td>the context information popups' one-pixel frame (upstream black)</td></tr>
 *   <tr><td>{@value #RULER}</td><td>vertical rulers and their columns (line numbers, annotations,
 *       folding, changes) and the overview ruler</td></tr>
 * </table>
 *
 * Without a theme the controls are only tagged and look as upstream. Where the application chose
 * colors (for example {@code ContentAssistant.setProposalSelectorBackground}), the fork leaves the
 * control unclassed.
 *
 * <p><b>Syntax colors.</b> JFace Text leaves syntax colors to the application's scanners. The
 * {@code KEYWORD}, ... keys are JFace {@link org.eclipse.jface.resource.ColorRegistry ColorRegistry}
 * keys that the bundled themes set in {@code registries.colors}, so a scanner can take its token
 * colors from {@code JFaceResources.getColorRegistry().get(TextTheming.KEYWORD)} and recolor when
 * the registry changes (it does on every theme switch):
 *
 * <pre>{@code "registries": { "colors": { "com.zapthemes.text.keyword": "#CF8E6D" } }}</pre>
 */
public final class TextTheming {

    /** Rulers. */
    public static final String RULER = "ruler";
    /** Popup frames. */
    public static final String FRAME = "frame";

    /** Keywords. */
    public static final String KEYWORD = "com.zapthemes.text.keyword";
    /** String and character literals. */
    public static final String STRING = "com.zapthemes.text.string";
    /** Comments. */
    public static final String COMMENT = "com.zapthemes.text.comment";
    /** Number literals. */
    public static final String NUMBER = "com.zapthemes.text.number";

    private TextTheming() {
    }

    /** Gives {@code root} and its descendants {@code popup}, and {@code muted} (status lines) {@code popup muted}. */
    public static void popup(Control root, Control... muted) {
        JFaceTheming.classedTree(JFaceTheming.POPUP, root, List.of(nonNull(muted)));
        JFaceTheming.classed(JFaceTheming.POPUP + " " + JFaceTheming.MUTED, muted);
    }

    /** Gives {@code controls} {@value #FRAME}. */
    public static void frame(Control... controls) {
        JFaceTheming.classed(FRAME, controls);
    }

    /** Gives {@code controls} {@value #RULER}. */
    public static void ruler(Control... controls) {
        JFaceTheming.classed(RULER, controls);
    }

    /**
     * The background a ruler column paints with when the application set none: the theme's for
     * {@code canvas} (a {@value #RULER}), or upstream's {@code fallback} without a theme.
     */
    public static Color rulerBackground(Control canvas, Color fallback) {
        Color themed = rulerColor(canvas, "background");
        return themed != null ? themed : fallback;
    }

    /**
     * The foreground (line numbers) a ruler column paints with when the application set none: the
     * theme's for {@code canvas}, or {@code null} without a theme (upstream leaves the GC's default).
     */
    public static Color rulerForeground(Control canvas) {
        return rulerColor(canvas, "foreground");
    }

    private static Color rulerColor(Control canvas, String property) {
        if (canvas == null || canvas.isDisposed() || !RULER.equals(canvas.getData(ThemeKeys.THEME_CLASS))) {
            return null;
        }
        ThemeManager manager = ThemeManager.get(canvas.getDisplay());
        if (manager.getTheme() == Theme.EMPTY) {
            return null;
        }
        return manager.styleOf(canvas).color(property).map(rgba -> new Color(rgba.rgb)).orElse(null);
    }

    private static Control[] nonNull(Control... controls) {
        return java.util.Arrays.stream(controls).filter(c -> c != null).toArray(Control[]::new);
    }
}
