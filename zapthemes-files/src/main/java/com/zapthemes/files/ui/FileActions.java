package com.zapthemes.files.ui;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MessageBox;

import com.zapthemes.files.Capability;
import com.zapthemes.files.EntryKind;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.FileTransferHandler;
import com.zapthemes.files.FileTransfers;
import com.zapthemes.files.LocalFileSystem;
import com.zapthemes.files.TransferRequest;
import com.zapthemes.files.ui.FileIcons.Glyph;
import com.zapthemes.swt.widgets.MenuItem;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandEvent;

/**
 * The browser's commands (New, Cut, Copy, Paste, Rename, Share, Delete, Sort, View, ...) and what
 * they do: renaming in place, new folders and documents, deleting after a confirmation, the
 * clipboard (in this process, and as files for other applications when they are local), and the
 * menus of the command bar and the context menus.
 */
final class FileActions {

    static final String NEW = "files.new";
    static final String NEW_FOLDER = "files.newFolder";
    static final String NEW_TEXT = "files.newTextDocument";
    static final String CUT = "files.cut";
    static final String COPY = "files.copy";
    static final String PASTE = "files.paste";
    static final String RENAME = "files.rename";
    static final String SHARE = "files.share";
    static final String DELETE = "files.delete";
    static final String SORT = "files.sort";
    static final String VIEW = "files.view";
    static final String MORE = "files.more";
    static final String DETAILS_PANE = "files.detailsPane";
    static final String PREVIEW_PANE = "files.previewPane";
    static final String OPEN = "files.open";
    static final String COPY_PATH = "files.copyPath";
    static final String COPY_NAME = "files.copyName";
    static final String COPY_FOLDER_PATH = "files.copyFolderPath";
    static final String SELECT_ALL = "files.selectAll";
    static final String SELECT_NONE = "files.selectNone";
    static final String INVERT_SELECTION = "files.invertSelection";
    static final String PROPERTIES = "files.properties";
    static final String REFRESH = "files.refresh";
    static final String SHOW_HIDDEN = "files.showHidden";
    static final String SHOW_EXTENSIONS = "files.showExtensions";
    static final String SHOW_NAVIGATION_PANE = "files.showNavigationPane";
    static final String PIN = "files.pin";
    static final String CLEAR_FILTER = "files.clearFilter";

    /** What the clipboard holds from a browser of this process. */
    private record Held(FileSystem fs, List<FileEntry> entries, boolean cut, String text) {
    }

    /** Shared by every browser of the process, so one can paste what another copied. */
    private static Held held;

    private final FileBrowser browser;
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final Map<String, Glyph> glyphs = new LinkedHashMap<>();
    private final List<FileBrowser.MenuContributor> contributors = new ArrayList<>();
    private FileTransferHandler transferHandler = FileTransferHandler.direct();
    private Predicate<List<FileEntry>> deleteConfirmation;
    private Consumer<List<FileEntry>> shareHandler;
    private Consumer<List<FileEntry>> propertiesHandler;
    private boolean pasteAvailable;

    /** Whether Paste has something to paste (tests). */
    boolean pasteAvailable() {
        return pasteAvailable;
    }
    private int renames;

    FileActions(FileBrowser browser) {
        this.browser = browser;
        add(NEW, "&New", Glyph.NEW, 0, null).style(Command.Style.DROP_DOWN).description("Create a folder or a document");
        add(NEW_FOLDER, "&Folder", Glyph.NEW, SWT.MOD1 | SWT.SHIFT | 'N', e -> newFolder());
        add(NEW_TEXT, "&Text document", null, 0, e -> newTextDocument());
        add(CUT, "Cu&t", Glyph.CUT, SWT.MOD1 | 'X', e -> putOnClipboard(true));
        add(COPY, "&Copy", Glyph.COPY, SWT.MOD1 | 'C', e -> putOnClipboard(false));
        add(PASTE, "&Paste", Glyph.PASTE, SWT.MOD1 | 'V', e -> paste(null));
        add(RENAME, "Rena&me", Glyph.RENAME, SWT.F2, e -> rename());
        add(SHARE, "&Share", Glyph.SHARE, 0, e -> share());
        add(DELETE, "&Delete", Glyph.DELETE, SWT.DEL, e -> delete(false));
        add(SORT, "Sort", Glyph.SORT, 0, null).style(Command.Style.DROP_DOWN).description("Sort the items");
        add(VIEW, "View", Glyph.DETAILS_VIEW, 0, null).style(Command.Style.DROP_DOWN).description("Change the layout and the panes");
        add(MORE, "See more", Glyph.MORE, 0, null).style(Command.Style.DROP_DOWN);
        add(DETAILS_PANE, "Details", Glyph.DETAILS_PANE, SWT.ALT | SWT.SHIFT | 'P',
                e -> browser.setDetailsPaneVisible(!browser.isDetailsPaneVisible())).style(Command.Style.TOGGLE)
                .description("Show or hide the details pane");
        add(PREVIEW_PANE, "Preview pane", null, SWT.ALT | 'P', e -> browser.setPreviewPaneVisible(!browser.isPreviewPaneVisible()))
                .style(Command.Style.TOGGLE).description("Show or hide the preview pane");
        add(OPEN, "&Open", null, SWT.CR, e -> browser.openSelected());
        add(COPY_PATH, "Copy as p&ath", null, SWT.MOD1 | SWT.SHIFT | 'C', e -> copyPath());
        add(COPY_NAME, "Copy &name", null, 0, e -> copyText(browser.targets().stream().map(FileEntry::name).toList()));
        add(COPY_FOLDER_PATH, "Copy folder p&ath", null, 0, e -> {
            FileSystem fs = browser.getFileSystem();
            if (fs != null && browser.getCurrentPath() != null) {
                copyText(List.of(fs.format(browser.getCurrentPath())));
            }
        });
        add(SELECT_ALL, "Select &all", null, SWT.MOD1 | 'A', e -> browser.setSelectedEntries(browser.getVisibleEntries()));
        add(SELECT_NONE, "Select &none", null, 0, e -> browser.setSelectedEntries(List.of()));
        add(INVERT_SELECTION, "&Invert selection", null, 0, e -> invertSelection());
        add(PROPERTIES, "P&roperties", null, SWT.ALT | SWT.CR, e -> properties());
        add(REFRESH, "R&efresh", null, SWT.F5, e -> browser.refresh());
        add(SHOW_HIDDEN, "&Hidden items", null, 0, e -> browser.setShowHidden(commands.get(SHOW_HIDDEN).isChecked()))
                .style(Command.Style.TOGGLE);
        add(SHOW_EXTENSIONS, "File name &extensions", null, 0, e -> browser.setShowExtensions(commands.get(SHOW_EXTENSIONS).isChecked()))
                .style(Command.Style.TOGGLE).setChecked(true);
        add(SHOW_NAVIGATION_PANE, "&Navigation pane", null, 0,
                e -> browser.setNavigationPaneVisible(commands.get(SHOW_NAVIGATION_PANE).isChecked())).style(Command.Style.TOGGLE)
                .setChecked(true);
        add(PIN, "&Pin to Quick access", Glyph.PIN, 0, e -> togglePin())
                .description("Show the folder in the navigation pane's places, or stop showing it");
        add(CLEAR_FILTER, "Clear &filter", null, 0, e -> browser.setFilter(null)).description("Show every item again");
    }

    private Command add(String id, String label, Glyph glyph, int shortcut, Consumer<CommandEvent> run) {
        Command command = new Command(id, label).shortcut(shortcut).category("File browser");
        if (run != null) {
            command.onRun(run);
        }
        commands.put(id, command);
        if (glyph != null) {
            glyphs.put(id, glyph);
        }
        return command;
    }

    Command command(String id) {
        return commands.get(id);
    }

    List<Command> commands() {
        return List.copyOf(commands.values());
    }

    /** The glyph the command bar draws for a built-in command, or null. */
    Glyph glyph(String id) {
        if (id.equals(VIEW)) {
            // View shows the layout in use, as Explorer's button does.
            return browser.getView() == FileView.DETAILS ? Glyph.DETAILS_VIEW : Glyph.LARGE_ICONS_VIEW;
        }
        return glyphs.get(id);
    }

    void setTransferHandler(FileTransferHandler handler) {
        transferHandler = handler != null ? handler : FileTransferHandler.direct();
    }

    void setDeleteConfirmation(Predicate<List<FileEntry>> confirmation) {
        deleteConfirmation = confirmation;
    }

    void setShareHandler(Consumer<List<FileEntry>> handler) {
        shareHandler = handler;
        update();
    }

    void setPropertiesHandler(Consumer<List<FileEntry>> handler) {
        propertiesHandler = handler;
        update();
    }

    void addMenuContributor(FileBrowser.MenuContributor contributor) {
        contributors.add(contributor);
    }

    void removeMenuContributor(FileBrowser.MenuContributor contributor) {
        contributors.remove(contributor);
    }

    /** How many in-place renames have started (tests: the field may lose its focus at once under Xvfb). */
    int renamesStarted() {
        return renames;
    }

    // ------------------------------------------------------------------ state

    /** The folder new items and pastes go into: the folder shown, if things can be made there. */
    private FilePath creatableFolder() {
        FileSystem fs = browser.getFileSystem();
        FilePath current = browser.getCurrentPath();
        if (fs == null || current == null) {
            return null;
        }
        // "This PC" on Windows holds drives, not files.
        if (fs instanceof LocalFileSystem && LocalFileSystem.diskPath(fs, current).isEmpty()) {
            return null;
        }
        return current;
    }

    /** Re-checks what each command can do now. */
    void update() {
        FileSystem fs = browser.getFileSystem();
        List<FileEntry> selection = browser.targets();
        boolean any = !selection.isEmpty() && fs != null;
        boolean create = creatableFolder() != null;
        boolean drives = selection.stream().anyMatch(e -> e.kind() == EntryKind.DRIVE || e.path().isTop());
        commands.get(NEW).setEnabled(create && (fs.has(Capability.CREATE_DIRECTORY) || fs.has(Capability.WRITE)));
        commands.get(NEW_FOLDER).setEnabled(create && fs.has(Capability.CREATE_DIRECTORY));
        commands.get(NEW_TEXT).setEnabled(create && fs.has(Capability.WRITE));
        commands.get(COPY).setEnabled(any && !drives);
        commands.get(CUT).setEnabled(any && !drives && (fs.has(Capability.MOVE) || fs.has(Capability.DELETE)));
        commands.get(COPY_PATH).setEnabled(any);
        commands.get(COPY_NAME).setEnabled(any);
        commands.get(COPY_FOLDER_PATH).setEnabled(fs != null && browser.getCurrentPath() != null);
        commands.get(PASTE).setEnabled(create && pasteAvailable && (fs.has(Capability.WRITE) || fs.has(Capability.CREATE_DIRECTORY)));
        boolean place = browser.navigationTreeHasFocus() && browser.navigationTree().selectedPlace() != null;
        commands.get(RENAME).setEnabled(place || selection.size() == 1 && !drives && fs.has(Capability.RENAME));
        FileEntry folder = selection.size() == 1 && selection.getFirst().isContainer() ? selection.getFirst() : null;
        boolean pinned = place || folder != null && browser.isPinned(folder.path());
        commands.get(PIN).setEnabled(place || folder != null);
        String pinLabel = pinned ? "Unpin from &Quick access" : "&Pin to Quick access";
        if (!pinLabel.equals(commands.get(PIN).label())) {
            commands.get(PIN).label(pinLabel);
        }
        commands.get(CLEAR_FILTER).setEnabled(!browser.getFilter().isEmpty());
        commands.get(DELETE).setEnabled(any && !drives && fs.has(Capability.DELETE));
        commands.get(SHARE).setEnabled(any && shareHandler != null);
        commands.get(PROPERTIES).setEnabled(propertiesHandler != null && fs != null);
        commands.get(OPEN).setEnabled(any);
        commands.get(SHOW_HIDDEN).setChecked(browser.isShowHidden());
        commands.get(SHOW_EXTENSIONS).setChecked(browser.isShowExtensions());
        commands.get(SHOW_NAVIGATION_PANE).setChecked(browser.isNavigationPaneVisible());
        commands.get(DETAILS_PANE).setChecked(browser.isDetailsPaneVisible());
        commands.get(PREVIEW_PANE).setChecked(browser.isPreviewPaneVisible());
    }

    /** Looks at the clipboard again (on focus, before menus, after copying). */
    void checkClipboard() {
        Clipboard clipboard = new Clipboard(browser.getDisplay());
        try {
            boolean files = false;
            for (TransferData type : clipboard.getAvailableTypes()) {
                files |= FileTransfer.getInstance().isSupportedType(type);
            }
            // What this process copied counts even when the clipboard can't be read for a moment
            // (on Windows other readers, such as the clipboard history, hold it right after a copy).
            // If another application has copied since, a paste finds neither and does nothing.
            pasteAvailable = files || held != null;
        } finally {
            clipboard.dispose();
        }
        update();
    }

    /** Whether {@code entry} was cut and waits for a paste (shown faded). */
    boolean isCut(FileEntry entry) {
        Held h = held;
        return h != null && h.cut() && h.fs() == browser.getFileSystem() && h.entries().stream().anyMatch(e -> e.path().equals(entry.path()));
    }

    // ------------------------------------------------------------------ new items

    void newFolder() {
        create("New folder", true);
    }

    void newTextDocument() {
        create("New Text Document.txt", false);
    }

    private void create(String base, boolean folder) {
        FilePath parent = creatableFolder();
        FileSystem fs = browser.getFileSystem();
        if (parent == null) {
            return;
        }
        browser.loader().run(() -> {
            String name = FileTransfers.newName(fs, parent, base, folder);
            if (folder) {
                fs.createDirectory(parent, name);
            } else {
                try (OutputStream out = fs.openWrite(parent.resolve(name))) {
                    out.flush();
                }
            }
            return name;
        }, (name, failure) -> {
            if (failure != null) {
                browser.reportFailure("Can't create \"" + base + "\"", failure);
            } else if (fs == browser.getFileSystem()) {
                // Explorer opens the new item's name for editing.
                browser.refreshThen(name, () -> {
                    if (browser.getCurrentPath() != null && parent.equals(browser.getCurrentPath())) {
                        rename();
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------------ places

    /** Pins the selected folder to the places, or unpins it (or the selected place). */
    private void togglePin() {
        update();
        if (!commands.get(PIN).isEnabled()) {
            return;
        }
        com.zapthemes.files.Place place = browser.navigationTreeHasFocus() ? browser.navigationTree().selectedPlace() : null;
        FileEntry folder = place == null ? browser.targets().getFirst() : null;
        FilePath path = place != null ? place.path() : folder.path();
        if (browser.isPinned(path)) {
            browser.unpin(path);
        } else {
            browser.pin(path, folder.displayName());
        }
    }

    // ------------------------------------------------------------------ rename

    /** Opens the name of the one selected item for editing, in the file view or the navigation pane. */
    void rename() {
        update();
        if (!commands.get(RENAME).isEnabled()) {
            return;
        }
        com.zapthemes.files.Place place = browser.navigationTreeHasFocus() ? browser.navigationTree().selectedPlace() : null;
        if (place != null) {
            // A pinned place: its label is renamed, as Explorer renames a Quick access shortcut, not the folder.
            if (browser.navigationTree().startRenamePlace(label -> browser.renamePlace(place, label))) {
                renames++;
            }
            return;
        }
        FileEntry entry = browser.targets().getFirst();
        Consumer<String> commit = name -> commitRename(entry, name);
        boolean started = browser.navigationTreeHasFocus() ? browser.navigationTree().startRename(entry, commit)
                : browser.view().startRename(entry, commit);
        if (started) {
            renames++;
        }
    }

    void commitRename(FileEntry entry, String typed) {
        String name = typed.strip();
        if (name.isEmpty() || name.equals(entry.name())) {
            return;
        }
        FileSystem fs = browser.getFileSystem();
        browser.loader().run(() -> fs.rename(entry.path(), name), (path, failure) -> {
            if (failure != null) {
                browser.reportFailure("Can't rename \"" + entry.displayName() + "\"", failure);
            } else if (fs == browser.getFileSystem()) {
                FilePath current = browser.getCurrentPath();
                browser.navigationTree().renamed(entry.path(), path);
                if (current != null && current.startsWith(entry.path())) {
                    // The folder shown (or one above it) was renamed in the navigation pane.
                    browser.navigate(path.resolve(current.relativeTo(entry.path())));
                } else {
                    browser.refresh(path.name());
                }
            }
        });
    }

    // ------------------------------------------------------------------ delete

    /**
     * Deletes the targets: to the trash where the filesystem has one (without asking, as Explorer),
     * unless {@code permanently} (Shift+Del), else for good after the confirmation. What can't go
     * to the trash (a network drive) is offered to be deleted for good.
     */
    void delete(boolean permanently) {
        update();
        if (!commands.get(DELETE).isEnabled()) {
            return;
        }
        FileSystem fs = browser.getFileSystem();
        boolean toTrash = !permanently && fs.has(Capability.TRASH);
        List<FileEntry> entries = browser.targets();
        if (!toTrash && !confirmDelete(entries, null)) {
            return;
        }
        delete(fs, entries, toTrash);
    }

    private void delete(FileSystem fs, List<FileEntry> entries, boolean toTrash) {
        int[] done = { 0 };
        browser.loader().run(() -> {
            for (FileEntry entry : entries) {
                if (toTrash) {
                    fs.trash(entry.path());
                } else {
                    fs.delete(entry.path(), true);
                }
                done[0]++;
            }
            return entries.size();
        }, (count, error) -> {
            Throwable failure = error;
            if (toTrash && failure instanceof java.io.IOException && !(failure instanceof java.nio.file.NoSuchFileException)
                    && fs == browser.getFileSystem()) {
                // As Explorer: what the trash can't take may be deleted for good, if the user says so.
                List<FileEntry> rest = List.copyOf(entries.subList(Math.min(done[0], entries.size()), entries.size()));
                if (!rest.isEmpty() && confirmDelete(rest, FileBrowser.describe(failure))) {
                    delete(fs, rest, false);
                    return;
                }
                failure = null;
            }
            if (failure != null) {
                browser.reportFailure("Can't delete", failure);
            }
            if (fs == browser.getFileSystem()) {
                FilePath current = browser.getCurrentPath();
                FilePath gone = entries.stream().map(FileEntry::path).filter(p -> current != null && current.startsWith(p)).findFirst()
                        .orElse(null);
                browser.navigationTree().deleted(entries);
                if (gone != null && gone.parent() != null) {
                    browser.navigate(gone.parent());
                } else {
                    browser.refresh();
                }
            }
        });
    }

    /** Asks whether to delete {@code entries} for good; {@code reason} says why they can't go to the trash, if that is why. */
    private boolean confirmDelete(List<FileEntry> entries, String reason) {
        if (deleteConfirmation != null) {
            return deleteConfirmation.test(entries);
        }
        MessageBox box = new MessageBox(browser.getShell(), SWT.ICON_WARNING | SWT.YES | SWT.NO);
        box.setText("Delete");
        String what = entries.size() == 1 ? "\"" + entries.getFirst().displayName() + "\"" : "these " + entries.size() + " items";
        String why = reason == null ? "" : (entries.size() == 1 ? "It" : "They") + " can't go to the " + trashName() + ": " + reason + "\n\n";
        box.setMessage(why + "Permanently delete " + what + "?\n\nThis can't be undone.");
        return box.open() == SWT.YES;
    }

    /** "Recycle Bin" on Windows, "Trash" elsewhere. */
    static String trashName() {
        return SWT.getPlatform().equals("win32") ? "Recycle Bin" : "Trash";
    }

    // ------------------------------------------------------------------ clipboard

    private void putOnClipboard(boolean cut) {
        update();
        if (!commands.get(cut ? CUT : COPY).isEnabled()) {
            return;
        }
        FileSystem fs = browser.getFileSystem();
        List<FileEntry> entries = browser.targets();
        String text = String.join(System.lineSeparator(), entries.stream().map(e -> fs.format(e.path())).toList());
        List<String> files = new ArrayList<>();
        for (FileEntry entry : entries) {
            Optional<Path> disk = LocalFileSystem.diskPath(fs, entry.path());
            disk.ifPresent(p -> files.add(p.toString()));
        }
        Clipboard clipboard = new Clipboard(browser.getDisplay());
        try {
            if (files.size() == entries.size()) {
                clipboard.setContents(new Object[] { files.toArray(String[]::new), text },
                        new Transfer[] { FileTransfer.getInstance(), TextTransfer.getInstance() });
            } else {
                clipboard.setContents(new Object[] { text }, new Transfer[] { TextTransfer.getInstance() });
            }
        } finally {
            clipboard.dispose();
        }
        held = new Held(fs, List.copyOf(entries), cut, text);
        pasteAvailable = true;
        update();
        browser.view().control().redraw();
    }

    /** Copies the selected items' paths as text, one per line. */
    void copyPath() {
        FileSystem fs = browser.getFileSystem();
        if (fs != null) {
            copyText(browser.targets().stream().map(e -> fs.format(e.path())).toList());
        }
    }

    /** Puts {@code lines} on the clipboard as text, one per line. */
    private void copyText(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        Clipboard clipboard = new Clipboard(browser.getDisplay());
        try {
            clipboard.setContents(new Object[] { String.join(System.lineSeparator(), lines) }, new Transfer[] { TextTransfer.getInstance() });
        } finally {
            clipboard.dispose();
        }
        checkClipboard();
    }

    /**
     * Pastes what the clipboard holds into {@code folder} (the folder shown if null): items a
     * browser of this process copied or cut, or files another application copied.
     */
    void paste(FilePath folder) {
        FilePath target = folder != null ? folder : creatableFolder();
        FileSystem fs = browser.getFileSystem();
        if (target == null || fs == null) {
            return;
        }
        Clipboard clipboard = new Clipboard(browser.getDisplay());
        String text;
        String[] files;
        try {
            text = (String) clipboard.getContents(TextTransfer.getInstance());
            files = (String[]) clipboard.getContents(FileTransfer.getInstance());
        } finally {
            clipboard.dispose();
        }
        Held h = held;
        // Windows may refuse the clipboard for a moment after a copy: with neither text nor files
        // to read, the items copied here are pasted.
        boolean unreadable = text == null && (files == null || files.length == 0);
        if (h != null && (unreadable || Objects.equals(text, h.text()))) {
            transfer(new TransferRequest(h.fs(), h.entries(), fs, target, h.cut()), h.cut(), "Can't paste", null);
            return;
        }
        if (files == null || files.length == 0) {
            return;
        }
        LocalFileSystem local = LocalFileSystem.get();
        browser.loader().run(() -> {
            List<FileEntry> entries = new ArrayList<>();
            for (String file : files) {
                local.stat(local.fromPath(Path.of(file))).ifPresent(entries::add);
            }
            return entries;
        }, (entries, failure) -> {
            if (failure != null) {
                browser.reportFailure("Can't paste", failure);
            } else if (!entries.isEmpty() && fs == browser.getFileSystem()) {
                transfer(new TransferRequest(local, entries, fs, target, false), false, "Can't paste", null);
            }
        });
    }

    /** Whether a rename field is open, in the file view or the navigation pane. */
    boolean renaming() {
        return browser.view().editor() != null || browser.navigationTree().editor() != null;
    }

    /**
     * Hands {@code request} to the transfer handler (a paste, a drop), shows it in the status bar,
     * then lists the folder again; a failure is reported as "{@code failureTitle}: reason", and
     * {@code done} runs after a success.
     */
    void transfer(TransferRequest request, boolean cut, String failureTitle, Runnable done) {
        FileSystem fs = request.target();
        browser.showActivity((request.move() ? "Moving " : "Copying ") + request.sources().size()
                + (request.sources().size() == 1 ? " item…" : " items…"));
        transferHandler.transfer(request).whenComplete((created, failure) -> browser.getDisplay().asyncExec(() -> {
            if (browser.isDisposed()) {
                return;
            }
            browser.showActivity(null);
            if (failure != null) {
                browser.reportFailure(failureTitle, failure instanceof java.util.concurrent.CompletionException c && c.getCause() != null
                        ? c.getCause() : failure);
            } else {
                if (cut) {
                    held = null;
                    checkClipboard();
                }
                if (done != null) {
                    done.run();
                }
            }
            if (fs == browser.getFileSystem() && request.targetFolder().equals(browser.getCurrentPath())) {
                browser.refresh(created == null || created.isEmpty() ? null : created.getFirst());
            } else if (fs == browser.getFileSystem()) {
                browser.refresh();
            }
        }));
    }

    // ------------------------------------------------------------------ other commands

    private void share() {
        if (shareHandler != null && !browser.targets().isEmpty()) {
            shareHandler.accept(browser.targets());
        }
    }

    private void properties() {
        if (propertiesHandler != null) {
            List<FileEntry> selection = browser.targets();
            propertiesHandler.accept(selection.isEmpty() && browser.currentEntry() != null ? List.of(browser.currentEntry()) : selection);
        }
    }

    private void invertSelection() {
        List<FileEntry> selected = browser.getSelectedEntries();
        browser.setSelectedEntries(browser.getVisibleEntries().stream().filter(e -> !selected.contains(e)).toList());
    }

    // ------------------------------------------------------------------ menus

    /** A menu item for {@code id}: its label and shortcut, enabled and checked state; runs it. */
    MenuItem item(Menu menu, String id, int style) {
        Command command = commands.get(id);
        MenuItem item = new MenuItem(menu, style);
        String shortcut = command.shortcutText();
        item.setText(command.label() + (shortcut.isEmpty() ? "" : "\t" + shortcut));
        item.setEnabled(command.isEnabled());
        if ((style & (SWT.CHECK | SWT.RADIO)) != 0) {
            item.setSelection(command.isChecked());
        }
        item.setData(id);
        item.addListener(SWT.Selection, e -> command.run(new CommandEvent(command, item, e, false, null)));
        return item;
    }

    private static MenuItem plain(Menu menu, int style, String text, boolean enabled, boolean selected, Runnable run) {
        MenuItem item = new MenuItem(menu, style);
        item.setText(text);
        item.setEnabled(enabled);
        if ((style & (SWT.CHECK | SWT.RADIO)) != 0) {
            item.setSelection(selected);
        }
        if (run != null) {
            item.addListener(SWT.Selection, e -> {
                // A radio item is also told when it is deselected.
                if ((style & SWT.RADIO) == 0 || item.getSelection()) {
                    run.run();
                }
            });
        }
        return item;
    }

    private static void separator(Menu menu) {
        if (menu.getItemCount() > 0 && (menu.getItem(menu.getItemCount() - 1).getStyle() & SWT.SEPARATOR) == 0) {
            new MenuItem(menu, SWT.SEPARATOR);
        }
    }

    private static Menu cascade(Menu menu, String text, boolean enabled) {
        MenuItem item = new MenuItem(menu, SWT.CASCADE);
        item.setText(text);
        item.setEnabled(enabled);
        Menu sub = new com.zapthemes.swt.widgets.Menu(item);
        item.setMenu(sub);
        return sub;
    }

    void fillNewMenu(Menu menu) {
        update();
        item(menu, NEW_FOLDER, SWT.PUSH);
        separator(menu);
        item(menu, NEW_TEXT, SWT.PUSH);
        contribute(menu, FileBrowser.MenuKind.NEW, List.of());
    }

    void fillSortMenu(Menu menu) {
        FileOrder.Key current = browser.getSortKey();
        for (DetailsColumns.Column column : browser.fileGrid().columns()) {
            if (column.key() != null) {
                FileOrder.Key key = column.key();
                plain(menu, SWT.RADIO, column.title(), true, key == current, () -> browser.setSort(key, browser.isSortAscending()));
            }
        }
        separator(menu);
        plain(menu, SWT.RADIO, "Ascending", true, browser.isSortAscending(), () -> browser.setSort(browser.getSortKey(), true));
        plain(menu, SWT.RADIO, "Descending", true, !browser.isSortAscending(), () -> browser.setSort(browser.getSortKey(), false));
        separator(menu);
        fillGroupBy(cascade(menu, "Group by", true));
    }

    void fillGroupBy(Menu menu) {
        FileOrder.Key current = browser.getGroupBy();
        plain(menu, SWT.RADIO, "(None)", true, current == null, () -> browser.setGroupBy(null, true));
        for (DetailsColumns.Column column : browser.fileGrid().columns()) {
            FileOrder.Key key = column.key();
            if (key != null) {
                // Dates and sizes newest and largest first, as Explorer starts them.
                boolean ascending = key != FileOrder.Key.MODIFIED && key != FileOrder.Key.SIZE;
                plain(menu, SWT.RADIO, column.title(), true, key == current, () -> browser.setGroupBy(key, ascending));
            }
        }
        separator(menu);
        boolean grouped = current != null;
        plain(menu, SWT.RADIO, "Ascending", grouped, grouped && browser.isGroupAscending(), () -> browser.setGroupBy(current, true));
        plain(menu, SWT.RADIO, "Descending", grouped, grouped && !browser.isGroupAscending(), () -> browser.setGroupBy(current, false));
        separator(menu);
        plain(menu, SWT.PUSH, "&Collapse all groups", grouped, false, () -> browser.setGroupsCollapsed(true));
        plain(menu, SWT.PUSH, "&Expand all groups", grouped, false, () -> browser.setGroupsCollapsed(false));
    }

    void fillViewMenu(Menu menu) {
        update();
        FileView shown = browser.getView();
        for (FileView view : FileView.values()) {
            plain(menu, SWT.RADIO, view.label() + "\tCtrl+Shift+" + (view.ordinal() + 1), true, view == shown, () -> browser.setView(view));
        }
        separator(menu);
        item(menu, DETAILS_PANE, SWT.CHECK).setText("Details pane\tAlt+Shift+P");
        item(menu, PREVIEW_PANE, SWT.CHECK).setText("Preview pane\tAlt+P");
        separator(menu);
        Menu show = cascade(menu, "Show", true);
        item(show, SHOW_NAVIGATION_PANE, SWT.CHECK);
        item(show, SHOW_EXTENSIONS, SWT.CHECK);
        item(show, SHOW_HIDDEN, SWT.CHECK);
        if (commands.get(CLEAR_FILTER).isEnabled()) {
            separator(menu);
            item(menu, CLEAR_FILTER, SWT.PUSH);
        }
    }

    void fillMoreMenu(Menu menu) {
        update();
        item(menu, SELECT_ALL, SWT.PUSH);
        item(menu, SELECT_NONE, SWT.PUSH);
        item(menu, INVERT_SELECTION, SWT.PUSH);
        separator(menu);
        item(menu, COPY_PATH, SWT.PUSH);
        item(menu, REFRESH, SWT.PUSH);
        separator(menu);
        item(menu, PROPERTIES, SWT.PUSH);
    }

    /**
     * The context menu, condensed as in Windows 11: of the selected items, or of the folder shown
     * ({@code onItems} false). It ends in "Show more options", which runs {@code showMore} (the
     * browser opens the classic menu with it).
     */
    void fillContextMenu(Menu menu, boolean onItems, Runnable showMore) {
        checkClipboard();
        List<FileEntry> selection = browser.targets();
        if (onItems && !selection.isEmpty()) {
            MenuItem open = item(menu, OPEN, SWT.PUSH);
            menu.setDefaultItem(open);
            if (commands.get(PIN).isEnabled()) {
                item(menu, PIN, SWT.PUSH);
            }
            separator(menu);
            item(menu, CUT, SWT.PUSH);
            item(menu, COPY, SWT.PUSH);
            pasteInto(menu, selection);
            item(menu, RENAME, SWT.PUSH);
            item(menu, DELETE, SWT.PUSH);
            separator(menu);
            item(menu, COPY_PATH, SWT.PUSH);
            item(menu, SHARE, SWT.PUSH);
            separator(menu);
            item(menu, PROPERTIES, SWT.PUSH);
            contribute(menu, FileBrowser.MenuKind.ITEMS, selection);
        } else {
            fillViewMenu(cascade(menu, "&View", true));
            fillSortMenu(cascade(menu, "&Sort by", true));
            fillGroupBy(cascade(menu, "&Group by", true));
            item(menu, REFRESH, SWT.PUSH);
            separator(menu);
            item(menu, PASTE, SWT.PUSH);
            separator(menu);
            fillNewMenu(cascade(menu, "Ne&w", commands.get(NEW).isEnabled()));
            separator(menu);
            item(menu, PROPERTIES, SWT.PUSH);
            contribute(menu, FileBrowser.MenuKind.BACKGROUND, List.of());
        }
        separator(menu);
        plain(menu, SWT.PUSH, "Show more &options\tShift+F10", showMore != null, false, showMore);
    }

    /**
     * The classic, longer context menu ("Show more options", or a right click with Shift): every
     * command that applies, in Windows 10's order.
     */
    void fillClassicMenu(Menu menu, boolean onItems) {
        checkClipboard();
        List<FileEntry> selection = browser.targets();
        if (onItems && !selection.isEmpty()) {
            MenuItem open = item(menu, OPEN, SWT.PUSH);
            menu.setDefaultItem(open);
            if (commands.get(PIN).isEnabled()) {
                item(menu, PIN, SWT.PUSH);
            }
            separator(menu);
            item(menu, SHARE, SWT.PUSH);
            separator(menu);
            item(menu, CUT, SWT.PUSH);
            item(menu, COPY, SWT.PUSH);
            pasteInto(menu, selection);
            separator(menu);
            item(menu, DELETE, SWT.PUSH);
            item(menu, RENAME, SWT.PUSH);
            separator(menu);
            item(menu, COPY_PATH, SWT.PUSH);
            item(menu, COPY_NAME, SWT.PUSH);
            separator(menu);
            item(menu, SELECT_ALL, SWT.PUSH);
            item(menu, INVERT_SELECTION, SWT.PUSH);
            separator(menu);
            item(menu, PROPERTIES, SWT.PUSH);
            contribute(menu, FileBrowser.MenuKind.ITEMS, selection);
            return;
        }
        fillViewMenu(cascade(menu, "&View", true));
        fillSortMenu(cascade(menu, "&Sort by", true));
        fillGroupBy(cascade(menu, "&Group by", true));
        item(menu, REFRESH, SWT.PUSH);
        separator(menu);
        item(menu, PASTE, SWT.PUSH);
        separator(menu);
        fillNewMenu(cascade(menu, "Ne&w", commands.get(NEW).isEnabled()));
        separator(menu);
        item(menu, SELECT_ALL, SWT.PUSH);
        item(menu, SHOW_HIDDEN, SWT.CHECK);
        item(menu, SHOW_EXTENSIONS, SWT.CHECK);
        separator(menu);
        item(menu, COPY_FOLDER_PATH, SWT.PUSH);
        separator(menu);
        item(menu, PROPERTIES, SWT.PUSH);
        contribute(menu, FileBrowser.MenuKind.BACKGROUND, List.of());
    }

    private void pasteInto(Menu menu, List<FileEntry> selection) {
        if (selection.size() == 1 && selection.getFirst().isContainer() && pasteAvailable) {
            FilePath into = selection.getFirst().path();
            plain(menu, SWT.PUSH, "&Paste into folder", commands.get(PASTE).isEnabled() || creatableFolder() != null, false,
                    () -> paste(into));
        }
    }

    private void contribute(Menu menu, FileBrowser.MenuKind kind, List<FileEntry> selection) {
        if (contributors.isEmpty()) {
            return;
        }
        int before = menu.getItemCount();
        for (FileBrowser.MenuContributor contributor : contributors) {
            contributor.contribute(menu, kind, selection);
        }
        if (menu.getItemCount() > before && before > 0) {
            // A separator between the browser's items and the application's.
            new MenuItem(menu, SWT.SEPARATOR, before);
        }
    }
}
