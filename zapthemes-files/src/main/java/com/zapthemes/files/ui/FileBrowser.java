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
import java.util.function.Predicate;

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
import com.zapthemes.files.FileTransferHandler;
import com.zapthemes.files.FileTypes;
import com.zapthemes.files.LocalFileSystem;
import com.zapthemes.files.Place;
import com.zapthemes.files.RootedFileSystem;
import com.zapthemes.files.ui.FileIcons.Icon;
import com.zapthemes.swt.custom.SashForm;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandEvent;

/**
 * A file browser modeled on the Windows 11 File Explorer, over any {@link FileSystem}: the local
 * disks, one folder of them ({@link RootedFileSystem}), or a virtual one such as a remote server.
 *
 * <p>From the top: the navigation row (Back, Forward, Up, Refresh and the breadcrumb address bar,
 * whose chevrons drop down the subfolders of each level and which turns into a text field when its
 * empty space is clicked), the command bar (New, Cut, Copy, Paste, Rename, Share, Delete, Sort,
 * View, ...), then the navigation pane (places and the folder tree) beside the Details view, and
 * the status bar. Items are renamed in place (F2, Rename, or a slow second click); pastes go to a
 * {@link FileTransferHandler}.
 *
 * <p>Every filesystem call runs in the background; the browser stays responsive while a slow one
 * lists ({@link Capability#SLOW} shows "Loading…" meanwhile), drops results that are no longer
 * wanted, and reports failures to its {@link ErrorHandler} (by default, in the status bar).
 *
 * <p>Keys: Alt+Left and Alt+Right (or Backspace in the file view) go back and forward, Alt+Up goes
 * up, F5 refreshes, Enter opens, Alt+D, Ctrl+L and F4 edit the path; Ctrl+X, C and V cut, copy
 * and paste, Del deletes, F2 renames, Ctrl+Shift+N makes a folder, Ctrl+A selects all.
 *
 * <p>Theme types: {@code FileBrowser} (the frame), {@code FileNavigationBar}, {@code FileCommandBar}, {@code FileStatusBar};
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
    private final FileActions actions;
    private final CommandBar commandBar;
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
    private boolean showExtensions = true;
    private boolean treeFocused;
    private Runnable afterLoad;
    private String activity;
    private boolean shiftRightClick;
    private boolean shiftKeyDown;

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
        actions = new FileActions(this);
        navigationBar = new NavigationBar(this);
        navigationBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        commandBar = new CommandBar(this, actions);
        commandBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sash = new SashForm(this, SWT.HORIZONTAL | SWT.SMOOTH);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        navigationTree = new NavigationTree(this, sash);
        fileTable = new FileTable(this, sash);
        sash.setWeights(24, 76);
        statusBar = new StatusBar(this);
        statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        fileTable.table.addListener(SWT.Selection, e -> selectionChanged());
        fileTable.table.addListener(SWT.DefaultSelection, e -> openSelection());
        for (Control control : new Control[] { navigationBar, commandBar, navigationTree.tree, fileTable.table, statusBar }) {
            control.addListener(SWT.KeyDown, this::keyDown);
        }
        fileTable.table.addListener(SWT.FocusIn, e -> focused(false));
        navigationTree.tree.addListener(SWT.FocusIn, e -> focused(true));
        navigationTree.tree.addListener(SWT.Selection, e -> actions.update());
        fileTable.table.addListener(SWT.MenuDetect, this::tableMenu);
        for (Control control : new Control[] { fileTable.table, navigationTree.tree }) {
            control.addListener(SWT.MouseDown, e -> shiftRightClick = e.button == 3 && (e.stateMask & SWT.SHIFT) != 0);
            control.addListener(SWT.KeyDown, e -> shiftKeyDown = (e.stateMask & SWT.SHIFT) != 0 || e.keyCode == SWT.SHIFT);
            control.addListener(SWT.KeyUp, e -> shiftKeyDown = false);
        }
        navigationTree.tree.addListener(SWT.MenuDetect, this::treeMenu);
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

    // ------------------------------------------------------------------ commands and operations

    /** Command ids, for {@link #getCommand}: bind them to a ribbon, a menu or a toolbar of the application. */
    public static final String COMMAND_NEW_FOLDER = FileActions.NEW_FOLDER;
    public static final String COMMAND_NEW_TEXT_DOCUMENT = FileActions.NEW_TEXT;
    public static final String COMMAND_CUT = FileActions.CUT;
    public static final String COMMAND_COPY = FileActions.COPY;
    public static final String COMMAND_PASTE = FileActions.PASTE;
    public static final String COMMAND_RENAME = FileActions.RENAME;
    public static final String COMMAND_SHARE = FileActions.SHARE;
    public static final String COMMAND_DELETE = FileActions.DELETE;
    public static final String COMMAND_COPY_PATH = FileActions.COPY_PATH;
    public static final String COMMAND_COPY_NAME = FileActions.COPY_NAME;
    public static final String COMMAND_SELECT_ALL = FileActions.SELECT_ALL;
    public static final String COMMAND_PROPERTIES = FileActions.PROPERTIES;
    public static final String COMMAND_REFRESH = FileActions.REFRESH;
    public static final String COMMAND_SHOW_HIDDEN = FileActions.SHOW_HIDDEN;

    /** Which menu a {@link MenuContributor} adds to. */
    public enum MenuKind {
        /** The menu of the selected items. */
        ITEMS,
        /** The menu of the folder shown (a click on empty space). */
        BACKGROUND,
        /** The New menu (command bar and context menu). */
        NEW
    }

    /** Adds the application's items to the browser's menus. */
    @FunctionalInterface
    public interface MenuContributor {
        /** Adds items to {@code menu} (after the browser's, separated); {@code selection} is empty for the background and New menus. */
        void contribute(org.eclipse.swt.widgets.Menu menu, MenuKind kind, List<FileEntry> selection);
    }

    /** The browser's command {@code id} ({@code COMMAND_...}), whose enabled state follows the selection. */
    public Command getCommand(String id) {
        return actions.command(id);
    }

    /** All of the browser's commands. */
    public List<Command> getCommands() {
        return actions.commands();
    }

    /** Adds an application's command to the command bar, before "…". */
    public void addCommand(Command command) {
        checkWidget();
        commandBar.add(command);
    }

    public void removeCommand(Command command) {
        checkWidget();
        commandBar.remove(command);
    }

    /** What carries out pastes; by default {@link FileTransferHandler#direct()}. An application with a transfer queue sets its own. */
    public void setTransferHandler(FileTransferHandler handler) {
        actions.setTransferHandler(handler);
    }

    /**
     * Asks whether to delete; by default a message box ("Permanently delete ...?"). Return false to
     * keep the items.
     */
    public void setDeleteConfirmation(Predicate<List<FileEntry>> confirmation) {
        actions.setDeleteConfirmation(confirmation);
    }

    /** Turns Share on: called with the selected items. Off (disabled) without one. */
    public void setShareHandler(Consumer<List<FileEntry>> handler) {
        actions.setShareHandler(handler);
    }

    /** Turns Properties (Alt+Enter) on: called with the selected items, or the folder shown. Off without one. */
    public void setPropertiesHandler(Consumer<List<FileEntry>> handler) {
        actions.setPropertiesHandler(handler);
    }

    public void addMenuContributor(MenuContributor contributor) {
        actions.addMenuContributor(contributor);
    }

    public void removeMenuContributor(MenuContributor contributor) {
        actions.removeMenuContributor(contributor);
    }

    /** Renames the selected item in place (F2). */
    public void rename() {
        checkWidget();
        actions.rename();
    }

    /** Creates "New folder" in the folder shown and opens its name for editing (Ctrl+Shift+N). */
    public void newFolder() {
        checkWidget();
        actions.newFolder();
    }

    /** Deletes the selected items after the confirmation (Del). */
    public void delete() {
        checkWidget();
        actions.delete();
    }

    /** Puts the selected items on the clipboard, to be moved by a paste (Ctrl+X). */
    public void cut() {
        checkWidget();
        Command command = getCommand(COMMAND_CUT);
        actions.update();
        command.run(new CommandEvent(command, this));
    }

    /** Puts the selected items on the clipboard (Ctrl+C). */
    public void copy() {
        checkWidget();
        Command command = getCommand(COMMAND_COPY);
        actions.update();
        command.run(new CommandEvent(command, this));
    }

    /** Pastes into the folder shown (Ctrl+V). */
    public void paste() {
        checkWidget();
        actions.checkClipboard();
        actions.paste(null);
    }

    /** Whether file name extensions are shown; on by default. */
    public void setShowExtensions(boolean show) {
        checkWidget();
        if (show != showExtensions) {
            showExtensions = show;
            fileTable.restripe();
            actions.update();
        }
    }

    public boolean isShowExtensions() {
        return showExtensions;
    }

    /** Shows or hides the navigation pane. */
    public void setNavigationPaneVisible(boolean visible) {
        checkWidget();
        sash.setMaximizedControl(visible ? null : fileTable.table);
        actions.update();
    }

    public boolean isNavigationPaneVisible() {
        return sash.getMaximizedControl() == null;
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
            Runnable then = afterLoad;
            afterLoad = null;
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
            actions.update();
            navigationListeners.forEach(listener -> listener.accept(current));
            if (then != null) {
                then.run();
            }
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
        // A hidden folder on the way to the one shown is listed anyway (AppData, for %TEMP%), so
        // the tree and the breadcrumb can show where the browser is.
        FilePath shown = current;
        loader.run(() -> fs.list(folder).stream().filter(FileEntry::isContainer)
                .filter(e -> hidden || !e.hidden() || shown != null && shown.startsWith(e.path()))
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

    FileActions actions() {
        return actions;
    }

    CommandBar commandBar() {
        return commandBar;
    }

    FileLoader loader() {
        return loader;
    }

    /** What the commands act on: the navigation pane's folder while it has the focus, else the file view's selection. */
    List<FileEntry> targets() {
        if (treeFocused) {
            FileEntry entry = navigationTree.selectedEntry();
            return entry == null ? List.of() : List.of(entry);
        }
        return fileTable.selection();
    }

    boolean navigationTreeHasFocus() {
        return treeFocused;
    }

    /** The folder shown, as an entry. */
    FileEntry currentEntry() {
        return current == null ? null
                : FileEntry.builder(current, com.zapthemes.files.EntryKind.DIRECTORY).label(crumbLabels().getLast()).build();
    }

    boolean isCut(FileEntry entry) {
        return actions.isCut(entry);
    }

    /** Reports {@code failure} as "{@code what}: reason". */
    void reportFailure(String what, Throwable failure) {
        reportError(what + ": " + describe(failure), failure);
    }

    /** Lists the folder again, selects {@code name}, then runs {@code then}. */
    void refreshThen(String name, Runnable then) {
        if (current != null) {
            navigate(current, name, Move.REFRESH);
            afterLoad = then;
        }
    }

    /** Shows an operation in progress in the status bar ("Copying 3 items…"), or nothing. */
    void showActivity(String text) {
        activity = text;
        updateStatus();
    }

    void selectionChangedByCommand() {
        selectionChanged();
    }

    /** Which pane the commands act on (tests set it without a real focus). */
    void focused(boolean tree) {
        treeFocused = tree;
        actions.checkClipboard();
    }

    /** Runs the command whose shortcut {@code event} is; false if none is. */
    private boolean runShortcut(Event event) {
        if (event.widget != navigationTree.tree && event.widget != fileTable.table) {
            return false;
        }
        int key = event.keyCode == SWT.KEYPAD_CR ? SWT.CR : event.keyCode;
        if (key >= 'A' && key <= 'Z') {
            key = Character.toLowerCase(key);
        }
        int accelerator = (event.stateMask & SWT.MODIFIER_MASK) | key;
        if (accelerator == SWT.CR) {
            return false; // Enter opens through DefaultSelection.
        }
        actions.checkClipboard();
        for (Command command : actions.commands()) {
            int shortcut = command.shortcut();
            int expected = (shortcut & SWT.MODIFIER_MASK) | Character.toLowerCase(shortcut & SWT.KEY_MASK);
            if (shortcut != 0 && expected == accelerator) {
                command.run(new CommandEvent(command, event.widget, event, false, null));
                return true;
            }
        }
        return false;
    }

    private void tableMenu(Event event) {
        org.eclipse.swt.widgets.Table table = fileTable.table;
        treeFocused = false;
        boolean onItems;
        if (event.detail == SWT.MENU_MOUSE) {
            org.eclipse.swt.widgets.TableItem item = table.getItem(table.toControl(event.x, event.y));
            if (item == null) {
                table.deselectAll();
                selectionChanged();
                onItems = false;
            } else {
                if (!table.isSelected(table.indexOf(item))) {
                    table.setSelection(item);
                    selectionChanged();
                }
                onItems = true;
            }
        } else {
            onItems = table.getSelectionCount() > 0;
        }
        showMenu(table, event, onItems);
    }

    private void treeMenu(Event event) {
        org.eclipse.swt.widgets.Tree tree = navigationTree.tree;
        treeFocused = true;
        if (event.detail == SWT.MENU_MOUSE) {
            org.eclipse.swt.widgets.TreeItem item = tree.getItem(tree.toControl(event.x, event.y));
            if (item == null) {
                event.doit = false;
                return;
            }
            // Selected without opening it, as Explorer does.
            navigationTree.selectQuietly(item);
        }
        showMenu(tree, event, navigationTree.selectedEntry() != null);
    }

    private void showMenu(Control control, Event event, boolean onItems) {
        // Shift with a right click (or Shift+F10) opens the classic menu at once, as in Explorer.
        boolean classic = event.detail == SWT.MENU_MOUSE ? shiftRightClick : shiftKeyDown;
        showMenu(control, event.x, event.y, onItems, classic);
        event.doit = false;
    }

    private void showMenu(Control control, int x, int y, boolean onItems, boolean classic) {
        org.eclipse.swt.widgets.Menu menu = classic ? createClassicMenu(control, onItems)
                : createContextMenu(control, onItems, () -> getDisplay().asyncExec(() -> {
                    if (!control.isDisposed()) {
                        showMenu(control, x, y, onItems, true);
                    }
                }));
        menu.setLocation(x, y);
        menu.addListener(SWT.Hide, e -> getDisplay().asyncExec(() -> {
            if (!menu.isDisposed()) {
                menu.dispose();
            }
        }));
        menu.setVisible(true);
    }

    /** A filled condensed context menu for {@code control}, not shown (tests show nothing). */
    org.eclipse.swt.widgets.Menu createContextMenu(Control control, boolean onItems) {
        return createContextMenu(control, onItems, null);
    }

    private org.eclipse.swt.widgets.Menu createContextMenu(Control control, boolean onItems, Runnable showMore) {
        org.eclipse.swt.widgets.Menu menu = new com.zapthemes.swt.widgets.Menu(control);
        actions.fillContextMenu(menu, onItems, showMore);
        return menu;
    }

    /** A filled classic context menu for {@code control}, not shown. */
    org.eclipse.swt.widgets.Menu createClassicMenu(Control control, boolean onItems) {
        org.eclipse.swt.widgets.Menu menu = new com.zapthemes.swt.widgets.Menu(control);
        actions.fillClassicMenu(menu, onItems);
        return menu;
    }

    /** Calls not yet delivered (tests). */
    int pendingCalls() {
        return loader.pending();
    }

    // ------------------------------------------------------------------ events

    private void selectionChanged() {
        updateStatus();
        actions.update();
        List<FileEntry> selected = fileTable.selection();
        selectionListeners.forEach(listener -> listener.accept(selected));
    }

    /** Opens the selected items: a folder in the browser, files through the open listeners. */
    void openSelected() {
        if (treeFocused) {
            FileEntry entry = navigationTree.selectedEntry();
            if (entry != null) {
                navigate(entry.path());
            }
            return;
        }
        openSelection();
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
        } else if (!runShortcut(event)) {
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
        if (activity != null) {
            statusBar.setStatus(activity, selection, false);
        } else if (error != null) {
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
        commandBar.redraw();
        old.dispose();
        layout(true, true);
    }
}
