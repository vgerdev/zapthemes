package com.zapthemes.visualization;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.nebula.visualization.widgets.figures.GaugeFigure;
import org.eclipse.nebula.visualization.widgets.figures.TankFigure;
import org.eclipse.nebula.visualization.xygraph.dataprovider.CircularBufferDataProvider;
import org.eclipse.nebula.visualization.xygraph.figures.Trace;
import org.eclipse.nebula.visualization.xygraph.figures.XYGraph;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
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

/**
 * Nebula Visualization's draw2d figures take the theme's figure rules with no call from the
 * application ({@link FigureTheming#attach} from the fork's {@code addNotify}): when shown, on theme
 * switches and for figures added later.
 */
class FigureThemingTest {

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private ThemeManager manager;
    private Shell shell;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        light = registry.resolve("zap-light");
        dark = registry.resolve("zap-dark");
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.setTheme(dark);
        shell = new com.zapthemes.swt.widgets.Shell(display);
        shell.setLayout(new FillLayout());
        shell.setSize(500, 400);
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.setTheme(dark);
    }

    @Test
    void typesAreTheVisualizationClassesMostGeneralFirst() {
        assertThat(FigureTheming.types(GaugeFigure.class)).containsExactly("AbstractScaledWidgetFigure",
                "AbstractMarkedWidgetFigure", "AbstractRoundRampedFigure", "GaugeFigure");
        assertThat(FigureTheming.types(org.eclipse.draw2d.Label.class)).as("draw2d's own figures are not types").isEmpty();
    }

    @Test
    void anXYGraphFollowsTheTheme() {
        XYGraph graph = new XYGraph();
        show(graph);

        assertGraph(graph, dark);
        manager.setTheme(light);
        assertGraph(graph, light);
    }

    @Test
    void figuresAddedLaterGetTheThemeAndTraceColorsStayTheApps() {
        XYGraph graph = new XYGraph();
        show(graph);
        Trace trace = new Trace("t", graph.getPrimaryXAxis(), graph.getPrimaryYAxis(), new CircularBufferDataProvider(false));
        trace.setTraceColor(new Color(10, 20, 30));
        // The legend appears with the first trace.
        graph.addTrace(trace);
        graph.getUpdateManager().performValidation();

        var legend = graph.getLegendMap().get(graph.getPrimaryYAxis());
        assertThat(legend.getForegroundColor().getRGB()).isEqualTo(palette(dark, "foreground"));
        assertThat(trace.getTraceColor().getRGB()).isEqualTo(new RGB(10, 20, 30));
    }

    @Test
    void widgetFiguresTakeScaleAndFillColors() {
        TankFigure tank = new TankFigure();
        show(tank);

        assertThat(tank.getForegroundColor().getRGB()).isEqualTo(palette(dark, "foreground"));
        assertThat(tank.getFillColor().getRGB()).isEqualTo(palette(dark, "accent"));
        assertThat(tank.getFillBackgroundColor().getRGB()).isEqualTo(palette(dark, "raised"));
    }

    @Test
    void withoutAThemeTheFiguresGetTheirOwnColorsBack() {
        XYGraph graph = new XYGraph();
        RGB plot = graph.getPlotArea().getBackgroundColor().getRGB();
        Color axis = graph.getPrimaryXAxis().getLocalForegroundColor();
        show(graph);
        assertThat(graph.getPlotArea().getBackgroundColor().getRGB()).isNotEqualTo(plot);

        manager.setTheme(Theme.EMPTY);
        assertThat(graph.getPlotArea().getBackgroundColor().getRGB()).isEqualTo(plot);
        assertThat(graph.getPrimaryXAxis().getLocalForegroundColor()).as("its own color (none: inherited), as before").isEqualTo(axis);
    }

    @Test
    void aRemovedFigureNoLongerFollowsTheTheme() {
        TankFigure tank = new TankFigure();
        show(tank);
        tank.getParent().remove(tank);
        manager.setTheme(light);

        assertThat(tank.getFillColor().getRGB()).as("still dark's").isEqualTo(palette(dark, "accent"));
        assertThat(tank.getForegroundColor().getRGB()).isEqualTo(palette(dark, "foreground"));
    }

    private void show(org.eclipse.draw2d.IFigure figure) {
        Canvas canvas = new com.zapthemes.swt.widgets.Canvas(shell, SWT.NONE);
        // Plain upstream code: the forked figures attach the theme themselves when shown.
        new LightweightSystem(canvas).setContents(figure);
        shell.open();
        while (display.readAndDispatch()) {
            // Settle.
        }
    }

    private static void assertGraph(XYGraph graph, Theme theme) {
        assertThat(graph.getPlotArea().getBackgroundColor().getRGB()).as("plot area").isEqualTo(palette(theme, "surface"));
        assertThat(graph.getPrimaryXAxis().getForegroundColor().getRGB()).as("axis").isEqualTo(palette(theme, "foreground"));
        assertThat(graph.getPrimaryYAxis().getMajorGridColor().getRGB()).as("grid").isEqualTo(palette(theme, "border"));
        assertThat(graph.getTitleColor().getRGB()).as("title").isEqualTo(palette(theme, "foreground"));
    }

    private static RGB palette(Theme theme, String name) {
        return com.zapthemes.core.style.ThemeNames.of(theme).color(name).orElseThrow().rgb;
    }
}
