package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.files.Capability;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.LocalFileSystem;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.files.RootedFileSystem;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/** Icon providers and platform icons, watching the folder shown, and deleting to the trash (step 7). */
class IconsAndWatchingTest {

    private static Display display;
    private static Theme dark;
    private ThemeManager manager;
    private Shell shell;
    private FileBrowser browser;
    private MemoryFileSystem server;

    private static final FilePath ME = FilePath.of("home", "me");
    private static final RGB BLUE = new RGB(0, 0, 255);

    @TempDir
    Path temp;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        dark = registry.resolve("zap-dark");
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
        server = MemoryFileSystem.builder("server")
                .folder("home/me/projects")
                .file("home/me/notes.txt", "Hello")
                .file("home/me/.hidden.txt", "x")
                .file("home/me/photo.png", 10)
                .file("home/me/report2.pdf", 7000)
                .file("home/me/report10.pdf", 5000)
                .home("home/me")
                .build();
        browser.setFileSystem(server);
        idle();
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.resetZoom();
    }

    private void waitFor(BooleanSupplier condition) {
        waitFor(condition, 10_000);
    }

    private void waitFor(BooleanSupplier condition, long millis) {
        long end = System.currentTimeMillis() + millis;
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

    /** Lets the event loop run for {@code millis}. */
    private void pause(int millis) {
        long end = System.currentTimeMillis() + millis;
        waitFor(() -> System.currentTimeMillis() >= end);
    }

    private List<String> names() {
        return FileBrowserProbe.names(browser.getVisibleEntries());
    }

    private List<String> selected() {
        return FileBrowserProbe.names(browser.getSelectedEntries());
    }

    private static RGB center(Image image, int zoom) {
        ImageData data = image.getImageData(zoom);
        return data.palette.getRGB(data.getPixel(data.width / 2, data.height / 2));
    }

    private static int centerAlpha(Image image) {
        ImageData data = image.getImageData(100);
        return data.getAlpha(data.width / 2, data.height / 2);
    }

    /** Gives text files a blue square up to 48 pixels and the type name "Plain words"; counts what it is asked for. */
    private static final class BlueText implements IconProvider {
        final List<String> made = new CopyOnWriteArrayList<>();

        @Override
        public String iconKey(FileSystem fs, FileEntry entry) {
            return entry.name().endsWith(".txt") ? ".txt" : null;
        }

        @Override
        public ImageData iconData(String key, int pixels) {
            made.add(key + "@" + pixels);
            if (pixels > 96) {
                return null;
            }
            ImageData data = new ImageData(pixels, pixels, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
            data.alphaData = new byte[pixels * pixels];
            for (int y = 0; y < pixels; y++) {
                for (int x = 0; x < pixels; x++) {
                    data.setPixel(x, y, 0x0000FF);
                    data.alphaData[y * pixels + x] = (byte) 255;
                }
            }
            return data;
        }

        @Override
        public String typeName(FileSystem fs, FileEntry entry) {
            return entry.name().endsWith(".txt") ? "Plain words" : null;
        }
    }

    // ------------------------------------------------------------------ icons

    @Test
    void aProvidersIconsAndTypeNamesShowEverywhere() {
        BlueText provider = new BlueText();
        assertThat(FileBrowserProbe.typeName(browser, "notes.txt")).isEqualTo("Text document");
        browser.setIconProvider(provider);
        assertThat(browser.getIconProvider()).isSameAs(provider);

        // The Details table, the grid's layouts and the details pane ask for their sizes.
        for (int size : new int[] { 16, 32, 48, 96 }) {
            Image icon = FileBrowserProbe.icon(browser, "notes.txt", size);
            assertThat(icon.getBounds().width).isEqualTo(size);
            assertThat(center(icon, 100)).as(size + " px").isEqualTo(BLUE);
            // Sharp at any zoom: asked for again at the zoom's pixels.
            assertThat(icon.getImageData(200).width).isEqualTo(size * 2);
            assertThat(center(icon, 200)).isEqualTo(BLUE);
        }
        // Too large for it: our own, sharp.
        Image large = FileBrowserProbe.icon(browser, "notes.txt", 256);
        assertThat(center(large, 100)).isNotEqualTo(BLUE);
        // What it has none for keeps ours.
        assertThat(center(FileBrowserProbe.icon(browser, "report2.pdf", 32), 100)).isNotEqualTo(BLUE);
        // Once per key and size: every row and paint shares one image.
        int asked = provider.made.size();
        FileBrowserProbe.icon(browser, "notes.txt", 32);
        browser.fileTable().refreshRows();
        idle();
        assertThat(provider.made).hasSize(asked);

        // Type names in Details, the tiles and the details pane; sorting and grouping by type use them.
        assertThat(FileBrowserProbe.typeName(browser, "notes.txt")).isEqualTo("Plain words");
        assertThat(FileBrowserProbe.typeName(browser, "report2.pdf")).isEqualTo("PDF document");
        assertThat(FileBrowserProbe.row(browser, "notes.txt")).contains("Plain words");
        browser.setSort(FileOrder.Key.TYPE, true);
        idle();
        assertThat(names()).containsExactly("projects", "report2.pdf", "report10.pdf", "notes.txt", "photo.png");
        browser.setGroupBy(FileOrder.Key.TYPE, true);
        idle();
        assertThat(FileBrowserProbe.groupHeaders(browser)).anyMatch(h -> h.startsWith("Plain words"));
        browser.setGroupBy(null, true);
        FileBrowserProbe.select(browser, "notes.txt");
        browser.setDetailsPaneVisible(true);
        idle();
        assertThat(FileBrowserProbe.detailsPane(browser)).contains("Plain words");

        // Back to our own.
        browser.setIconProvider(null);
        idle();
        assertThat(FileBrowserProbe.typeName(browser, "notes.txt")).isEqualTo("Text document");
        assertThat(center(FileBrowserProbe.icon(browser, "notes.txt", 32), 100)).isNotEqualTo(BLUE);
    }

    @Test
    void providedIconsFadeForHiddenAndCutItemsAndFollowTheZoom() {
        browser.setIconProvider(new BlueText());
        browser.setShowHidden(true);
        idle();
        FileEntry hidden = browser.getVisibleEntries().stream().filter(e -> e.name().equals(".hidden.txt")).findFirst().orElseThrow();
        FileEntry notes = browser.getVisibleEntries().stream().filter(e -> e.name().equals("notes.txt")).findFirst().orElseThrow();
        assertThat(centerAlpha(browser.icons().get(notes, 32))).isEqualTo(255);
        assertThat(centerAlpha(browser.icons().get(hidden, 32))).isBetween(120, 135);
        assertThat(centerAlpha(browser.icons().get(notes, 32, true))).isBetween(120, 135);

        // A new zoom makes new icons, from the same provider.
        manager.setZoom(1.5);
        idle();
        Image zoomed = browser.icons().get(notes, browser.iconSize());
        assertThat(zoomed.getBounds().width).isEqualTo(browser.iconSize());
        assertThat(center(zoomed, 100)).isEqualTo(BLUE);
    }

    @Test
    void platformIconsAreForLocalFilesOnly() throws IOException {
        IconProvider platform = IconProvider.platform();
        FileEntry folder = FileEntry.builder(FilePath.of("a"), com.zapthemes.files.EntryKind.DIRECTORY).build();
        FileEntry remoteText = FileEntry.builder(ME.resolve("notes.txt"), com.zapthemes.files.EntryKind.FILE).build();
        assertThat(platform.iconKey(server, remoteText)).isNull();
        assertThat(platform.typeName(server, remoteText)).isNull();
        assertThat(platform.iconKey(LocalFileSystem.get(), folder)).isNull();

        Files.writeString(temp.resolve("notes.txt"), "Hello");
        Files.writeString(temp.resolve("no-extension"), "x");
        Files.writeString(temp.resolve("odd.zapthemes-unknown-type"), "x");
        RootedFileSystem local = new RootedFileSystem(LocalFileSystem.get(), LocalFileSystem.get().fromPath(temp), "Temp");
        browser.setFileSystem(local);
        browser.setIconProvider(platform);
        idle();
        FileEntry text = browser.getVisibleEntries().stream().filter(e -> e.name().equals("notes.txt")).findFirst().orElseThrow();
        FileEntry bare = browser.getVisibleEntries().stream().filter(e -> e.name().equals("no-extension")).findFirst().orElseThrow();
        FileEntry odd = browser.getVisibleEntries().stream().filter(e -> e.name().startsWith("odd.")).findFirst().orElseThrow();
        assertThat(platform.iconKey(local, bare)).isNull();
        assertThat(platform.iconKey(local, odd)).isNull();
        assertThat(FileBrowserProbe.typeName(browser, "odd.zapthemes-unknown-type")).isEqualTo("ZAPTHEMES-UNKNOWN-TYPE file");
        // The rest only where the platform knows a program for text files (a desktop has one; a bare CI runner may not).
        String key = platform.iconKey(local, text);
        if (key == null) {
            assertThat(FileBrowserProbe.typeName(browser, "notes.txt")).isEqualTo("Text document");
            return;
        }
        assertThat(key).isEqualTo(".txt");
        assertThat(IconProvider.platformForAllFileSystems().iconKey(server, remoteText)).isEqualTo(".txt");
        for (int size : new int[] { 16, 24, 32, 48 }) {
            ImageData data = platform.iconData(key, size);
            if (data != null) {
                assertThat(data.width).isEqualTo(size);
                assertThat(data.height).isEqualTo(size);
                assertThat(data.alphaData).isNotNull();
                Image icon = FileBrowserProbe.icon(browser, "notes.txt", size);
                assertThat(icon.getBounds().width).isEqualTo(size);
            }
        }
        if (SWT.getPlatform().equals("win32")) {
            // Windows extracts a type's icon at any size, and names the type.
            assertThat(platform.iconData(key, 32)).isNotNull();
            assertThat(platform.iconData(key, 96)).isNotNull();
            assertThat(FileBrowserProbe.typeName(browser, "notes.txt")).isNotBlank().isNotEqualTo("Text document");
        } else {
            assertThat(FileBrowserProbe.typeName(browser, "notes.txt")).isEqualTo("Text document");
        }
        // Every layout paints with them.
        for (FileView view : FileView.values()) {
            browser.setView(view);
            idle();
        }
    }

    @Test
    void fitScalesWithTransparency() {
        ImageData small = new ImageData(16, 8, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        small.alphaData = new byte[16 * 8];
        for (int i = 0; i < small.alphaData.length; i++) {
            small.alphaData[i] = (byte) 255;
        }
        ImageData fitted = PlatformIcons.fit(small, 32);
        assertThat(fitted.width).isEqualTo(32);
        assertThat(fitted.height).isEqualTo(32);
        // Centered, transparent above and below.
        assertThat(fitted.getAlpha(16, 2)).isZero();
        assertThat(fitted.getAlpha(16, 16)).isEqualTo(255);
        ImageData down = PlatformIcons.fit(fitted, 8);
        assertThat(down.width).isEqualTo(8);
        assertThat(down.getAlpha(4, 4)).isEqualTo(255);
    }

    // ------------------------------------------------------------------ watching

    @Test
    void theFolderShownIsWatchedAndListedAgainKeepingSelectionAndScroll() throws IOException {
        for (int i = 0; i < 80; i++) {
            server.createDirectory(ME, String.format("folder %02d", i));
        }
        browser.refresh();
        idle();
        waitFor(() -> ME.equals(FileBrowserProbe.watchedFolder(browser)));
        assertThat(server.watchedCount()).isEqualTo(1);
        FileBrowserProbe.select(browser, "folder 40", "folder 41");
        browser.fileTable().table.setTopIndex(30);
        idle();
        int top = browser.fileTable().table.getTopIndex();
        int refreshes = FileBrowserProbe.watchRefreshes(browser);

        // Another client makes a burst of changes: one listing, a moment after the last.
        Thread other = Thread.ofVirtual().start(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    server.createDirectory(ME, "zz new " + i);
                }
                server.delete(ME.resolve("report10.pdf"), false);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        waitFor(() -> names().contains("zz new 9") && !names().contains("report10.pdf"));
        idle();
        assertThat(other.isAlive()).isFalse();
        assertThat(FileBrowserProbe.watchRefreshes(browser)).isEqualTo(refreshes + 1);
        assertThat(selected()).containsExactly("folder 40", "folder 41");
        assertThat(browser.fileTable().table.getTopIndex()).isEqualTo(top);

        // The same in the grid.
        browser.setView(FileView.LIST);
        idle();
        int[] scroll = browser.view().scroll();
        server.createDirectory(ME, "zz grid");
        waitFor(() -> names().contains("zz grid"));
        assertThat(selected()).containsExactly("folder 40", "folder 41");
        assertThat(browser.view().scroll()).containsExactly(scroll);
    }

    @Test
    void theWatchMovesWithTheBrowserAndStopsWithIt() throws IOException {
        waitFor(() -> ME.equals(FileBrowserProbe.watchedFolder(browser)));
        browser.navigate(ME.resolve("projects"));
        idle();
        waitFor(() -> ME.resolve("projects").equals(FileBrowserProbe.watchedFolder(browser)));
        assertThat(server.watchedCount()).isEqualTo(1);
        // Changes in the folder left are no longer listened to.
        int refreshes = FileBrowserProbe.watchRefreshes(browser);
        server.createDirectory(ME, "elsewhere");
        pause(FolderWatcher.DELAY * 2);
        assertThat(FileBrowserProbe.watchRefreshes(browser)).isEqualTo(refreshes);

        // Turned off and on.
        browser.setWatchFolders(false);
        assertThat(browser.isWatchFolders()).isFalse();
        assertThat(server.watchedCount()).isZero();
        server.createDirectory(ME.resolve("projects"), "unseen");
        pause(FolderWatcher.DELAY * 2);
        assertThat(names()).doesNotContain("unseen");
        browser.setWatchFolders(true);
        waitFor(() -> server.watchedCount() == 1);

        // Another filesystem: the old watch goes.
        MemoryFileSystem other = MemoryFileSystem.builder("other").folder("x").build();
        browser.setFileSystem(other);
        idle();
        waitFor(() -> other.watchedCount() == 1);
        assertThat(server.watchedCount()).isZero();

        shell.dispose();
        assertThat(other.watchedCount()).isZero();
    }

    @Test
    void aWatchedFolderThatGoesAwayOpensTheOneAbove() throws IOException {
        browser.navigate(ME.resolve("projects"));
        idle();
        waitFor(() -> ME.resolve("projects").equals(FileBrowserProbe.watchedFolder(browser)));
        server.delete(ME.resolve("projects"), true);
        waitFor(() -> ME.equals(browser.getCurrentPath()) && !browser.isLoading());
        assertThat(names()).doesNotContain("projects");
    }

    @Test
    void aRenameInPlaceIsNotInterrupted() throws IOException {
        waitFor(() -> ME.equals(FileBrowserProbe.watchedFolder(browser)));
        FileBrowserProbe.select(browser, "notes.txt");
        // On CI the test window may not be active: the field would lose the focus and commit.
        InlineEditor.finishOnFocusOut = false;
        try {
            browser.rename();
            assertThat(browser.view().editor()).isNotNull();
            renameWhileTheFolderChanges();
        } finally {
            InlineEditor.finishOnFocusOut = true;
        }
    }

    private void renameWhileTheFolderChanges() throws IOException {
        int refreshes = FileBrowserProbe.watchRefreshes(browser);
        server.createDirectory(ME, "while renaming");
        pause(FolderWatcher.DELAY * 3);
        assertThat(browser.view().editor()).isNotNull();
        assertThat(FileBrowserProbe.watchRefreshes(browser)).isEqualTo(refreshes);
        FileBrowserProbe.finishRename(browser, null);
        waitFor(() -> names().contains("while renaming"));
    }

    @Test
    void aLocalFolderIsWatchedToo() throws IOException {
        Files.writeString(temp.resolve("first.txt"), "x");
        RootedFileSystem local = new RootedFileSystem(LocalFileSystem.get(), LocalFileSystem.get().fromPath(temp), "Temp");
        browser.setFileSystem(local);
        idle();
        waitFor(() -> FilePath.TOP.equals(FileBrowserProbe.watchedFolder(browser)));
        Files.writeString(temp.resolve("second.txt"), "x");
        // macOS polls, every few seconds.
        waitFor(() -> names().contains("second.txt"), 30_000);
        assertThat(names()).containsExactly("first.txt", "second.txt");
    }

    // ------------------------------------------------------------------ trash

    /** The server, with a trash that keeps what goes into it, and refuses names starting with "no-trash". */
    private static final class WithTrash implements FileSystem {
        final MemoryFileSystem base;
        final List<FilePath> trashed = new ArrayList<>();

        WithTrash(MemoryFileSystem base) {
            this.base = base;
        }

        @Override
        public String displayName() {
            return base.displayName();
        }

        @Override
        public String topName() {
            return base.topName();
        }

        @Override
        public FilePath home() {
            return base.home();
        }

        @Override
        public Set<Capability> capabilities() {
            Set<Capability> set = EnumSet.copyOf(base.capabilities());
            set.add(Capability.TRASH);
            return set;
        }

        @Override
        public List<FileEntry> list(FilePath folder) throws IOException {
            return base.list(folder);
        }

        @Override
        public Optional<FileEntry> stat(FilePath path) throws IOException {
            return base.stat(path);
        }

        @Override
        public FilePath parse(String text, FilePath from) {
            return base.parse(text, from);
        }

        @Override
        public String format(FilePath path) {
            return base.format(path);
        }

        @Override
        public void delete(FilePath path, boolean recursive) throws IOException {
            base.delete(path, recursive);
        }

        @Override
        public void trash(FilePath path) throws IOException {
            if (path.name().startsWith("no-trash")) {
                throw new IOException("This drive has no Recycle Bin");
            }
            base.delete(path, true);
            synchronized (trashed) {
                trashed.add(path);
            }
        }

        @Override
        public Watch watch(FilePath folder, Consumer<FilePath> listener) throws IOException {
            return base.watch(folder, listener);
        }

        @Override
        public InputStream openRead(FilePath path) throws IOException {
            return base.openRead(path);
        }

        @Override
        public OutputStream openWrite(FilePath path) throws IOException {
            return base.openWrite(path);
        }
    }

    @Test
    void deleteGoesToTheTrashWithoutAskingAndShiftDeleteAsks() throws IOException {
        server.createDirectory(ME, "no-trash here");
        WithTrash fs = new WithTrash(server);
        browser.setFileSystem(fs);
        idle();
        List<Integer> asked = new ArrayList<>();
        browser.setDeleteConfirmation(entries -> {
            asked.add(entries.size());
            return true;
        });

        FileBrowserProbe.select(browser, "notes.txt", "photo.png");
        browser.delete();
        waitFor(() -> !names().contains("photo.png"));
        idle();
        assertThat(fs.trashed).containsExactly(ME.resolve("notes.txt"), ME.resolve("photo.png"));
        assertThat(asked).isEmpty();

        // Shift+Delete: for good, after asking.
        FileBrowserProbe.select(browser, "report2.pdf");
        Event shiftDelete = new Event();
        shiftDelete.keyCode = SWT.DEL;
        shiftDelete.stateMask = SWT.SHIFT;
        browser.fileTable().table.notifyListeners(SWT.KeyDown, shiftDelete);
        waitFor(() -> !names().contains("report2.pdf"));
        idle();
        assertThat(asked).containsExactly(1);
        assertThat(fs.trashed).hasSize(2);

        // What the trash can't take is offered to be deleted for good.
        FileBrowserProbe.select(browser, "no-trash here");
        browser.delete();
        waitFor(() -> !names().contains("no-trash here"));
        idle();
        assertThat(asked).containsExactly(1, 1);
        assertThat(fs.trashed).hasSize(2);
    }

    @Test
    void withoutATrashDeleteAsksFirst() {
        assertThat(server.has(Capability.TRASH)).isFalse();
        List<Integer> asked = new ArrayList<>();
        browser.setDeleteConfirmation(entries -> {
            asked.add(entries.size());
            return true;
        });
        FileBrowserProbe.select(browser, "notes.txt");
        browser.delete();
        waitFor(() -> !names().contains("notes.txt"));
        assertThat(asked).containsExactly(1);
    }

    @Test
    void localDeleteGoesToTheRecycleBin() throws IOException {
        // Really trashing litters the user's trash: on CI, or when asked for.
        org.junit.jupiter.api.Assumptions.assumeTrue(LocalFileSystem.get().has(Capability.TRASH)
                && (System.getenv("CI") != null || System.getenv("ZAPTHEMES_TRASH_TEST") != null));
        Path dir = Files.createDirectories(Path.of(System.getProperty("user.home"), ".zapthemes-trash-test"));
        try {
            Path file = Files.writeString(dir.resolve("zapthemes-browser-trash-" + System.nanoTime() + ".txt"), "x");
            browser.setFileSystem(new RootedFileSystem(LocalFileSystem.get(), LocalFileSystem.get().fromPath(dir), "Trash test"));
            idle();
            List<Integer> asked = new ArrayList<>();
            browser.setDeleteConfirmation(entries -> {
                asked.add(entries.size());
                return false;
            });
            FileBrowserProbe.select(browser, file.getFileName().toString());
            browser.delete();
            waitFor(() -> !Files.exists(file));
            assertThat(asked).isEmpty();
        } finally {
            browser.setFileSystem(server);
            idle();
            Files.deleteIfExists(dir);
        }
    }
}
