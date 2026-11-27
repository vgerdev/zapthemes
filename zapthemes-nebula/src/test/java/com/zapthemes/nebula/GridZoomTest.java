package com.zapthemes.nebula;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.nebula.grid.Grid;
import com.zapthemes.nebula.grid.GridColumn;
import com.zapthemes.nebula.grid.GridItem;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/** The Grid's header and item heights follow its font: zooming in grows them, zooming out shrinks them. */
class GridZoomTest {

    private static Display display;
    private static Theme dark;
    private ThemeManager manager;
    private Shell shell;
    private Grid grid;

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
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
        shell.setSize(400, 300);
        grid = new Grid(shell, SWT.BORDER);
        grid.setHeaderVisible(true);
        for (String name : new String[] { "Task", "Owner" }) {
            GridColumn column = new GridColumn(grid, SWT.NONE);
            column.setText(name);
            column.setWidth(120);
        }
        for (int i = 1; i <= 4; i++) {
            GridItem item = new GridItem(grid, SWT.NONE);
            item.setText(0, "Task " + i);
            item.setText(1, i % 2 == 0 ? "Ada" : "Linus");
        }
        shell.open();
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.resetZoom();
    }

    @Test
    void headerAndItemHeightsGrowWhenZoomingInAndShrinkWhenZoomingOut() {
        int header = grid.getHeaderHeight();
        int item = grid.getItemHeight();
        int row = grid.getItem(3).getHeight();

        manager.setZoom(2);
        assertThat(grid.getHeaderHeight()).isGreaterThan(header);
        assertThat(grid.getItemHeight()).isGreaterThan(item);
        assertThat(grid.getItem(3).getHeight()).isEqualTo(grid.getItemHeight());
        assertTextFits();

        manager.resetZoom();
        assertThat(grid.getHeaderHeight()).isEqualTo(header);
        assertThat(grid.getItemHeight()).isEqualTo(item);
        assertThat(grid.getItem(3).getHeight()).isEqualTo(row);

        manager.setZoom(0.5);
        assertThat(grid.getHeaderHeight()).isLessThan(header);
        assertThat(grid.getItemHeight()).isLessThan(item);
    }

    @Test
    void zoomStepsDoNotDrift() {
        int header = grid.getHeaderHeight();
        int item = grid.getItemHeight();
        for (int i = 0; i < 4; i++) {
            manager.zoomIn();
        }
        for (int i = 0; i < 4; i++) {
            manager.zoomOut();
        }
        assertThat(manager.getZoom()).isEqualTo(1.0);
        assertThat(grid.getHeaderHeight()).isEqualTo(header);
        assertThat(grid.getItemHeight()).isEqualTo(item);
    }

    @Test
    void aFontChangeOutsideTheZoomResizesToo() {
        int header = grid.getHeaderHeight();
        int item = grid.getItemHeight();
        FontData data = grid.getFont().getFontData()[0];
        Font big = new Font(display, data.getName(), data.getHeight() * 2, data.getStyle());
        try {
            grid.setFont(big);
            assertThat(grid.getHeaderHeight()).isGreaterThan(header);
            assertThat(grid.getItemHeight()).isGreaterThan(item);
            assertTextFits();
        } finally {
            shell.dispose();
            big.dispose();
        }
    }

    @Test
    void anItemHeightTheApplicationSetIsKept() {
        grid.setItemHeight(40);
        org.eclipse.nebula.widgets.grid.GridItem tall = grid.getItem(1);
        tall.setHeight(60);

        manager.setZoom(2);
        assertThat(grid.getItemHeight()).isEqualTo(40);
        assertThat(grid.getItem(0).getHeight()).isEqualTo(40);
        assertThat(tall.getHeight()).isEqualTo(60);
    }

    @Test
    void anItemSizedOneByOneKeepsItsHeight() {
        org.eclipse.nebula.widgets.grid.GridItem tall = grid.getItem(1);
        tall.setHeight(80);
        int item = grid.getItemHeight();

        manager.setZoom(2);
        assertThat(grid.getItemHeight()).isGreaterThan(item);
        assertThat(grid.getItem(0).getHeight()).isEqualTo(grid.getItemHeight());
        assertThat(tall.getHeight()).isEqualTo(80);
    }

    /** The rows and the header are at least as tall as a line of the grid's font. */
    private void assertTextFits() {
        org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(grid);
        try {
            gc.setFont(grid.getFont());
            int line = gc.getFontMetrics().getHeight();
            assertThat(grid.getItemHeight()).isGreaterThanOrEqualTo(line);
            assertThat(grid.getHeaderHeight()).isGreaterThanOrEqualTo(line);
        } finally {
            gc.dispose();
        }
    }
}
