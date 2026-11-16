package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.LocalFileSystem;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.files.RootedFileSystem;
import com.zapthemes.files.TransferRequest;
import com.zapthemes.files.ui.FileEntryTransfer.Dragged;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/**
 * Step 4: what a drop does (move or copy, into which folder, refused where), and that it reaches the
 * transfer handler and refreshes both browsers. The native drag itself is the platform's; these
 * tests drive the browser's side of it.
 */
class DragAndDropTest {

    private static final FilePath ME = FilePath.of("home", "me");
    private static Display display;
    private Shell shell;
    private FileBrowser browser;
    private MemoryFileSystem fs;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        ThemeManager.get(display).setTheme(registry.resolve("zap-light"));
        FolderPopup.closeOnDeactivate = false;
    }

    @BeforeEach
    void setUp() {
        shell = new Shell(display);
        shell.setLayout(new FillLayout());
        browser = new FileBrowser(shell, 0);
        shell.setSize(1100, 600);
        shell.open();
        fs = MemoryFileSystem.builder("server")
                .folder("home/me/projects/zap")
                .folder("home/me/archive")
                .file("home/me/notes.txt", "Hello")
                .file("home/me/report.pdf", 5000)
                .home("home/me")
                .build();
        browser.setFileSystem(fs);
        idle(browser);
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
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

    private void idle(FileBrowser b) {
        waitFor(() -> !b.isLoading() && b.pendingCalls() == 0);
    }

    private static List<String> names(FileBrowser b) {
        return b.getVisibleEntries().stream().map(FileEntry::name).toList();
    }

    private FileEntry entry(String name) {
        return browser.getVisibleEntries().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }

    private TableItem row(String name) {
        return browser.fileTable().table.getItem(browser.fileTable().rows().indexOf(entry(name)));
    }

    private Dragged dragged(String... names) {
        return new Dragged(fs, List.of(names).stream().map(this::entry).toList());
    }

    /** Lists {@code folder}'s subfolders in the navigation pane, as expanding it does. */
    private TreeItem listInTree(FilePath folder) {
        TreeItem item = browser.navigationTree().itemOf(folder);
        Event expand = new Event();
        expand.item = item;
        browser.navigationTree().tree.notifyListeners(SWT.Expand, expand);
        item.setExpanded(true);
        waitFor(() -> item.getItemCount() > 0 && browser.navigationTree().folderOf(item.getItem(0)) != null);
        return item;
    }

    private FileDragAndDrop dnd() {
        return browser.dragAndDrop();
    }

    @Test
    void whereADropGoes() {
        // A folder row: into it; a file row or empty space: into the folder shown.
        assertThat(dnd().folderAt(row("projects"))).isEqualTo(ME.resolve("projects"));
        assertThat(dnd().folderAt(row("notes.txt"))).isEqualTo(ME);
        assertThat(dnd().folderAt(null)).isEqualTo(ME);
        // In the navigation pane: the folder of the item.
        listInTree(ME);
        TreeItem projects = browser.navigationTree().itemOf(ME.resolve("projects"));
        assertThat(projects).isNotNull();
        assertThat(dnd().folderAt(projects)).isEqualTo(ME.resolve("projects"));
    }

    @Test
    void withinOneFilesystemADropMovesAndCtrlCopies() {
        FilePath projects = ME.resolve("projects");
        assertThat(dnd().operation(dragged("notes.txt"), projects, DND.DROP_DEFAULT)).isEqualTo(DND.DROP_MOVE);
        assertThat(dnd().operation(dragged("notes.txt"), projects, DND.DROP_COPY)).isEqualTo(DND.DROP_COPY);
        // Into the folder it is in: a move does nothing, a copy makes "- Copy".
        assertThat(dnd().operation(dragged("notes.txt"), ME, DND.DROP_DEFAULT)).isEqualTo(DND.DROP_NONE);
        assertThat(dnd().operation(dragged("notes.txt"), ME, DND.DROP_COPY)).isEqualTo(DND.DROP_COPY);
        // A folder never goes into itself or below it.
        assertThat(dnd().operation(dragged("projects"), projects, DND.DROP_COPY)).isEqualTo(DND.DROP_NONE);
        assertThat(dnd().operation(dragged("projects", "notes.txt"), projects.resolve("zap"), DND.DROP_MOVE)).isEqualTo(DND.DROP_NONE);
        assertThat(dnd().operation(new Dragged(fs, List.of()), projects, DND.DROP_DEFAULT)).isEqualTo(DND.DROP_NONE);
        assertThat(dnd().operation(dragged("notes.txt"), null, DND.DROP_DEFAULT)).isEqualTo(DND.DROP_NONE);
    }

    @Test
    void betweenFilesystemsADropCopiesAndShiftMoves() throws IOException {
        MemoryFileSystem laptop = MemoryFileSystem.builder("laptop").folder("work").file("work/a.txt", "A").build();
        Dragged fromLaptop = new Dragged(laptop, List.of(laptop.stat(FilePath.of("work", "a.txt")).orElseThrow()));
        assertThat(dnd().operation(fromLaptop, ME, DND.DROP_DEFAULT)).isEqualTo(DND.DROP_COPY);
        assertThat(dnd().operation(fromLaptop, ME, DND.DROP_MOVE)).isEqualTo(DND.DROP_MOVE);
        // Files of another application: copied unless Shift is held.
        assertThat(dnd().operation(null, ME, DND.DROP_DEFAULT)).isEqualTo(DND.DROP_COPY);
        assertThat(dnd().operation(null, ME, DND.DROP_MOVE)).isEqualTo(DND.DROP_MOVE);
    }

    @Test
    void aDropMovesIntoAFolderThroughTheHandler() throws IOException {
        List<TransferRequest> requests = new ArrayList<>();
        browser.setTransferHandler(request -> {
            requests.add(request);
            return com.zapthemes.files.FileTransferHandler.direct().transfer(request);
        });
        FilePath projects = ME.resolve("projects");
        assertThat(dnd().drop(dragged("notes.txt", "report.pdf"), projects, DND.DROP_MOVE)).isTrue();
        waitFor(() -> !names(browser).contains("notes.txt"));
        idle(browser);

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().move()).isTrue();
        assertThat(requests.getFirst().targetFolder()).isEqualTo(projects);
        assertThat(names(browser)).containsExactly("archive", "projects");
        assertThat(fs.list(projects)).extracting(FileEntry::name).containsExactlyInAnyOrder("zap", "notes.txt", "report.pdf");
        assertThat(dnd().drop(dragged("archive"), ME, DND.DROP_NONE)).isFalse();
    }

    @Test
    void aMovedFolderLeavesTheTreeAndTheOtherBrowser() throws IOException {
        Shell other = new Shell(display);
        other.setLayout(new FillLayout());
        FileBrowser second = new FileBrowser(other, 0);
        second.setFileSystem(fs, ME.resolve("projects"));
        other.setSize(800, 500);
        other.open();
        idle(second);
        listInTree(ME);
        assertThat(browser.navigationTree().itemOf(ME.resolve("archive"))).isNotNull();

        // "archive" is dragged from the first browser onto the second one's folder.
        Dragged archive = dragged("archive");
        int operation = second.dragAndDrop().operation(archive, ME.resolve("projects"), DND.DROP_DEFAULT);
        assertThat(operation).isEqualTo(DND.DROP_MOVE);
        FileDragAndDrop.setDragFrom(browser);
        assertThat(second.dragAndDrop().drop(archive, ME.resolve("projects"), operation)).isTrue();
        waitFor(() -> names(second).contains("archive") && !names(browser).contains("archive"));
        idle(browser);
        idle(second);

        assertThat(fs.stat(ME.resolve("archive"))).isEmpty();
        assertThat(browser.navigationTree().itemOf(ME.resolve("archive"))).isNull();
        FileDragAndDrop.setDragFrom(null);
        other.dispose();
    }

    @Test
    void aCopyToAnotherFilesystemKeepsTheOriginal() throws IOException {
        Shell other = new Shell(display);
        other.setLayout(new FillLayout());
        FileBrowser second = new FileBrowser(other, 0);
        MemoryFileSystem laptop = MemoryFileSystem.builder("laptop").folder("work").build();
        second.setFileSystem(laptop, FilePath.of("work"));
        other.setSize(800, 500);
        other.open();
        idle(second);

        Dragged notes = dragged("notes.txt", "projects");
        int operation = second.dragAndDrop().operation(notes, FilePath.of("work"), DND.DROP_DEFAULT);
        assertThat(operation).isEqualTo(DND.DROP_COPY);
        assertThat(second.dragAndDrop().drop(notes, FilePath.of("work"), operation)).isTrue();
        waitFor(() -> names(second).size() == 2);
        assertThat(names(second)).containsExactly("projects", "notes.txt");
        assertThat(laptop.stat(FilePath.of("work", "projects", "zap"))).isPresent();
        assertThat(fs.stat(ME.resolve("notes.txt"))).isPresent();
        other.dispose();
    }

    @Test
    void theFolderUnderTheMouseIsHighlighted() {
        TableItem projects = row("projects");
        dnd().highlight(projects);
        assertThat(dnd().dropItem()).isSameAs(projects);
        // Painted as a row under the mouse, not left to the platform.
        Event erase = new Event();
        erase.item = projects;
        erase.gc = new org.eclipse.swt.graphics.GC(browser.fileTable().table);
        erase.detail = SWT.BACKGROUND | SWT.FOREGROUND;
        erase.index = 0;
        erase.width = 100;
        erase.height = 20;
        try {
            browser.fileTable().table.notifyListeners(SWT.EraseItem, erase);
        } finally {
            erase.gc.dispose();
        }
        assertThat(erase.detail & SWT.BACKGROUND).isZero();
        dnd().highlight(null);
        assertThat(dnd().dropItem()).isNull();
    }

    @Test
    void whatCanBeDragged() {
        browser.fileTable().table.setSelection(browser.fileTable().rows().indexOf(entry("notes.txt")));
        assertThat(dnd().draggable(false, 0, 0)).extracting(FileEntry::name).containsExactly("notes.txt");
        browser.fileTable().table.deselectAll();
        assertThat(dnd().draggable(false, 0, 0)).isEmpty();
        // The tree's places and its top stay put; its folders can be dragged.
        listInTree(ME);
        TreeItem top = browser.navigationTree().tree.getItem(browser.navigationTree().tree.getItemCount() - 1);
        org.eclipse.swt.graphics.Rectangle bounds = top.getBounds();
        assertThat(dnd().draggable(true, bounds.x + 2, bounds.y + 2)).isEmpty();
        TreeItem projects = browser.navigationTree().itemOf(ME.resolve("projects"));
        browser.navigationTree().tree.showItem(projects);
        while (display.readAndDispatch()) {
            // GTK lays the expanded items out on its next pass.
        }
        bounds = projects.getBounds();
        assertThat(dnd().draggable(true, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2)).extracting(FileEntry::name).containsExactly("projects");
    }

    @Test
    void twoViewsOfTheDiskMoveOnDisk(@TempDir Path temp) throws IOException {
        Path real = temp.toRealPath();
        Files.createDirectories(real.resolve("in"));
        Files.writeString(real.resolve("a.txt"), "A");
        LocalFileSystem local = LocalFileSystem.get();
        RootedFileSystem rooted = new RootedFileSystem(local, local.fromPath(real), "Temp");
        browser.setFileSystem(rooted, FilePath.TOP);
        idle(browser);
        Shell other = new Shell(display);
        other.setLayout(new FillLayout());
        FileBrowser second = new FileBrowser(other, 0);
        second.setFileSystem(local, local.fromPath(real.resolve("in")));
        other.setSize(800, 500);
        other.open();
        idle(second);

        Dragged a = new Dragged(rooted, List.of(rooted.stat(FilePath.of("a.txt")).orElseThrow()));
        FilePath in = local.fromPath(real.resolve("in"));
        int operation = second.dragAndDrop().operation(a, in, DND.DROP_DEFAULT);
        assertThat(operation).isEqualTo(DND.DROP_MOVE);
        // The rooted folder dropped into itself, seen from the whole disk, is refused.
        Dragged top = new Dragged(local, List.of(local.stat(local.fromPath(real)).orElseThrow()));
        assertThat(browser.dragAndDrop().operation(top, FilePath.of("in"), DND.DROP_COPY)).isEqualTo(DND.DROP_NONE);

        assertThat(second.dragAndDrop().drop(a, in, operation)).isTrue();
        waitFor(() -> names(second).contains("a.txt"));
        assertThat(Files.exists(real.resolve("a.txt"))).isFalse();
        assertThat(Files.readString(real.resolve("in").resolve("a.txt"))).isEqualTo("A");
        other.dispose();
    }
}
