package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TableItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/** The layouts other than Details, Group by, and the details and preview panes (step 5). */
class ViewsAndPanesTest {

    private static Display display;
    private static Theme dark;
    private ThemeManager manager;
    private Shell shell;
    private FileBrowser browser;

    private static final FilePath ME = FilePath.of("home", "me");
    private static final Instant OLD = Instant.parse("2024-04-08T13:45:00Z");

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        dark = registry.resolve("zap-dark");
        FolderPopup.closeOnDeactivate = false;
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.setTheme(dark);
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
        browser = new FileBrowser(shell, 0);
        shell.setSize(1000, 600);
        shell.open();
        browser.setFileSystem(server());
        idle();
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.resetZoom();
    }

    /** A small red picture, as PNG. */
    static byte[] png() {
        ImageData data = new ImageData(40, 30, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
        for (int y = 0; y < 30; y++) {
            for (int x = 0; x < 40; x++) {
                data.setPixel(x, y, 0xFF0000);
            }
        }
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { data };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        loader.save(out, SWT.IMAGE_PNG);
        return out.toByteArray();
    }

    private static MemoryFileSystem server() {
        return MemoryFileSystem.builder("server")
                .folder("home/me/projects/zap").modified(OLD)
                .folder("home/me/Documents").modified(OLD)
                .file("home/me/notes.txt", "Hello\nworld").modified(OLD)
                .file("home/me/photo.png", png()).modified(OLD)
                .file("home/me/report2.pdf", 7000).modified(OLD)
                .file("home/me/report10.pdf", 5000).modified(OLD)
                .home("home/me")
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

    private List<String> names() {
        return FileBrowserProbe.names(browser.getVisibleEntries());
    }

    private List<String> selected() {
        return FileBrowserProbe.names(browser.getSelectedEntries());
    }

    private FileEntry entry(String name) {
        return browser.getVisibleEntries().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private void mouse(int type, int x, int y, int stateMask) {
        Event event = new Event();
        event.x = x;
        event.y = y;
        event.button = 1;
        event.stateMask = stateMask;
        browser.fileGrid().notifyListeners(type, event);
    }

    /** A click on the middle of {@code name} in the grid, with {@code stateMask} held. */
    private void click(String name, int stateMask) {
        Rectangle r = FileBrowserProbe.itemBounds(browser, name);
        assertThat(r).as(name + " shown").isNotNull();
        mouse(SWT.MouseDown, r.x + r.width / 2, r.y + r.height / 4, stateMask);
        mouse(SWT.MouseUp, r.x + r.width / 2, r.y + r.height / 4, stateMask);
    }

    private void key(int keyCode, char character, int stateMask) {
        Event event = new Event();
        event.keyCode = keyCode;
        event.character = character;
        event.stateMask = stateMask;
        FileBrowserProbe.viewControl(browser).notifyListeners(SWT.KeyDown, event);
    }

    private static List<String> labels(Menu menu) {
        return Arrays.stream(menu.getItems()).map(i -> (i.getStyle() & SWT.SEPARATOR) != 0 ? "-" : i.getText().split("\t")[0].replace("&", ""))
                .toList();
    }

    private static MenuItem itemNamed(Menu menu, String label) {
        return Arrays.stream(menu.getItems()).filter(i -> i.getText().split("\t")[0].replace("&", "").equals(label)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ layouts

    @Test
    void everyLayoutShowsTheSameItemsAndKeepsTheSelection() {
        FileBrowserProbe.select(browser, "notes.txt", "photo.png");
        for (FileView view : FileView.values()) {
            browser.setView(view);
            idle();
            assertThat(browser.getView()).isEqualTo(view);
            assertThat(names()).as(view.label()).containsExactly("Documents", "projects", "notes.txt", "photo.png", "report2.pdf", "report10.pdf");
            assertThat(selected()).as(view.label()).containsExactly("notes.txt", "photo.png");
            assertThat(FileBrowserProbe.viewControl(browser) == browser.fileTable().table).isEqualTo(view == FileView.DETAILS);
            assertThat(FileBrowserProbe.viewControl(browser).isVisible()).isTrue();
            // The other view stays hidden (Windows shows a hidden table again when its redraw is turned on).
            assertThat(browser.fileTable().table.getVisible()).isEqualTo(view == FileView.DETAILS);
            assertThat(browser.fileGrid().getVisible()).isEqualTo(view != FileView.DETAILS);
        }
        assertThat(FileBrowserProbe.status(browser)).startsWith("6 items").contains("2 items selected");
    }

    @Test
    void shortcutsTheMenuAndTheStatusBarPickTheLayout() {
        key('2', '@', SWT.MOD1 | SWT.SHIFT);
        assertThat(browser.getView()).isEqualTo(FileView.LARGE_ICONS);
        key('7', '&', SWT.MOD1 | SWT.SHIFT);
        assertThat(browser.getView()).isEqualTo(FileView.TILES);

        Menu menu = new com.zapthemes.swt.widgets.Menu(shell);
        browser.actions().fillViewMenu(menu);
        assertThat(labels(menu)).startsWith("Extra large icons", "Large icons", "Medium icons", "Small icons", "List", "Details", "Tiles",
                "Content", "-", "Details pane", "Preview pane");
        assertThat(itemNamed(menu, "Tiles").getSelection()).isTrue();
        assertThat(itemNamed(menu, "Content").getText()).endsWith("\tCtrl+Shift+8");
        MenuItem content = itemNamed(menu, "Content");
        content.setSelection(true);
        content.notifyListeners(SWT.Selection, new Event());
        assertThat(browser.getView()).isEqualTo(FileView.CONTENT);
        menu.dispose();

        // The status bar's toggles: Details, then Large icons.
        StatusBar bar = browser.statusBar();
        for (int toggle = 0; toggle < 2; toggle++) {
            Rectangle r = bar.toggleBounds(toggle);
            Event up = new Event();
            up.x = r.x + r.width / 2;
            up.y = r.y + r.height / 2;
            up.button = 1;
            bar.notifyListeners(SWT.MouseUp, up);
            assertThat(browser.getView()).isEqualTo(toggle == 0 ? FileView.DETAILS : FileView.LARGE_ICONS);
        }
        assertThat(browser.actions().glyph(FileActions.VIEW)).isEqualTo(FileIcons.Glyph.LARGE_ICONS_VIEW);
    }

    @Test
    void theLayoutsSizeTheirItems() {
        browser.setView(FileView.EXTRA_LARGE_ICONS);
        idle();
        Rectangle huge = FileBrowserProbe.itemBounds(browser, "notes.txt");
        browser.setView(FileView.MEDIUM_ICONS);
        idle();
        Rectangle medium = FileBrowserProbe.itemBounds(browser, "notes.txt");
        browser.setView(FileView.CONTENT);
        idle();
        Rectangle content = FileBrowserProbe.itemBounds(browser, "notes.txt");
        Rectangle area = browser.fileGrid().getClientArea();
        assertThat(huge.height).isGreaterThan(256);
        assertThat(medium.width).isLessThan(huge.width);
        assertThat(content.width).isGreaterThan(area.width - 40);
        // List goes down the columns first.
        browser.setView(FileView.LIST);
        idle();
        assertThat(FileBrowserProbe.itemBounds(browser, "projects").x).isEqualTo(FileBrowserProbe.itemBounds(browser, "Documents").x);
        assertThat(FileBrowserProbe.itemBounds(browser, "projects").y).isGreaterThan(FileBrowserProbe.itemBounds(browser, "Documents").y);
        // Small icons go along the rows.
        browser.setView(FileView.SMALL_ICONS);
        idle();
        assertThat(FileBrowserProbe.itemBounds(browser, "projects").y).isEqualTo(FileBrowserProbe.itemBounds(browser, "Documents").y);

        // At 150 % everything grows with the zoom, without resizing the window.
        browser.setView(FileView.MEDIUM_ICONS);
        idle();
        org.eclipse.swt.graphics.Point size = shell.getSize();
        manager.setZoom(1.5);
        idle();
        assertThat(FileBrowserProbe.itemBounds(browser, "notes.txt").width).isGreaterThan(medium.width);
        assertThat(shell.getSize()).isEqualTo(size);
    }

    // ------------------------------------------------------------------ the grid's mouse and keys

    @Test
    void clicksSelectAsInExplorer() {
        browser.setView(FileView.LARGE_ICONS);
        idle();
        List<List<FileEntry>> told = new java.util.ArrayList<>();
        browser.addSelectionChangeListener(told::add);

        click("notes.txt", 0);
        assertThat(selected()).containsExactly("notes.txt");
        click("report2.pdf", SWT.MOD1);
        assertThat(selected()).containsExactly("notes.txt", "report2.pdf");
        click("notes.txt", SWT.MOD1);
        assertThat(selected()).containsExactly("report2.pdf");
        // Shift selects from the last item clicked.
        click("Documents", SWT.SHIFT);
        assertThat(selected()).containsExactly("Documents", "projects", "notes.txt");
        // A click on one of several selected items selects it alone (once the button is up).
        click("projects", 0);
        assertThat(selected()).containsExactly("projects");
        assertThat(told).isNotEmpty();
        assertThat(FileBrowserProbe.status(browser)).contains("1 item selected");

        // A click on empty space clears the selection.
        Rectangle area = browser.fileGrid().getClientArea();
        mouse(SWT.MouseDown, area.width - 5, area.height - 5, 0);
        mouse(SWT.MouseUp, area.width - 5, area.height - 5, 0);
        assertThat(selected()).isEmpty();
    }

    @Test
    void aRubberBandSelectsWhatItCrosses() {
        browser.setView(FileView.MEDIUM_ICONS);
        idle();
        Rectangle area = browser.fileGrid().getClientArea();
        Rectangle documents = FileBrowserProbe.itemBounds(browser, "Documents");
        Rectangle projects = FileBrowserProbe.itemBounds(browser, "projects");
        // From empty space below the first line up over its first two items.
        int x = projects.x + projects.width / 2;
        mouse(SWT.MouseDown, x, area.height - 5, 0);
        mouse(SWT.MouseMove, documents.x + 2, documents.y + 2, SWT.BUTTON1);
        mouse(SWT.MouseUp, documents.x + 2, documents.y + 2, 0);
        assertThat(selected()).contains("Documents", "projects").doesNotContain("notes.txt", "photo.png");
    }

    @Test
    void keysMoveByTheLayoutAndTypingFindsAName() {
        browser.setView(FileView.LARGE_ICONS);
        idle();
        click("Documents", 0);
        key(SWT.ARROW_RIGHT, '\0', 0);
        assertThat(selected()).containsExactly("projects");
        key(SWT.END, '\0', 0);
        assertThat(selected()).containsExactly("report10.pdf");
        key(SWT.HOME, '\0', SWT.SHIFT);
        assertThat(selected()).hasSize(6);
        key(SWT.HOME, '\0', 0);
        assertThat(selected()).containsExactly("Documents");

        // Down goes to the item below: the same column on the next line, if there is one.
        browser.setView(FileView.SMALL_ICONS);
        idle();
        shell.setSize(560, 600);
        idle();
        browser.setSelectedEntries(List.of(entry("Documents")));
        Rectangle first = FileBrowserProbe.itemBounds(browser, "Documents");
        key(SWT.ARROW_DOWN, '\0', 0);
        Rectangle below = FileBrowserProbe.itemBounds(browser, selected().getFirst());
        assertThat(below.x).isEqualTo(first.x);
        assertThat(below.y).isGreaterThan(first.y);

        key('r', 'r', 0);
        assertThat(selected()).containsExactly("report2.pdf");
        key('r', 'r', 0);
        assertThat(selected()).containsExactly("report10.pdf");
    }

    @Test
    void enterAndDoubleClickOpen() {
        browser.setView(FileView.TILES);
        idle();
        List<String> opened = new java.util.ArrayList<>();
        browser.addOpenListener(e -> opened.add(e.name()));
        click("notes.txt", 0);
        key(SWT.CR, '\r', 0);
        assertThat(opened).containsExactly("notes.txt");

        Rectangle r = FileBrowserProbe.itemBounds(browser, "projects");
        click("projects", 0);
        mouse(SWT.MouseDoubleClick, r.x + 10, r.y + 10, 0);
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME.resolve("projects"));
        assertThat(names()).containsExactly("zap");
    }

    @Test
    void renamesInPlaceInTheGrid() {
        browser.setView(FileView.TILES);
        idle();
        click("notes.txt", 0);
        browser.rename();
        assertThat(browser.fileGrid().editor()).isNotNull();
        assertThat(browser.fileGrid().editor().text().getText()).isEqualTo("notes.txt");
        FileBrowserProbe.finishRename(browser, "memo.txt");
        idle();
        assertThat(names()).contains("memo.txt").doesNotContain("notes.txt");
        assertThat(selected()).containsExactly("memo.txt");
    }

    @Test
    void aDropOnAFolderOfTheGridGoesIntoIt() {
        browser.setView(FileView.MEDIUM_ICONS);
        idle();
        FileBrowserProbe.highlightDrop(browser, "projects");
        assertThat(browser.fileGrid().dropTarget().name()).isEqualTo("projects");
        FileBrowserProbe.highlightDrop(browser, null);
        assertThat(browser.fileGrid().dropTarget()).isNull();

        assertThat(FileBrowserProbe.drop(browser, List.of("notes.txt"), browser, "projects", DND.DROP_DEFAULT)).isEqualTo(DND.DROP_MOVE);
        waitFor(() -> !names().contains("notes.txt"));
        browser.navigate(ME.resolve("projects"));
        idle();
        assertThat(names()).containsExactly("zap", "notes.txt");
        // Dragging from empty space draws a rubber band: nothing to drag.
        Rectangle area = browser.fileGrid().getClientArea();
        assertThat(browser.dragAndDrop().draggable(false, area.width - 4, area.height - 4)).isEmpty();
        click("zap", 0);
        Rectangle zap = FileBrowserProbe.itemBounds(browser, "zap");
        assertThat(FileBrowserProbe.names(browser.dragAndDrop().draggable(false, zap.x + 5, zap.y + 5))).containsExactly("zap");
    }

    @Test
    void theContextMenuKnowsWhatIsUnderTheMouse() {
        browser.setView(FileView.LARGE_ICONS);
        idle();
        assertThat(FileBrowserProbe.contextMenu(browser, false)).startsWith("View", "Sort by", "Group by");
        click("notes.txt", 0);
        assertThat(FileBrowserProbe.contextMenu(browser, true)).contains("Open", "Rename", "Delete");
    }

    // ------------------------------------------------------------------ groups

    @Test
    void groupsByTypeInTheGridAndInDetails() {
        browser.setGroupBy(FileOrder.Key.TYPE, true);
        idle();
        assertThat(FileBrowserProbe.groupHeaders(browser)).containsExactly("File folder (2)", "PDF document (2)", "PNG image (1)",
                "Text document (1)");
        assertThat(names()).containsExactly("Documents", "projects", "report2.pdf", "report10.pdf", "photo.png", "notes.txt");
        assertThat(browser.fileTable().table.getItemCount()).isEqualTo(6 + 4);

        // A click on a header row selects its group; the header itself is never selected.
        TableItem header = browser.fileTable().headerItem("PDF document");
        Event click = new Event();
        click.item = header;
        browser.fileTable().table.setSelection(header);
        browser.fileTable().table.notifyListeners(SWT.Selection, click);
        assertThat(selected()).containsExactly("report2.pdf", "report10.pdf");
        assertThat(browser.fileTable().table.isSelected(browser.fileTable().table.indexOf(header))).isFalse();

        // Collapsed, a group keeps its header and count; the status bar still counts everything.
        FileBrowserProbe.toggleGroup(browser, "File folder");
        assertThat(FileBrowserProbe.groupHeaders(browser)).first().isEqualTo("File folder (2)");
        assertThat(names()).doesNotContain("Documents", "projects");
        assertThat(browser.getCollapsedGroups()).containsExactly("File folder");
        assertThat(FileBrowserProbe.status(browser)).startsWith("6 items");

        browser.setView(FileView.LARGE_ICONS);
        idle();
        assertThat(FileBrowserProbe.groupHeaders(browser)).hasSize(4);
        assertThat(names()).doesNotContain("Documents");
        // The chevron expands it again; a click elsewhere on a header selects the group.
        Rectangle folders = browser.fileGrid().headerBounds("File folder");
        mouse(SWT.MouseDown, folders.x + 6, folders.y + folders.height / 2, 0);
        assertThat(names()).contains("Documents", "projects");
        Rectangle images = browser.fileGrid().headerBounds("PNG image");
        mouse(SWT.MouseDown, images.x + images.width / 2, images.y + images.height / 2, 0);
        assertThat(selected()).containsExactly("photo.png");

        browser.setGroupsCollapsed(true);
        assertThat(names()).isEmpty();
        assertThat(browser.fileGrid().message()).isNull();
        browser.setGroupsCollapsed(false);
        assertThat(names()).hasSize(6);

        browser.setGroupBy(null, true);
        assertThat(FileBrowserProbe.groupHeaders(browser)).isEmpty();
        assertThat(browser.getGroupBy()).isNull();
    }

    @Test
    void groupsBySizeAndNameWithTheMenu() {
        Menu menu = new com.zapthemes.swt.widgets.Menu(shell);
        browser.actions().fillGroupBy(menu);
        assertThat(labels(menu)).containsExactly("(None)", "Name", "Date modified", "Type", "Size", "-", "Ascending", "Descending", "-",
                "Collapse all groups", "Expand all groups");
        MenuItem size = itemNamed(menu, "Size");
        size.setSelection(true);
        size.notifyListeners(SWT.Selection, new Event());
        menu.dispose();
        // Size starts with the largest, as Explorer does; folders have no size.
        assertThat(browser.getGroupBy()).isEqualTo(FileOrder.Key.SIZE);
        assertThat(browser.isGroupAscending()).isFalse();
        assertThat(FileBrowserProbe.groupHeaders(browser)).containsExactly("Tiny (4)", "Unspecified (2)");

        browser.setGroupBy(FileOrder.Key.NAME, true);
        assertThat(FileBrowserProbe.groupHeaders(browser)).containsExactly("A - H (1)", "I - P (3)", "Q - Z (2)");
        // Leaving the folder forgets the collapsed groups.
        FileBrowserProbe.toggleGroup(browser, "Q - Z");
        browser.navigate(ME.resolve("projects"));
        idle();
        assertThat(browser.getCollapsedGroups()).isEmpty();
        assertThat(FileBrowserProbe.groupHeaders(browser)).containsExactly("Q - Z (1)");
    }

    // ------------------------------------------------------------------ panes

    @Test
    void aHiddenNavigationPaneStaysHiddenWhenItsTreeIsRebuilt() {
        browser.setDetailsPaneVisible(true);
        browser.setNavigationPaneVisible(false);
        browser.setShowHidden(true);
        browser.navigate(ME.resolve("projects"));
        idle();
        assertThat(browser.isNavigationPaneVisible()).isFalse();
        assertThat(browser.navigationTree().tree.getVisible()).isFalse();
        // The details pane stays, beside the file view.
        assertThat(browser.sidePane().getVisible()).isTrue();
        browser.setNavigationPaneVisible(true);
        assertThat(browser.navigationTree().tree.getVisible()).isTrue();
    }

    @Test
    void theDetailsPaneShowsTheSelectionOrTheFolder() {
        browser.setDetailsPaneVisible(true);
        idle();
        assertThat(browser.isDetailsPaneVisible()).isTrue();
        assertThat(browser.sidePane().isVisible()).isTrue();
        assertThat(browser.getCommand(FileActions.DETAILS_PANE).isChecked()).isTrue();
        assertThat(FileBrowserProbe.detailsPane(browser)).containsExactly("me", "6 items");

        FileBrowserProbe.select(browser, "notes.txt");
        assertThat(FileBrowserProbe.detailsPane(browser)).startsWith("notes.txt", "Text document", "Size: 11 bytes")
                .anyMatch(l -> l.startsWith("Date modified: "));
        FileBrowserProbe.select(browser, "notes.txt", "report2.pdf");
        assertThat(FileBrowserProbe.detailsPane(browser)).first().isEqualTo("2 items selected");

        // The preview pane takes its place; Alt+Shift+P brings it back, and hides it.
        browser.setPreviewPaneVisible(true);
        assertThat(browser.isDetailsPaneVisible()).isFalse();
        assertThat(browser.isPreviewPaneVisible()).isTrue();
        key('p', 'p', SWT.ALT | SWT.SHIFT);
        assertThat(browser.isDetailsPaneVisible()).isTrue();
        key('p', 'p', SWT.ALT | SWT.SHIFT);
        assertThat(browser.isDetailsPaneVisible()).isFalse();
        assertThat(browser.sidePane().isVisible()).isFalse();
    }

    @Test
    void thePreviewPaneShowsTextAndPictures() {
        key('p', 'p', SWT.ALT);
        assertThat(browser.isPreviewPaneVisible()).isTrue();
        assertThat(FileBrowserProbe.previewMessage(browser)).isEqualTo("Select a file to preview.");

        FileBrowserProbe.select(browser, "notes.txt");
        waitFor(() -> "Hello\nworld".equals(FileBrowserProbe.previewText(browser)));

        FileBrowserProbe.select(browser, "photo.png");
        waitFor(() -> FileBrowserProbe.previewShowsPicture(browser));
        assertThat(FileBrowserProbe.previewText(browser)).isNull();

        // A PDF of zeros isn't text.
        FileBrowserProbe.select(browser, "report2.pdf");
        waitFor(() -> "No preview available.".equals(FileBrowserProbe.previewMessage(browser)));
        FileBrowserProbe.select(browser, "notes.txt", "photo.png");
        assertThat(FileBrowserProbe.previewMessage(browser)).isEqualTo("No preview available.");
    }

    @Test
    void imagesShowTheirPictureInTheLargerLayouts() {
        browser.setView(FileView.SMALL_ICONS);
        idle();
        paintGrid();
        idle();
        assertThat(FileBrowserProbe.thumbnails(browser)).isZero();

        browser.setView(FileView.LARGE_ICONS);
        idle();
        paintGrid();
        waitFor(() -> FileBrowserProbe.thumbnails(browser) == 1);
        // Painted again, it takes the picture from the cache.
        paintGrid();
        idle();
        assertThat(FileBrowserProbe.thumbnails(browser)).isEqualTo(1);
    }

    /** Paints the grid as a screenshot does (a window behind others may get no paint of its own). */
    private void paintGrid() {
        FileGrid grid = browser.fileGrid();
        org.eclipse.swt.graphics.Point size = grid.getSize();
        org.eclipse.swt.graphics.Image image = new org.eclipse.swt.graphics.Image(display, size.x, size.y);
        org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(image);
        try {
            grid.print(gc);
        } finally {
            gc.dispose();
            image.dispose();
        }
    }

    @Test
    void textPreviewsAreReadUpToTheLimitAndDecoded() throws Exception {
        // The limit falls in the middle of an "é": the text stops before it.
        MemoryFileSystem fs = MemoryFileSystem.builder("t").file("long.txt", "a" + "é".repeat(SidePane.TEXT_BYTES)).build();
        String text = SidePane.readText(fs, fs.stat(FilePath.of("long.txt")).orElseThrow());
        assertThat(text).hasSize(SidePane.TEXT_BYTES / 2).startsWith("aé").doesNotContain("�", "Ã");
        MemoryFileSystem latin = MemoryFileSystem.builder("l").file("latin.txt", new byte[] { 'c', 'a', 'f', (byte) 0xE9 }).build();
        assertThat(SidePane.readText(latin, latin.stat(FilePath.of("latin.txt")).orElseThrow())).isEqualTo("café");
    }

    @Test
    void namesWrapAfterHyphensAndDots() {
        org.eclipse.swt.graphics.GC gc = new org.eclipse.swt.graphics.GC(shell);
        try {
            int width = gc.textExtent("uptime-kuma-helm.").x + 2;
            assertThat(FileGrid.wrap(gc, "uptime-kuma-helm.7z", width, 2)).containsExactly("uptime-kuma-helm.", "7z");
            assertThat(FileGrid.wrap(gc, "iad-email-images", gc.textExtent("iad-email-").x + 2, 2)).containsExactly("iad-email-", "images");
            assertThat(FileGrid.wrap(gc, "Kubernetes Install On RHEL 9.docx", gc.textExtent("Kubernetes Install On").x + 2, 2))
                    .containsExactly("Kubernetes Install On", "RHEL 9.docx");
            List<String> cut = FileGrid.wrap(gc, "averyveryverylongnamewithoutbreaks.txt", gc.textExtent("averyvery").x, 2);
            assertThat(cut).hasSize(2);
            assertThat(cut.get(1)).endsWith("…");
        } finally {
            gc.dispose();
        }
    }

    @Test
    void picturesAreShrunkWithTheirTransparency() {
        ImageData data = new ImageData(400, 200, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
        data.alphaData = new byte[400 * 200];
        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 200; y++) {
                data.setAlpha(x, y, 255);
                data.setPixel(x, y, 0x0000FF);
            }
        }
        ImageData small = Thumbnails.shrink(data, 100);
        assertThat(small.width).isEqualTo(100);
        assertThat(small.height).isEqualTo(50);
        assertThat(small.getAlpha(10, 10)).isEqualTo(255);
        assertThat(small.getAlpha(90, 10)).isZero();
        assertThat(small.palette.getRGB(small.getPixel(10, 10)).blue).isEqualTo(255);
        assertThat(Thumbnails.shrink(data, 1000)).isSameAs(data);
    }
}
