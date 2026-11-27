package com.zapthemes.core.platform;

import java.lang.System.Logger.Level;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 *   <tr><td>{@code darkScrollbars} (boolean; when left out, {@code darkThemePreferred})</td>
 *       <td>{@code useDarkModeExplorerTheme}: dark scrollbars, check/radio and push-button frames,
 *       tree arrows</td></tr>
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
 *
 * <p>The drop-down list of a native {@link Combo} (a {@code ComboLBox} window, not an SWT widget)
 * paints its selected item in the system highlight; in a window allowed the dark mode that is white
 * text on white. SWT allows it on every drop-down while {@code darkScrollbars} is on, so the list
 * box is subclassed (one shared native callback): before it handles {@code WM_THEMECHANGED}, which
 * {@code SetWindowTheme} sends, the dark mode is taken back for that window. The list keeps its
 * dark background (the combo's colors) and, in the {@code DarkMode_Explorer} theme, its dark scroll
 * bar; the selected item is the readable Windows highlight, white on blue.
 */
public final class Win32ThemeSupport extends PlatformThemeSupport {

    private static final System.Logger LOG = System.getLogger(Win32ThemeSupport.class.getName());
    private static final String PREFIX = "org.eclipse.swt.internal.win32.";

    public static final String DARK_SCROLLBARS = "darkScrollbars";

    private static final Map<String, String> BOOLEANS = Map.of(
            DARK_SCROLLBARS, "useDarkModeExplorerTheme",
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
    private static final int GWLP_WNDPROC = -4;
    private static final int WM_NCDESTROY = 0x82;
    private static final int WM_THEMECHANGED = 0x31A;

    private static Native nativeApi;
    private static boolean nativeLookupDone;

    @Override
    public void beforeTheme(Display display, ResolvedStyle settings, ThemeResources resources) {
        BOOLEANS.forEach((key, swtKey) -> display.setData(PREFIX + swtKey, settings.bool(key).orElse(false)));
        display.setData(PREFIX + BOOLEANS.get(DARK_SCROLLBARS), darkScrollbars(settings));
        // A null value makes SWT go back to its default color.
        COLORS.forEach((key, swtKey) -> display.setData(PREFIX + swtKey, settings.color(key).map(resources::color).orElse(null)));
        if (!menuBarColorsReadable(settings.color("menuBarBackground").map(c -> c.rgb))) {
            display.setData(PREFIX + "menuBarBackgroundColor", null);
            display.setData(PREFIX + "menuBarForegroundColor", null);
        }
    }

    /**
     * Whether the native scroll bars (and the other parts {@code useDarkModeExplorerTheme} covers)
     * are dark: {@code darkScrollbars}, or when the theme leaves it out, {@code darkThemePreferred}.
     * A dark theme that sets only the latter gets dark scroll bars too, not light ones on a dark window.
     */
    public static boolean darkScrollbars(ResolvedStyle settings) {
        return settings.bool(DARK_SCROLLBARS).orElseGet(() -> settings.bool(DARK_THEME_PREFERRED).orElse(false));
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
        if (control instanceof Combo combo) {
            boolean dark = settings.bool("darkCombos").orElse(false);
            api.theme(control, dark, dark ? "DarkMode_CFD" : "CFD");
            api.themeDropDown(combo, dark);
        } else if (control instanceof Scrollable || control instanceof Button || control instanceof Slider) {
            boolean dark = darkScrollbars(settings);
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

    /** The window procedure of the subclassed drop-down list boxes; called by native code through SWT's {@code Callback}. */
    @SuppressWarnings("unused")
    private static long listProc(long hwnd, long msg, long wParam, long lParam) {
        return nativeApi.listProc(hwnd, msg, wParam, lParam);
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
        /** {@code Control.topHandle()}, or null: the window that holds a Tree's scroll bars may be another. */
        private final Method topHandle;
        private final Method allowDarkMode;
        private final Method setWindowTheme;
        private final Method redrawWindow;
        private final Method drawMenuBar;
        private final Method initThemeColors;
        private final Method updateBackground;
        private final Method updateImage;
        private final Method getComboBoxInfo;
        private final Constructor<?> comboBoxInfo;
        private final Field comboBoxInfoSize;
        private final Field comboBoxInfoList;
        private final int comboBoxInfoSizeof;
        private final Method getWindowLongPtr;
        private final Method setWindowLongPtr;
        private final Method callWindowProc;
        private final Constructor<?> callbackConstructor;
        private final Method callbackAddress;

        /** The drop-down list boxes subclassed by {@link #listProc}: window to the procedure it replaced. */
        private final Map<Long, Long> listProcs = new ConcurrentHashMap<>();
        /** The shared native callback that calls {@link #listProc}; created once, never freed. */
        private Object listProcCallback;
        private long listProcAddress;

        Native() throws ReflectiveOperationException {
            Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
            Class<?> rect = Class.forName("org.eclipse.swt.internal.win32.RECT");
            Class<?> info = Class.forName("org.eclipse.swt.internal.win32.COMBOBOXINFO");
            Class<?> callback = Class.forName("org.eclipse.swt.internal.Callback");
            getComboBoxInfo = os.getMethod("GetComboBoxInfo", long.class, info);
            comboBoxInfo = info.getConstructor();
            comboBoxInfoSize = info.getField("cbSize");
            comboBoxInfoList = info.getField("hwndList");
            comboBoxInfoSizeof = (int) os.getMethod("COMBOBOXINFO_sizeof").invoke(null);
            getWindowLongPtr = os.getMethod("GetWindowLongPtr", long.class, int.class);
            setWindowLongPtr = os.getMethod("SetWindowLongPtr", long.class, int.class, long.class);
            callWindowProc = os.getMethod("CallWindowProc", long.class, long.class, int.class, long.class, long.class);
            callbackConstructor = callback.getConstructor(Object.class, String.class, int.class);
            callbackAddress = callback.getMethod("getAddress");
            handle = Control.class.getField("handle");
            topHandle = optionalMethod(Control.class, "topHandle");
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

        private static Method optionalMethod(Class<?> type, String name) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.log(Level.DEBUG, "No " + type.getSimpleName() + "." + name + "(); only the control's own window is themed", e);
                return null;
            }
        }

        /**
         * Themes the control's window and, when it differs, its top window. A Tree with columns, a
         * header or {@code SWT.MeasureItem} listeners is put in an {@code hwndParent} window, made
         * when the first of these comes, that owns its scroll bars. SWT gives that window the dark
         * mode only when it makes it, so a later switch must reach it too.
         */
        void theme(Control control, boolean dark, String themeName) {
            try {
                long hwnd = handle.getLong(control);
                theme(hwnd, dark, themeName);
                long top = topHandle != null ? (long) topHandle.invoke(control) : hwnd;
                if (top != hwnd && top != 0) {
                    theme(top, dark, themeName);
                }
            } catch (ReflectiveOperationException e) {
                LOG.log(Level.DEBUG, "Could not switch the window theme of " + control, e);
            }
        }

        private void theme(long hwnd, boolean dark, String themeName) throws ReflectiveOperationException {
            allowDarkMode.invoke(null, hwnd, dark);
            setWindowTheme.invoke(null, hwnd, (themeName + "\0").toCharArray(), null);
        }

        /**
         * Gives the drop-down list box of {@code combo} the scroll bar theme that goes with
         * {@code dark}, without the dark mode, and keeps SWT from allowing it again on a drop-down.
         */
        void themeDropDown(Combo combo, boolean dark) {
            try {
                Object info = comboBoxInfo.newInstance();
                comboBoxInfoSize.setInt(info, comboBoxInfoSizeof);
                if (!(boolean) getComboBoxInfo.invoke(null, handle.getLong(combo), info)) {
                    return;
                }
                long list = comboBoxInfoList.getLong(info);
                if (list == 0) {
                    return;
                }
                subclass(list);
                // Through listProc, whose WM_THEMECHANGED takes the dark mode back.
                setWindowTheme.invoke(null, list, ((dark ? "DarkMode_Explorer" : "Explorer") + "\0").toCharArray(), null);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.log(Level.DEBUG, "Could not theme the drop-down list of " + combo, e);
            }
        }

        private void subclass(long list) throws ReflectiveOperationException {
            if (listProcs.containsKey(list)) {
                return;
            }
            if (listProcAddress == 0) {
                listProcCallback = callbackConstructor.newInstance(Win32ThemeSupport.class, "listProc", 4);
                listProcAddress = (long) callbackAddress.invoke(listProcCallback);
                if (listProcAddress == 0) {
                    throw new IllegalStateException("No native callbacks left");
                }
            }
            long old = (long) getWindowLongPtr.invoke(null, list, GWLP_WNDPROC);
            listProcs.put(list, old);
            setWindowLongPtr.invoke(null, list, GWLP_WNDPROC, listProcAddress);
        }

        long listProc(long hwnd, long msg, long wParam, long lParam) {
            Long old = listProcs.get(hwnd);
            if (old == null) {
                return 0;
            }
            try {
                if (msg == WM_THEMECHANGED) {
                    allowDarkMode.invoke(null, hwnd, false);
                } else if (msg == WM_NCDESTROY) {
                    listProcs.remove(hwnd);
                }
                return (long) callWindowProc.invoke(null, old, hwnd, (int) msg, wParam, lParam);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOG.log(Level.DEBUG, "Drop-down list message " + msg + " failed", e);
                return 0;
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
