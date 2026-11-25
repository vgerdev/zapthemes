package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
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

/** Details cells keep to one line and end in "…" when their column is too narrow, as in Explorer. */
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

    private Table table() {
        return (Table) FileBrowserProbe.viewControl(browser);
    }

    private Point extent(String text) {
        GC gc = new GC(table());
        try {
            gc.setFont(table().getFont());
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
    void aLongTypeNameInANarrowColumnStaysOnOneLineAndEndsInAnEllipsis() {
        // Zoomed in and out again: the Windows list view keeps its taller rows, room enough to wrap in.
        ThemeManager.get(display).setZoom(1.5);
        idle();
        ThemeManager.get(display).resetZoom();
        idle();
        assertThat(FileBrowserProbe.row(browser, DOCX)[2]).isEqualTo(WORD);
        // Wide enough for "Microsoft Word" but not "Document": where the list view would break the line.
        TableColumn type = table().getColumn(2);
        assertThat(type.getText()).isEqualTo("Type");
        type.setWidth(extent("Microsoft Word Docu").x + 12);
        idle();

        String shown = FileBrowserProbe.shownText(browser, DOCX, 2);
        assertThat(shown).startsWith("Microsoft Word").endsWith("…").isNotEqualTo(WORD);
        assertThat(extent(shown).x).isLessThanOrEqualTo(type.getWidth() - 12);
        // The item keeps its whole text (sorting, the native tooltip).
        assertThat(FileBrowserProbe.row(browser, DOCX)[2]).isEqualTo(WORD);
        // Short texts are left as they are.
        assertThat(FileBrowserProbe.shownText(browser, "notes.txt", 2)).isEqualTo("Text document");

        // What is painted: one line of text in the cell, not two.
        Rectangle cell = browser.fileTable().itemOf(entry(DOCX)).getBounds(2);
        int[] ink = inkRows(cell);
        assertThat(ink).as("text in the cell").isNotNull();
        assertThat(ink[1] - ink[0] + 1).as("height of the text painted in a row %s px high", cell.height)
                .isLessThanOrEqualTo(extent(WORD).y);
    }

    @Test
    void aLongNameIsCutWithAnEllipsisToo() {
        table().getColumn(0).setWidth(extent("Install Kubernetes").x + 40);
        idle();
        assertThat(FileBrowserProbe.shownText(browser, DOCX, 0)).startsWith("Install").endsWith("…");
        assertThat(FileBrowserProbe.shownText(browser, "notes.txt", 0)).isEqualTo("notes.txt");
    }

    /** The first and last pixel rows of {@code cell} with text in them, or null: text is what differs much from the cell's left edge. */
    private int[] inkRows(Rectangle cell) {
        table().redraw();
        idle();
        Image image = new Image(display, cell.width, cell.height);
        GC gc = new GC(table());
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
