package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Scrollable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/**
 * The file browser's native scroll bars (the folder tree, the Details table) are dark in the dark
 * bundled themes, zap-high-contrast included, and light in zap-light, on Windows. Read from the
 * screen, as {@code Control.print} does not draw the scroll bars.
 */
class ScrollBarThemeTest {

    private static Display display;
    private static ThemeRegistry registry;
    private ThemeManager manager;
    private Shell shell;
    private FileBrowser browser;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
    }

    private ScreenLock screen;

    @BeforeEach
    void setUp() throws java.io.IOException {
        Assumptions.assumeTrue("win32".equals(SWT.getPlatform()), "Windows draws the native scroll bars");
        screen = ScreenLock.acquire();
        manager = ThemeManager.get(display);
    }

    @AfterEach
    void tearDown() throws java.io.IOException {
        if (screen != null) {
            screen.close();
        }
        if (shell != null) {
            shell.dispose();
        }
        if (manager != null) {
            manager.setTheme(registry.resolve("zap-light"));
        }
    }

    private void open(String themeId) {
        manager.setTheme(registry.resolve(themeId));
        shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
        shell.setLayout(new FillLayout());
        browser = new FileBrowser(shell, 0);
        shell.setBounds(80, 80, 900, 360);
        shell.open();
        shell.forceActive();
        MemoryFileSystem.Builder fs = MemoryFileSystem.builder("server");
        for (int i = 0; i < 60; i++) {
            fs.folder(String.format("home/folder %02d", i));
            fs.file(String.format("home/me/file %02d.txt", i), "x");
        }
        browser.setFileSystem(fs.folder("home/me").home("home/me").build());
        idle();
    }

    @ParameterizedTest
    @ValueSource(strings = { "zap-dark", "zap-high-contrast" })
    void darkInADarkTheme(String themeId) {
        open(themeId);
        assertThat(track(tree())).as("the folder tree in %s", themeId).isLessThan(0.1);
        assertThat(track(table())).as("the Details table in %s", themeId).isLessThan(0.1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "zap-dark", "zap-high-contrast" })
    void followASwitchAtRuntime(String themeId) {
        open("zap-light");
        assertThat(track(tree())).as("the folder tree in zap-light").isGreaterThan(0.5);
        manager.setTheme(registry.resolve(themeId));
        idle();
        assertThat(track(tree())).as("the folder tree in %s", themeId).isLessThan(0.1);
        assertThat(track(table())).as("the Details table in %s", themeId).isLessThan(0.1);
        manager.setTheme(registry.resolve("zap-light"));
        idle();
        assertThat(track(tree())).as("the folder tree back in zap-light").isGreaterThan(0.5);
        assertThat(track(table())).as("the Details table back in zap-light").isGreaterThan(0.5);
    }

    private Scrollable tree() {
        return browser.navigationTree().tree;
    }

    private Scrollable table() {
        return browser.fileTable().table;
    }

    private void idle() {
        BooleanSupplier done = () -> !browser.isLoading() && browser.pendingCalls() == 0;
        long end = System.currentTimeMillis() + 10_000;
        while (!done.getAsBoolean() && System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
        long settle = System.currentTimeMillis() + 400;
        while (System.currentTimeMillis() < settle) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
    }

    /** The mean luminance of the lower part of the vertical scroll bar's track, from the screen. */
    private double track(Scrollable scrollable) {
        assertThat(scrollable.getVerticalBar().getVisible()).as("%s scrolls", scrollable).isTrue();
        Rectangle client = scrollable.getClientArea();
        int barWidth = scrollable.getVerticalBar().getSize().x;
        Rectangle bounds = scrollable.getBounds();
        int top = client.y + bounds.height / 2;
        int bottom = bounds.height - 2 * barWidth - 4;
        var origin = scrollable.toDisplay(client.x + client.width, 0);
        int width = Math.max(1, barWidth - 4);
        int height = Math.max(1, bottom - top);
        Image image = new Image(display, width, height);
        GC gc = new GC(display);
        gc.copyArea(image, origin.x + 2, origin.y + top);
        gc.dispose();
        ImageData data = image.getImageData();
        image.dispose();
        double sum = 0;
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                sum += luminance(data.palette.getRGB(data.getPixel(x, y)));
            }
        }
        Assumptions.assumeFalse(sum == 0 && locked(), "the screen cannot be read (locked desktop)");
        return sum / (data.width * data.height);
    }

    private static boolean locked() {
        return ProcessHandle.allProcesses()
                .anyMatch(p -> p.info().command().map(c -> c.toLowerCase().endsWith("logonui.exe")).orElse(false));
    }

    private static double luminance(RGB rgb) {
        return 0.2126 * linear(rgb.red) + 0.7152 * linear(rgb.green) + 0.0722 * linear(rgb.blue);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
