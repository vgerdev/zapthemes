package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;
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
                           "menuBarBackground": "$bg", "menuBarForeground": "$fg" },
                "gtk": { "menuBarBackground": "$bg", "menuBarForeground": "$fg" }
              }
            }
            """), "dark");
    private static final Theme LIGHT = ThemeJson.read(new StringReader("""
            {
              "id": "platform-light",
              "palette": { "bg": "#F7F8FA", "fg": "#1F2328" },
              "platform": { "win32": { "menuBarBackground": "$bg", "menuBarForeground": "$fg" } }
            }
            """), "light");
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

    @Test
    void win32MenuBarsMadeUnderOneThemeTakeTheNextOnesColors() throws ReflectiveOperationException {
        Assumptions.assumeTrue(SWT.getPlatform().equals("win32"), "win32 only");
        ThemeManager manager = ThemeManager.get(display);
        manager.setTheme(DARK);
        Shell shell = new Shell(display);
        Menu bar = new Menu(shell, SWT.BAR);
        new MenuItem(bar, SWT.CASCADE).setText("File");
        shell.setMenuBar(bar);
        shell.open();
        assertThat(menuColors(bar)).containsExactly(colorRef(0x10, 0x10, 0x10), colorRef(0xEE, 0xEE, 0xEE));

        // SWT reads the menu bar colors when a menu bar is created; a switch must tell existing ones.
        manager.setTheme(LIGHT);
        assertThat(menuColors(bar)).containsExactly(colorRef(0xF7, 0xF8, 0xFA), colorRef(0x1F, 0x23, 0x28));

        manager.setTheme(PLAIN);
        assertThat(menuColors(bar)).as("SWT's own colors").containsExactly(-1, -1);
    }

    /** The menu's background and foreground pixels, as SWT keeps them. */
    private static int[] menuColors(org.eclipse.swt.widgets.Menu menu) throws ReflectiveOperationException {
        var background = org.eclipse.swt.widgets.Menu.class.getDeclaredField("background");
        var foreground = org.eclipse.swt.widgets.Menu.class.getDeclaredField("foreground");
        background.setAccessible(true);
        foreground.setAccessible(true);
        return new int[] { background.getInt(menu), foreground.getInt(menu) };
    }

    /** A Windows COLORREF: 0x00BBGGRR. */
    private static int colorRef(int red, int green, int blue) {
        return blue << 16 | green << 8 | red;
    }

    @Test
    void gtkMenuBarFollowsTheTheme() {
        Assumptions.assumeTrue(SWT.getPlatform().equals("gtk"), "gtk only");
        ThemeManager manager = ThemeManager.get(display);
        Shell shell = new Shell(display);
        Menu bar = new Menu(shell, SWT.BAR);
        new MenuItem(bar, SWT.CASCADE).setText("File");
        shell.setMenuBar(bar);
        shell.setSize(300, 150);
        shell.open();

        RGB expected = new RGB(0x10, 0x10, 0x10);
        manager.setTheme(DARK);
        RGB dark = menuBarPixel(shell, expected::equals);
        manager.setTheme(PLAIN);
        RGB plain = menuBarPixel(shell, rgb -> !rgb.equals(expected));

        assertThat(dark).isEqualTo(expected);
        assertThat(plain).as("the GTK theme's own menu bar").isNotEqualTo(dark);
    }

    /**
     * A menu bar pixel right of its items, once GTK has restyled and painted: sampled until it
     * satisfies {@code settled} or 3 s pass. A fixed wait was flaky on loaded CI machines (GTK
     * repaints the menu bar later there), so the assertion sees the last sample instead.
     */
    private RGB menuBarPixel(Shell shell, java.util.function.Predicate<RGB> settled) {
        long end = System.currentTimeMillis() + 3000;
        RGB rgb;
        do {
            long next = System.currentTimeMillis() + 100;
            while (System.currentTimeMillis() < next) {
                if (!display.readAndDispatch()) {
                    display.timerExec(20, () -> { });
                    display.sleep();
                }
            }
            rgb = sample(shell);
        } while (!settled.test(rgb) && System.currentTimeMillis() < end);
        return rgb;
    }

    private RGB sample(Shell shell) {
        var size = shell.getSize();
        Image image = new Image(display, size.x, size.y);
        GC gc = new GC(image);
        shell.print(gc);
        gc.dispose();
        ImageData data = image.getImageData();
        RGB rgb = data.palette.getRGB(data.getPixel(size.x / 2, 5));
        image.dispose();
        return rgb;
    }
}
