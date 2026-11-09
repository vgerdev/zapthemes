package com.zapthemes.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.nebula.visualization.xygraph.dataprovider.CircularBufferDataProvider;
import org.eclipse.nebula.visualization.xygraph.figures.Trace;
import org.eclipse.nebula.visualization.xygraph.figures.XYGraph;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Widget;

import com.google.gson.JsonParser;
import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.nebula.grid.Grid;
import com.zapthemes.nebula.grid.GridColumn;
import com.zapthemes.nebula.grid.GridItem;
import com.zapthemes.swt.custom.CTabFolder;
import com.zapthemes.swt.custom.CTabItem;
import com.zapthemes.swt.custom.SashForm;
import com.zapthemes.swt.custom.StyledText;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Canvas;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Group;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.ProgressBar;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeItem;

/**
 * {@link ThemeManager#preview}: the same controls twice, side by side. The left pane has the
 * window's theme; the right pane previews another theme, as a theme editor's preview pane will.
 * Each pane has buttons, check and radio buttons, a text field and a combo, a table and a tree,
 * tabs, a styled text with headings and code, a progress bar, a Nebula Grid and an XYGraph.
 * <em>Edit preview</em> changes the previewed theme's accent and background in place.
 *
 * <p>{@code --smoke} checks, for every pair of bundled themes, that each control of each pane
 * has the colors its pane's theme gives it, and that editing the previewed theme recolors the same
 * controls without disposing any; exits non-zero on failure. {@code --screenshots <dir>} also saves
 * the window for each window theme, with the next theme previewed.
 */
public final class PreviewDemo {

    private static final RGB EDITED_BACKGROUND = new RGB(0x24, 0x1B, 0x2E);

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available = DemoSupport.bundledThemes();
    private Shell shell;
    private Composite own;
    private Composite preview;
    private Combo previewPicker;
    private final Map<Composite, XYGraph> graphs = new IdentityHashMap<>();
    private final List<CTabFolder> folders = new ArrayList<>();

    private PreviewDemo(Display display) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        Display display = new Display();
        PreviewDemo demo = new PreviewDemo(display);
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
        shell.setText("ZapThemes — Preview themes");
        shell.setLayout(new GridLayout(1, false));

        Composite bar = new Composite(shell, SWT.NONE);
        bar.setLayout(new GridLayout(6, false));
        new Label(bar, SWT.NONE).setText("Window theme:");
        Combo windowPicker = picker(bar);
        windowPicker.select(0);
        windowPicker.addListener(SWT.Selection, e -> themes.setTheme(available.get(windowPicker.getSelectionIndex())));
        ThemeChangeListener follow = (previous, current) -> windowPicker.select(available.indexOf(current));
        themes.addThemeChangeListener(follow);
        windowPicker.addDisposeListener(e -> themes.removeThemeChangeListener(follow));
        new Label(bar, SWT.NONE).setText("Preview theme:");
        previewPicker = picker(bar);
        previewPicker.select(1);
        previewPicker.addListener(SWT.Selection, e -> themes.preview(preview, available.get(previewPicker.getSelectionIndex())));
        Button edit = new Button(bar, SWT.PUSH);
        edit.setText("Edit preview");
        edit.setToolTipText("Changes the previewed theme's accent and background, in place");
        edit.addListener(SWT.Selection, e -> themes.preview(preview, edited(themes.previewOf(preview))));
        new Label(bar, SWT.NONE).setText("The right pane keeps its own theme through window theme switches.");

        SashForm sash = new SashForm(shell, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        own = pane(sash, "Window theme");
        preview = pane(sash, "Preview");
        themes.preview(preview, available.get(1));

        shell.setSize(1280, 760);
        shell.open();
    }

    private Combo picker(Composite parent) {
        Combo picker = new Combo(parent, SWT.READ_ONLY);
        available.forEach(theme -> picker.add(theme.name()));
        return picker;
    }

    /** {@code theme} with a pink accent on a plum background, as an editor's edit would give. */
    static Theme edited(Theme theme) {
        var names = Map.of(
                "accent", JsonParser.parseString("{\"color\": \"#D94F9A\"}"),
                "background", JsonParser.parseString("{\"color\": \"#241B2E\"}"));
        return new Theme(Theme.FORMAT_VERSION, theme.id(), theme.name() + " (edited)", null, null, null, names, null, null,
                null, null, null, null).over(theme);
    }

    /** One pane of sample controls. */
    private Composite pane(org.eclipse.swt.widgets.Composite parent, String title) {
        Composite pane = new Composite(parent, SWT.NONE);
        pane.setLayout(new GridLayout(2, true));

        Label heading = new Label(pane, SWT.NONE);
        heading.setText(title);
        themes.setThemeClass(heading, "heading1");
        heading.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));

        Composite buttons = new Composite(pane, SWT.NONE);
        buttons.setLayout(new RowLayout());
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        Button ok = new Button(buttons, SWT.PUSH);
        ok.setText("Primary");
        themes.setThemeClass(ok, "primary");
        new Button(buttons, SWT.PUSH).setText("Cancel");
        Button check = new Button(buttons, SWT.CHECK);
        check.setText("Check");
        check.setSelection(true);
        Button radio = new Button(buttons, SWT.RADIO);
        radio.setText("Radio");
        radio.setSelection(true);
        new Button(buttons, SWT.RADIO).setText("Other");

        Text text = new Text(pane, SWT.BORDER);
        text.setText("A text field");
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Combo combo = new Combo(pane, SWT.READ_ONLY);
        combo.setItems("A combo", "Second", "Third");
        combo.select(0);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Table table = new Table(pane, SWT.BORDER | SWT.FULL_SELECTION);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        for (String name : new String[] { "Name", "Size" }) {
            TableColumn column = new TableColumn(table, SWT.NONE);
            column.setText(name);
            column.setWidth(110);
        }
        for (int i = 1; i <= 4; i++) {
            new TableItem(table, SWT.NONE).setText(new String[] { "Row " + i, i * 12 + " KB" });
        }
        table.select(1);
        table.setLayoutData(grow(90));
        Tree tree = new Tree(pane, SWT.BORDER);
        TreeItem root = new TreeItem(tree, SWT.NONE);
        root.setText("Project");
        for (String name : new String[] { "src", "docs", "build.gradle" }) {
            new TreeItem(root, SWT.NONE).setText(name);
        }
        root.setExpanded(true);
        tree.setLayoutData(grow(90));

        CTabFolder tabs = new CTabFolder(pane, SWT.BORDER);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        CTabItem editorTab = new CTabItem(tabs, SWT.NONE);
        editorTab.setText("Styled text");
        StyledText styled = new StyledText(tabs, SWT.MULTI | SWT.WRAP);
        styled.setText("Heading\nSome text with code: theme.over(base)\n");
        styled.setStyleRange(new StyleRange(0, 7, null, null, SWT.BOLD));
        StyleRange code = new StyleRange(styled.getText().indexOf("theme."), "theme.over(base)".length(), null, null);
        code.font = themes.resources().font("Consolas", SWT.NORMAL, 9);
        styled.setStyleRange(code);
        editorTab.setControl(styled);
        CTabItem gridTab = new CTabItem(tabs, SWT.NONE);
        gridTab.setText("Grid");
        Grid grid = new Grid(tabs, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        grid.setHeaderVisible(true);
        for (String name : new String[] { "Task", "Owner" }) {
            GridColumn column = new GridColumn(grid, SWT.NONE);
            column.setText(name);
            column.setWidth(120);
        }
        for (int i = 1; i <= 4; i++) {
            GridItem row = new GridItem(grid, SWT.NONE);
            row.setText(0, "Task " + i);
            row.setText(1, i % 2 == 0 ? "Ada" : "Linus");
        }
        grid.setSelection(0);
        gridTab.setControl(grid);
        CTabItem graphTab = new CTabItem(tabs, SWT.NONE);
        graphTab.setText("Chart");
        Canvas canvas = new Canvas(tabs, SWT.DOUBLE_BUFFERED);
        XYGraph graph = graph();
        new LightweightSystem(canvas).setContents(graph);
        graphTab.setControl(canvas);
        graphs.put(pane, graph);
        tabs.setSelection(0);
        folders.add(tabs);

        ProgressBar progress = new ProgressBar(pane, SWT.HORIZONTAL);
        progress.setSelection(60);
        progress.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        return pane;
    }

    private static GridData grow(int height) {
        GridData data = new GridData(SWT.FILL, SWT.FILL, true, false);
        data.heightHint = height;
        return data;
    }

    private static XYGraph graph() {
        XYGraph graph = new XYGraph();
        graph.setTitle("Signal");
        graph.getPrimaryXAxis().setShowMajorGrid(true);
        graph.getPrimaryYAxis().setShowMajorGrid(true);
        graph.getPrimaryXAxis().setAutoScale(true);
        graph.getPrimaryYAxis().setAutoScale(true);
        CircularBufferDataProvider data = new CircularBufferDataProvider(false);
        data.setBufferSize(100);
        double[] x = new double[100];
        double[] y = new double[100];
        for (int i = 0; i < x.length; i++) {
            x[i] = i / 10.0;
            y[i] = Math.sin(x[i]);
        }
        data.setCurrentXDataArray(x);
        data.setCurrentYDataArray(y);
        Trace trace = new Trace("sin", graph.getPrimaryXAxis(), graph.getPrimaryYAxis(), data);
        trace.setTraceColor(new Color(new RGB(0xE0, 0x9A, 0x1E)));
        graph.addTrace(trace);
        return graph;
    }

    // ------------------------------------------------------------------ smoke test

    private int smokeTest(Path screenshots) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (int w = 0; w < available.size(); w++) {
            Theme window = available.get(w);
            themes.setTheme(window);
            for (Theme previewed : available) {
                themes.preview(preview, previewed);
                DemoSupport.settle(display);
                checked += check(own, window, problems);
                checked += check(preview, previewed, problems);
            }
            if (screenshots != null) {
                Theme next = available.get((w + 1) % available.size());
                previewPicker.select(available.indexOf(next));
                themes.preview(preview, next);
                DemoSupport.settle(display);
                DemoSupport.screenshot(shell, screenshots, "preview-" + window.id() + "-showing-" + next.id());
            }
        }

        if (screenshots != null) {
            // The Grid paints with the forks' system colors, and the chart is draw2d: both follow the pane.
            themes.setTheme(available.get(0));
            themes.preview(preview, available.get(1));
            previewPicker.select(1);
            for (int tab = 1; tab <= 2; tab++) {
                int selected = tab;
                folders.forEach(folder -> folder.setSelection(selected));
                DemoSupport.settle(display);
                DemoSupport.screenshot(shell, screenshots, "preview-" + (tab == 1 ? "grid" : "chart"));
            }
            folders.forEach(folder -> folder.setSelection(0));
        }

        // An edit re-themes the same controls: nothing is disposed or created.
        List<Widget> before = widgets(preview);
        List<Widget> disposed = new ArrayList<>();
        before.forEach(widget -> widget.addListener(SWT.Dispose, e -> disposed.add(e.widget)));
        Theme edited = edited(available.get(1));
        themes.preview(preview, edited);
        DemoSupport.settle(display);
        checked += check(preview, edited, problems);
        if (!disposed.isEmpty() || !widgets(preview).equals(before)) {
            problems.add("editing the preview disposed " + disposed.size() + " widgets or changed the tree");
        }
        if (!preview.getBackground().getRGB().equals(EDITED_BACKGROUND)) {
            problems.add("edited preview background " + preview.getBackground().getRGB() + ", expected " + EDITED_BACKGROUND);
        }
        if (screenshots != null) {
            DemoSupport.screenshot(shell, screenshots, "preview-edited");
        }

        // Ending the preview gives the pane the window's theme back.
        themes.endPreview(preview);
        DemoSupport.settle(display);
        checked += check(preview, themes.getTheme(), problems);
        themes.preview(preview, available.get(1));

        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: " + checked + " control checks over " + available.size() * available.size()
                    + " window and preview theme pairs, an edit in place and the end of a preview");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    /** Every control of {@code pane}: tagged, styled with {@code theme}, background as its style says. */
    private int check(Composite pane, Theme theme, List<String> problems) {
        int[] count = { 0 };
        walk(pane, control -> {
            count[0]++;
            if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
                problems.add(control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
            }
            if (themes.themeOf(control) != theme) {
                problems.add(control + " is styled with " + themes.themeOf(control).id() + ", not " + theme.id());
            }
            ResolvedStyle style = themes.styleOf(control);
            style.color("background").ifPresent(expected -> {
                if (!control.getBackground().getRGB().equals(expected.rgb)) {
                    problems.add(theme.id() + ": " + control + " background " + control.getBackground().getRGB()
                            + ", its style says " + expected.rgb);
                }
            });
        });
        XYGraph graph = graphs.get(pane);
        themes.ruleOf(pane, "PlotArea").color("background").ifPresent(expected -> {
            if (!graph.getPlotArea().getBackgroundColor().getRGB().equals(expected.rgb)) {
                problems.add(theme.id() + ": plot area " + graph.getPlotArea().getBackgroundColor().getRGB() + ", theme says " + expected.rgb);
            }
        });
        return count[0] + 1;
    }

    private static void walk(Control control, java.util.function.Consumer<Control> visitor) {
        visitor.accept(control);
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                walk(child, visitor);
            }
        }
    }

    private static List<Widget> widgets(Composite pane) {
        List<Widget> widgets = new ArrayList<>();
        walk(pane, widgets::add);
        return widgets;
    }
}
