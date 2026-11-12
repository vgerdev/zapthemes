package com.zapthemes.demo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.LocalFileSystem;
import com.zapthemes.files.MemoryFileSystem;
import com.zapthemes.files.RootedFileSystem;
import com.zapthemes.files.ui.FileBrowser;
import com.zapthemes.files.ui.FileBrowserProbe;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.TabFolder;
import com.zapthemes.swt.widgets.TabItem;

/**
 * The Explorer-style file browser over three filesystems, one per tab: the local disks, a folder
 * of them as a rooted filesystem, and a slow in-memory "server" (a stand-in for SFTP).
 *
 * <p>{@code --smoke} builds a sample folder in the temporary directory, browses all three in every
 * bundled theme (breadcrumb, chevron drop-downs, history, the tree, sorting, the rooted top, a slow
 * listing and a failing one, zoom) and exits with the problems found. {@code --screenshots <dir>}
 * also saves each tab in every theme, and a chevron drop-down.
 */
public final class FileBrowserDemo {

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available;
    private final Path sample;
    private final MemoryFileSystem server;
    private Shell shell;
    private TabFolder tabs;
    private FileBrowser local;
    private FileBrowser rooted;
    private FileBrowser remote;

    private FileBrowserDemo(Display display, Path sample) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        this.available = DemoSupport.bundledThemes();
        this.sample = sample;
        this.server = sampleServer();
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        boolean smoke = options.contains("--smoke");
        int shots = options.indexOf("--screenshots");
        Display display = new Display();
        Path sample = createSample();
        FileBrowserDemo demo = new FileBrowserDemo(display, sample);
        demo.open(smoke);
        int exitCode = 0;
        if (smoke) {
            exitCode = demo.smokeTest();
            if (shots >= 0) {
                demo.screenshots(Path.of(options.get(shots + 1)));
            }
            demo.shell.dispose();
        }
        while (!demo.shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        display.dispose();
        deleteSample(sample);
        if (smoke) {
            System.exit(exitCode);
        }
    }

    // ------------------------------------------------------------------ the sample filesystems

    /** A folder like the one in Explorer's screenshots, with fixed dates so screenshots repeat. */
    private static Path createSample() {
        try {
            Path root = Files.createTempDirectory("zapthemes-files-demo").resolve("tiaa-work");
            Files.createDirectories(root.resolve("iad-email-images"));
            Files.createDirectories(root.resolve("uptime-kuma-helm/templates"));
            Files.createDirectories(root.resolve("reports/2025/q1"));
            Files.createDirectories(root.resolve("reports/2024"));
            file(root.resolve("uptime-kuma-helm/Chart.yaml"), 412, "2024-03-07T17:00");
            file(root.resolve("uptime-kuma-helm/templates/deployment.yaml"), 2_310, "2024-03-07T17:00");
            file(root.resolve("iad-email-images/banner.png"), 88_201, "2024-04-08T13:45");
            file(root.resolve("iad-email-images/logo.webp"), 9_870, "2024-04-08T13:45");
            file(root.resolve("Install Kubernetes Cluster Node with Cilium.docx"), 1_066_000, "2025-02-03T04:50");
            file(root.resolve("Kubernetes Install On RHEL 9.docx"), 51_400, "2025-02-06T11:40");
            file(root.resolve("Picking Between Calico and Cilium for Your Cluster.docx"), 232_000, "2025-02-06T11:43");
            file(root.resolve("uptime-kuma-helm.7z"), 235_100, "2024-03-07T17:01");
            file(root.resolve("notes.txt"), 1_200, "2025-01-15T09:12");
            file(root.resolve("cluster-diagram.png"), 145_000, "2025-01-20T16:30");
            file(root.resolve("install.sh"), 3_400, "2025-02-01T08:05");
            file(root.resolve("reports/2025/q1/summary.pdf"), 380_000, "2025-03-31T18:00");
            file(root.resolve("reports/2024/budget.xlsx"), 64_000, "2024-12-20T10:00");
            Path hidden = root.resolve(".cache");
            Files.createDirectories(hidden);
            if (System.getProperty("os.name").startsWith("Windows")) {
                Files.setAttribute(hidden, "dos:hidden", true);
            }
            for (Path dir : List.of(root.resolve("iad-email-images"), root.resolve("uptime-kuma-helm"), root.resolve("reports"), root)) {
                Files.setLastModifiedTime(dir, time("2024-04-08T13:45"));
            }
            return root;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void file(Path path, int size, String modified) throws IOException {
        Files.write(path, new byte[size]);
        Files.setLastModifiedTime(path, time(modified));
    }

    private static FileTime time(String local) {
        return FileTime.from(LocalDateTime.parse(local).atZone(ZoneId.systemDefault()).toInstant());
    }

    private static void deleteSample(Path sample) {
        try {
            LocalFileSystem fs = LocalFileSystem.get();
            fs.delete(fs.fromPath(sample.getParent()), true);
        } catch (IOException e) {
            // Left in the temporary directory.
        }
    }

    /** A slow server with POSIX permissions, as zapscp's SFTP sessions list. */
    private static MemoryFileSystem sampleServer() {
        Instant then = LocalDateTime.parse("2025-02-06T11:40").atZone(ZoneId.systemDefault()).toInstant();
        return MemoryFileSystem.builder("deploy@build-01")
                .folder("home/deploy").modified(then).posix("rwxr-x---", "deploy", "deploy")
                .folder("home/deploy/releases").modified(then).posix("rwxr-xr-x", "deploy", "deploy")
                .folder("home/deploy/releases/2025.02").modified(then).posix("rwxr-xr-x", "deploy", "deploy")
                .folder("home/deploy/logs").modified(then).posix("rwxr-x---", "deploy", "adm")
                .folder("home/deploy/secrets").modified(then).posix("rwx------", "root", "root")
                .file("home/deploy/app.jar", 48_200_000).modified(then).posix("rw-r--r--", "deploy", "deploy")
                .file("home/deploy/application.yml", 2_048).modified(then).posix("rw-r-----", "deploy", "deploy")
                .file("home/deploy/start.sh", 640).modified(then).posix("rwxr-xr-x", "deploy", "deploy")
                .file("home/deploy/.bashrc", 3_771).modified(then).posix("rw-r--r--", "deploy", "deploy")
                .file("home/deploy/logs/app.log", 12_900_000).modified(then).posix("rw-r-----", "deploy", "adm")
                .folder("etc").modified(then).posix("rwxr-xr-x", "root", "root")
                .folder("var/www").modified(then).posix("rwxr-xr-x", "root", "root")
                .home("home/deploy")
                .delay(Duration.ofMillis(250))
                .build();
    }

    // ------------------------------------------------------------------ the window

    private void open(boolean smoke) {
        shell = new Shell(display);
        shell.setText("ZapThemes file browser");
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 8;
        layout.marginHeight = 8;
        shell.setLayout(layout);
        new Label(shell, SWT.NONE).setText("Theme:");
        Combo theme = new Combo(shell, SWT.READ_ONLY);
        available.forEach(t -> theme.add(t.name()));
        theme.select(0);
        theme.addListener(SWT.Selection, e -> themes.setTheme(available.get(theme.getSelectionIndex())));
        themes.addThemeChangeListener(theme, (previous, current) -> {
            for (int i = 0; i < available.size(); i++) {
                if (available.get(i).id().equals(current.id())) {
                    theme.select(i);
                }
            }
        });
        Label hint = new Label(shell, SWT.NONE);
        hint.setText("Ctrl+Plus and Ctrl+Minus zoom. The Server tab lists slowly, like a remote session.");
        hint.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        tabs = new TabFolder(shell, SWT.NONE);
        tabs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 1));
        LocalFileSystem disks = LocalFileSystem.get();
        local = tab("This PC");
        local.setFileSystem(disks, smoke ? disks.fromPath(sample) : disks.home());
        rooted = tab("Rooted folder");
        rooted.setFileSystem(new RootedFileSystem(disks, disks.fromPath(sample), "Projects"));
        remote = tab("Server (slow)");
        remote.setFileSystem(server);
        local.addOpenListener(entry -> Program.launch(disks.format(entry.path())));
        rooted.addOpenListener(entry -> Program.launch(disks.format(((RootedFileSystem) rooted.getFileSystem()).toBase(entry.path()))));
        shell.setSize(1180, 760);
        shell.open();
    }

    private FileBrowser tab(String title) {
        TabItem item = new TabItem(tabs, SWT.NONE);
        item.setText(title);
        Composite page = new Composite(tabs, SWT.NONE);
        page.setLayout(new FillLayout());
        FileBrowser browser = new FileBrowser(page, SWT.NONE);
        item.setControl(page);
        return browser;
    }

    // ------------------------------------------------------------------ smoke test and screenshots

    private boolean waitFor(BooleanSupplier condition) {
        long end = System.currentTimeMillis() + 30_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > end) {
                return false;
            }
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
        return true;
    }

    private boolean idle(FileBrowser browser) {
        return waitFor(() -> !browser.isLoading() && FileBrowserProbe.pendingCalls(browser) == 0);
    }

    private static List<String> names(FileBrowser browser) {
        return browser.getVisibleEntries().stream().map(FileEntry::name).toList();
    }

    private void select(FileBrowser browser) {
        for (TabItem item : tabsOf()) {
            if (item.getControl() == browser.getParent()) {
                tabs.setSelection(item);
            }
        }
        DemoSupport.settle(display);
    }

    private List<TabItem> tabsOf() {
        List<TabItem> list = new ArrayList<>();
        for (org.eclipse.swt.widgets.TabItem item : tabs.getItems()) {
            list.add((TabItem) item);
        }
        return list;
    }

    private int smokeTest() {
        List<String> problems = new ArrayList<>();
        FileBrowserProbe.setPopupsCloseOnDeactivate(false);
        LocalFileSystem disks = LocalFileSystem.get();
        FilePath samplePath = disks.fromPath(sample);
        int checked = 0;

        // The local disks: the sample folder, its breadcrumb and its order.
        if (!idle(local)) {
            problems.add("the local browser did not finish listing: loading " + local.isLoading() + ", pending " + FileBrowserProbe.pendingCalls(local) + ", status " + FileBrowserProbe.status(local));
        }
        expect(problems, samplePath.equals(local.getCurrentPath()), "local: shows " + local.getCurrentPath());
        List<String> shown = names(local);
        expect(problems, shown.equals(List.of("iad-email-images", "reports", "uptime-kuma-helm", "cluster-diagram.png",
                "Install Kubernetes Cluster Node with Cilium.docx", "install.sh", "Kubernetes Install On RHEL 9.docx", "notes.txt",
                "Picking Between Calico and Cilium for Your Cluster.docx", "uptime-kuma-helm.7z")), "local: listed " + shown);
        List<String> crumbs = FileBrowserProbe.crumbs(local);
        expect(problems, crumbs.getLast().equals("tiaa-work") && crumbs.size() == samplePath.depth() + 1, "local: breadcrumb " + crumbs);
        if (System.getProperty("os.name").startsWith("Windows")) {
            expect(problems, crumbs.getFirst().equals("This PC") && crumbs.get(1).matches(".*\\([A-Z]:\\)"), "local: drive crumb " + crumbs);
        }
        expect(problems, FileBrowserProbe.status(local).equals("10 items"), "local: status " + FileBrowserProbe.status(local));
        String[] row = FileBrowserProbe.row(local, "Install Kubernetes Cluster Node with Cilium.docx");
        expect(problems, row[2].equals("Word document") && row[3].replaceAll("\\D", "").equals("1042"), "local: row " + String.join(" | ", row));
        expect(problems, samplePath.equals(FileBrowserProbe.treeSelection(local)), "local: the tree shows " + FileBrowserProbe.treeSelection(local));

        // The trailing chevron drops down the subfolders; picking one opens it.
        FileBrowserProbe.openChevron(local, samplePath.depth());
        waitFor(() -> FileBrowserProbe.popupLabels(local) != null);
        List<String> popup = FileBrowserProbe.popupLabels(local);
        expect(problems, List.of("iad-email-images", "reports", "uptime-kuma-helm").equals(popup), "local: chevron lists " + popup);
        FileBrowserProbe.pickPopup(local, 1);
        idle(local);
        expect(problems, samplePath.resolve("reports").equals(local.getCurrentPath()), "local: the drop-down opened " + local.getCurrentPath());
        local.back();
        idle(local);
        expect(problems, samplePath.equals(local.getCurrentPath()) && local.canGoForward(), "local: back to " + local.getCurrentPath());
        expect(problems, local.getSelectedEntries().stream().map(FileEntry::name).toList().equals(List.of("reports")),
                "local: back selects the folder left");
        local.forward();
        idle(local);
        local.up();
        idle(local);
        expect(problems, samplePath.equals(local.getCurrentPath()), "local: up to " + local.getCurrentPath());
        // Sorting by size, largest first: folders stay first.
        local.setSort(FileOrder.Key.SIZE, false);
        expect(problems, names(local).get(3).equals("Install Kubernetes Cluster Node with Cilium.docx"), "local: by size " + names(local));
        local.setSort(FileOrder.Key.NAME, true);
        local.setShowHidden(true);
        expect(problems, names(local).contains(".cache"), "local: hidden files shown");
        local.setShowHidden(false);
        // A typed path to a file opens its folder with the file selected.
        local.navigateText(disks.format(samplePath.resolve("reports").resolve("2024").resolve("budget.xlsx")));
        waitFor(() -> samplePath.resolve("reports").resolve("2024").equals(local.getCurrentPath()));
        idle(local);
        expect(problems, local.getSelectedEntries().stream().map(FileEntry::name).toList().equals(List.of("budget.xlsx")),
                "local: a typed file is selected");
        local.navigate(samplePath);
        idle(local);

        // The rooted folder: its top is the sample, and nothing above it opens.
        select(rooted);
        idle(rooted);
        expect(problems, FilePath.TOP.equals(rooted.getCurrentPath()) && !rooted.canGoUp(), "rooted: starts at its top");
        expect(problems, FileBrowserProbe.crumbs(rooted).equals(List.of("Projects")), "rooted: crumbs " + FileBrowserProbe.crumbs(rooted));
        expect(problems, !rooted.navigateText(".."), "rooted: \"..\" is refused");
        expect(problems, rooted.navigateText(disks.separator() + "reports" + disks.separator() + "2025"), "rooted: a path from the top");
        idle(rooted);
        expect(problems, FilePath.of("reports", "2025").equals(rooted.getCurrentPath()), "rooted: shows " + rooted.getCurrentPath());
        expect(problems, FileBrowserProbe.crumbs(rooted).equals(List.of("Projects", "reports", "2025")),
                "rooted: crumbs " + FileBrowserProbe.crumbs(rooted));
        rooted.navigate(FilePath.TOP);
        idle(rooted);

        // The slow server: "Loading…" while it lists, permissions and owners, a failure in the status bar.
        select(remote);
        idle(remote);
        expect(problems, names(remote).equals(List.of("logs", "releases", "secrets", "app.jar", "application.yml", "start.sh")),
                "remote: listed " + names(remote));
        expect(problems, FileBrowserProbe.columns(remote).containsAll(List.of("Permissions", "Owner", "Group")),
                "remote: columns " + FileBrowserProbe.columns(remote));
        remote.navigate(FilePath.of("home", "deploy", "releases"));
        expect(problems, remote.isLoading() && FileBrowserProbe.status(remote).equals("Loading…"), "remote: loading shows");
        idle(remote);
        expect(problems, names(remote).equals(List.of("2025.02")), "remote: releases " + names(remote));
        server.failOn(FilePath.of("home", "deploy", "secrets"), new AccessDeniedException("/home/deploy/secrets"));
        remote.navigate(FilePath.of("home", "deploy", "secrets"));
        idle(remote);
        expect(problems, FileBrowserProbe.status(remote).contains("Access is denied"), "remote: failure shows " + FileBrowserProbe.status(remote));
        expect(problems, FilePath.of("home", "deploy", "releases").equals(remote.getCurrentPath()), "remote: stays after a failure");
        server.clearFailures();
        remote.navigate(FilePath.of("home", "deploy"));
        idle(remote);

        // Step 2: commands, the context menus, new folder, rename, copy and paste, delete, on the rooted folder.
        select(rooted);
        rooted.setDeleteConfirmation(entries -> true);
        rooted.navigate(FilePath.TOP);
        idle(rooted);
        List<String> background = FileBrowserProbe.contextMenu(rooted, false);
        expect(problems, background.equals(List.of("View", "Sort by", "Group by", "Refresh", "-", "Paste", "-", "New", "-", "Properties", "-",
                "Show more options")), "rooted: background menu " + background);
        List<String> classic = FileBrowserProbe.contextMenu(rooted, false, true);
        expect(problems, classic.contains("Copy folder path") && classic.contains("Hidden items"), "rooted: classic menu " + classic);
        int renames = FileBrowserProbe.renamesStarted(rooted);
        rooted.newFolder();
        waitFor(() -> FileBrowserProbe.renamesStarted(rooted) > renames);
        idle(rooted);
        expect(problems, names(rooted).contains("New folder"), "rooted: new folder " + names(rooted));
        FileBrowserProbe.finishRename(rooted, "Made by the smoke test");
        waitFor(() -> names(rooted).contains("Made by the smoke test"));
        idle(rooted);
        expect(problems, Files.isDirectory(sample.resolve("Made by the smoke test")), "rooted: the renamed folder is on disk");
        FileBrowserProbe.select(rooted, "notes.txt");
        List<String> items = FileBrowserProbe.contextMenu(rooted, true);
        expect(problems, items.contains("Rename") && items.contains("Copy as path"), "rooted: item menu " + items);
        rooted.copy();
        rooted.paste();
        waitFor(() -> names(rooted).contains("notes - Copy.txt"));
        idle(rooted);
        expect(problems, Files.exists(sample.resolve("notes - Copy.txt")), "rooted: the copy is on disk");
        FileBrowserProbe.select(rooted, "notes - Copy.txt", "Made by the smoke test");
        rooted.delete();
        waitFor(() -> !names(rooted).contains("notes - Copy.txt"));
        idle(rooted);
        expect(problems, !Files.exists(sample.resolve("notes - Copy.txt")) && !Files.exists(sample.resolve("Made by the smoke test")),
                "rooted: deleted from disk");
        // Copied from the local disks, pasted on the slow server: an upload through the FileSystem API.
        FileBrowserProbe.select(rooted, "install.sh");
        rooted.copy();
        select(remote);
        remote.paste();
        waitFor(() -> names(remote).contains("install.sh"));
        idle(remote);
        expect(problems, names(remote).contains("install.sh"), "remote: pasted " + names(remote));
        select(local);

        // Every theme: each part is themed; zoom grows the bars, not the window.
        for (Theme theme : available) {
            themes.setTheme(theme);
            DemoSupport.settle(display);
            checked += check(shell, problems);
        }
        themes.setTheme(available.getFirst());
        select(local);
        Point size = shell.getSize();
        int barHeight = FileBrowserProbe.navigationBarHeight(local);
        themes.setZoom(1.5);
        DemoSupport.settle(display);
        expect(problems, shell.getSize().equals(size), "zoom resized the window");
        expect(problems, FileBrowserProbe.navigationBarHeight(local) > barHeight, "zoom: the navigation bar did not grow");
        themes.resetZoom();
        DemoSupport.settle(display);

        problems.forEach(p -> System.err.println("PROBLEM: " + p));
        System.out.println("File browser smoke test: " + checked + " widgets checked, " + problems.size() + " problems");
        return problems.isEmpty() ? 0 : 1;
    }

    private static void expect(List<String> problems, boolean ok, String what) {
        if (!ok) {
            problems.add(what);
        }
    }

    private int check(Control control, List<String> problems) {
        int checked = 1;
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null && !(control instanceof org.eclipse.swt.widgets.Sash)) {
            problems.add(control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                checked += check(child, problems);
            }
        }
        return checked;
    }

    private void screenshots(Path dir) {
        for (Theme theme : available) {
            themes.setTheme(theme);
            for (FileBrowser browser : List.of(local, rooted, remote)) {
                select(browser);
                idle(browser);
                DemoSupport.settle(display);
                String name = browser == local ? "local" : browser == rooted ? "rooted" : "remote";
                DemoSupport.screenshot(shell, dir, "filebrowser-" + name + "-" + theme.id());
            }
        }
        // Renaming in place.
        themes.setTheme(available.getFirst());
        select(rooted);
        FileBrowserProbe.select(rooted, "Kubernetes Install On RHEL 9.docx");
        rooted.rename();
        DemoSupport.settle(display);
        DemoSupport.screenshot(shell, dir, "filebrowser-rename");
        FileBrowserProbe.finishRename(rooted, null);
        DemoSupport.settle(display);

        // A chevron's drop-down, with the window behind it.
        themes.setTheme(available.getFirst());
        select(local);
        FilePath at = local.getCurrentPath();
        FileBrowserProbe.openChevron(local, at.depth());
        waitFor(() -> FileBrowserProbe.popupLabels(local) != null);
        DemoSupport.settle(display);
        org.eclipse.swt.widgets.Shell popup = FileBrowserProbe.popupShell(local);
        if (popup != null) {
            DemoSupport.screenshot((Control) popup, dir, "filebrowser-chevron-popup");
            Rectangle bounds = popup.getBounds();
            System.out.println("Popup at " + bounds);
            FileBrowserProbe.closePopup(local);
        }
        // At 150 %.
        themes.setZoom(1.5);
        DemoSupport.settle(display);
        DemoSupport.screenshot(shell, dir, "filebrowser-local-zoom150");
        themes.resetZoom();
        DemoSupport.settle(display);
    }
}
