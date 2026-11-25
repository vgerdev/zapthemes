package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.files.Place;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/** What zapscp's panes need (step 6): the Group column, hiding columns, the mask filter, and bookmarks in the tree. */
class ZapscpFitTest {

    private static Display display;
    private Shell shell;
    private FileBrowser browser;

    private static final FilePath HOME = FilePath.of("home", "deploy");
    private static final Instant OLD = Instant.parse("2024-04-08T13:45:00Z");

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
        browser.setFileSystem(server());
        idle();
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
    }

    private static MemoryFileSystem server() {
        return MemoryFileSystem.builder("server")
                .folder("home/deploy/logs").modified(OLD).posix("rwxr-x---", "deploy", "adm")
                .folder("home/deploy/src").modified(OLD).posix("rwxr-xr-x", "deploy", "deploy")
                .folder("home/deploy/build").modified(OLD).posix("rwxr-xr-x", "deploy", "deploy")
                .file("home/deploy/app.log", 900).modified(OLD).posix("rw-r-----", "deploy", "adm")
                .file("home/deploy/APP.LOG.1", 800).modified(OLD).posix("rw-r-----", "deploy", "adm")
                .file("home/deploy/notes.txt", 100).modified(OLD).posix("rw-r--r--", "deploy", "staff")
                .file("home/deploy/app.jar", 48_000).modified(OLD).posix("rw-r--r--", "root", "wheel")
                .file("home/deploy/.bashrc", 30).modified(OLD).posix("rw-r--r--", "deploy", "deploy")
                .home("home/deploy")
                .build();
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

    private void run(String id) {
        browser.getCommand(id).run(new com.zapthemes.widgets.command.CommandEvent(browser.getCommand(id), browser));
    }

    private List<String> names() {
        return FileBrowserProbe.names(browser.getVisibleEntries());
    }

    // ------------------------------------------------------------------ columns

    @Test
    void groupColumnSortsAndGroups() {
        assertThat(FileBrowserProbe.columns(browser)).containsExactly("Name", "Date modified", "Type", "Size", "Permissions", "Owner", "Group");
        assertThat(FileBrowserProbe.row(browser, "app.jar")[6]).isEqualTo("wheel");
        browser.setSort(FileOrder.Key.GROUP, true);
        // Folders first, then by group: adm, staff, wheel.
        assertThat(names()).containsExactly("logs", "build", "src", "app.log", "APP.LOG.1", "notes.txt", "app.jar");
        browser.setGroupBy(FileOrder.Key.GROUP, true);
        idle();
        assertThat(FileBrowserProbe.groupHeaders(browser)).containsExactly("adm (3)", "deploy (2)", "staff (1)", "wheel (1)");
        assertThat(FileBrowserProbe.contextMenu(browser, false)).contains("Group by");
    }

    @Test
    void theHeaderMenuHidesAndShowsColumns() {
        assertThat(FileBrowserProbe.columnsMenu(browser)).containsExactly("[x] Date modified", "[x] Type", "[x] Size", "[x] Permissions",
                "[x] Owner", "[x] Group");
        browser.setSort(FileOrder.Key.OWNER, false);
        browser.setGroupBy(FileOrder.Key.OWNER, true);
        FileBrowserProbe.toggleColumn(browser, "Owner");
        idle();
        assertThat(browser.isColumnVisible(FileOrder.Key.OWNER)).isFalse();
        assertThat(FileBrowserProbe.columns(browser)).containsExactly("Name", "Date modified", "Type", "Size", "Permissions", "Group");
        // Sorted and grouped by it no more.
        assertThat(browser.getSortKey()).isEqualTo(FileOrder.Key.NAME);
        assertThat(browser.getGroupBy()).isNull();
        assertThat(FileBrowserProbe.row(browser, "app.jar")).containsExactly("app.jar", FileBrowserProbe.row(browser, "app.jar")[1],
                "Java archive", "47 KB", "rw-r--r--", "wheel");
        assertThat(FileBrowserProbe.columnsMenu(browser)).contains("[ ] Owner");
        browser.setColumnVisible(FileOrder.Key.SIZE, false);
        browser.setColumnVisible(FileOrder.Key.NAME, false);
        assertThat(FileBrowserProbe.columns(browser)).containsExactly("Name", "Date modified", "Type", "Permissions", "Group");
        FileBrowserProbe.toggleColumn(browser, "Owner");
        browser.setColumnVisible(FileOrder.Key.SIZE, true);
        assertThat(FileBrowserProbe.columns(browser)).containsExactly("Name", "Date modified", "Type", "Size", "Permissions", "Owner", "Group");
        // Kept for another filesystem, shown only where it has them.
        browser.setColumnVisible(FileOrder.Key.TYPE, false);
        browser.setFileSystem(MemoryFileSystem.builder("plain").file("a.txt", 1).build());
        idle();
        assertThat(FileBrowserProbe.columns(browser)).containsExactly("Name", "Date modified", "Size");
        assertThat(FileBrowserProbe.columnsMenu(browser)).containsExactly("[x] Date modified", "[ ] Type", "[x] Size");
    }

    // ------------------------------------------------------------------ filter

    @Test
    void theMaskFilterShowsMatchingFilesAndEveryFolder() {
        FileBrowserProbe.select(browser, "notes.txt", "app.log");
        browser.setFilter("*.log; *.log.?");
        idle();
        assertThat(browser.getFilter()).isEqualTo("*.log; *.log.?");
        assertThat(names()).containsExactly("build", "logs", "src", "app.log", "APP.LOG.1");
        // What is filtered out isn't selected any more.
        assertThat(FileBrowserProbe.names(browser.getSelectedEntries())).containsExactly("app.log");
        assertThat(FileBrowserProbe.status(browser)).contains("5 items").contains("filter: *.log; *.log.?");
        assertThat(browser.getCommand(FileBrowser.COMMAND_CLEAR_FILTER).isEnabled()).isTrue();
        // Hidden files stay hidden unless shown.
        browser.setFilter(".*");
        assertThat(names()).containsExactly("build", "logs", "src");
        browser.setShowHidden(true);
        assertThat(names()).containsExactly("build", "logs", "src", ".bashrc");
        // Folder masks choose the folders too.
        browser.setFilter("src/; *.txt");
        assertThat(names()).containsExactly("src", "notes.txt");
        // Kept while browsing.
        browser.navigate(HOME.resolve("logs"));
        idle();
        assertThat(browser.getFilter()).isEqualTo("src/; *.txt");
        browser.back();
        idle();
        run(FileBrowser.COMMAND_CLEAR_FILTER);
        idle();
        assertThat(browser.getFilter()).isEmpty();
        assertThat(names()).hasSize(8);
        assertThat(FileBrowserProbe.status(browser)).doesNotContain("filter");
        assertThat(browser.getCommand(FileBrowser.COMMAND_CLEAR_FILTER).isEnabled()).isFalse();
    }

    @Test
    void theFilterAppliesToEveryLayoutAndToGroups() {
        browser.setFilter("*.log");
        browser.setView(FileView.LARGE_ICONS);
        idle();
        assertThat(names()).containsExactly("build", "logs", "src", "app.log");
        browser.setGroupBy(FileOrder.Key.TYPE, true);
        idle();
        assertThat(FileBrowserProbe.groupHeaders(browser)).containsExactly("File folder (3)", "Log file (1)");
        assertThat(FileBrowserProbe.status(browser)).contains("4 items");
    }

    // ------------------------------------------------------------------ bookmarks

    @Test
    void foldersArePinnedToThePlacesAndUnpinned() {
        List<List<Place>> saved = new ArrayList<>();
        browser.addPlacesListener(saved::add);
        assertThat(browser.getPlaces()).isEmpty();
        FileBrowserProbe.select(browser, "logs");
        assertThat(browser.getCommand(FileBrowser.COMMAND_PIN).isEnabled()).isTrue();
        assertThat(browser.getCommand(FileBrowser.COMMAND_PIN).label()).isEqualTo("&Pin to Quick access");
        assertThat(FileBrowserProbe.contextMenu(browser, true)).contains("Pin to Quick access");
        run(FileBrowser.COMMAND_PIN);
        idle();
        assertThat(browser.isPinned(HOME.resolve("logs"))).isTrue();
        assertThat(browser.navigationTree().rootLabels()).containsExactly("logs", "---", "server");
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst()).extracting(Place::path).containsExactly(HOME.resolve("logs"));
        assertThat(browser.getCommand(FileBrowser.COMMAND_PIN).label()).isEqualTo("Unpin from &Quick access");
        assertThat(FileBrowserProbe.contextMenu(browser, true)).contains("Unpin from Quick access");
        // Files aren't pinned.
        FileBrowserProbe.select(browser, "notes.txt");
        assertThat(browser.getCommand(FileBrowser.COMMAND_PIN).isEnabled()).isFalse();
        assertThat(FileBrowserProbe.contextMenu(browser, true)).doesNotContain("Pin to Quick access");
        browser.pin(HOME.resolve("src"), "Sources");
        browser.pin(HOME.resolve("src"), "Again");
        assertThat(browser.getPlaces()).extracting(Place::label).containsExactly("logs", "Sources");
        // The pinned place itself, in the navigation pane.
        FileBrowserProbe.selectPlace(browser, 0);
        assertThat(browser.getCommand(FileBrowser.COMMAND_PIN).label()).isEqualTo("Unpin from &Quick access");
        run(FileBrowser.COMMAND_PIN);
        idle();
        assertThat(browser.getPlaces()).extracting(Place::label).containsExactly("Sources");
        assertThat(saved.getLast()).extracting(Place::label).containsExactly("Sources");
        browser.unpin(HOME.resolve("src"));
        assertThat(browser.navigationTree().rootLabels()).containsExactly("server");
        assertThat(saved).hasSize(4);
    }

    @Test
    void aPlaceIsRenamedInPlaceWithoutRenamingTheFolder() {
        browser.setPlaces(List.of(Place.folder(HOME.resolve("logs")), Place.folder(HOME.resolve("src"))));
        List<List<Place>> saved = new ArrayList<>();
        browser.addPlacesListener(saved::add);
        FileBrowserProbe.selectPlace(browser, 1);
        assertThat(browser.getCommand(FileBrowser.COMMAND_RENAME).isEnabled()).isTrue();
        int before = FileBrowserProbe.renamesStarted(browser);
        browser.rename();
        assertThat(FileBrowserProbe.renamesStarted(browser)).isEqualTo(before + 1);
        InlineEditor editor = browser.navigationTree().editor();
        if (editor != null) {
            // The field is over the place's label, with all of it selected.
            assertThat(editor.text().getText()).isEqualTo("src");
            assertThat(editor.text().getSelectionText()).isEqualTo("src");
        }
        FileBrowserProbe.finishRename(browser, "Sources");
        idle();
        assertThat(browser.getPlaces()).extracting(Place::label).containsExactly("logs", "Sources");
        assertThat(browser.getPlaces().get(1).path()).isEqualTo(HOME.resolve("src"));
        assertThat(browser.navigationTree().rootLabels()).containsExactly("logs", "Sources", "---", "server");
        assertThat(names()).contains("src");
        assertThat(saved).hasSize(1);
        // The renamed place stays selected; blank or the same label changes nothing.
        assertThat(browser.navigationTree().selectedPlace().label()).isEqualTo("Sources");
        browser.renamePlace(browser.getPlaces().get(1), "  ");
        browser.renamePlace(browser.getPlaces().get(1), "Sources");
        assertThat(saved).hasSize(1);
    }

    /** Windows clips each row's own painting to the widest name: the pins at the pane's right edge are drawn all the same. */
    @Test
    void pinsShowAtTheRightOfThePane() {
        browser.setPlaces(List.of(Place.folder(HOME.resolve("logs"))));
        idle();
        org.eclipse.swt.widgets.Tree tree = browser.navigationTree().tree;
        org.eclipse.swt.graphics.Rectangle pin = browser.navigationTree().pinBounds(0);
        assertThat(pin).isNotNull();
        assertThat(pin.x).as("near the right edge").isGreaterThan(tree.getClientArea().width / 2);
        org.eclipse.swt.graphics.Point size = tree.getSize();
        org.eclipse.swt.graphics.Image image = new org.eclipse.swt.graphics.Image(display, size.x, size.y);
        org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(image);
        try {
            tree.print(gc);
        } finally {
            gc.dispose();
        }
        org.eclipse.swt.graphics.ImageData data = image.getImageData();
        image.dispose();
        org.eclipse.swt.graphics.RGB background = data.palette.getRGB(data.getPixel(pin.x + pin.width + 4, pin.y + pin.height / 2));
        int differing = 0;
        for (int y = pin.y; y < pin.y + pin.height; y++) {
            for (int x = pin.x; x < pin.x + pin.width; x++) {
                if (!data.palette.getRGB(data.getPixel(x, y)).equals(background)) {
                    differing++;
                }
            }
        }
        assertThat(differing).as("pixels of the pin").isGreaterThan(5);
    }
}
