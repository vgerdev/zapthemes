package com.zapthemes.files.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.Location;
import com.zapthemes.files.LocationHistory;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.files.RootedFileSystem;
import com.zapthemes.files.ui.NavigationBar.Kind;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/** Step 3: the address field's suggestions and the recent locations, across filesystems. */
class AddressBarTest {

    private static final FilePath ME = FilePath.of("home", "me");

    private static Display display;
    private ThemeManager manager;
    private Shell shell;
    private FileBrowser browser;

    @BeforeAll
    static void setUpDisplay() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        ThemeManager.get(display).setTheme(registry.resolve("zap-light"));
        FolderPopup.closeOnDeactivate = false;
        AddressField.stopOnFocusOut = false;
    }

    @AfterAll
    static void restore() {
        AddressField.stopOnFocusOut = true;
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
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
        return MemoryFileSystem.builder("server")
                .folder("home/me/projects/zap")
                .folder("home/me/Documents")
                .folder("home/me/Downloads")
                .folder("home/me/.config")
                .file("home/me/notes.txt", "Hello")
                .folder("etc")
                .home("home/me")
                .build();
    }

    private static MemoryFileSystem laptop() {
        return MemoryFileSystem.builder("laptop").folder("work/reports").home("work").build();
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

    private void idle() {
        idle(browser);
    }

    private AddressField field() {
        return browser.navigationBar().addressField();
    }

    private List<String> suggestions() {
        FolderPopup popup = field() == null ? null : field().suggestions();
        return popup == null ? List.of() : popup.labels();
    }

    private void type(String text) {
        field().text.setText(text);
        idle();
    }

    private static List<String> texts(FileBrowser b) {
        return b.getLocationHistory().entries().stream().map(l -> l.text(b.getFileSystem())).toList();
    }

    @Test
    void typedLocationsGoIntoTheRecentOnes() {
        browser.setFileSystem(server());
        idle();
        assertThat(browser.getLocationHistory().entries()).isEmpty();

        assertThat(browser.navigateText("/etc")).isTrue();
        idle();
        assertThat(browser.navigateText("~/notes.txt")).isTrue(); // A file: its folder is the location.
        waitFor(() -> ME.equals(browser.getCurrentPath()));
        idle();
        assertThat(browser.navigateText("/etc")).isTrue();
        idle();
        assertThat(texts(browser)).containsExactly("/etc", "/home/me");
        // Clicks don't add: only what is typed or picked.
        browser.navigate(ME.resolve("projects"));
        idle();
        assertThat(texts(browser)).containsExactly("/etc", "/home/me");
        assertThat(browser.getCurrentLocation()).isEqualTo(Location.of(browser.getFileSystem(), ME.resolve("projects")));
    }

    @Test
    void theDropDownButtonListsThemAndPicksOne() {
        browser.setFileSystem(server());
        idle();
        browser.navigateText("/etc");
        idle();
        browser.navigateText("/home/me/Documents");
        idle();
        NavigationBar bar = browser.navigationBar();
        assertThat(bar.part(Kind.HISTORY, 0)).isNotNull();

        bar.activate(bar.part(Kind.HISTORY, 0));
        assertThat(field()).isNotNull();
        assertThat(field().showingAll()).isTrue();
        assertThat(suggestions()).containsExactly("/home/me/Documents", "/etc");
        assertThat(bar.part(Kind.CLEAR, 0)).isNotNull();
        assertThat(bar.part(Kind.HISTORY, 0)).isNotNull();
        // Again: it closes, the field stays.
        bar.activate(bar.part(Kind.HISTORY, 0));
        assertThat(field().suggestions()).isNull();

        browser.showRecentLocations(); // F4
        field().move(1);
        field().move(1);
        assertThat(field().text.getText()).isEqualTo("/etc");
        field().commit();
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(FilePath.of("etc"));
        assertThat(field()).isNull();
        assertThat(texts(browser)).containsExactly("/etc", "/home/me/Documents");
    }

    @Test
    void typingSuggestsRecentLocationsThenFolders() {
        browser.setFileSystem(server());
        idle();
        browser.navigateText("/home/me/projects/zap");
        idle();
        browser.editPath();

        type("/home/me/");
        waitFor(() -> suggestions().size() > 1);
        // The recent one first, then the subfolders (hidden ones left out), in name order.
        assertThat(suggestions()).containsExactly("/home/me/projects/zap", "/home/me/Documents", "/home/me/Downloads", "/home/me/projects");
        type("/home/me/do");
        assertThat(suggestions()).containsExactly("/home/me/Documents", "/home/me/Downloads");

        // Down puts the selection in the field, Up past the first gives back what was typed.
        Event down = new Event();
        down.keyCode = SWT.ARROW_DOWN;
        field().text.notifyListeners(SWT.KeyDown, down);
        assertThat(field().text.getText()).isEqualTo("/home/me/Documents");
        field().move(-1);
        assertThat(field().text.getText()).isEqualTo("/home/me/do");

        // Tab completes with the first and suggests from there.
        assertThat(field().complete()).isTrue();
        assertThat(field().text.getText()).isEqualTo("/home/me/Documents");
        type("/home/me/Downl");
        field().move(1);
        field().commit();
        idle();
        assertThat(browser.getCurrentPath()).isEqualTo(ME.resolve("Downloads"));
        assertThat(texts(browser)).startsWith("/home/me/Downloads");

        // A name alone completes in the current folder; nothing matches, nothing drops down.
        browser.navigate(ME);
        idle();
        browser.editPath();
        type("pro");
        waitFor(() -> !suggestions().isEmpty());
        assertThat(suggestions()).containsExactly("projects");
        type("/nowhere/x");
        assertThat(suggestions()).isEmpty();
    }

    @Test
    void aRootedBrowserSuggestsNothingAboveItsTop() {
        browser.setFileSystem(new RootedFileSystem(server(), ME, "Me"));
        idle();
        browser.editPath();
        type("/");
        waitFor(() -> !suggestions().isEmpty());
        assertThat(suggestions()).containsExactly("/Documents", "/Downloads", "/projects");
        type("../");
        assertThat(suggestions()).isEmpty();
    }

    @Test
    void sharedRecentLocationsCrossFilesystems() {
        MemoryFileSystem server = server();
        MemoryFileSystem laptop = laptop();
        LocationHistory shared = new LocationHistory();
        FileBrowser other = new FileBrowser(shell, 0);
        browser.setLocationHistory(shared);
        other.setLocationHistory(shared);
        browser.setFileSystem(laptop);
        other.setFileSystem(server);
        idle(browser);
        idle(other);
        other.navigateText("/etc");
        idle(other);
        shared.add(new Location(server, ME.resolve("projects"), "Projects", "documents"));

        // The laptop's browser lists the server's folders, named after the server.
        browser.showRecentLocations();
        assertThat(suggestions()).containsExactly("Projects", "/etc on server");

        // Typing a label goes there; the browser switches filesystems, and Back comes home.
        browser.navigationBar().stopEditing();
        assertThat(browser.navigateText("projects")).isTrue();
        idle();
        assertThat(browser.getFileSystem()).isSameAs(server);
        assertThat(browser.getCurrentPath()).isEqualTo(ME.resolve("projects"));
        assertThat(browser.getVisibleEntries()).extracting(FileEntry::name).containsExactly("zap");
        assertThat(browser.canGoBack()).isTrue();
        browser.back();
        idle();
        assertThat(browser.getFileSystem()).isSameAs(laptop);
        assertThat(browser.getCurrentPath()).isEqualTo(FilePath.of("work"));
        browser.forward();
        idle();
        assertThat(browser.getFileSystem()).isSameAs(server);
        browser.back();
        idle();

        // An application can open another filesystem's locations itself (in that one's pane).
        List<Location> handled = new ArrayList<>();
        browser.setLocationHandler(location -> handled.add(location));
        browser.showRecentLocations();
        field().move(2);
        assertThat(field().text.getText()).isEqualTo("/etc on server");
        field().commit();
        idle();
        assertThat(handled).extracting(Location::path).containsExactly(FilePath.of("etc"));
        assertThat(browser.getFileSystem()).isSameAs(laptop);
    }

    @Test
    void escapeClosesTheDropDownThenStopsEditing() {
        browser.setFileSystem(server());
        idle();
        browser.navigateText("/etc");
        idle();
        browser.showRecentLocations();
        assertThat(field().suggestions()).isNotNull();
        Event escape = new Event();
        escape.detail = SWT.TRAVERSE_ESCAPE;
        escape.doit = true;
        field().text.notifyListeners(SWT.Traverse, escape);
        assertThat(field()).isNotNull();
        assertThat(field().suggestions()).isNull();
        escape.doit = true;
        escape.detail = SWT.TRAVERSE_ESCAPE;
        field().text.notifyListeners(SWT.Traverse, escape);
        assertThat(field()).isNull();
    }
}
