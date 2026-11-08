package com.zapthemes.visualization;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.nebula.visualization.xygraph.figures.XYGraph;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.themes.BundledThemes;

/** Fonts of draw2d figures the theme does not set (XYGraph's title, its axes) follow the zoom. */
class FigureZoomTest {

    private final Display display = Display.getDefault();
    private ThemeManager manager;
    private Shell shell;

    @BeforeEach
    void setUp() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        manager = ThemeManager.get(display);
        manager.resetZoom();
        manager.setTheme(registry.resolve("zap-dark"));
        shell = new com.zapthemes.swt.widgets.Shell(display);
        shell.setLayout(new FillLayout());
        shell.setSize(500, 400);
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.resetZoom();
    }

    @Test
    void xyGraphTitleAndAxisFontsScaleAndComeBack() {
        XYGraph graph = new XYGraph();
        graph.setTitle("Zoom");
        show(graph);
        Font title = graph.getTitleFont();
        Font axisTitle = graph.getPrimaryXAxis().getTitleFont();
        assertThat(height(title)).isEqualTo(12); // XYGraph's own 12 pt bold

        manager.setZoom(1.5);
        assertThat(height(graph.getTitleFont())).isEqualTo(18);
        assertThat(graph.getTitleFont().getFontData()[0].getStyle()).isEqualTo(SWT.BOLD);
        assertThat(height(graph.getPrimaryXAxis().getTitleFont())).isEqualTo(18);

        manager.resetZoom();
        assertThat(graph.getTitleFont()).isSameAs(title);
        assertThat(graph.getPrimaryXAxis().getTitleFont()).isSameAs(axisTitle);
    }

    @Test
    void repeatedZoomingDoesNotDriftOrLeak() {
        XYGraph graph = new XYGraph();
        show(graph);
        cycle();
        int fonts = manager.resources().fontCount();
        for (int i = 0; i < 5; i++) {
            cycle();
        }
        assertThat(manager.resources().fontCount()).isEqualTo(fonts);
        assertThat(height(graph.getTitleFont())).isEqualTo(12);
    }

    @Test
    void aFontTheAppSetsWhileZoomedIsTakenAtThatZoom() {
        XYGraph graph = new XYGraph();
        show(graph);
        manager.setZoom(2);
        Font big = new Font(display, "Arial", 28, SWT.NORMAL);
        try {
            graph.setTitleFont(big);
            manager.resetZoom();
            assertThat(height(graph.getTitleFont())).isEqualTo(14);
        } finally {
            shell.dispose();
            big.dispose();
        }
    }

    private void cycle() {
        for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
            manager.zoomIn();
        }
        for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
            manager.zoomOut();
        }
        manager.resetZoom();
    }

    private static int height(Font font) {
        return font.getFontData()[0].getHeight();
    }

    private void show(org.eclipse.draw2d.IFigure figure) {
        Canvas canvas = new com.zapthemes.swt.widgets.Canvas(shell, SWT.NONE);
        new LightweightSystem(canvas).setContents(figure);
        shell.open();
        while (display.readAndDispatch()) {
            // Settle.
        }
    }
}
