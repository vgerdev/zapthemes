package com.zapthemes.jface;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;

/** JFace's font registry follows the zoom: theme fonts, JFace's own fonts and the app's. */
class JFaceZoomTest {

    private static final String APP_FONT = "zoom.test.appfont";

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private ThemeManager manager;
    private FontRegistry registry;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry themes = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> themes.loadResource(BundledThemes.class.getClassLoader(), r));
        light = themes.resolve("zap-light");
        dark = themes.resolve("zap-dark");
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.resetZoom();
        manager.setTheme(light);
        registry = JFaceResources.getFontRegistry();
    }

    @AfterEach
    void tearDown() {
        manager.resetZoom();
        for (Shell shell : display.getShells()) {
            shell.dispose();
        }
    }

    @Test
    void themeRegistryFontsScaleAndReset() {
        // zap-base: dialog font 9 pt, header font 12 pt bold.
        manager.setZoom(2);

        assertThat(height(JFaceResources.DIALOG_FONT)).isEqualTo(18);
        assertThat(height(JFaceResources.HEADER_FONT)).isEqualTo(24);
        assertThat(registry.getFontData(JFaceResources.HEADER_FONT)[0].getStyle()).isEqualTo(SWT.BOLD);
        assertThat(JFaceResources.getDialogFont().getFontData()[0].getHeight()).isEqualTo(18);

        manager.setTheme(dark);
        assertThat(height(JFaceResources.DIALOG_FONT)).isEqualTo(18);

        manager.resetZoom();
        assertThat(height(JFaceResources.DIALOG_FONT)).isEqualTo(9);
        assertThat(height(JFaceResources.HEADER_FONT)).isEqualTo(12);
    }

    @Test
    void fontsTheThemeDoesNotSetScaleTooAndAreRestored() {
        int text = height(JFaceResources.TEXT_FONT); // not in zap-base's registries.fonts
        registry.put(APP_FONT, new FontData[] { new FontData("Arial", 10, SWT.ITALIC) });

        manager.setZoom(1.5);
        assertThat(height(JFaceResources.TEXT_FONT)).isEqualTo((int) Math.round(text * 1.5));
        assertThat(height(APP_FONT)).isEqualTo(15);
        assertThat(registry.getFontData(APP_FONT)[0].getStyle()).isEqualTo(SWT.ITALIC);

        manager.resetZoom();
        assertThat(height(JFaceResources.TEXT_FONT)).isEqualTo(text);
        assertThat(height(APP_FONT)).isEqualTo(10);
    }

    @Test
    void aFontTheAppPutsWhileZoomedIsZoomedAndBecomesTheOriginal() {
        manager.setZoom(2);
        registry.put(APP_FONT, new FontData[] { new FontData("Arial", 11, SWT.NORMAL) });

        assertThat(height(APP_FONT)).isEqualTo(22);

        manager.resetZoom();
        assertThat(height(APP_FONT)).isEqualTo(11);
    }

    @Test
    void repeatedZoomingReusesTheRegistrysFonts() {
        manager.zoomIn();
        Font dialogAt110 = JFaceResources.getDialogFont();
        Font boldAt110 = registry.getBold(JFaceResources.DIALOG_FONT);

        for (int i = 0; i < 10; i++) {
            manager.zoomIn();
            JFaceResources.getDialogFont();
            registry.getBold(JFaceResources.DIALOG_FONT);
            manager.zoomOut();
        }

        assertThat(JFaceResources.getDialogFont()).isSameAs(dialogAt110);
        assertThat(registry.getBold(JFaceResources.DIALOG_FONT)).isSameAs(boldAt110);
    }

    private int height(String key) {
        return registry.getFontData(key)[0].getHeight();
    }
}
