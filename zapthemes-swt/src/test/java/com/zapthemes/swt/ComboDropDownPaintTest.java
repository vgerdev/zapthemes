package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

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

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/**
 * The drop-down list of a native Combo is a Windows list box, not an SWT widget. Allowed the dark
 * mode, it paints its selected item white on white; in zap-dark ({@code darkCombos},
 * {@code darkScrollbars}) the selected item must read, on every drop-down. Read from the screen, as
 * {@code Control.print} does not reach the list box.
 */
class ComboDropDownPaintTest {

    private static final String[] ITEMS = { "alpha", "beta", "gamma" };
    private static final int LB_GETITEMRECT = 0x198;

    private final Display display = Display.getDefault();

    @BeforeEach
    void onWindows() {
        Assumptions.assumeTrue("win32".equals(SWT.getPlatform()), "the ComboLBox is a Windows window");
        ThemeManager.get(display).setTheme(theme("zap-dark"));
    }

    @AfterEach
    void tearDown() {
        for (var shell : display.getShells()) {
            shell.dispose();
        }
        ThemeManager.get(display).setTheme(theme("zap-light"));
    }

    private static Theme theme(String id) {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        return registry.resolve(id);
    }

    @ParameterizedTest
    @ValueSource(ints = { SWT.READ_ONLY, SWT.DROP_DOWN })
    void theSelectedDropDownItemReads(int style) throws ReflectiveOperationException {
        Shell shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
        shell.setLayout(new FillLayout());
        shell.setBounds(100, 100, 220, 40);
        Combo combo = new Combo(shell, style);
        combo.setItems(ITEMS);
        combo.select(1);
        shell.open();
        shell.forceActive();
        settle();

        // SWT allows the list box the dark mode again on each drop-down: the second one counts too.
        for (int time = 1; time <= 2; time++) {
            combo.setListVisible(true);
            settle();
            long list = listHandle(combo);
            Assumptions.assumeTrue(visible(list), "the drop-down did not open");

            ImageData selected = row(list, 1);
            ImageData other = row(list, 0);
            RGB fill = pixel(selected, selected.width - 4, selected.height / 2);
            RGB otherFill = pixel(other, other.width - 4, other.height / 2);
            assertThat(luminance(otherFill)).as("drop-down %d: the list stays dark (%s)", time, otherFill).isLessThan(0.1);
            assertThat(bestContrast(selected, fill)).as("drop-down %d: text on the selected item (%s)", time, fill)
                    .isGreaterThanOrEqualTo(4.0);
            combo.setListVisible(false);
            settle();
        }
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

    // ------------------------------------------------------------------ the list box, through SWT's OS

    private static Class<?> os() throws ClassNotFoundException {
        return Class.forName("org.eclipse.swt.internal.win32.OS");
    }

    private static Class<?> win32(String name) throws ClassNotFoundException {
        return Class.forName("org.eclipse.swt.internal.win32." + name);
    }

    private static long listHandle(Combo combo) throws ReflectiveOperationException {
        Class<?> info = win32("COMBOBOXINFO");
        Object pcbi = info.getConstructor().newInstance();
        info.getField("cbSize").setInt(pcbi, (int) os().getMethod("COMBOBOXINFO_sizeof").invoke(null));
        os().getMethod("GetComboBoxInfo", long.class, info).invoke(null, Control.class.getField("handle").getLong(combo), pcbi);
        return info.getField("hwndList").getLong(pcbi);
    }

    private static boolean visible(long hwnd) throws ReflectiveOperationException {
        return hwnd != 0 && (boolean) os().getMethod("IsWindowVisible", long.class).invoke(null, hwnd);
    }

    /** Item {@code index} of the list box as it is on the screen. */
    private ImageData row(long list, int index) throws ReflectiveOperationException {
        Class<?> rectClass = win32("RECT");
        Class<?> pointClass = win32("POINT");
        Object rect = rectClass.getConstructor().newInstance();
        os().getMethod("SendMessage", long.class, int.class, long.class, rectClass).invoke(null, list, LB_GETITEMRECT, (long) index, rect);
        Object origin = pointClass.getConstructor().newInstance();
        os().getMethod("ClientToScreen", long.class, pointClass).invoke(null, list, origin);
        int left = rectClass.getField("left").getInt(rect);
        int top = rectClass.getField("top").getInt(rect);
        int width = rectClass.getField("right").getInt(rect) - left;
        int height = rectClass.getField("bottom").getInt(rect) - top;
        int x = pointClass.getField("x").getInt(origin) + left;
        int y = pointClass.getField("y").getInt(origin) + top;
        Image image = new Image(display, width, height);
        GC gc = new GC(display);
        gc.copyArea(image, x, y);
        gc.dispose();
        ImageData data = image.getImageData();
        image.dispose();
        Assumptions.assumeFalse(colors(data).equals(Set.of(new RGB(0, 0, 0))), "the screen cannot be read (locked desktop?)");
        return data;
    }

    // ------------------------------------------------------------------ pixels

    private static RGB pixel(ImageData data, int x, int y) {
        return data.palette.getRGB(data.getPixel(x, y));
    }

    private static Set<RGB> colors(ImageData data) {
        Set<RGB> colors = new HashSet<>();
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                colors.add(pixel(data, x, y));
            }
        }
        return colors;
    }

    /** The WCAG contrast of the row's most contrasting pixel (its text) on {@code fill}. */
    private static double bestContrast(ImageData row, RGB fill) {
        double fillLuminance = luminance(fill);
        double best = 1;
        for (RGB c : colors(row)) {
            double l = luminance(c);
            best = Math.max(best, (Math.max(l, fillLuminance) + 0.05) / (Math.min(l, fillLuminance) + 0.05));
        }
        return best;
    }

    private static double luminance(RGB rgb) {
        return 0.2126 * linear(rgb.red) + 0.7152 * linear(rgb.green) + 0.0722 * linear(rgb.blue);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
