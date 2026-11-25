package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiFunction;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Scrollable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.swt.widgets.Canvas;
import com.zapthemes.swt.widgets.List;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeColumn;
import com.zapthemes.swt.widgets.TreeItem;
import com.zapthemes.themes.BundledThemes;

/**
 * Native scroll bars are drawn by Windows: dark in a dark theme (zap-dark, and zap-high-contrast,
 * which only inherits {@code darkScrollbars}), light in a light one, and they follow a switch at
 * runtime. Read from the screen, as {@code Control.print} does not draw the non-client area.
 */
class ScrollBarPaintTest {

    private static final int ITEMS = 60;

    private final Display display = Display.getDefault();

    @BeforeEach
    void onWindows() throws java.io.IOException {
        Assumptions.assumeTrue("win32".equals(SWT.getPlatform()), "Windows draws the native scroll bars");
        screen = ScreenLock.acquire();
    }

    private ScreenLock screen;

    @AfterEach
    void tearDown() throws java.io.IOException {
        if (screen != null) {
            screen.close();
        }
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
    @ValueSource(strings = { "tree", "measuredTree", "columnTree", "table", "list", "text", "canvas" })
    void scrollBarsFollowTheTheme(String kind) {
        ThemeManager manager = ThemeManager.get(display);
        // Created light, then switched: the switch must reach widgets that already exist.
        manager.setTheme(theme("zap-light"));
        Shell shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
        shell.setLayout(new FillLayout());
        shell.setBounds(120, 120, 240, 200);
        Scrollable scrollable = create(kind).apply(shell, SWT.V_SCROLL | SWT.H_SCROLL);
        shell.open();
        shell.forceActive();
        settle();
        assertThat(track(scrollable)).as("%s in zap-light", kind).isGreaterThan(0.5);

        manager.setTheme(theme("zap-dark"));
        settle();
        assertThat(track(scrollable)).as("%s in zap-dark", kind).isLessThan(0.1);

        manager.setTheme(theme("zap-high-contrast"));
        settle();
        assertThat(track(scrollable)).as("%s in zap-high-contrast (inherits darkScrollbars)", kind).isLessThan(0.1);

        manager.setTheme(theme("zap-light"));
        settle();
        assertThat(track(scrollable)).as("%s back in zap-light", kind).isGreaterThan(0.5);
    }

    @ParameterizedTest
    @ValueSource(strings = { "tree", "measuredTree", "columnTree", "table", "list", "text", "canvas" })
    void scrollBarsOfWidgetsMadeInADarkThemeAreDark(String kind) {
        ThemeManager.get(display).setTheme(theme("zap-high-contrast"));
        Shell shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
        shell.setLayout(new FillLayout());
        shell.setBounds(120, 120, 240, 200);
        Scrollable scrollable = create(kind).apply(shell, SWT.V_SCROLL | SWT.H_SCROLL);
        shell.open();
        shell.forceActive();
        settle();
        assertThat(track(scrollable)).as("%s made in zap-high-contrast", kind).isLessThan(0.1);
    }

    @ParameterizedTest
    @ValueSource(strings = { "tree", "measuredTree", "table" })
    void aDarkThemeWithoutDarkScrollbarsGetsDarkOnes(String kind) {
        ThemeManager manager = ThemeManager.get(display);
        manager.setTheme(theme("zap-light"));
        Shell shell = new Shell(display, SWT.NO_TRIM | SWT.ON_TOP);
        shell.setLayout(new FillLayout());
        shell.setBounds(120, 120, 240, 200);
        Scrollable scrollable = create(kind).apply(shell, SWT.V_SCROLL | SWT.H_SCROLL);
        shell.open();
        shell.forceActive();
        settle();

        manager.setTheme(com.zapthemes.core.ThemeJson.read(new java.io.StringReader("""
                { "id": "only-dark-preferred", "platform": { "*": { "darkThemePreferred": true } } }
                """), "only-dark-preferred"));
        settle();
        assertThat(track(scrollable)).as("%s under darkThemePreferred alone", kind).isLessThan(0.1);
    }

    private static BiFunction<Composite, Integer, Scrollable> create(String kind) {
        return switch (kind) {
            case "tree" -> (parent, style) -> {
                Tree tree = new Tree(parent, style | SWT.FULL_SELECTION);
                for (int i = 0; i < ITEMS; i++) {
                    new TreeItem(tree, SWT.NONE).setText("Folder " + i + " with a long name for the horizontal bar");
                }
                return tree;
            };
            // A MeasureItem listener or a column puts the tree in a parent window that holds the scroll bars.
            case "measuredTree" -> (parent, style) -> {
                Scrollable tree = create("tree").apply(parent, style);
                tree.addListener(SWT.MeasureItem, e -> { });
                return tree;
            };
            case "columnTree" -> (parent, style) -> {
                Tree tree = (Tree) create("tree").apply(parent, style);
                tree.setHeaderVisible(true);
                new TreeColumn(tree, SWT.NONE).setWidth(400);
                return tree;
            };
            case "table" -> (parent, style) -> {
                Table table = new Table(parent, style | SWT.FULL_SELECTION);
                for (int i = 0; i < ITEMS; i++) {
                    new TableItem(table, SWT.NONE).setText("Row " + i + " with a long name for the horizontal bar");
                }
                return table;
            };
            case "list" -> (parent, style) -> {
                List list = new List(parent, style);
                for (int i = 0; i < ITEMS; i++) {
                    list.add("Item " + i + " with a long name for the horizontal bar");
                }
                return list;
            };
            case "text" -> (parent, style) -> {
                Text text = new Text(parent, style | SWT.MULTI);
                StringBuilder lines = new StringBuilder();
                for (int i = 0; i < ITEMS; i++) {
                    lines.append("Line ").append(i).append(" with a long text for the horizontal bar\n");
                }
                text.setText(lines.toString());
                return text;
            };
            case "canvas" -> (parent, style) -> {
                Canvas canvas = new Canvas(parent, style);
                canvas.getVerticalBar().setValues(0, 0, 1000, 100, 10, 100);
                canvas.getHorizontalBar().setValues(0, 0, 1000, 100, 10, 100);
                return canvas;
            };
            default -> throw new IllegalArgumentException(kind);
        };
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
     * The mean luminance of the lower part of the vertical scroll bar's track, as it is on the
     * screen: below the thumb (scrolled to the top), above the down arrow.
     */
    private double track(Scrollable scrollable) {
        Rectangle client = scrollable.getClientArea();
        int barWidth = scrollable.getVerticalBar().getSize().x;
        int arrow = barWidth;
        int top = client.height / 2;
        int bottom = client.height - arrow - 2;
        var origin = scrollable.toDisplay(client.x + client.width, client.y);
        int width = Math.max(1, barWidth - 4);
        int height = Math.max(1, bottom - top);
        Image image = new Image(display, width, height);
        GC gc = new GC(display);
        gc.copyArea(image, origin.x + 2, origin.y + top);
        gc.dispose();
        ImageData data = image.getImageData();
        image.dispose();
        double sum = 0;
        boolean allBlack = true;
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                RGB rgb = data.palette.getRGB(data.getPixel(x, y));
                allBlack &= rgb.red == 0 && rgb.green == 0 && rgb.blue == 0;
                sum += luminance(rgb);
            }
        }
        double mean = sum / (data.width * data.height);
        Assumptions.assumeFalse(allBlack && mean == 0 && isLocked(), "the screen cannot be read (locked desktop?)");
        return mean;
    }

    private static boolean isLocked() {
        return ProcessHandle.allProcesses().anyMatch(p -> p.info().command().map(c -> c.toLowerCase().endsWith("logonui.exe")).orElse(false));
    }

    private static double luminance(RGB rgb) {
        return 0.2126 * linear(rgb.red) + 0.7152 * linear(rgb.green) + 0.0722 * linear(rgb.blue);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
