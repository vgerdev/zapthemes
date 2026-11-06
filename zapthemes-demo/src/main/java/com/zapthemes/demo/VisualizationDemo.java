package com.zapthemes.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.nebula.visualization.widgets.figures.AbstractScaledWidgetFigure;
import org.eclipse.nebula.visualization.widgets.figures.GaugeFigure;
import org.eclipse.nebula.visualization.widgets.figures.KnobFigure;
import org.eclipse.nebula.visualization.widgets.figures.MeterFigure;
import org.eclipse.nebula.visualization.widgets.figures.ProgressBarFigure;
import org.eclipse.nebula.visualization.widgets.figures.TankFigure;
import org.eclipse.nebula.visualization.widgets.figures.ThermometerFigure;
import org.eclipse.nebula.visualization.xygraph.dataprovider.CircularBufferDataProvider;
import org.eclipse.nebula.visualization.xygraph.figures.ToolbarArmedXYGraph;
import org.eclipse.nebula.visualization.xygraph.figures.Trace;
import org.eclipse.nebula.visualization.xygraph.figures.XYGraph;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.custom.SashForm;
import com.zapthemes.swt.widgets.Canvas;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.visualization.FigureTheming;

/**
 * Nebula Visualization on ZapThemes: an {@link XYGraph} with its toolbar and two traces, and the
 * gauge, meter, knob, tank, thermometer and progress bar figures, each on a canvas. The code is
 * plain Nebula Visualization: the forked figures take the theme's figure rules themselves
 * ({@link FigureTheming}).
 *
 * <p>{@code --smoke} checks, in every bundled theme, that the controls are tagged and that the
 * graph, its axes and the widgets' scales take the theme's colors, and exits non-zero on failure.
 * {@code --screenshots <dir>} also saves the window in each theme.
 */
public final class VisualizationDemo {

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available = DemoSupport.bundledThemes();
    private final List<AbstractScaledWidgetFigure> widgets = new ArrayList<>();
    private Shell shell;
    private XYGraph graph;

    private VisualizationDemo(Display display) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        Display display = new Display();
        VisualizationDemo demo = new VisualizationDemo(display);
        demo.open();
        int exit = 0;
        int shots = options.indexOf("--screenshots");
        if (options.contains("--smoke") || shots >= 0) {
            exit = demo.smokeTest(shots >= 0 ? Path.of(options.get(shots + 1)) : null);
            demo.shell.dispose();
        }
        while (!demo.shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
        System.exit(exit);
    }

    private void open() {
        shell = new Shell(display);
        shell.setText("ZapThemes — Nebula Visualization");
        shell.setLayout(new GridLayout(1, false));

        Composite bar = new Composite(shell, SWT.NONE);
        bar.setLayout(new GridLayout(2, false));
        Combo picker = new Combo(bar, SWT.READ_ONLY);
        available.forEach(theme -> picker.add(theme.name()));
        picker.select(0);
        picker.addListener(SWT.Selection, e -> themes.setTheme(available.get(picker.getSelectionIndex())));
        ThemeChangeListener follow = (previous, current) -> picker.select(available.indexOf(current));
        themes.addThemeChangeListener(follow);
        picker.addDisposeListener(e -> themes.removeThemeChangeListener(follow));
        new Label(bar, SWT.NONE).setText("Plain Nebula Visualization code; the forked figures take the theme's figure rules themselves.");

        SashForm sash = new SashForm(shell, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        graph = new XYGraph();
        graph.setTitle("Signal");
        graph.getPrimaryXAxis().setTitle("Time");
        graph.getPrimaryYAxis().setTitle("Amplitude");
        graph.getPrimaryXAxis().setShowMajorGrid(true);
        graph.getPrimaryYAxis().setShowMajorGrid(true);
        graph.getPrimaryXAxis().setAutoScale(true);
        graph.getPrimaryYAxis().setAutoScale(true);
        // XYGraph's default trace colors start with a dark blue, which dark themes hide.
        trace("sin", Math::sin, new RGB(0xE0, 0x9A, 0x1E));
        // Trace colors are the application's (data series): the theme leaves them alone.
        trace("cos", Math::cos, new RGB(0x2A, 0xAC, 0xB8));
        figure(sash, () -> new ToolbarArmedXYGraph(graph));

        Composite gauges = new Composite(sash, SWT.NONE);
        gauges.setLayout(new GridLayout(3, true));
        widget(gauges, new GaugeFigure(), 62);
        widget(gauges, new MeterFigure(), 35);
        widget(gauges, new KnobFigure(), 48);
        widget(gauges, new TankFigure(), 70);
        widget(gauges, new ThermometerFigure(), 22);
        ProgressBarFigure progress = new ProgressBarFigure();
        progress.setHorizontal(false);
        widget(gauges, progress, 80);
        sash.setWeights(3, 2);

        shell.setSize(1100, 560);
        shell.open();
    }

    private void trace(String name, java.util.function.DoubleUnaryOperator f, RGB color) {
        CircularBufferDataProvider data = new CircularBufferDataProvider(false);
        data.setBufferSize(200);
        double[] x = new double[200];
        double[] y = new double[200];
        for (int i = 0; i < x.length; i++) {
            x[i] = i / 10.0;
            y[i] = f.applyAsDouble(x[i]);
        }
        data.setCurrentXDataArray(x);
        data.setCurrentYDataArray(y);
        Trace trace = new Trace(name, graph.getPrimaryXAxis(), graph.getPrimaryYAxis(), data);
        if (color != null) {
            trace.setTraceColor(new Color(color));
        }
        graph.addTrace(trace);
    }

    private void widget(Composite parent, AbstractScaledWidgetFigure figure, double value) {
        figure.setRange(0, 100);
        figure.setValue(value);
        widgets.add(figure);
        Control canvas = figure(parent, () -> figure);
        canvas.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    /** A canvas showing {@code contents}. */
    private static Control figure(org.eclipse.swt.widgets.Composite parent, Supplier<IFigure> contents) {
        Canvas canvas = new Canvas(parent, SWT.DOUBLE_BUFFERED);
        canvas.setLayout(new FillLayout());
        IFigure root = contents.get();
        // Plain upstream code: the forked figures take the theme when shown and follow switches.
        new LightweightSystem(canvas).setContents(root);
        return canvas;
    }

    // ------------------------------------------------------------------ smoke test

    private int smokeTest(Path screenshots) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Theme theme : available) {
            themes.setTheme(theme);
            DemoSupport.settle(display);
            checked += check(shell, problems);
            expect(problems, theme, "plot area", graph.getPlotArea().getBackgroundColor(), "PlotArea", "background");
            expect(problems, theme, "x axis", graph.getPrimaryXAxis().getForegroundColor(), "Axis", "foreground");
            expect(problems, theme, "grid", graph.getPrimaryXAxis().getMajorGridColor(), "Axis", "majorGridColor");
            expect(problems, theme, "title", graph.getTitleColor(), "XYGraph", "titleColor");
            for (AbstractScaledWidgetFigure widget : widgets) {
                expect(problems, theme, widget.getClass().getSimpleName() + " scale", widget.getForegroundColor(),
                        "AbstractScaledWidgetFigure", "foreground");
            }
            if (screenshots != null) {
                DemoSupport.screenshot(shell, screenshots, "visualization-" + theme.id());
            }
        }
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: " + checked + " controls tagged; the graph and " + widgets.size()
                    + " widget figures follow the theme, in " + available.size() + " themes");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    private void expect(List<String> problems, Theme theme, String what, Color actual, String type, String key) {
        themes.ruleOf(type).color(key).ifPresent(expected -> {
            if (actual == null || !actual.getRGB().equals(expected.rgb)) {
                problems.add(theme.id() + ": " + what + " " + (actual == null ? null : actual.getRGB()) + ", theme says " + expected.rgb);
            }
        });
    }

    private int check(Control control, List<String> problems) {
        int count = 1;
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                count += check(child, problems);
            }
        }
        return count;
    }
}
