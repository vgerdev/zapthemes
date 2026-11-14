package com.zapthemes.files.ui;

import java.util.List;

import org.eclipse.swt.widgets.Shell;

import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FilePath;

/**
 * Looks into a {@link FileBrowser} and drives its drawn parts, for tests and smoke tests that live
 * outside this package (the demos). Not for applications.
 */
public final class FileBrowserProbe {

    private FileBrowserProbe() {
    }

    /** The breadcrumb's segment labels, from the top. */
    public static List<String> crumbs(FileBrowser browser) {
        return browser.crumbLabels();
    }

    /** The status bar's text, as one line. */
    public static String status(FileBrowser browser) {
        return browser.statusBar().getText();
    }

    /** The Details view's texts for the entry named {@code name}, or an empty array. */
    public static String[] row(FileBrowser browser, String name) {
        FileTable table = browser.fileTable();
        return table.rows().stream().filter(e -> e.name().equals(name)).findFirst().map(table::texts).orElse(new String[0]);
    }

    /** The Details view's column titles. */
    public static List<String> columns(FileBrowser browser) {
        return browser.fileTable().columns().stream().map(FileTable.Column::title).toList();
    }

    /** The navigation pane's selected folder, or null. */
    public static FilePath treeSelection(FileBrowser browser) {
        return browser.navigationTree().selectedPath();
    }

    /** The navigation pane's top-level labels ({@code ---} for the separator). */
    public static List<String> treeRoots(FileBrowser browser) {
        return browser.navigationTree().rootLabels();
    }

    /** Opens the drop-down of the chevron after segment {@code depth} (-1: after the icon). */
    public static void openChevron(FileBrowser browser, int depth) {
        browser.navigationBar().openChevron(depth);
    }

    /** The open drop-down's labels, or null if none is open. */
    public static List<String> popupLabels(FileBrowser browser) {
        FolderPopup popup = browser.navigationBar().popup();
        return popup != null && popup.isOpen() ? popup.labels() : null;
    }

    /** The open drop-down's shell, or null. */
    public static Shell popupShell(FileBrowser browser) {
        FolderPopup popup = browser.navigationBar().popup();
        return popup != null && popup.isOpen() ? popup.shell : null;
    }

    /** Picks row {@code index} of the open drop-down. */
    public static void pickPopup(FileBrowser browser, int index) {
        FolderPopup popup = browser.navigationBar().popup();
        if (popup != null) {
            popup.pick(index);
        }
    }

    public static void closePopup(FileBrowser browser) {
        browser.navigationBar().closePopup();
    }

    /** Whether drop-downs close when their window is deactivated (on by default; off without a window manager). */
    public static void setPopupsCloseOnDeactivate(boolean close) {
        FolderPopup.closeOnDeactivate = close;
    }

    public static int navigationBarHeight(FileBrowser browser) {
        return browser.navigationBar().getSize().y;
    }

    /** Filesystem calls whose results have not been delivered yet. */
    public static int pendingCalls(FileBrowser browser) {
        return browser.pendingCalls();
    }

    /** The names of {@code entries} (a convenience for checks). */
    public static List<String> names(List<FileEntry> entries) {
        return entries.stream().map(FileEntry::name).toList();
    }

    /** The error shown in the status bar, or null. */
    public static String error(FileBrowser browser) {
        return browser.error();
    }

    /** The labels of the context menu ({@code "-"} for separators), of the selection or of the folder shown. */
    public static List<String> contextMenu(FileBrowser browser, boolean onItems) {
        return contextMenu(browser, onItems, false);
    }

    /** The labels of the condensed or the classic ("Show more options") context menu. */
    public static List<String> contextMenu(FileBrowser browser, boolean onItems, boolean classic) {
        org.eclipse.swt.widgets.Menu menu = classic ? browser.createClassicMenu(browser.fileTable().table, onItems)
                : browser.createContextMenu(browser.fileTable().table, onItems);
        try {
            return java.util.Arrays.stream(menu.getItems())
                    .map(i -> (i.getStyle() & org.eclipse.swt.SWT.SEPARATOR) != 0 ? "-" : i.getText().split("\t")[0].replace("&", "")).toList();
        } finally {
            menu.dispose();
        }
    }

    /** Selects the entries named {@code names} in the file view, as a click would. */
    public static void select(FileBrowser browser, String... names) {
        List<FileEntry> rows = browser.fileTable().rows();
        List<String> wanted = List.of(names);
        int[] indices = java.util.stream.IntStream.range(0, rows.size()).filter(i -> wanted.contains(rows.get(i).name())).toArray();
        browser.fileTable().table.setSelection(indices);
        browser.fileTable().table.notifyListeners(org.eclipse.swt.SWT.Selection, new org.eclipse.swt.widgets.Event());
        browser.focused(false);
    }

    /** How many in-place renames have started. */
    public static int renamesStarted(FileBrowser browser) {
        return browser.actions().renamesStarted();
    }

    /**
     * Ends an in-place rename: commits {@code name}, or cancels with null. If the field already
     * closed (it may lose its focus at once without a window manager), renames the selection directly.
     */
    public static void finishRename(FileBrowser browser, String name) {
        InlineEditor editor = browser.fileTable().editor() != null ? browser.fileTable().editor() : browser.navigationTree().editor();
        if (editor != null) {
            if (name != null) {
                editor.text().setText(name);
            }
            editor.finish(name != null);
        } else if (name != null && browser.getSelectedEntries().size() == 1) {
            browser.actions().commitRename(browser.getSelectedEntries().getFirst(), name);
        }
    }

    /** Whether the address field stops editing when it loses the focus (on by default; off without a window manager). */
    public static void setAddressStopsOnFocusOut(boolean stop) {
        AddressField.stopOnFocusOut = stop;
    }

    /** Edits the path and types {@code text} into it, as the keyboard would. */
    public static void typeAddress(FileBrowser browser, String text) {
        if (browser.navigationBar().addressField() == null) {
            browser.editPath();
        }
        browser.navigationBar().addressField().text.setText(text);
    }

    /** The labels of the address field's drop-down (suggestions or recent locations), or an empty list. */
    public static List<String> suggestions(FileBrowser browser) {
        AddressField field = browser.navigationBar().addressField();
        return field == null || field.suggestions() == null ? List.of() : field.suggestions().labels();
    }

    /** The shell of the address field's drop-down, or null. */
    public static Shell suggestionsShell(FileBrowser browser) {
        AddressField field = browser.navigationBar().addressField();
        return field == null || field.suggestions() == null ? null : field.suggestions().shell;
    }

    /** Picks row {@code index} of the address field's drop-down. */
    public static void pickSuggestion(FileBrowser browser, int index) {
        AddressField field = browser.navigationBar().addressField();
        if (field != null && field.suggestions() != null) {
            field.suggestions().pick(index);
        }
    }

    public static void stopEditing(FileBrowser browser) {
        browser.navigationBar().stopEditing();
    }
    /**
     * Drops the items named {@code names} of {@code source}'s folder on {@code target}: on its row
     * {@code onto}, or into the folder shown when null; {@code operation} is a {@code DND} constant
     * ({@code DROP_DEFAULT} for no key held). Returns the operation the drop did, {@code DROP_NONE}
     * if refused. The browser's side of a drag, without the platform's.
     */
    public static int drop(FileBrowser source, List<String> names, FileBrowser target, String onto, int operation) {
        List<FileEntry> entries = source.fileTable().rows().stream().filter(e -> names.contains(e.name())).toList();
        FileDragAndDrop dnd = target.dragAndDrop();
        FilePath into = dnd.folderAt(onto == null ? null : rowItem(target, onto));
        FileEntryTransfer.Dragged dragged = new FileEntryTransfer.Dragged(source.getFileSystem(), entries);
        int done = dnd.operation(dragged, into, operation);
        FileDragAndDrop.setDragFrom(source);
        try {
            return dnd.drop(dragged, into, done) ? done : org.eclipse.swt.dnd.DND.DROP_NONE;
        } finally {
            FileDragAndDrop.setDragFrom(null);
        }
    }

    /** Highlights the row named {@code name} as where a drop goes (null: none), as while dragging over it. */
    public static void highlightDrop(FileBrowser browser, String name) {
        browser.dragAndDrop().highlight(name == null ? null : rowItem(browser, name));
    }

    private static org.eclipse.swt.widgets.TableItem rowItem(FileBrowser browser, String name) {
        List<FileEntry> rows = browser.fileTable().rows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).name().equals(name)) {
                return browser.fileTable().table.getItem(i);
            }
        }
        return null;
    }
}
