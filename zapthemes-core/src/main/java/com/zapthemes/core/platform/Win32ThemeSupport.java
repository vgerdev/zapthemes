package com.zapthemes.core.platform;

import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Scrollable;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeResources;
import com.zapthemes.core.style.ResolvedStyle;

/**
 * Windows: SWT's documented {@code Display.setData} theme keys, plus a live switch of the
 * explorer dark mode on existing windows.
 *
 * <table>
 *   <caption>{@code platform.win32} settings</caption>
 *   <tr><th>Key</th><th>SWT key ({@code org.eclipse.swt.internal.win32.}...)</th></tr>
 *   <tr><td>{@code darkScrollbars} (boolean)</td><td>{@code useDarkModeExplorerTheme}: dark scrollbars,
 *       check/radio and push-button frames, tree arrows</td></tr>
 *   <tr><td>{@code darkCombos} (boolean)</td><td>{@code Combo.useDarkTheme}</td></tr>
 *   <tr><td>{@code darkTextIcons} (boolean)</td><td>{@code Text.useDarkThemeIcons} (search/cancel icons)</td></tr>
 *   <tr><td>{@code progressBarColors} (boolean)</td><td>{@code ProgressBar.useColors}: ProgressBar honors
 *       its background/foreground</td></tr>
 *   <tr><td>{@code menuBarBackground}, {@code menuBarForeground}, {@code menuBarBorderColor} (colors)</td>
 *       <td>{@code menuBarBackgroundColor}, {@code menuBarForegroundColor}, {@code menuBarBorderColor}</td></tr>
 *   <tr><td>{@code tableHeaderLineColor} (color)</td><td>{@code Table.headerLineColor}</td></tr>
 *   <tr><td>{@code disabledLabelForeground} (color)</td><td>{@code Label.disabledForegroundColor}</td></tr>
 * </table>
 *
 * SWT draws the text of a colored menu bar itself, always white (gray in an inactive window),
 * whatever {@code menuBarForeground} says. So the menu bar colors are used only on a background
 * that white text reads on ({@link #MENU_BAR_TEXT_CONTRAST}); on a lighter one the menu bar is left
 * to Windows, which draws it light with dark text.
 *
 * <p>SWT reads most of these flags when a widget is created. For a live switch the explorer dark mode
 * of existing scrollables, buttons, sliders and combos is updated through SWT's internal
 * {@code OS} class; if that is not available (another SWT version) only new widgets change, and a
 * warning is logged once.
 */
public final class Win32ThemeSupport extends PlatformThemeSupport {

    private static final System.Logger LOG = System.getLogger(Win32ThemeSupport.class.getName());
    private static final String PREFIX = "org.eclipse.swt.internal.win32.";

    private static final Map<String, String> BOOLEANS = Map.of(
            "darkScrollbars", "useDarkModeExplorerTheme",
            "darkCombos", "Combo.useDarkTheme",
            "darkTextIcons", "Text.useDarkThemeIcons",
            "progressBarColors", "ProgressBar.useColors");

    private static final Map<String, String> COLORS = Map.of(
            "menuBarBackground", "menuBarBackgroundColor",
            "menuBarForeground", "menuBarForegroundColor",
            "menuBarBorderColor", "menuBarBorderColor",
            "tableHeaderLineColor", "Table.headerLineColor",
            "disabledLabelForeground", "Label.disabledForegroundColor");

    /** The least contrast of SWT's white menu bar text on {@code menuBarBackground}: WCAG AA for text. */
    public static final double MENU_BAR_TEXT_CONTRAST = 4.5;

    private static final int RDW_INVALIDATE = 0x1;
    private static final int RDW_ERASE = 0x4;
    private static final int RDW_ALLCHILDREN = 0x80;
    private static final int RDW_FRAME = 0x400;

    private static Native nativeApi;
    private static boolean nativeLookupDone;

    @Override
    public void beforeTheme(Display display, ResolvedStyle settings, ThemeResources resources) {
        BOOLEANS.forEach((key, swtKey) -> display.setData(PREFIX + swtKey, settings.bool(key).orElse(false)));
        // A null value makes SWT go back to its default color.
        COLORS.forEach((key, swtKey) -> display.setData(PREFIX + swtKey, settings.color(key).map(resources::color).orElse(null)));
        if (!menuBarColorsReadable(settings.color("menuBarBackground").map(c -> c.rgb))) {
            display.setData(PREFIX + "menuBarBackgroundColor", null);
            display.setData(PREFIX + "menuBarForegroundColor", null);
        }
    }

    /**
     * Whether SWT's white menu bar text reads on {@code background}; true when there is none, as
     * then SWT does not draw the menu bar itself.
     */
    public static boolean menuBarColorsReadable(java.util.Optional<org.eclipse.swt.graphics.RGB> background) {
        return background.map(rgb -> whiteTextContrast(rgb) >= MENU_BAR_TEXT_CONTRAST).orElse(true);
    }

    /** The WCAG contrast ratio of white on {@code rgb}. */
    public static double whiteTextContrast(org.eclipse.swt.graphics.RGB rgb) {
        double luminance = 0.2126 * linear(rgb.red) + 0.7152 * linear(rgb.green) + 0.0722 * linear(rgb.blue);
        return 1.05 / (luminance + 0.05);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    @Override
    public void apply(Widget widget, ResolvedStyle settings) {
        super.apply(widget, settings);
        Native api = nativeApi();
        if (api == null || !(widget instanceof Control control)) {
            return;
        }
        // The DarkMode_ classes are dark whatever the application's mode, so a preview can be dark
        // in a light window; SWT's own switch (beforeTheme) is display-wide.
        if (control instanceof Combo) {
            boolean dark = settings.bool("darkCombos").orElse(false);
            api.theme(control, dark, dark ? "DarkMode_CFD" : "CFD");
        } else if (control instanceof Scrollable || control instanceof Button || control instanceof Slider) {
            boolean dark = settings.bool("darkScrollbars").orElse(false);
            api.theme(control, dark, dark ? "DarkMode_Explorer" : "Explorer");
        }
    }

    @Override
    public void afterTheme(Display display, ResolvedStyle settings) {
        Native api = nativeApi();
        if (api == null) {
            return;
        }
        for (Shell shell : display.getShells()) {
            // A menu bar takes the menuBar colors only when it is created; existing ones are told again.
            if (shell.getMenuBar() != null && !shell.getMenuBar().isDisposed()) {
                api.recolor(shell.getMenuBar());
            }
            api.redraw(shell);
        }
    }

    private static synchronized Native nativeApi() {
        if (!nativeLookupDone) {
            nativeLookupDone = true;
            try {
                nativeApi = new Native();
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.log(Level.WARNING, "SWT's win32 OS API is not available; dark mode switches apply to new widgets only", e);
            }
        }
        return nativeApi;
    }

    /** SWT's internal {@code org.eclipse.swt.internal.win32.OS}, looked up reflectively. */
    private static final class Native {
        private final Field handle;
        private final Method allowDarkMode;
        private final Method setWindowTheme;
        private final Method redrawWindow;
        private final Method drawMenuBar;
        private final Method initThemeColors;
        private final Method updateBackground;
        private final Method updateImage;

        Native() throws ReflectiveOperationException {
            Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
            Class<?> rect = Class.forName("org.eclipse.swt.internal.win32.RECT");
            handle = Control.class.getField("handle");
            allowDarkMode = os.getMethod("AllowDarkModeForWindow", long.class, boolean.class);
            setWindowTheme = os.getMethod("SetWindowTheme", long.class, char[].class, char[].class);
            redrawWindow = os.getMethod("RedrawWindow", long.class, rect, long.class, int.class);
            drawMenuBar = os.getMethod("DrawMenuBar", long.class);
            initThemeColors = Menu.class.getDeclaredMethod("initThemeColors");
            initThemeColors.setAccessible(true);
            updateBackground = Menu.class.getDeclaredMethod("updateBackground");
            updateBackground.setAccessible(true);
            updateImage = MenuItem.class.getDeclaredMethod("updateImage");
            updateImage.setAccessible(true);
        }

        /**
         * Gives {@code menuBar} the display's menu bar colors again, as SWT does when it creates one,
         * and its items the drawing that goes with them: drawn by SWT when there are colors, by
         * Windows when there are none.
         */
        void recolor(Menu menuBar) {
            try {
                initThemeColors.invoke(menuBar);
                updateBackground.invoke(menuBar);
                for (MenuItem item : menuBar.getItems()) {
                    updateImage.invoke(item);
                }
            } catch (ReflectiveOperationException e) {
                LOG.log(Level.DEBUG, "Could not recolor the menu bar of " + menuBar.getParent(), e);
            }
        }

        void theme(Control control, boolean dark, String themeName) {
            try {
                long hwnd = handle.getLong(control);
                allowDarkMode.invoke(null, hwnd, dark);
                setWindowTheme.invoke(null, hwnd, (themeName + "\0").toCharArray(), null);
            } catch (ReflectiveOperationException e) {
                LOG.log(Level.DEBUG, "Could not switch the window theme of " + control, e);
            }
        }

        void redraw(Shell shell) {
            try {
                long hwnd = handle.getLong(shell);
                redrawWindow.invoke(null, hwnd, null, 0L, RDW_FRAME | RDW_INVALIDATE | RDW_ERASE | RDW_ALLCHILDREN);
                drawMenuBar.invoke(null, hwnd);
            } catch (ReflectiveOperationException e) {
                LOG.log(Level.DEBUG, "Could not redraw " + shell, e);
            }
        }
    }
}
