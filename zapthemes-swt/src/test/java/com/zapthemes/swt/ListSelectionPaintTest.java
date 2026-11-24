package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.List;
import com.zapthemes.swt.widgets.Shell;

/**
 * On Windows a list box paints its selection in the system's highlight colors, which in the
 * explorer dark mode are white text on white: the theme's {@code selectionBackground} and
 * {@code selectionForeground} are painted over them, also in a CCombo's drop-down list.
 */
class ListSelectionPaintTest {

    private static final RGB SELECTION = new RGB(0x00, 0x40, 0xA0);
    private static final RGB HOVER = new RGB(0x43, 0x45, 0x4A);
    private static final RGB BACKGROUND = new RGB(0x20, 0x20, 0x20);

    private static final Theme THEME = ThemeJson.read(new StringReader("""
            {
              "id": "list-selection",
              "controls": {
                "List": { "background": "#202020", "foreground": "#EEEEEE",
                          "selectionBackground": "#0040A0", "selectionForeground": "#FFFFFF",
                          "states": { "hover": { "background": "#43454A" } } }
              },
              "platform": { "win32": { "darkScrollbars": true } }
            }
            """), "list-selection");
    private static final Theme PLAIN = ThemeJson.read(new StringReader("{ \"id\": \"plain\" }"), "plain");
    private static final String[] ITEMS = { "alpha", "beta", "gamma" };

    private final Display display = Display.getDefault();

    @BeforeEach
    void onWindows() {
        Assumptions.assumeTrue("win32".equals(SWT.getPlatform()), "the list box is painted over on Windows only");
        ThemeManager.get(display).setTheme(THEME);
    }

    @AfterEach
    void tearDown() {
        ThemeManager.get(display).setTheme(PLAIN);
        for (var shell : display.getShells()) {
            shell.dispose();
        }
    }

    @Test
    void theSelectedItemIsPaintedInTheThemesColors() {
        Shell shell = shell();
        List list = new List(shell, SWT.SINGLE);
        list.setItems(ITEMS);
        list.select(1);
        open(shell);

        ImageData row = capture(list, 1);
        assertThat(pixel(row, row.width - 4, row.height / 2)).as("selected item").isEqualTo(SELECTION);
        assertThat(colors(row)).as("the text on it").contains(new RGB(255, 255, 255));
        assertThat(pixel(capture(list, 0), row.width - 4, row.height / 2)).as("other item").isEqualTo(new RGB(0x20, 0x20, 0x20));

        // A new selection is painted too, though the list box draws it without a paint message.
        list.select(2);
        list.notifyListeners(SWT.Selection, null);
        settle();
        assertThat(pixel(capture(list, 2), row.width - 4, row.height / 2)).isEqualTo(SELECTION);
        assertThat(pixel(capture(list, 1), row.width - 4, row.height / 2)).isNotEqualTo(SELECTION);
    }

    @Test
    void movingThePointerRedrawsTheRowsItCrossesButNeverTheSelectedOne() {
        // A redraw of the selected row has the list box paint its white highlight first: a flash.
        // The other rows are redrawn as the pointer enters and leaves them, for the hover highlight
        // in the theme's hover color (the list box has none of its own).
        Shell shell = shell();
        List list = new List(shell, SWT.SINGLE);
        list.setItems(ITEMS);
        list.select(1);
        int[] paints = { 0 };
        java.util.List<Rectangle> painted = new java.util.ArrayList<>();
        list.addListener(SWT.Paint, e -> {
            paints[0]++;
            painted.add(new Rectangle(e.x, e.y, e.width, e.height));
        });
        open(shell);

        paints[0] = 0;
        painted.clear();
        int height = list.getItemHeight();
        Rectangle selected = new Rectangle(0, height, list.getClientArea().width, height);
        for (int y = 2; y < 3 * height; y += 3) {
            org.eclipse.swt.widgets.Event move = new org.eclipse.swt.widgets.Event();
            move.x = 20;
            move.y = y;
            list.notifyListeners(SWT.MouseMove, move);
            settle(30);
        }
        settle();
        ImageData last = capture(list, 2);
        assertThat(pixel(last, last.width - 4, last.height / 2)).as("the row under the pointer").isEqualTo(HOVER);
        assertThat(pixel(capture(list, 0), last.width - 4, last.height / 2)).as("a row it left").isEqualTo(BACKGROUND);
        list.notifyListeners(SWT.MouseExit, new org.eclipse.swt.widgets.Event());
        settle();
        assertThat(pixel(capture(list, 2), last.width - 4, last.height / 2)).as("after the pointer left").isEqualTo(BACKGROUND);
        assertThat(painted).as("rows the pointer crossed").isNotEmpty();
        assertThat(painted).as("paints of the selected row").noneMatch(r -> r.intersects(selected));

        paints[0] = 0;
        org.eclipse.swt.widgets.Event drag = new org.eclipse.swt.widgets.Event();
        drag.stateMask = SWT.BUTTON1;
        list.notifyListeners(SWT.MouseMove, drag);
        list.notifyListeners(SWT.MouseUp, new org.eclipse.swt.widgets.Event());
        settle();
        assertThat(paints[0]).as("paints with nothing changed").isZero();

        // A changed selection is still painted over.
        list.select(2);
        list.notifyListeners(SWT.MouseUp, new org.eclipse.swt.widgets.Event());
        settle();
        assertThat(paints[0]).as("paints after the selection changed").isPositive();
        ImageData row = capture(list, 2);
        assertThat(pixel(row, row.width - 4, row.height / 2)).isEqualTo(SELECTION);
    }

    @Test
    void aComboDropDownListIsAListToo() throws ReflectiveOperationException {
        Shell shell = shell();
        var combo = new com.zapthemes.swt.custom.CCombo(shell, SWT.READ_ONLY | SWT.BORDER);
        combo.setItems(ITEMS);
        combo.select(1);
        open(shell);
        combo.setListVisible(true);
        settle();
        var field = org.eclipse.swt.custom.CCombo.class.getDeclaredField("list");
        field.setAccessible(true);
        var list = (org.eclipse.swt.widgets.List) field.get(combo);
        Assumptions.assumeTrue(list.isVisible(), "the drop-down did not open");

        ImageData row = capture(list, 1);
        assertThat(pixel(row, row.width - 4, row.height / 2)).isEqualTo(SELECTION);
        combo.setListVisible(false);
    }

    private Shell shell() {
        Shell shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
        shell.setLayout(new FillLayout());
        shell.setBounds(100, 100, 220, 120);
        return shell;
    }

    private void open(Shell shell) {
        shell.open();
        shell.forceActive();
        settle();
    }

    private void settle() {
        settle(300);
    }

    private void settle(int millis) {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
    }

    /** Item {@code index} of {@code list} as it is on the screen (Control.print leaves out paint listeners). */
    private ImageData capture(Control list, int index) {
        int height = ((org.eclipse.swt.widgets.List) list).getItemHeight();
        Rectangle area = ((org.eclipse.swt.widgets.List) list).getClientArea();
        Point origin = list.toDisplay(area.x, area.y + index * height);
        Image image = new Image(display, area.width, height);
        GC gc = new GC(display);
        gc.copyArea(image, origin.x, origin.y);
        gc.dispose();
        ImageData data = image.getImageData();
        image.dispose();
        assumeScreenWorks(data);
        return data;
    }

    private static RGB pixel(ImageData data, int x, int y) {
        return data.palette.getRGB(data.getPixel(x, y));
    }

    private static java.util.Set<RGB> colors(ImageData data) {
        java.util.Set<RGB> colors = new java.util.HashSet<>();
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                colors.add(pixel(data, x, y));
            }
        }
        return colors;
    }

    /** The screen cannot be read while the session is locked: all black. */
    private static void assumeScreenWorks(ImageData data) {
        Assumptions.assumeFalse(colors(data).equals(java.util.Set.of(new RGB(0, 0, 0))), "the screen cannot be read (locked desktop?)");
    }
}
