package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;

/** Table rows are owner-drawn from the theme: selection, stripes and grid lines. */
class TableOwnerDrawTest {

    private static final RGB BACKGROUND = new RGB(0x20, 0x20, 0x20);
    private static final RGB SELECTION = new RGB(0x00, 0x40, 0xA0);
    private static final RGB STRIPE = new RGB(0x30, 0x30, 0x30);
    private static final RGB GRID = new RGB(0xC0, 0x40, 0x40);

    private static final Theme THEME = ThemeJson.read(new StringReader("""
            {
              "id": "owner-draw",
              "controls": {
                "Table": { "background": "#202020", "foreground": "#EEEEEE", "selectionBackground": "#0040A0",
                           "alternateRowBackground": "#303030", "gridLineColor": "#C04040" },
                "TableItem": { "inherit": "Table" }
              }
            }
            """), "owner-draw");
    private static final Theme PLAIN = ThemeJson.read(new StringReader("{ \"id\": \"plain\" }"), "plain");

    private final Display display = Display.getDefault();

    @AfterEach
    void tearDown() {
        ThemeManager.get(display).setTheme(PLAIN);
        for (var shell : display.getShells()) {
            shell.dispose();
        }
    }

    @Test
    void rowsAreOwnerDrawnFromTheTheme() {
        ThemeManager.get(display).setTheme(THEME);
        Table table = table();

        Image image = print(table);
        ImageData data = image.getImageData();
        assumePrintWorks(data);
        Rectangle row0 = table.getItem(0).getBounds(0);
        Rectangle row1 = table.getItem(1).getBounds(0);
        Rectangle row2 = table.getItem(2).getBounds(0);

        assertThat(pixel(data, row0.x + row0.width / 2, row0.y + 2)).as("selected row").isEqualTo(SELECTION);
        assertThat(pixel(data, row1.x + row1.width / 2, row1.y + 2)).as("striped row").isEqualTo(STRIPE);
        assertThat(pixel(data, row2.x + row2.width / 2, row2.y + 2)).as("plain row").isEqualTo(BACKGROUND);
        image.dispose();
    }

    @ParameterizedTest(name = "header visible: {0}")
    @ValueSource(booleans = { false, true })
    void gridLinesSeparateRows(boolean header) {
        ThemeManager.get(display).setTheme(THEME);
        Table table = table(header, false);

        Image image = print(table);
        ImageData data = image.getImageData();
        assumePrintWorks(data);
        Rectangle row0 = table.getItem(0).getBounds(0);
        int x = row0.x + row0.width / 2;
        List<Integer> lines = new ArrayList<>();
        for (int y = (header ? table.getHeaderHeight() : 0) + 1; y < data.height - 1 && lines.size() < 3; y++) {
            if (pixel(data, x, y).equals(GRID)) {
                lines.add(y);
            }
        }

        // Selected, striped, plain and striped rows: each of the first three lines lies between two
        // differently filled rows. On GTK they used to land mid-row, offset by the header height.
        assertThat(lines).hasSize(3);
        for (int y : lines) {
            assertThat(pixel(data, x, y - 1)).as("rows around the line at y=" + y).isNotEqualTo(pixel(data, x, y + 1));
        }
        image.dispose();
    }

    @Test
    void linesVisibleKeepsMeaningWhatTheApplicationAsked() {
        ThemeManager manager = ThemeManager.get(display);
        manager.setTheme(THEME);
        Table table = table();

        assertThat(table.getLinesVisible()).isTrue();
        manager.setTheme(PLAIN);
        assertThat(table.getLinesVisible()).isTrue();
        table.setLinesVisible(false);
        manager.setTheme(THEME);
        assertThat(table.getLinesVisible()).isFalse();
    }

    private Table table() {
        return table(false, true);
    }

    private Table table(boolean header, boolean text) {
        Shell shell = new Shell(display);
        shell.setLayout(new FillLayout());
        Table table = new Table(shell, SWT.FULL_SELECTION);
        table.setLinesVisible(true);
        table.setHeaderVisible(header);
        new TableColumn(table, SWT.NONE).setWidth(150);
        for (String label : new String[] { "zero", "one", "two", "three" }) {
            // Without text, a pixel column shows only row fills and lines.
            new TableItem(table, SWT.NONE).setText(text ? label : "");
        }
        table.select(0);
        shell.setSize(300, 200);
        shell.open();
        while (display.readAndDispatch()) {
            // Paint.
        }
        return table;
    }

    private Image print(Table table) {
        var size = table.getSize();
        Image image = new Image(display, size.x, size.y);
        GC gc = new GC(image);
        table.print(gc);
        gc.dispose();
        return image;
    }

    /**
     * {@code Control.print} returns a blank (all black) image when the desktop cannot be drawn, e.g. a
     * locked Windows session; pixel checks are meaningless then.
     */
    private static void assumePrintWorks(ImageData data) {
        for (int y = 0; y < data.height; y += 4) {
            for (int x = 0; x < data.width; x += 4) {
                RGB rgb = data.palette.getRGB(data.getPixel(x, y));
                if (rgb.red != 0 || rgb.green != 0 || rgb.blue != 0) {
                    return;
                }
            }
        }
        org.junit.jupiter.api.Assumptions.abort("Control.print returned a blank image (locked desktop?)");
    }

    private static RGB pixel(ImageData data, int x, int y) {
        return data.palette.getRGB(data.getPixel(x, y));
    }
}
