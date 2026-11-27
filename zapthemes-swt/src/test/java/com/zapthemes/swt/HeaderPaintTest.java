package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.lang.reflect.Field;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeColumn;

/**
 * A Table's or Tree's header in a dark theme that sets no header colors is dark with light text,
 * not the light header Windows draws, also after a runtime switch; a light theme keeps the one
 * Windows draws. Read with {@code PrintWindow} on the header window.
 */
class HeaderPaintTest {

    private static final int PW_RENDERFULLCONTENT = 2;

    private final Display display = Display.getDefault();

    @BeforeEach
    void onWindows() {
        Assumptions.assumeTrue("win32".equals(SWT.getPlatform()), "Windows draws the native header");
    }

    @AfterEach
    void tearDown() {
        for (var shell : display.getShells()) {
            shell.dispose();
        }
        ThemeManager.get(display).setTheme(light());
    }

    /** A theme whose Tables and Trees have only a background and a foreground, plus {@code extra}. */
    private static Theme theme(String id, boolean dark, String background, String foreground, String extra) {
        String rule = "{ \"background\": \"" + background + "\", \"foreground\": \"" + foreground + "\"" + extra + " }";
        String platform = dark ? ", \"platform\": { \"*\": { \"darkThemePreferred\": true } }" : "";
        return ThemeJson.read(new StringReader("{ \"id\": \"" + id + "\", \"controls\": { \"Table\": " + rule
                + ", \"Tree\": " + rule + " }" + platform + " }"), id);
    }

    private static Theme dark() {
        return theme("plain-dark", true, "#1E1E1E", "#E0E0E0", "");
    }

    private static Theme light() {
        return theme("plain-light", false, "#FFFFFF", "#000000", "");
    }

    @ParameterizedTest
    @ValueSource(strings = { "tree", "table" })
    void aDarkThemeWithoutHeaderColorsHasADarkHeader(String kind) throws Exception {
        ThemeManager.get(display).setTheme(dark());
        Control control = open(kind);
        assertDark(kind + " made dark", control);
    }

    @ParameterizedTest
    @ValueSource(strings = { "tree", "table" })
    void theHeaderFollowsASwitchAtRuntime(String kind) throws Exception {
        ThemeManager manager = ThemeManager.get(display);
        manager.setTheme(light());
        Control control = open(kind);
        assertThat(header(control)[0]).as("%s light: the header Windows draws", kind).isGreaterThan(0.8);

        manager.setTheme(dark());
        settle();
        assertDark(kind + " switched to dark", control);

        manager.setTheme(light());
        settle();
        assertThat(header(control)[0]).as("%s back to light: the header Windows draws", kind).isGreaterThan(0.8);
    }

    @ParameterizedTest
    @ValueSource(strings = { "tree", "table" })
    void explicitHeaderColorsWin(String kind) {
        ThemeManager.get(display).setTheme(theme("explicit", true, "#1E1E1E", "#E0E0E0",
                ", \"headerBackground\": \"#3C3C3C\", \"headerForeground\": \"#FFFFFF\""));
        Control control = open(kind);
        RGB header = control instanceof Tree tree ? tree.getHeaderBackground().getRGB()
                : ((Table) control).getHeaderBackground().getRGB();
        assertThat(header).isEqualTo(new RGB(0x3C, 0x3C, 0x3C));
    }

    private void assertDark(String what, Control control) throws Exception {
        double[] header = header(control);
        assertThat(header[0]).as("%s: header background", what).isLessThan(0.1);
        assertThat(header[1]).as("%s: header text", what).isGreaterThan(0.5);
    }

    private Control open(String kind) {
        Shell shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
        shell.setLayout(new FillLayout());
        shell.setBounds(120, 120, 300, 200);
        Control control;
        if (kind.equals("tree")) {
            Tree tree = new Tree(shell, SWT.NONE);
            tree.setHeaderVisible(true);
            for (String name : new String[] { "Name", "Size" }) {
                TreeColumn column = new TreeColumn(tree, SWT.NONE);
                column.setText(name);
                column.setWidth(100);
            }
            control = tree;
        } else {
            Table table = new Table(shell, SWT.NONE);
            table.setHeaderVisible(true);
            for (String name : new String[] { "Name", "Size" }) {
                TableColumn column = new TableColumn(table, SWT.NONE);
                column.setText(name);
                column.setWidth(100);
            }
            control = table;
        }
        shell.open();
        settle();
        return control;
    }

    private void settle() {
        long end = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
    }

    /**
     * The header's mean luminance right of the columns (its background), and the brightest
     * luminance over the first column's label (its text).
     */
    private double[] header(Control control) throws Exception {
        Class<?> owner = control instanceof Tree ? org.eclipse.swt.widgets.Tree.class : org.eclipse.swt.widgets.Table.class;
        Field field = owner.getDeclaredField("hwndHeader");
        field.setAccessible(true);
        long hwnd = (long) field.get(control);
        Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
        Class<?> rectClass = Class.forName("org.eclipse.swt.internal.win32.RECT");
        Object rect = rectClass.getConstructor().newInstance();
        os.getMethod("GetWindowRect", long.class, rectClass).invoke(null, hwnd, rect);
        int width = rectClass.getField("right").getInt(rect) - rectClass.getField("left").getInt(rect);
        int height = rectClass.getField("bottom").getInt(rect) - rectClass.getField("top").getInt(rect);
        Image image = new Image(display, width, height);
        GC gc = new GC(image);
        try {
            os.getMethod("PrintWindow", long.class, long.class, int.class).invoke(null, hwnd, gc.handle, PW_RENDERFULLCONTENT);
        } finally {
            gc.dispose();
        }
        ImageData data = image.getImageData();
        image.dispose();
        // Two columns of 100 points take two thirds of 300 at 100%; the last tenth is past them up to 250%.
        double sum = 0;
        int count = 0;
        for (int y = 2; y < height - 2; y++) {
            for (int x = width * 9 / 10; x < width - 2; x++) {
                sum += luminance(data.palette.getRGB(data.getPixel(x, y)));
                count++;
            }
        }
        double text = 0;
        for (int y = 2; y < height - 2; y++) {
            for (int x = 4; x < width / 4; x++) {
                text = Math.max(text, luminance(data.palette.getRGB(data.getPixel(x, y))));
            }
        }
        return new double[] { sum / count, text };
    }

    private static double luminance(RGB rgb) {
        return 0.2126 * linear(rgb.red) + 0.7152 * linear(rgb.green) + 0.0722 * linear(rgb.blue);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
