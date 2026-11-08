package com.zapthemes.widgets;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/** The canvas widgets' padding, margins, boxes and the theme's {@code arc} follow the zoom. */
class FlatZoomTest {

    private final Display display = Display.getDefault();
    private ThemeManager manager;
    private Shell shell;

    @BeforeEach
    void setUp() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        manager = ThemeManager.get(display);
        manager.resetZoom();
        manager.setTheme(registry.resolve("zap-light"));
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.resetZoom();
    }

    @Test
    void buttonPaddingScales() {
        FlatButton button = new FlatButton(shell, SWT.PUSH);
        Point at100 = button.computeSize(SWT.DEFAULT, SWT.DEFAULT);

        manager.setZoom(2);
        Point at200 = button.computeSize(SWT.DEFAULT, SWT.DEFAULT);

        // No text: only the padding (2 x 10 px wide) and the font's line height remain.
        assertThat(at100.x).isEqualTo(20);
        assertThat(at200.x).isEqualTo(40);
        assertThat(at200.y).isGreaterThan(at100.y);
    }

    @Test
    void groupMarginsScale() {
        FlatGroup group = new FlatGroup(shell, SWT.NONE);
        shell.setSize(300, 200);
        shell.layout(true, true);
        int at100 = group.getClientArea().x;

        manager.setZoom(2);
        int at200 = group.getClientArea().x;

        assertThat(at100).isEqualTo(7); // margin 6 + border 1
        assertThat(at200).isEqualTo(13);
    }

    @Test
    void theThemesArcIsASizeThatScales() {
        FlatButton button = new FlatButton(shell, SWT.PUSH);
        manager.setZoom(1.5);

        assertThat(FlatPalette.of(button, null).size("arc", 4)).isEqualTo(9); // zap-base's 6 at 150 %
        assertThat(FlatPalette.of(button, null).size("noSuchKey", 4)).isEqualTo(6);
    }
}
