package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;

/** The theme's {@code platform} block reaches SWT, and switching it back off works. */
class PlatformThemeSupportTest {

    private static final String WIN32 = "org.eclipse.swt.internal.win32.";

    private static final Theme DARK = ThemeJson.read(new StringReader("""
            {
              "id": "platform-dark",
              "palette": { "bg": "#101010", "fg": "#EEEEEE" },
              "platform": {
                "*": { "darkThemePreferred": true },
                "win32": { "darkScrollbars": true, "darkCombos": true, "progressBarColors": true,
                           "menuBarBackground": "$bg", "menuBarForeground": "$fg" }
              }
            }
            """), "dark");
    private static final Theme PLAIN = ThemeJson.read(new StringReader("{ \"id\": \"platform-plain\" }"), "plain");

    private final Display display = Display.getDefault();

    @AfterEach
    void tearDown() {
        ThemeManager.get(display).setTheme(PLAIN);
        for (var shell : display.getShells()) {
            shell.dispose();
        }
    }

    @Test
    void switchingThemesOnExistingWidgetsDoesNotFail() {
        Shell shell = new Shell(display);
        new Table(shell, SWT.BORDER);
        new Combo(shell, SWT.DROP_DOWN);
        shell.open();

        ThemeManager.get(display).setTheme(DARK);
        ThemeManager.get(display).setTheme(PLAIN);
        ThemeManager.get(display).setTheme(DARK);

        assertThat(shell.isDisposed()).isFalse();
    }

    @Test
    void win32KeysFollowTheTheme() {
        Assumptions.assumeTrue(SWT.getPlatform().equals("win32"), "win32 only");
        ThemeManager manager = ThemeManager.get(display);

        // SWT applies useDarkModeExplorerTheme and the menu bar colors without storing them, so only
        // the keys it keeps can be read back.
        manager.setTheme(DARK);
        assertThat(display.getData(WIN32 + "Combo.useDarkTheme")).isEqualTo(true);
        assertThat(display.getData(WIN32 + "ProgressBar.useColors")).isEqualTo(true);

        manager.setTheme(PLAIN);
        assertThat(display.getData(WIN32 + "Combo.useDarkTheme")).isEqualTo(false);
        assertThat(display.getData(WIN32 + "ProgressBar.useColors")).isEqualTo(false);
    }
}
