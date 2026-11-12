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
        add(DELETE, "&Delete", Glyph.DELETE, SWT.DEL, e -> delete());
        add(SORT, "Sort", Glyph.SORT, 0, null).style(Command.Style.DROP_DOWN).description("Sort the items");
        add(VIEW, "View", Glyph.DETAILS_VIEW, 0, null).style(Command.Style.DROP_DOWN).description("Change the layout and the panes");
        add(MORE, "See more", Glyph.MORE, 0, null).style(Command.Style.DROP_DOWN);
        add(DETAILS_PANE, "Details", Glyph.DETAILS_PANE, 0, null).style(Command.Style.TOGGLE)
                .description("The details pane comes with a later version");
        add(PREVIEW_PANE, "Preview pane", null, 0, null).style(Command.Style.TOGGLE);
        add(OPEN, "&Open", null, SWT.CR, e -> browser.openSelected());
        add(COPY_PATH, "Copy as p&ath", null, SWT.MOD1 | SWT.SHIFT | 'C', e -> copyPath());
        add(COPY_NAME, "Copy &name", null, 0, e -> copyText(browser.targets().stream().map(FileEntry::name).toList()));
        add(COPY_FOLDER_PATH, "Copy folder p&ath", null, 0, e -> {
            FileSystem fs = browser.getFileSystem();
            if (fs != null && browser.getCurrentPath() != null) {
                copyText(List.of(fs.format(browser.getCurrentPath())));
            }
        });
        add(SELECT_ALL, "Select &all", null, SWT.MOD1 | 'A', e -> {
            browser.fileTable().table.selectAll();
            browser.selectionChangedByCommand();
        });
        add(SELECT_NONE, "Select &none", null, 0, e -> {
            browser.fileTable().table.deselectAll();
            browser.selectionChangedByCommand();
        });
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
        // Coming with the details and preview panes.
        commands.get(DETAILS_PANE).setEnabled(false);
        commands.get(PREVIEW_PANE).setEnabled(false);
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
        commands.get(RENAME).setEnabled(selection.size() == 1 && !drives && fs.has(Capability.RENAME));
        commands.get(DELETE).setEnabled(any && !drives && fs.has(Capability.DELETE));
        commands.get(SHARE).setEnabled(any && shareHandler != null);
        commands.get(PROPERTIES).setEnabled(propertiesHandler != null && fs != null);
        commands.get(OPEN).setEnabled(any);
        commands.get(SHOW_HIDDEN).setChecked(browser.isShowHidden());
        commands.get(SHOW_EXTENSIONS).setChecked(browser.isShowExtensions());
        commands.get(SHOW_NAVIGATION_PANE).setChecked(browser.isNavigationPaneVisible());
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

    // ------------------------------------------------------------------ rename

    /** Opens the name of the one selected item for editing, in the file view or the navigation pane. */
    void rename() {
        update();
        if (!commands.get(RENAME).isEnabled()) {
            return;
        }
        FileEntry entry = browser.targets().getFirst();
        Consumer<String> commit = name -> commitRename(entry, name);
        boolean started = browser.navigationTreeHasFocus() ? browser.navigationTree().startRename(entry, commit)
                : browser.fileTable().startRename(entry, commit);
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

    void delete() {
        update();
        if (!commands.get(DELETE).isEnabled()) {
            return;
        }
        List<FileEntry> entries = browser.targets();
        if (!confirmDelete(entries)) {
            return;
        }
        FileSystem fs = browser.getFileSystem();
        browser.loader().run(() -> {
            for (FileEntry entry : entries) {
                fs.delete(entry.path(), true);
            }
            return entries.size();
        }, (count, failure) -> {
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

    private boolean confirmDelete(List<FileEntry> entries) {
        if (deleteConfirmation != null) {
            return deleteConfirmation.test(entries);
        }
        MessageBox box = new MessageBox(browser.getShell(), SWT.ICON_WARNING | SWT.YES | SWT.NO);
        box.setText("Delete");
        String what = entries.size() == 1 ? "\"" + entries.getFirst().displayName() + "\"" : "these " + entries.size() + " items";
        box.setMessage("Permanently delete " + what + "?\n\nThis can't be undone.");
        return box.open() == SWT.YES;
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
        browser.fileTable().table.redraw();
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
        if (h != null && Objects.equals(text, h.text())) {
            transfer(new TransferRequest(h.fs(), h.entries(), fs, target, h.cut()), h.cut());
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
                transfer(new TransferRequest(local, entries, fs, target, false), false);
            }
        });
    }

    private void transfer(TransferRequest request, boolean cut) {
        FileSystem fs = request.target();
        browser.showActivity((request.move() ? "Moving " : "Copying ") + request.sources().size()
                + (request.sources().size() == 1 ? " item…" : " items…"));
        transferHandler.transfer(request).whenComplete((created, failure) -> browser.getDisplay().asyncExec(() -> {
            if (browser.isDisposed()) {
                return;
            }
            browser.showActivity(null);
            if (failure != null) {
                browser.reportFailure("Can't paste", failure instanceof java.util.concurrent.CompletionException c && c.getCause() != null
                        ? c.getCause() : failure);
            } else if (cut) {
                held = null;
                checkClipboard();
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
        org.eclipse.swt.widgets.Table table = browser.fileTable().table;
        int count = table.getItemCount();
        boolean[] selected = new boolean[count];
        for (int index : table.getSelectionIndices()) {
            selected[index] = true;
        }
        table.deselectAll();
        List<Integer> invert = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!selected[i]) {
                invert.add(i);
            }
        }
        table.select(invert.stream().mapToInt(Integer::intValue).toArray());
        browser.selectionChangedByCommand();
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
        for (FileTable.Column column : browser.fileTable().columns()) {
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

    private static void fillGroupBy(Menu menu) {
        plain(menu, SWT.RADIO, "(None)", true, true, null);
        for (String key : List.of("Name", "Date modified", "Type", "Size")) {
            plain(menu, SWT.RADIO, key, false, false, null);
        }
    }

    void fillViewMenu(Menu menu) {
        update();
        // The other layouts come with the owner-drawn views.
        for (String layout : List.of("Extra large icons", "Large icons", "Medium icons", "Small icons", "List")) {
            plain(menu, SWT.RADIO, layout, false, false, null);
        }
        plain(menu, SWT.RADIO, "Details", true, true, null);
        for (String layout : List.of("Tiles", "Content")) {
            plain(menu, SWT.RADIO, layout, false, false, null);
        }
        separator(menu);
        item(menu, DETAILS_PANE, SWT.CHECK).setText("Details pane");
        item(menu, PREVIEW_PANE, SWT.CHECK);
        separator(menu);
        Menu show = cascade(menu, "Show", true);
        item(show, SHOW_NAVIGATION_PANE, SWT.CHECK);
        item(show, SHOW_EXTENSIONS, SWT.CHECK);
        item(show, SHOW_HIDDEN, SWT.CHECK);
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
