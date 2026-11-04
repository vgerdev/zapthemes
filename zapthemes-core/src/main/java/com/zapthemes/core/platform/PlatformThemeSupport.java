package com.zapthemes.core.platform;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.ThemeResources;
import com.zapthemes.core.style.ResolvedStyle;

/**
 * Applies a theme's {@code platform} settings: what the operating system draws itself (title bars,
 * scrollbars, native dialogs, the Windows menu bar) and widgets cannot color through SWT's API.
 *
 * <p>Settings are {@code platform["*"]} overlaid with {@code platform[<SWT.getPlatform()>]}
 * (see {@link com.zapthemes.core.style.StyleResolver#platform()}):
 * <table>
 *   <caption>Platform settings</caption>
 *   <tr><th>Block</th><th>Key</th><th>Effect</th></tr>
 *   <tr><td>{@code *}</td><td>{@code darkThemePreferred}</td><td>{@link Shell#setDarkThemePreferred(boolean)}
 *       on every shell: dark title bars and native dialogs (all platforms)</td></tr>
 *   <tr><td>{@code win32}</td><td>see {@link Win32ThemeSupport}</td><td>dark scrollbars and combos,
 *       menu bar colors, ...</td></tr>
 * </table>
 */
public class PlatformThemeSupport {

    public static final String DARK_THEME_PREFERRED = "darkThemePreferred";

    /** The support for the platform SWT runs on. */
    public static PlatformThemeSupport forCurrentPlatform() {
        return SWT.getPlatform().equals("win32") ? new Win32ThemeSupport() : new PlatformThemeSupport();
    }

    /** Called before the widgets are (re)themed, with the new theme's resources. */
    public void beforeTheme(Display display, ResolvedStyle settings, ThemeResources resources) {
    }

    /** Called for every widget after its styler, on creation and on every theme switch. */
    public void apply(Widget widget, ResolvedStyle settings) {
        if (widget instanceof Shell shell) {
            shell.setDarkThemePreferred(settings.bool(DARK_THEME_PREFERRED).orElse(false));
        }
    }

    /** Called after every widget of every shell has been re-themed. */
    public void afterTheme(Display display, ResolvedStyle settings) {
    }
}
