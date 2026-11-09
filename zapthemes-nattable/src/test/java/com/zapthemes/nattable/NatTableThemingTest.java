package com.zapthemes.nattable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.eclipse.nebula.widgets.nattable.NatTable;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.grid.GridRegion;
import org.eclipse.nebula.widgets.nattable.grid.layer.DefaultGridLayer;
import org.eclipse.nebula.widgets.nattable.style.CellStyleAttributes;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.style.IStyle;
import org.eclipse.nebula.widgets.nattable.style.Style;
import org.eclipse.nebula.widgets.nattable.style.theme.IThemeExtension;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
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

/**
 * A NatTable takes the theme's {@code NatTable} rule as a NatTable theme configuration when it is
 * created and on every switch, with no call from the application.
 */
class NatTableThemingTest {

    /** Grid rows; NatTable's reflective accessor reads the getters. */
    public static final class Row {
        private final String name;
        private final int size;

        Row(String name, int size) {
            this.name = name;
            this.size = size;
        }

        public String getName() {
            return name;
        }

        public int getSize() {
            return size;
        }
    }

    private static final List<Row> ROWS = List.of(new Row("alpha", 1), new Row("beta", 2), new Row("gamma", 3), new Row("delta", 4));

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
        shell.setSize(400, 200);
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.setTheme(dark);
    }

    @Test
    void aNewTableTakesTheThemeAndFollowsSwitches() {
        NatTable table = open();

        assertStyled(table, dark);
        manager.setTheme(light);
        dispatch();
        assertStyled(table, light);
    }

    @Test
    void paintsInTheThemesColors() {
        NatTable table = open();
        ImageData painted = print(table);
        int header = table.getRowHeightByPosition(0) / 2;
        int body = table.getRowHeightByPosition(0) + table.getRowHeightByPosition(1) / 2;
        int x = table.getColumnWidthByPosition(0) + 40;

        assertThat(pixel(painted, x, header)).as("column header").isEqualTo(palette(dark, "raised"));
        assertThat(pixel(painted, x + 50, body)).as("first body row").isEqualTo(palette(dark, "surface"));
    }

    @Test
    void darkThemesUseTheInvertedIcons() {
        assertThat(((ZapNatTableThemeConfiguration) NatTableTheming.configuration(style(dark), new com.zapthemes.core.style.ThemeContext(manager.resources()))).isDark()).isTrue();
        manager.setTheme(light);
        assertThat(((ZapNatTableThemeConfiguration) NatTableTheming.configuration(style(light), new com.zapthemes.core.style.ThemeContext(manager.resources()))).isDark()).isFalse();
    }

    @Test
    void extensionsAreAddedToEveryConfiguration() {
        NatTable table = new NatTable(shell, grid(), false);
        Style special = new Style();
        special.setAttributeValue(CellStyleAttributes.BACKGROUND_COLOR, new Color(200, 0, 0));
        NatTableTheming.addExtension(table, new IThemeExtension() {
            @Override
            public void registerStyles(IConfigRegistry registry) {
                registry.registerConfigAttribute(CellConfigAttributes.CELL_STYLE, special, DisplayMode.NORMAL, "SPECIAL");
            }

            @Override
            public void unregisterStyles(IConfigRegistry registry) {
                registry.unregisterConfigAttribute(CellConfigAttributes.CELL_STYLE, DisplayMode.NORMAL, "SPECIAL");
            }
        });
        table.configure();
        shell.open();
        dispatch();
        manager.setTheme(light);
        dispatch();

        assertThat(background(table, "SPECIAL")).isEqualTo(new RGB(200, 0, 0));
        assertThat(background(table, GridRegion.COLUMN_HEADER)).isEqualTo(palette(light, "raised"));
    }

    @Test
    void manualTablesAreLeftAlone() {
        NatTable table = new NatTable(shell, grid());
        table.setData(NatTableTheming.MANUAL, Boolean.TRUE);
        shell.open();
        dispatch();

        assertThat(background(table, GridRegion.COLUMN_HEADER)).isNotEqualTo(palette(dark, "raised"));
    }

    @Test
    void withoutAThemeTheTableKeepsNatTablesLook() {
        manager.setTheme(Theme.EMPTY);
        NatTable plain = new NatTable(shell, grid());
        shell.open();
        dispatch();
        RGB upstream = background(plain, GridRegion.COLUMN_HEADER);

        manager.setTheme(dark);
        dispatch();
        assertThat(background(plain, GridRegion.COLUMN_HEADER)).isEqualTo(palette(dark, "raised"));
        manager.setTheme(Theme.EMPTY);
        dispatch();
        assertThat(background(plain, GridRegion.COLUMN_HEADER)).as("NatTable's default theme again").isEqualTo(upstream);
    }

    // ------------------------------------------------------------------ helpers

    private void assertStyled(NatTable table, Theme theme) {
        assertThat(background(table, null)).as("body").isEqualTo(palette(theme, "surface"));
        assertThat(background(table, GridRegion.COLUMN_HEADER)).as("column header").isEqualTo(palette(theme, "raised"));
        assertThat(attribute(table, DisplayMode.SELECT, null, CellStyleAttributes.BACKGROUND_COLOR)).as("selection")
                .isEqualTo(palette(theme, "selection"));
        assertThat(attribute(table, DisplayMode.SELECT, org.eclipse.nebula.widgets.nattable.style.SelectionStyleLabels.ROW_FULLY_SELECTED_STYLE,
                CellStyleAttributes.BACKGROUND_COLOR)).as("header of a fully selected row").isEqualTo(palette(theme, "selection"));
        Color grid = table.getConfigRegistry().getConfigAttribute(CellConfigAttributes.GRID_LINE_COLOR, DisplayMode.NORMAL);
        assertThat(grid.getRGB()).as("grid lines").isEqualTo(palette(theme, "raised"));
    }

    private static RGB background(NatTable table, String label) {
        return attribute(table, DisplayMode.NORMAL, label, CellStyleAttributes.BACKGROUND_COLOR);
    }

    private static RGB attribute(NatTable table, DisplayMode mode, String label,
            org.eclipse.nebula.widgets.nattable.style.ConfigAttribute<Color> key) {
        IStyle style = label == null
                ? table.getConfigRegistry().getConfigAttribute(CellConfigAttributes.CELL_STYLE, mode)
                : table.getConfigRegistry().getSpecificConfigAttribute(CellConfigAttributes.CELL_STYLE, mode, label);
        return style.getAttributeValue(key).getRGB();
    }

    private com.zapthemes.core.style.ResolvedStyle style(Theme theme) {
        NatTable table = open();
        return manager.styleOf(table);
    }

    private NatTable open() {
        NatTable table = new NatTable(shell, grid());
        shell.open();
        dispatch();
        return table;
    }

    private static DefaultGridLayer grid() {
        return new DefaultGridLayer(ROWS, new String[] { "name", "size" }, Map.of("name", "Name", "size", "Size"));
    }

    private static ImageData print(NatTable table) {
        var size = table.getSize();
        Image image = new Image(display, size.x, size.y);
        GC gc = new GC(image);
        table.print(gc);
        gc.dispose();
        ImageData data = image.getImageData();
        image.dispose();
        return data;
    }

    private static RGB pixel(ImageData data, int x, int y) {
        return data.palette.getRGB(data.getPixel(x, y));
    }

    private static RGB palette(Theme theme, String name) {
        return com.zapthemes.core.style.ThemeNames.of(theme).color(name).orElseThrow().rgb;
    }

    private static void dispatch() {
        long end = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
    }
}
