package com.zapthemes.files.ui;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ZoomListener;
import com.zapthemes.files.Capability;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.FileTypes;
import com.zapthemes.files.LocalFileSystem;
import com.zapthemes.files.Place;
import com.zapthemes.files.RootedFileSystem;
import com.zapthemes.files.ui.FileIcons.Icon;
import com.zapthemes.swt.custom.SashForm;

/**
 * A file browser modeled on the Windows 11 File Explorer, over any {@link FileSystem}: the local
 * disks, one folder of them ({@link RootedFileSystem}), or a virtual one such as a remote server.
 *
 * <p>From the top: the navigation row (Back, Forward, Up, Refresh and the breadcrumb address bar,
 * whose chevrons drop down the subfolders of each level and which turns into a text field when its
 * empty space is clicked), then the navigation pane (places and the folder tree) beside the Details
 * view, and the status bar.
 *
 * <p>Every filesystem call runs in the background; the browser stays responsive while a slow one
 * lists ({@link Capability#SLOW} shows "Loading…" meanwhile), drops results that are no longer
 * wanted, and reports failures to its {@link ErrorHandler} (by default, in the status bar).
 *
 * <p>Keys: Alt+Left and Alt+Right (or Backspace in the file view) go back and forward, Alt+Up goes
 * up, F5 refreshes, Enter opens, Alt+D, Ctrl+L and F4 edit the path.
 *
 * <p>Theme types: {@code FileBrowser} (the frame), {@code FileNavigationBar}, {@code FileStatusBar};
 * the panes are a {@code Tree} and a {@code Table}.
 */
public class FileBrowser extends Composite {

    /** Receives failures: listing a folder, parsing a typed path. */
    @FunctionalInterface
    public interface ErrorHandler {
        /** {@code message} says what failed, for people ("Can't open "x": Access is denied."). */
        void error(String message, Throwable cause);
    }

    private enum Move {
        NEW, BACK, FORWARD, REFRESH
    }

    private record Listing(FilePath path, List<FileEntry> entries, List<String> labels) {
    }

    private static final int ICON = 16;

    private final FileLoader loader = new FileLoader(this);
    private final NavigationBar navigationBar;
    private final SashForm sash;
    private final NavigationTree navigationTree;
    private final FileTable fileTable;
    private final StatusBar statusBar;
    private final List<Consumer<FilePath>> navigationListeners = new ArrayList<>();
    private final List<Consumer<FileEntry>> openListeners = new ArrayList<>();
    private final List<Consumer<List<FileEntry>>> selectionListeners = new ArrayList<>();
    private final Deque<FilePath> backStack = new ArrayDeque<>();
    private final Deque<FilePath> forwardStack = new ArrayDeque<>();
    private final ZoomListener zoomListener = (previous, current) -> zoomChanged();
    private final AtomicInteger generation = new AtomicInteger();

    private FileIcons icons;
    private FileSystem fileSystem;
    private FilePath current;
    private List<String> labels = List.of();
    private List<FileEntry> entries = List.of();
    private List<Place> places;
    private List<Place> defaultPlaces = List.of();
    private boolean showHidden;
    private boolean loading;
    private Thread pending;
    private String error;
    private ErrorHandler errorHandler;

    /** A browser with nothing to show yet; give it a filesystem with {@link #setFileSystem}. */
    @SuppressWarnings("this-escape")
    public FileBrowser(Composite parent, int style) {
        super(parent, style);
        icons = new FileIcons(getDisplay());
        // The browser draws its icons at the zoom itself, sharper than scaled copies.
        setData(ThemeKeys.ZOOM_IMAGES, Boolean.FALSE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 0;
        setLayout(layout);
        navigationBar = new NavigationBar(this);
        navigationBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sash = new SashForm(this, SWT.HORIZONTAL | SWT.SMOOTH);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        navigationTree = new NavigationTree(this, sash);
        fileTable = new FileTable(this, sash);
        sash.setWeights(24, 76);
        statusBar = new StatusBar(this);
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        fileTable.table.addListener(SWT.Selection, e -> selectionChanged());
        fileTable.table.addListener(SWT.DefaultSelection, e -> openSelection());
        for (Control control : new Control[] { navigationBar, navigationTree.tree, fileTable.table, statusBar }) {
            control.addListener(SWT.KeyDown, this::keyDown);
        }
        ThemeManager.get(getDisplay()).addZoomListener(zoomListener);
        ThemeManager.get(getDisplay()).addThemeChangeListener(this, (previous, next) -> fileTable.restripe());
        addListener(SWT.Dispose, e -> {
            ThemeManager.get(getDisplay()).removeZoomListener(zoomListener);
            loader.close();
            icons.dispose();
        });
        ThemeManager.applyOnCreate(this, "FileBrowser");
        updateStatus();
    }

    // ------------------------------------------------------------------ public API

    /** Shows {@code fs}, starting at its home. */
    public void setFileSystem(FileSystem fs) {
        setFileSystem(fs, fs.home());
    }

    /** Shows {@code fs}, starting at {@code path}. History is cleared. */
    public void setFileSystem(FileSystem fs, FilePath path) {
        checkWidget();
        Objects.requireNonNull(fs, "fs");
        fileSystem = fs;
        current = null;
        labels = List.of();
        entries = List.of();
        backStack.clear();
        forwardStack.clear();
        defaultPlaces = List.of();
        fileTable.setColumns(fs.capabilities());
        fileTable.setRows(List.of(), List.of());
        navigationTree.rebuild();
        if (places == null && fs instanceof LocalFileSystem local) {
            loader.run(local::standardPlaces, (found, failure) -> {
                if (fileSystem == fs && found != null && places == null) {
                    defaultPlaces = found;
                    navigationTree.rebuild();
                    if (current != null) {
                        navigationTree.reveal(current);
                    }
                }
            });
        }
        navigate(path, null, Move.NEW);
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    /**
     * The places the navigation pane lists above the folder tree. By default, for the local
     * filesystem, the standard folders (Desktop, Downloads, Documents, ...); none for others.
     * {@code null} goes back to the default.
     */
    public void setPlaces(List<Place> places) {
        checkWidget();
        this.places = places == null ? null : List.copyOf(places);
        navigationTree.rebuild();
        if (current != null) {
            navigationTree.reveal(current);
        }
    }

    public List<Place> getPlaces() {
        return places != null ? places : defaultPlaces;
    }

    /** Opens the folder {@code path}. */
    public void navigate(FilePath path) {
        navigate(path, null, Move.NEW);
    }

    /** Opens the folder {@code path} with the entry {@code selectName} selected. */
    public void navigate(FilePath path, String selectName) {
        navigate(path, selectName, Move.NEW);
    }

    /**
     * Opens what a user typed: a folder, or a file (its folder opens with it selected).
     *
     * @return false if it isn't a path of the filesystem (the error handler is told)
     */
    public boolean navigateText(String text) {
        checkWidget();
        if (fileSystem == null) {
            return false;
        }
        FilePath path;
        try {
            path = fileSystem.parse(text, current);
        } catch (IllegalArgumentException e) {
            reportError(e.getMessage() != null ? e.getMessage() : "\"" + text + "\" is not a path", e);
            return false;
        }
        FileSystem fs = fileSystem;
        loader.run(() -> fs.stat(path), (stat, failure) -> {
            if (fs != fileSystem) {
                return;
            }
            if (stat != null && stat.isPresent() && !stat.get().isContainer() && path.parent() != null) {
                navigate(path.parent(), path.name());
            } else {
                navigate(path);
            }
        });
        return true;
    }

    /** Lists the current folder again, keeping the selection. */
    public void refresh() {
        checkWidget();
        if (current != null) {
            List<FileEntry> selected = fileTable.selection();
            navigate(current, selected.isEmpty() ? null : selected.getFirst().name(), Move.REFRESH);
        }
    }

    /** Lists the current folder again and selects {@code selectName}. */
    public void refresh(String selectName) {
        checkWidget();
        if (current != null) {
            navigate(current, selectName, Move.REFRESH);
        }
    }

    public boolean canGoBack() {
        return !backStack.isEmpty();
    }

    public boolean canGoForward() {
        return !forwardStack.isEmpty();
    }

    public boolean canGoUp() {
        return current != null && !current.isTop();
    }

    public void back() {
        checkWidget();
        if (canGoBack()) {
            navigate(backStack.peek(), current != null ? current.name() : null, Move.BACK);
        }
    }

    public void forward() {
        checkWidget();
        if (canGoForward()) {
            navigate(forwardStack.peek(), null, Move.FORWARD);
        }
    }

    /** Opens the parent folder, with the folder left selected. */
    public void up() {
        checkWidget();
        if (canGoUp()) {
            navigate(current.parent(), current.name(), Move.NEW);
        }
    }

    /** The folder shown, or null before the first one is listed. */
    public FilePath getCurrentPath() {
        return current;
    }

    /** The entries of the folder shown, as listed (hidden ones included), in no order. */
    public List<FileEntry> getEntries() {
        return entries;
    }

    /** The entries shown, in their order. */
    public List<FileEntry> getVisibleEntries() {
        return fileTable.rows();
    }

    public List<FileEntry> getSelectedEntries() {
        return fileTable.selection();
    }

    /** Whether hidden files are shown; off by default. */
    public void setShowHidden(boolean show) {
        checkWidget();
        if (show != showHidden) {
            showHidden = show;
            showEntries(fileTable.selection().stream().map(FileEntry::path).toList());
            navigationTree.rebuild();
            if (current != null) {
                navigationTree.reveal(current);
            }
        }
    }

    public boolean isShowHidden() {
        return showHidden;
    }

    /** Sorts the Details view by {@code key}; folders stay first. */
    public void setSort(FileOrder.Key key, boolean ascending) {
        checkWidget();
        fileTable.sortBy(key, ascending);
    }

    public FileOrder.Key getSortKey() {
        return fileTable.sortKey();
    }

    public boolean isSortAscending() {
        return fileTable.ascending();
    }

    /** Whether a folder is being listed. */
    public boolean isLoading() {
        return loading;
    }

    /** Turns the address bar into a text field with the path selected. */
    public void editPath() {
        checkWidget();
        navigationBar.startEditing();
    }

    /** Replaces where failures go; {@code null} shows them in the status bar (the default). */
    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    /** Called with each folder once it is shown. */
    public void addNavigationListener(Consumer<FilePath> listener) {
        navigationListeners.add(listener);
    }

    public void removeNavigationListener(Consumer<FilePath> listener) {
        navigationListeners.remove(listener);
    }

    /** Called when the user opens a file (Enter or a double click); folders open in the browser. */
    public void addOpenListener(Consumer<FileEntry> listener) {
        openListeners.add(listener);
    }

    public void removeOpenListener(Consumer<FileEntry> listener) {
        openListeners.remove(listener);
    }

    /** Called with the selected entries when the selection changes. */
    public void addSelectionChangeListener(Consumer<List<FileEntry>> listener) {
        selectionListeners.add(listener);
    }

    public void removeSelectionChangeListener(Consumer<List<FileEntry>> listener) {
        selectionListeners.remove(listener);
    }

    // ------------------------------------------------------------------ navigation

    private void navigate(FilePath path, String selectName, Move move) {
        checkWidget();
        Objects.requireNonNull(path, "path");
        if (fileSystem == null) {
            throw new IllegalStateException("Set a filesystem first");
        }
        int gen = generation.incrementAndGet();
        if (pending != null) {
            pending.interrupt();
        }
        loading = true;
        error = null;
        FileSystem fs = fileSystem;
        if (fs.has(Capability.SLOW) && move != Move.REFRESH) {
            fileTable.showMessage("Loading…");
        }
        updateStatus();
        pending = loader.run(() -> {
            List<FileEntry> listed = fs.list(path);
            List<String> names = new ArrayList<>(path.depth() + 1);
            for (int depth = 0; depth <= path.depth(); depth++) {
                names.add(fs.label(path.prefix(depth)));
            }
            return new Listing(path, listed, List.copyOf(names));
        }, (listing, failure) -> {
            if (gen != generation.get() || fs != fileSystem) {
                return;
            }
            loading = false;
            pending = null;
            if (failure != null) {
                failed(path, failure);
                return;
            }
            FilePath previous = current;
            switch (move) {
                case NEW -> {
                    if (previous != null && !previous.equals(path)) {
                        backStack.push(previous);
                        forwardStack.clear();
                    }
                }
                case BACK -> {
                    backStack.poll();
                    if (previous != null) {
                        forwardStack.push(previous);
                    }
                }
                case FORWARD -> {
                    forwardStack.poll();
                    if (previous != null) {
                        backStack.push(previous);
                    }
                }
                case REFRESH -> {
                }
            }
            current = listing.path();
            labels = listing.labels();
            entries = List.copyOf(listing.entries());
            showEntries(selectName == null ? List.of() : List.of(path.resolve(selectName)));
            navigationBar.update(!Objects.equals(previous, current));
            if (!Objects.equals(previous, current)) {
                navigationTree.reveal(current);
            }
            navigationListeners.forEach(listener -> listener.accept(current));
        });
    }

    private void showEntries(List<FilePath> select) {
        List<FileEntry> shown = showHidden ? entries : entries.stream().filter(e -> !e.hidden()).toList();
        fileTable.setRows(shown, select);
        updateStatus();
    }

    private void failed(FilePath path, Throwable failure) {
        if (failure instanceof InterruptedIOException) {
            return;
        }
        String name;
        try {
            name = path.isTop() ? fileSystem.topName() : path.name();
        } catch (RuntimeException e) {
            name = path.toString();
        }
        String message = "Can't open \"" + name + "\": " + describe(failure);
        if (current == null) {
            fileTable.showMessage(message);
        } else if (fileSystem.has(Capability.SLOW)) {
            // The list was cleared while loading: show the folder that is still current again.
            showEntries(List.of());
        }
        reportError(message, failure);
        navigationBar.update(false);
        if (current != null) {
            navigationTree.reveal(current);
        }
    }

    /** A failure in words: "Access is denied.", "It doesn't exist.", or the exception's message. */
    static String describe(Throwable failure) {
        return switch (failure) {
            case AccessDeniedException e -> "Access is denied.";
            case NoSuchFileException e -> "It doesn't exist.";
            case NotDirectoryException e -> "It isn't a folder.";
            case UnsupportedOperationException e -> e.getMessage() != null ? e.getMessage() : "Not supported.";
            case IOException e when e.getMessage() != null -> e.getMessage();
            default -> failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
        };
    }

    private void reportError(String message, Throwable cause) {
        if (errorHandler != null) {
            errorHandler.error(message, cause);
            return;
        }
        error = message;
        updateStatus();
    }

    /** The error the status bar shows, or null. */
    String error() {
        return error;
    }

    // ------------------------------------------------------------------ for the parts

    FileIcons icons() {
        return icons;
    }

    /** The size of item icons at the current zoom. */
    int iconSize() {
        return ThemeManager.get(getDisplay()).resources().zoomedPixels(ICON);
    }

    /** The labels of the breadcrumb segments, from the top to the current folder. */
    List<String> crumbLabels() {
        if (current == null || labels.size() != current.depth() + 1) {
            return fileSystem == null ? List.of() : List.of(fileSystem.topName());
        }
        return labels;
    }

    String parentLabel() {
        List<String> crumbs = crumbLabels();
        return crumbs.size() >= 2 ? crumbs.get(crumbs.size() - 2) : "";
    }

    /** The current path as the address field shows it. */
    String formattedPath() {
        return fileSystem == null || current == null ? "" : fileSystem.format(current);
    }

    /** The icon of the top: the computer, a folder for a rooted filesystem, else a server. */
    Image topIcon(int size) {
        if (fileSystem instanceof LocalFileSystem) {
            return icons.get(Icon.COMPUTER, size);
        }
        return icons.get(fileSystem instanceof RootedFileSystem ? Icon.FOLDER : Icon.NETWORK, size);
    }

    /** A folder's icon by its path alone: a drive at the top of the local disks on Windows. */
    Image folderIcon(FilePath path) {
        if (path.isTop()) {
            return topIcon(iconSize());
        }
        boolean drive = fileSystem instanceof LocalFileSystem && path.depth() == 1 && path.name().endsWith(":\\");
        return icons.get(drive ? Icon.DRIVE : Icon.FOLDER, iconSize());
    }

    /** Lists the subfolders of {@code folder}, sorted, hidden ones left out unless shown. */
    void listFolderEntries(FilePath folder, BiConsumer<List<FileEntry>, Throwable> done) {
        FileSystem fs = fileSystem;
        boolean hidden = showHidden;
        loader.run(() -> fs.list(folder).stream().filter(FileEntry::isContainer).filter(e -> hidden || !e.hidden())
                .sorted(FileOrder.by(FileOrder.Key.NAME, true)).toList(), done);
    }

    /** The drop-down items of a breadcrumb chevron: the subfolders of {@code folder}. */
    void listFolders(FilePath folder, Consumer<List<FolderPopup.Item>> done) {
        listFolderEntries(folder, (folders, failure) -> {
            List<FolderPopup.Item> items = new ArrayList<>();
            if (folders != null) {
                for (FileEntry entry : folders) {
                    items.add(new FolderPopup.Item(entry.displayName(), icons.get(entry, iconSize()), () -> navigate(entry.path())));
                }
            }
            done.accept(items);
        });
    }

    /** The drop-down items of the chevron after the top's icon: the places, then the top. */
    List<FolderPopup.Item> placeItems() {
        List<FolderPopup.Item> items = new ArrayList<>();
        for (Place place : getPlaces()) {
            items.add(new FolderPopup.Item(place.label(), icons.place(place.kind(), iconSize()), () -> navigate(place.path())));
        }
        if (fileSystem != null) {
            items.add(new FolderPopup.Item(fileSystem.topName(), topIcon(iconSize()), () -> navigate(FilePath.TOP)));
        }
        return items;
    }

    void sortChanged() {
        updateStatus();
    }

    NavigationBar navigationBar() {
        return navigationBar;
    }

    NavigationTree navigationTree() {
        return navigationTree;
    }

    FileTable fileTable() {
        return fileTable;
    }

    StatusBar statusBar() {
        return statusBar;
    }

    /** Calls not yet delivered (tests). */
    int pendingCalls() {
        return loader.pending();
    }

    // ------------------------------------------------------------------ events

    private void selectionChanged() {
        updateStatus();
        List<FileEntry> selected = fileTable.selection();
        selectionListeners.forEach(listener -> listener.accept(selected));
    }

    private void openSelection() {
        List<FileEntry> selected = fileTable.selection();
        if (selected.isEmpty()) {
            return;
        }
        FileEntry first = selected.getFirst();
        if (first.isContainer()) {
            navigate(first.path());
        } else {
            for (FileEntry entry : selected) {
                if (!entry.isContainer()) {
                    openListeners.forEach(listener -> listener.accept(entry));
                }
            }
        }
    }

    private void keyDown(Event event) {
        boolean alt = (event.stateMask & SWT.MODIFIER_MASK) == SWT.ALT;
        boolean ctrl = (event.stateMask & SWT.MODIFIER_MASK) == SWT.MOD1;
        boolean none = (event.stateMask & SWT.MODIFIER_MASK) == 0;
        if (alt && event.keyCode == SWT.ARROW_LEFT || none && event.keyCode == SWT.BS && event.widget == fileTable.table) {
            back();
        } else if (alt && event.keyCode == SWT.ARROW_RIGHT) {
            forward();
        } else if (alt && event.keyCode == SWT.ARROW_UP) {
            up();
        } else if (none && event.keyCode == SWT.F5) {
            refresh();
        } else if (alt && event.keyCode == 'd' || ctrl && event.keyCode == 'l' || none && event.keyCode == SWT.F4) {
            editPath();
        } else {
            return;
        }
        event.doit = false;
    }

    private void updateStatus() {
        if (statusBar == null) {
            return;
        }
        String text;
        if (loading && (fileSystem != null && fileSystem.has(Capability.SLOW) || current == null)) {
            text = "Loading…";
        } else {
            int count = fileTable.rows().size();
            text = count + (count == 1 ? " item" : " items");
        }
        String selection = "";
        List<FileEntry> selected = fileTable.selection();
        if (!selected.isEmpty()) {
            selection = selected.size() + (selected.size() == 1 ? " item selected" : " items selected");
            long bytes = 0;
            boolean files = false;
            for (FileEntry entry : selected) {
                if (!entry.isContainer() && entry.size() >= 0) {
                    bytes += entry.size();
                    files = true;
                }
            }
            if (files) {
                selection += "  " + FileTypes.shortSize(bytes);
            }
        }
        if (error != null) {
            statusBar.setStatus(error, "", true);
        } else {
            statusBar.setStatus(text, selection, false);
        }
    }

    private void zoomChanged() {
        FileIcons old = icons;
        icons = new FileIcons(getDisplay());
        navigationBar.closePopup();
        navigationTree.refreshImages();
        fileTable.refreshRows();
        navigationBar.redraw();
        old.dispose();
        layout(true, true);
    }
}
