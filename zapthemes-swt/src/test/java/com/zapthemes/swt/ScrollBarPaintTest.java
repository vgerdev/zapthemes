package com.zapthemes.swt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.BiFunction;

import org.eclipse.swt.SWT;
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
 * runtime. Read with {@code PrintWindow} ({@link ScrollBarPixels}), which draws the scroll bars.
 */
class ScrollBarPaintTest {

    private static final int ITEMS = 60;

    private final Display display = Display.getDefault();

    @BeforeEach
    void onWindows() {
        Assumptions.assumeTrue("win32".equals(SWT.getPlatform()), "Windows draws the native scroll bars");
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

    /** The mean luminance of the lower part of the vertical scroll bar's track, as Windows draws it. */
    private double track(Scrollable scrollable) {
        try {
            return ScrollBarPixels.track(scrollable);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
