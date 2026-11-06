package com.zapthemes.nattable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.grid.layer.DefaultGridLayer;
import org.eclipse.nebula.widgets.nattable.layer.DataLayer;
import org.eclipse.nebula.widgets.nattable.resize.command.ColumnResizeCommand;
import org.eclipse.swt.layout.FillLayout;
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

/** NatTable row heights and column widths follow the zoom, through NatTable's own scaling. */
class NatTableZoomTest {

    private static final List<NatTableThemingTest.Row> ROWS = List.of(new NatTableThemingTest.Row("alpha", 1),
            new NatTableThemingTest.Row("beta", 2));

    private static Display display;
    private static Theme dark;
    private ThemeManager manager;
    private Shell shell;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        dark = registry.resolve("zap-dark");
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.resetZoom();
        manager.setTheme(dark);
        shell = new com.zapthemes.swt.widgets.Shell(display);
        shell.setLayout(new FillLayout());
        shell.setSize(400, 200);
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.resetZoom();
    }

    @Test
    void rowHeightsAndColumnWidthsScaleAndReset() {
        DefaultGridLayer grid = grid();
        NatTable table = open(grid);
        DataLayer body = (DataLayer) grid.getBodyDataLayer();
        int row = body.getRowHeightByPosition(0);
        int column = body.getColumnWidthByPosition(0);

        manager.setZoom(2);
        assertThat(body.getRowHeightByPosition(0)).isEqualTo(row * 2);
        assertThat(body.getColumnWidthByPosition(0)).isEqualTo(column * 2);

        manager.resetZoom();
        assertThat(body.getRowHeightByPosition(0)).isEqualTo(row);
        assertThat(body.getColumnWidthByPosition(0)).isEqualTo(column);
        assertThat(table.isDisposed()).isFalse();
    }

    @Test
    void sizesDoNotDriftAndAResizedColumnStaysInProportion() {
        DefaultGridLayer grid = grid();
        NatTable table = open(grid);
        DataLayer body = (DataLayer) grid.getBodyDataLayer();
        int row = body.getRowHeightByPosition(0);

        manager.setZoom(2);
        table.doCommand(new ColumnResizeCommand(body, 0, 240, true)); // the user drags it to 240 px at 200 %
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
                manager.zoomIn();
            }
            for (int i = 0; i < ThemeManager.ZOOM_LEVELS.size(); i++) {
                manager.zoomOut();
            }
        }
        manager.resetZoom();

        assertThat(body.getRowHeightByPosition(0)).isEqualTo(row);
        assertThat(body.getColumnWidthByPosition(0)).isEqualTo(120);
    }

    @Test
    void aTableCreatedWhileZoomedIsScaled() {
        int row = grid().getBodyDataLayer().getRowHeightByPosition(0); // unscaled default
        manager.setZoom(1.5);
        DefaultGridLayer grid = grid();
        open(grid);

        assertThat(grid.getBodyDataLayer().getRowHeightByPosition(0)).isEqualTo((int) Math.round(row * 1.5));
    }

    private NatTable open(DefaultGridLayer grid) {
        NatTable table = new NatTable(shell, grid);
        shell.open();
        while (display.readAndDispatch()) {
            // layout and paint
        }
        return table;
    }

    private static DefaultGridLayer grid() {
        return new DefaultGridLayer(ROWS, new String[] { "name", "size" }, Map.of("name", "Name", "size", "Size"));
    }
}
