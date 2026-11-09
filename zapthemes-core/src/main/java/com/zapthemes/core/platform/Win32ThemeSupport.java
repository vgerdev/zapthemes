package com.zapthemes.core.platform;

import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
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
 * SWT reads most of these flags when a widget is created. For a live switch the explorer dark mode
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

        Native() throws ReflectiveOperationException {
            Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
            Class<?> rect = Class.forName("org.eclipse.swt.internal.win32.RECT");
            handle = Control.class.getField("handle");
            allowDarkMode = os.getMethod("AllowDarkModeForWindow", long.class, boolean.class);
            setWindowTheme = os.getMethod("SetWindowTheme", long.class, char[].class, char[].class);
            redrawWindow = os.getMethod("RedrawWindow", long.class, rect, long.class, int.class);
            drawMenuBar = os.getMethod("DrawMenuBar", long.class);
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
