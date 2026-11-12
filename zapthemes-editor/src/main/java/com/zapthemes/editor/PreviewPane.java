package com.zapthemes.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.draw2d.LightweightSystem;
import org.eclipse.nebula.visualization.xygraph.dataprovider.CircularBufferDataProvider;
import org.eclipse.nebula.visualization.xygraph.figures.Trace;
import org.eclipse.nebula.visualization.xygraph.figures.XYGraph;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.FontSpec;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ThemeNames;
import com.zapthemes.nebula.grid.Grid;
import com.zapthemes.nebula.grid.GridColumn;
import com.zapthemes.nebula.grid.GridItem;
import com.zapthemes.swt.custom.CTabFolder;
import com.zapthemes.swt.custom.CTabItem;
import com.zapthemes.swt.custom.StyledText;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Canvas;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.ProgressBar;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeItem;

/**
 * Sample controls themed with the theme being edited, apart from the wizard around them
 * ({@link ThemeManager#preview}). A document at the top shows the text names (headings, muted,
 * link, status colors, code); below are buttons, a text field and a combo, a table and a tree, a
 * Nebula Grid and an XYGraph in tabs, and a progress bar. {@link #show} re-themes them in place.
 */
public final class PreviewPane extends Composite {

    private static final String DOCUMENT = """
            Heading 1
            Heading 2
            Heading 3
            Body text with a link, a muted note and strong words.
            Error  Warning  Success  Info
            if (count > 0) { return "ready"; } // 42 items
            """;

    private final ThemeManager themes;
    private final StyledText document;
    private Theme shown;

    public PreviewPane(org.eclipse.swt.widgets.Composite parent) {
        super(parent, SWT.BORDER);
        themes = ThemeManager.get(getDisplay());
        setLayout(new GridLayout(2, true));

        document = new StyledText(this, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP);
        document.setText(DOCUMENT.stripTrailing());
        document.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
        document.setCaret(null);

        Composite buttons = new Composite(this, SWT.NONE);
        RowLayout row = new RowLayout();
        row.marginLeft = 0;
        row.center = true;
        buttons.setLayout(row);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        Button primary = new Button(buttons, SWT.PUSH);
        primary.setText("Primary");
        themes.setThemeClass(primary, "primary");
        new Button(buttons, SWT.PUSH).setText("Cancel");
        Button disabled = new Button(buttons, SWT.PUSH);
        disabled.setText("Disabled");
        disabled.setEnabled(false);
        Button check = new Button(buttons, SWT.CHECK);
        check.setText("Check");
        check.setSelection(true);
        Button radio = new Button(buttons, SWT.RADIO);
        radio.setText("Radio");
        radio.setSelection(true);

        Text text = new Text(this, SWT.BORDER);
        text.setText("A text field");
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Combo combo = new Combo(this, SWT.READ_ONLY);
        combo.setItems("A combo", "Second", "Third");
        combo.select(0);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        Table table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        for (String name : new String[] { "Name", "Size" }) {
            TableColumn column = new TableColumn(table, SWT.NONE);
            column.setText(name);
            column.setWidth(90);
        }
        for (int i = 1; i <= 4; i++) {
            new TableItem(table, SWT.NONE).setText(new String[] { "Row " + i, i * 12 + " KB" });
        }
        table.select(1);
        table.setLayoutData(grow(80));
        Tree tree = new Tree(this, SWT.BORDER);
        TreeItem root = new TreeItem(tree, SWT.NONE);
        root.setText("Project");
        for (String name : new String[] { "src", "docs", "build.gradle" }) {
            new TreeItem(root, SWT.NONE).setText(name);
        }
        root.setExpanded(true);
        tree.setLayoutData(grow(80));

        CTabFolder tabs = new CTabFolder(this, SWT.BORDER);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        CTabItem gridTab = new CTabItem(tabs, SWT.NONE);
        gridTab.setText("Grid");
        Grid grid = new Grid(tabs, SWT.FULL_SELECTION | SWT.V_SCROLL);
        grid.setHeaderVisible(true);
        for (String name : new String[] { "Task", "Owner" }) {
            GridColumn column = new GridColumn(grid, SWT.NONE);
            column.setText(name);
            column.setWidth(110);
        }
        for (int i = 1; i <= 4; i++) {
            GridItem item = new GridItem(grid, SWT.NONE);
            item.setText(0, "Task " + i);
            item.setText(1, i % 2 == 0 ? "Ada" : "Linus");
        }
        grid.setSelection(0);
        gridTab.setControl(grid);
        CTabItem chartTab = new CTabItem(tabs, SWT.NONE);
        chartTab.setText("Chart");
        Canvas canvas = new Canvas(tabs, SWT.DOUBLE_BUFFERED);
        new LightweightSystem(canvas).setContents(graph());
        chartTab.setControl(canvas);
        tabs.setSelection(0);

        Label caption = new Label(this, SWT.NONE);
        caption.setText("Progress");
        ProgressBar progress = new ProgressBar(this, SWT.HORIZONTAL);
        progress.setSelection(60);
        progress.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

    }

    /** Themes the pane with {@code theme}, in place: nothing is created or disposed. */
    public void show(Theme theme) {
        if (theme == shown || isDisposed()) {
            return;
        }
        shown = theme;
        themes.preview(this, theme);
        styleDocument(ThemeNames.of(theme));
    }

    public Theme shown() {
        return shown;
    }

    /** The document's colors and fonts come from the names, as an application's styled text would. */
    private void styleDocument(ThemeNames names) {
        String text = document.getText();
        List<StyleRange> ranges = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        int offset = 0;
        String[] headings = { "heading1", "heading2", "heading3" };
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i < headings.length) {
                ranges.add(range(names, offset, line.length(), headings[i], headings[i]));
            } else if (i == 3) {
                ranges.add(word(names, line, offset, "a link", "link", null, true));
                ranges.add(word(names, line, offset, "a muted note", "muted", null, false));
                ranges.add(word(names, line, offset, "strong words", "strong", "strong", false));
            } else if (i == 4) {
                for (String status : new String[] { "Error", "Warning", "Success", "Info" }) {
                    ranges.add(word(names, line, offset, status, status.toLowerCase(java.util.Locale.ROOT), "strong", false));
                }
            } else if (i == 5) {
                ranges.add(range(names, offset, line.length(), null, "code"));
                ranges.add(word(names, line, offset, "if", "keyword", "code", false));
                ranges.add(word(names, line, offset, "return", "keyword", "code", false));
                ranges.add(word(names, line, offset, "\"ready\"", "string", "code", false));
                ranges.add(word(names, line, offset, "0", "number", "code", false));
                ranges.add(word(names, line, offset, "// 42 items", "comment", "code", false));
            }
            offset += line.length() + 1;
        }
        ranges.removeIf(r -> r == null);
        ranges.sort(java.util.Comparator.comparingInt(r -> r.start));
        // Later ranges (the code words) are cut out of the code line's range.
        document.setStyleRanges(new StyleRange[0]);
        for (StyleRange range : ranges) {
            document.setStyleRange(range);
        }
    }

    private StyleRange word(ThemeNames names, String line, int offset, String word, String color, String font, boolean underline) {
        int at = line.indexOf(word);
        if (at < 0) {
            return null;
        }
        StyleRange range = range(names, offset + at, word.length(), color, font);
        range.underline = underline;
        return range;
    }

    private StyleRange range(ThemeNames names, int start, int length, String color, String font) {
        StyleRange range = new StyleRange();
        range.start = start;
        range.length = length;
        if (color != null) {
            colorOf(names, color).ifPresent(c -> range.foreground = c);
        }
        if (font != null) {
            fontOf(names, font).ifPresent(f -> range.font = f);
        }
        return range;
    }

    private static Optional<Color> colorOf(ThemeNames names, String name) {
        try {
            return names.color(name).map(rgba -> new Color(rgba.rgb));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Optional<Font> fontOf(ThemeNames names, String name) {
        try {
            Optional<FontSpec> spec = names.font(name);
            return spec.map(s -> themes.resources().font(s));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
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
        data.setBufferSize(80);
        double[] x = new double[80];
        double[] y = new double[80];
        for (int i = 0; i < x.length; i++) {
            x[i] = i / 10.0;
            y[i] = Math.sin(x[i]);
        }
        data.setCurrentXDataArray(x);
        data.setCurrentYDataArray(y);
        graph.addTrace(new Trace("sin", graph.getPrimaryXAxis(), graph.getPrimaryYAxis(), data));
        return graph;
    }
}
