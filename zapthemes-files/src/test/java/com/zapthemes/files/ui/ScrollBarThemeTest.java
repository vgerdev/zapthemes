package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
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
 * The file browser's native scroll bars (the folder tree, the Details view) are dark in the dark
 * bundled themes, zap-high-contrast included, and light in zap-light, on Windows. Read with
 * {@code PrintWindow} ({@link ScrollBarPixels}), not from the screen.
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


    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue("win32".equals(SWT.getPlatform()), "Windows draws the native scroll bars");
        manager = ThemeManager.get(display);
    }

    @AfterEach
    void tearDown() {
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
        assertThat(track(table())).as("the Details view in %s", themeId).isLessThan(0.1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "zap-dark", "zap-high-contrast" })
    void followASwitchAtRuntime(String themeId) {
        open("zap-light");
        assertThat(track(tree())).as("the folder tree in zap-light").isGreaterThan(0.5);
        manager.setTheme(registry.resolve(themeId));
        idle();
        assertThat(track(tree())).as("the folder tree in %s", themeId).isLessThan(0.1);
        assertThat(track(table())).as("the Details view in %s", themeId).isLessThan(0.1);
        manager.setTheme(registry.resolve("zap-light"));
        idle();
        assertThat(track(tree())).as("the folder tree back in zap-light").isGreaterThan(0.5);
        assertThat(track(table())).as("the Details view back in zap-light").isGreaterThan(0.5);
    }

    private Scrollable tree() {
        return browser.navigationTree().tree;
    }

    private Scrollable table() {
        return browser.fileGrid();
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

    /** The mean luminance of the lower part of the vertical scroll bar's track, as Windows draws it. */
    private double track(Scrollable scrollable) {
        assertThat(scrollable.getVerticalBar().getVisible()).as("%s scrolls", scrollable).isTrue();
        try {
            return ScrollBarPixels.track(scrollable);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
