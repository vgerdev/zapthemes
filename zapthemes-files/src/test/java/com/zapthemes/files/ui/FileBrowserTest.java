package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.AccessDeniedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.files.Place;
import com.zapthemes.files.RootedFileSystem;
import com.zapthemes.files.ui.NavigationBar.Kind;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

class FileBrowserTest {

    private static Display display;
    private static Theme dark;
    private ThemeManager manager;
    private Shell shell;
    private FileBrowser browser;

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
    }

    @AfterEach
    void tearDown() {
        shell.dispose();
        manager.resetZoom();
    }

    private static MemoryFileSystem server() {
        return server(Duration.ZERO);
    }

    private static MemoryFileSystem server(Duration delay) {
        return MemoryFileSystem.builder("server")
                .folder("home/me/projects/zap")
                .folder("home/me/Documents")
                .folder("home/me/.config")
                .file("home/me/notes.txt", "Hello")
                .file("home/me/report10.pdf", 5000)
                .file("home/me/report2.pdf", 7000)
                .file("home/me/.profile", 10)
                .folder("etc")
                .home("home/me")
                .delay(delay)
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
    }

    private List<String> names() {
        return browser.getVisibleEntries().stream().map(FileEntry::name).toList();
    }

    private static final FilePath ME = FilePath.of("home", "me");

    @Test
    void listsFoldersFirstInNaturalOrder() {
        browser.setFileSystem(server());
        idle();

        assertThat(browser.getCurrentPath()).isEqualTo(ME);
        assertThat(names()).containsExactly("Documents", "projects", "notes.txt", "report2.pdf", "report10.pdf");
        assertThat(browser.crumbLabels()).containsExactly("server", "home", "me");
        assertThat(browser.statusBar().getText()).isEqualTo("5 items");
        assertThat(browser.fileTable().texts(browser.getVisibleEntries().get(4))).containsExactly("report10.pdf",
                browser.fileTable().texts(browser.getVisibleEntries().get(4))[1], "PDF document", "5 KB");
        assertThat(browser.fileTable().message()).isNull();
    }

    @Test
    void showsHiddenFilesOnRequest() {
        browser.setFileSystem(server());
        idle();
        browser.setShowHidden(true);

        assertThat(names()).contains(".config", ".profile");
        browser.setShowHidden(false);
        assertThat(names()).doesNotContain(".config", ".profile");
    }

    @Test
    void sortsByAColumnKeepingFoldersFirst() {
        browser.setFileSystem(server());
        idle();
        browser.setSort(FileOrder.Key.SIZE, false);

        assertThat(names()).containsExactly("projects", "Documents", "report2.pdf", "report10.pdf", "notes.txt");
        assertThat(browser.fileTable().table.getSortDirection()).isEqualTo(org.eclipse.swt.SWT.DOWN);
        assertThat(browser.fileTable().table.getSortColumn().getText()).isEqualTo("Size");
    }

    @Test
    void goesBackForwardAndUp() {
        browser.setFileSystem(server());
        idle();
        assertThat(browser.canGoBack()).isFalse();
        assertThat(browser.canGoUp()).isTrue();

        browser.navigate(ME.resolve("projects"));
        idle();
        assertThat(names()).containsExactly("zap");
        assertThat(browser.canGoBack()).isTrue();

        browser.back();
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME);
        assertThat(browser.getSelectedEntries()).extracting(FileEntry::name).containsExactly("projects");
        assertThat(browser.canGoForward()).isTrue();

        browser.forward();
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME.resolve("projects"));

        browser.up();
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME);
        assertThat(browser.getSelectedEntries()).extracting(FileEntry::name).containsExactly("projects");
        assertThat(browser.canGoForward()).isFalse();
    }

    @Test
    void breadcrumbChevronsDropDownSubfolders() {
        browser.setFileSystem(server());
        idle();
        browser.setPlaces(List.of(new Place("Projects", ME.resolve("projects"), "folder")));

        NavigationBar bar = browser.navigationBar();
        bar.activate(bar.part(Kind.CHEVRON, 2));
        waitFor(() -> bar.popup() != null);
        assertThat(bar.popup().labels()).containsExactly("Documents", "projects");
        bar.popup().pick(1);
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME.resolve("projects"));

        bar.activate(bar.part(Kind.CHEVRON, 0));
        waitFor(() -> bar.popup() != null);
        assertThat(bar.popup().labels()).containsExactly("etc", "home");
        bar.closePopup();

        bar.activate(bar.part(Kind.CHEVRON, -1));
        assertThat(bar.popup().labels()).containsExactly("Projects", "server");
        bar.closePopup();

        bar.activate(bar.part(Kind.CRUMB, 1));
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(FilePath.of("home"));
    }

    @Test
    void longPathsFoldIntoAnOverflowChevron() {
        MemoryFileSystem fs = MemoryFileSystem.builder("server").folder("a-long-folder-name/another-long-folder-name/yet-another-long-name/and-the-last-one-here")
                .build();
        browser.setFileSystem(fs, FilePath.of("a-long-folder-name", "another-long-folder-name", "yet-another-long-name", "and-the-last-one-here"));
        idle();
        shell.setSize(520, 400);
        DemoWait.settle(display);

        NavigationBar bar = browser.navigationBar();
        NavigationBar.Part overflow = bar.parts().stream().filter(p -> p.kind() == Kind.OVERFLOW).findFirst().orElseThrow();
        assertThat(bar.part(Kind.CRUMB, 0)).isNull();
        assertThat(bar.part(Kind.CRUMB, 4)).isNotNull();
        bar.activate(overflow);
        assertThat(bar.popup().labels().getLast()).isEqualTo("server");
        bar.closePopup();
    }

    @Test
    void editingThePathNavigates() {
        browser.setFileSystem(server());
        idle();

        browser.editPath();
        NavigationBar bar = browser.navigationBar();
        assertThat(bar.editor()).isNotNull();
        assertThat(bar.editor().getText()).isEqualTo("/home/me");
        assertThat(bar.editor().getSelectionText()).isEqualTo("/home/me");
        assertThat(bar.part(Kind.CLEAR, 0)).isNotNull();

        assertThat(browser.navigateText("projects/zap")).isTrue();
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME.resolve("projects").resolve("zap"));
        assertThat(bar.editor()).isNull();

        assertThat(browser.navigateText("/home/me/notes.txt")).isTrue();
        waitFor(() -> ME.equals(browser.getCurrentPath()));
        idle();
        assertThat(browser.getSelectedEntries()).extracting(FileEntry::name).containsExactly("notes.txt");
    }

    @Test
    void slowListingsShowLoadingAndStaleResultsAreDropped() {
        browser.setFileSystem(server(Duration.ofMillis(150)));
        assertThat(browser.isLoading()).isTrue();
        assertThat(browser.statusBar().getText()).isEqualTo("Loading…");
        assertThat(browser.fileTable().message()).isEqualTo("Loading…");
        idle();

        browser.navigate(FilePath.of("etc"));
        browser.navigate(ME.resolve("projects"));
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME.resolve("projects"));
        assertThat(names()).containsExactly("zap");
    }

    @Test
    void failuresShowInTheStatusBarAndKeepTheFolder() {
        MemoryFileSystem fs = server();
        browser.setFileSystem(fs);
        idle();
        fs.failOn(FilePath.of("etc"), new AccessDeniedException("/etc"));
        List<String> handled = new ArrayList<>();

        browser.navigate(FilePath.of("etc"));
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME);
        assertThat(browser.error()).isEqualTo("Can't open \"etc\": Access is denied.");
        assertThat(browser.statusBar().getText()).contains("Access is denied");

        browser.setErrorHandler((message, cause) -> handled.add(message));
        browser.navigate(FilePath.of("nowhere"));
        idle();
        assertThat(handled).containsExactly("Can't open \"nowhere\": It doesn't exist.");
    }

    @Test
    void aRootedFilesystemNeverGoesAboveItsTop() {
        browser.setFileSystem(new RootedFileSystem(server(), ME, "Me"));
        idle();

        assertThat(browser.getCurrentPath()).isEqualTo(FilePath.TOP);
        assertThat(browser.crumbLabels()).containsExactly("Me");
        assertThat(browser.canGoUp()).isFalse();
        assertThat(browser.navigateText("..")).isFalse();
        assertThat(browser.error()).contains("outside Me");
        assertThat(browser.navigateText("/projects")).isTrue();
        idle();
        assertThat(browser.crumbLabels()).containsExactly("Me", "projects");
        assertThat(browser.formattedPath()).isEqualTo("/projects");
    }

    @Test
    void theTreeListsPlacesAndFollowsTheFolder() {
        browser.setFileSystem(server());
        browser.setPlaces(List.of(new Place("Documents", ME.resolve("Documents"), "documents")));
        idle();

        assertThat(browser.navigationTree().rootLabels()).containsExactly("Documents", "---", "server");
        waitFor(() -> ME.equals(browser.navigationTree().selectedPath()));

        browser.navigate(ME.resolve("Documents"));
        idle();
        assertThat(browser.navigationTree().selectedPath()).isEqualTo(ME.resolve("Documents"));
        assertThat(browser.navigationTree().tree.getSelection()[0].getText()).isEqualTo("Documents");

        browser.navigate(ME.resolve("projects").resolve("zap"));
        idle();
        waitFor(() -> ME.resolve("projects").resolve("zap").equals(browser.navigationTree().selectedPath()));
    }

    @Test
    void zoomGrowsTheBarsAndIconsButNotTheWindow() {
        browser.setFileSystem(server());
        idle();
        Point size = shell.getSize();
        int bar = browser.navigationBar().getSize().y;
        int icon = browser.iconSize();
        int row = browser.fileTable().table.getItemHeight();

        manager.setZoom(1.5);
        DemoWait.settle(display);

        assertThat(shell.getSize()).isEqualTo(size);
        assertThat(browser.navigationBar().getSize().y).isGreaterThan(bar);
        assertThat(browser.iconSize()).isEqualTo(icon * 3 / 2);
        assertThat(browser.fileTable().table.getItemHeight()).isGreaterThan(row);
        assertThat(browser.icons().get(FileIcons.Icon.FOLDER, browser.iconSize()).getBounds().width).isEqualTo(browser.iconSize());
    }

    @Test
    void partsAreThemedTypes() {
        assertThat(browser.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("FileBrowser");
        assertThat(browser.navigationBar().getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("FileNavigationBar");
        assertThat(browser.statusBar().getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("FileStatusBar");
        assertThat(browser.fileTable().table.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Table");
        assertThat(browser.navigationTree().tree.getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Tree");
    }

    @Test
    void iconsHaveAntialiasedTransparency() {
        ImageData data = browser.icons().get(FileIcons.Icon.FOLDER, 32).getImageData();

        assertThat(data.width).isEqualTo(32);
        assertThat(data.getAlpha(0, 0)).isZero();
        assertThat(data.getAlpha(16, 20)).isEqualTo(255);
        boolean partial = false;
        for (int x = 0; x < 32 && !partial; x++) {
            for (int y = 0; y < 32 && !partial; y++) {
                int a = data.getAlpha(x, y);
                partial = a > 0 && a < 255;
            }
        }
        assertThat(partial).as("some pixels are partly transparent (antialiased edges)").isTrue();
    }

    /** Lets layouts and paints run. */
    private static final class DemoWait {
        static void settle(Display display) {
            for (int i = 0; i < 10; i++) {
                while (display.readAndDispatch()) {
                    // Run pending events.
                }
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
    }
}
