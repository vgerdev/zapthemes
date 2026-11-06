package com.zapthemes.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.freeze.command.FreezeColumnCommand;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.grid.layer.DefaultGridLayer;
import org.eclipse.nebula.widgets.nattable.layer.cell.ColumnOverrideLabelAccumulator;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectCellCommand;
import org.eclipse.nebula.widgets.nattable.selection.command.SelectRowsCommand;
import org.eclipse.nebula.widgets.nattable.style.CellStyleAttributes;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.style.IStyle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;

/**
 * A NatTable grid (column and row headers, alternating rows, a selection, a frozen first column)
 * themed by {@code zapthemes-nattable}: the app code is plain NatTable and never calls the bridge.
 *
 * <p>{@code --smoke} checks, in every bundled theme, that the table is tagged and that NatTable's
 * header, body and selection styles carry the theme's colors, and exits non-zero on failure.
 * {@code --screenshots <dir>} also saves the window in each theme.
 */
public final class NatTableDemo {

    /** A row of the grid; NatTable's reflective accessor reads JavaBean getters. */
    public static final class Widget {
        private final String name;
        private final String bundle;
        private final String createdBy;
        private final int since;

        Widget(String name, String bundle, String createdBy, int since) {
            this.name = name;
            this.bundle = bundle;
            this.createdBy = createdBy;
            this.since = since;
        }

        public String getName() {
            return name;
        }

        public String getBundle() {
            return bundle;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public int getSince() {
            return since;
        }
    }

    private static final List<Widget> ROWS = List.of(
            new Widget("Button", "zapthemes-swt", "proxy", 1),
            new Widget("Table", "zapthemes-swt", "proxy", 1),
            new Widget("Tree", "zapthemes-swt", "proxy", 1),
            new Widget("StyledText", "zapthemes-swt", "proxy", 1),
            new Widget("TableViewer", "zapthemes-jface", "fork", 2),
            new Widget("PopupDialog", "zapthemes-jface", "fork", 2),
            new Widget("Grid", "zapthemes-nebula", "fork", 4),
            new Widget("CDateTime", "zapthemes-nebula", "fork", 4),
            new Widget("Gallery", "zapthemes-nebula", "fork", 4),
            new Widget("NotificationPopup", "zapthemes-jface-notifications", "fork", 7),
            new Widget("SourceViewer", "zapthemes-jface-text", "fork", 7),
            new Widget("NatTable", "zapthemes-nattable", "bridge", 7));

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available = DemoSupport.bundledThemes();
    private Shell shell;
    private NatTable table;

    private NatTableDemo(Display display) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        Display display = new Display();
        NatTableDemo demo = new NatTableDemo(display);
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
        shell.setText("ZapThemes — NatTable");
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
        new Label(bar, SWT.NONE).setText("Plain NatTable code; zapthemes-nattable turns the theme into its ThemeConfiguration.");

        DefaultGridLayer grid = new DefaultGridLayer(ROWS, new String[] { "name", "bundle", "createdBy", "since" },
                Map.of("name", "Widget", "bundle", "Module", "createdBy", "How", "since", "Phase"));
        // Right-aligned numbers, as an app would label a column.
        ColumnOverrideLabelAccumulator labels = new ColumnOverrideLabelAccumulator(grid.getBodyLayer());
        labels.registerColumnOverrides(3, "NUMBER");
        grid.getBodyLayer().setConfigLabelAccumulator(labels);
        table = new NatTable(shell, grid);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        shell.setSize(760, 420);
        shell.open();
        // A selection with its anchor, and a frozen first column (the freeze line).
        table.doCommand(new FreezeColumnCommand(grid.getBodyLayer(), 0));
        table.doCommand(new SelectRowsCommand(grid.getBodyLayer(), 0, 4, false, false));
        table.doCommand(new SelectCellCommand(grid.getBodyLayer(), 1, 5, false, true));
    }

    // ------------------------------------------------------------------ smoke test

    private int smokeTest(Path screenshots) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Theme theme : available) {
            themes.setTheme(theme);
            DemoSupport.settle(display);
            checked += check(shell, problems);
            var style = themes.styleOf(table);
            expect(problems, theme, "body", background(DisplayMode.NORMAL, null), style.color("background"));
            expect(problems, theme, "column header", background(DisplayMode.NORMAL, GridRegion.COLUMN_HEADER), style.color("headerBackground"));
            expect(problems, theme, "selection", background(DisplayMode.SELECT, null), style.color("selectionBackground"));
            if (screenshots != null) {
                DemoSupport.screenshot(shell, screenshots, "nattable-" + theme.id());
                // Control.print on Windows paints NatTable at the shell's origin; the table alone is right.
                DemoSupport.screenshot(table, screenshots, "nattable-table-" + theme.id());
            }
        }
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: " + checked + " controls tagged; NatTable header, body and selection follow the theme, in "
                    + available.size() + " themes");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    private RGB background(DisplayMode mode, String label) {
        IStyle style = label == null
                ? table.getConfigRegistry().getConfigAttribute(CellConfigAttributes.CELL_STYLE, mode)
                : table.getConfigRegistry().getSpecificConfigAttribute(CellConfigAttributes.CELL_STYLE, mode, label);
        return style == null || style.getAttributeValue(CellStyleAttributes.BACKGROUND_COLOR) == null ? null
                : style.getAttributeValue(CellStyleAttributes.BACKGROUND_COLOR).getRGB();
    }

    private static void expect(List<String> problems, Theme theme, String what, RGB actual,
            java.util.Optional<org.eclipse.swt.graphics.RGBA> expected) {
        if (expected.isPresent() && !expected.get().rgb.equals(actual)) {
            problems.add(theme.id() + ": NatTable " + what + " " + actual + ", theme says " + expected.get().rgb);
        }
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
