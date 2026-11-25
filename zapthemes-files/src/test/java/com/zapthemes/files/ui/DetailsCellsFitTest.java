package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.BooleanSupplier;

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
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/** Details cells keep to one line and end in "…" when their column is too narrow, and rows follow the zoom both ways, as in Explorer. */
class DetailsCellsFitTest {

    private static final String WORD = "Microsoft Word Document";
    private static final String DOCX = "Install Kubernetes Cluster Node with Cilium.docx";
    private static final Instant OLD = Instant.parse("2024-04-08T13:45:00Z");

    private static Display display;
    private Shell shell;
    private FileBrowser browser;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        ThemeManager.get(display).setTheme(registry.resolve("zap-dark"));
        FolderPopup.closeOnDeactivate = false;
    }

    @BeforeEach
    void setUp() {
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
        browser = new FileBrowser(shell, 0);
        shell.setSize(1100, 600);
        shell.open();
        // Long type names, as the platform's (IconProvider.platform()) are on Windows.
        browser.setIconProvider(new IconProvider() {
            @Override
            public String iconKey(FileSystem fs, FileEntry entry) {
                return null;
            }

            @Override
            public ImageData iconData(String key, int pixels) {
                return null;
            }

            @Override
            public String typeName(FileSystem fs, FileEntry entry) {
                return entry.name().endsWith(".docx") ? WORD : null;
            }
        });
        browser.setFileSystem(MemoryFileSystem.builder("docs")
                .file(DOCX, 1_042_000).modified(OLD)
                .file("notes.txt", 100).modified(OLD)
                .build());
        idle();
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        ThemeManager.get(display).resetZoom();
    }

    private void waitFor(BooleanSupplier condition) {
        long end = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            assertThat(System.currentTimeMillis()).as("timed out").isLessThan(end);
            if (!display.readAndDispatch()) {
                display.timerExec(10, () -> { });
                display.sleep();
            }
        }
    }

    private void idle() {
        waitFor(() -> !browser.isLoading() && browser.pendingCalls() == 0);
        while (display.readAndDispatch()) {
            // Layouts and paints.
        }
    }

    private Control view() {
        return FileBrowserProbe.viewControl(browser);
    }

    private Point extent(String text) {
        GC gc = new GC(view());
        try {
            gc.setFont(view().getFont());
            return gc.textExtent(text);
        } finally {
            gc.dispose();
        }
    }

    private FileEntry entry(String name) {
        return browser.getVisibleEntries().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void rowsAreAsHighAsTheZoomNeedsBothWays() {
        int row = FileBrowserProbe.rowHeight(browser);
        assertThat(row).isLessThan(2 * extent(WORD).y);
        ThemeManager.get(display).setZoom(1.5);
        idle();
        assertThat(FileBrowserProbe.rowHeight(browser)).isGreaterThan(row);
        // A native table's rows would stay as high as at 150 %.
        ThemeManager.get(display).resetZoom();
        idle();
        assertThat(FileBrowserProbe.rowHeight(browser)).isEqualTo(row);
        assertThat(FileBrowserProbe.cellBounds(browser, DOCX, 2).height).isEqualTo(row);
    }

    @Test
    void aLongTypeNameInANarrowColumnStaysOnOneLineAndEndsInAnEllipsis() {
        // Zoomed in and out again, which left a native table's rows tall enough to wrap in.
        ThemeManager.get(display).setZoom(1.5);
        idle();
        ThemeManager.get(display).resetZoom();
        idle();
        assertThat(FileBrowserProbe.row(browser, DOCX)[2]).isEqualTo(WORD);
        // Wide enough for "Microsoft Word" but not "Document": where a list view would break the line.
        assertThat(FileBrowserProbe.columns(browser).get(2)).isEqualTo("Type");
        FileBrowserProbe.setColumnWidth(browser, 2, extent("Microsoft Word Docu").x + 12);
        idle();
        int width = FileBrowserProbe.columnWidth(browser, 2);

        String shown = FileBrowserProbe.shownText(browser, DOCX, 2);
        assertThat(shown).startsWith("Microsoft Word").endsWith("…").isNotEqualTo(WORD);
        assertThat(extent(shown).x).isLessThanOrEqualTo(width - 12);
        // The entry keeps its whole text (sorting, the tooltip).
        assertThat(FileBrowserProbe.row(browser, DOCX)[2]).isEqualTo(WORD);
        // Short texts are left as they are.
        assertThat(FileBrowserProbe.shownText(browser, "notes.txt", 2)).isEqualTo("Text document");

        // What is painted: one line of text in the cell, not two.
        Rectangle cell = FileBrowserProbe.cellBounds(browser, DOCX, 2);
        int[] ink = inkRows(cell);
        assertThat(ink).as("text in the cell").isNotNull();
        assertThat(ink[1] - ink[0] + 1).as("height of the text painted in a row %s px high", cell.height)
                .isLessThanOrEqualTo(extent(WORD).y);
    }

    @Test
    void aLongNameIsCutWithAnEllipsisToo() {
        FileBrowserProbe.setColumnWidth(browser, 0, extent("Install Kubernetes").x + 40);
        idle();
        assertThat(FileBrowserProbe.shownText(browser, DOCX, 0)).startsWith("Install").endsWith("…");
        assertThat(FileBrowserProbe.shownText(browser, "notes.txt", 0)).isEqualTo("notes.txt");
    }

    // ------------------------------------------------------------------ column headers

    private void mouse(int type, int x, int y, int button, int state) {
        Event event = new Event();
        event.x = x;
        event.y = y;
        event.button = button;
        event.stateMask = state;
        view().notifyListeners(type, event);
    }

    /** Presses at {@code (x, y)} of the view, drags to {@code toX} and lets go there. */
    private void drag(int x, int y, int toX) {
        mouse(SWT.MouseDown, x, y, 1, 0);
        mouse(SWT.MouseMove, (x + toX) / 2, y, 0, SWT.BUTTON1);
        mouse(SWT.MouseMove, toX, y, 0, SWT.BUTTON1);
        mouse(SWT.MouseUp, toX, y, 1, SWT.BUTTON1);
        idle();
    }

    private int headerY() {
        return browser.fileGrid().headerHeight() / 2;
    }

    @Test
    void aClickOnAColumnHeaderSortsAndASecondOneReverses() {
        Rectangle size = FileBrowserProbe.cellBounds(browser, "notes.txt", 3);
        mouse(SWT.MouseDown, size.x + size.width / 2, headerY(), 1, 0);
        mouse(SWT.MouseUp, size.x + size.width / 2, headerY(), 1, SWT.BUTTON1);
        idle();
        assertThat(browser.getSortKey()).isEqualTo(com.zapthemes.files.FileOrder.Key.SIZE);
        assertThat(browser.isSortAscending()).isTrue();
        mouse(SWT.MouseDown, size.x + size.width / 2, headerY(), 1, 0);
        mouse(SWT.MouseUp, size.x + size.width / 2, headerY(), 1, SWT.BUTTON1);
        idle();
        assertThat(browser.isSortAscending()).isFalse();
        // The headers are not rows.
        assertThat(browser.fileGrid().entryAt(size.x + 4, headerY())).isNull();
    }

    @Test
    void dividersSizeAndFitColumnsAndHeadersMoveThem() {
        int width = FileBrowserProbe.columnWidth(browser, 1);
        Rectangle date = FileBrowserProbe.cellBounds(browser, "notes.txt", 1);
        int divider = date.x + date.width - 1;
        drag(divider, headerY(), divider + 40);
        assertThat(FileBrowserProbe.columnWidth(browser, 1)).isBetween(width + 38, width + 42);
        // A double click on the divider fits the column to its texts.
        int fitted = date.x + FileBrowserProbe.columnWidth(browser, 1) - 1;
        mouse(SWT.MouseDoubleClick, fitted, headerY(), 1, 0);
        idle();
        assertThat(FileBrowserProbe.columnWidth(browser, 1)).isLessThan(width + 38).isGreaterThan(extent(FileBrowserProbe.row(browser, DOCX)[1]).x);

        // Type dragged before Date modified.
        Rectangle type = FileBrowserProbe.cellBounds(browser, "notes.txt", 2);
        date = FileBrowserProbe.cellBounds(browser, "notes.txt", 1);
        drag(type.x + type.width / 2, headerY(), date.x + 2);
        assertThat(FileBrowserProbe.columns(browser)).containsExactly("Name", "Type", "Date modified", "Size");
        assertThat(FileBrowserProbe.row(browser, DOCX)[1]).isEqualTo(WORD);
        // Not a sort.
        assertThat(browser.getSortKey()).isEqualTo(com.zapthemes.files.FileOrder.Key.NAME);
        // Kept for the next folder.
        browser.refresh();
        idle();
        assertThat(FileBrowserProbe.columns(browser)).containsExactly("Name", "Type", "Date modified", "Size");
    }

    @Test
    void rowsScrollUnderTheHeadersAndTheArrowsScrollSideways() {
        MemoryFileSystem.Builder many = MemoryFileSystem.builder("many");
        for (int i = 0; i < 300; i++) {
            many.file(String.format("file %03d.txt", i), i).modified(OLD);
        }
        browser.setFileSystem(many.build());
        idle();
        FileBrowserProbe.select(browser, "file 299.txt");
        idle();
        int header = browser.fileGrid().headerHeight();
        Rectangle last = FileBrowserProbe.cellBounds(browser, "file 299.txt", 0);
        assertThat(last.y).isGreaterThanOrEqualTo(header);
        assertThat(last.y + last.height).isLessThanOrEqualTo(browser.fileGrid().getClientArea().height);
        // The headers stay at the top.
        assertThat(browser.fileGrid().entryAt(last.x + 4, header / 2)).isNull();
        assertThat(browser.fileGrid().onHeader(new Point(4, header / 2))).isTrue();

        // Wider columns than the view scroll sideways, with the headers.
        FileBrowserProbe.setColumnWidth(browser, 0, browser.fileGrid().getClientArea().width);
        idle();
        Event right = new Event();
        right.keyCode = SWT.ARROW_RIGHT;
        view().notifyListeners(SWT.KeyDown, right);
        idle();
        assertThat(browser.fileGrid().scroll()[0]).isPositive();
        assertThat(FileBrowserProbe.names(browser.getSelectedEntries())).containsExactly("file 299.txt");
    }

    /** The first and last pixel rows of {@code cell} with text in them, or null: text is what differs much from the cell's left edge. */
    private int[] inkRows(Rectangle cell) {
        view().redraw();
        idle();
        Image image = new Image(display, cell.width, cell.height);
        GC gc = new GC(view());
        try {
            gc.copyArea(image, cell.x, cell.y);
        } finally {
            gc.dispose();
        }
        ImageData data = image.getImageData();
        image.dispose();
        int first = -1;
        int last = -1;
        for (int y = 0; y < data.height; y++) {
            int background = lum(data.palette.getRGB(data.getPixel(1, y)));
            for (int x = 3; x < data.width - 3; x++) {
                if (Math.abs(lum(data.palette.getRGB(data.getPixel(x, y))) - background) > 60) {
                    first = first < 0 ? y : first;
                    last = y;
                    break;
                }
            }
        }
        return first < 0 ? null : new int[] { first, last };
    }

    private static int lum(RGB rgb) {
        return (rgb.red * 299 + rgb.green * 587 + rgb.blue * 114) / 1000;
    }
}
