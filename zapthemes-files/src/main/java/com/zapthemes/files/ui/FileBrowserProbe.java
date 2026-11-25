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
        return browser.getVisibleEntries().stream().filter(e -> e.name().equals(name)).findFirst().map(table::texts).orElse(new String[0]);
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

    /** The folder the browser watches for changes, or null. */
    public static FilePath watchedFolder(FileBrowser browser) {
        return browser.folderWatcher().started() ? browser.folderWatcher().folder() : null;
    }

    /** How many times a watched change listed the folder again. */
    public static int watchRefreshes(FileBrowser browser) {
        return browser.folderWatcher().refreshes();
    }

    /** The type name the browser shows for {@code name}: its icon provider's, or its own. */
    public static String typeName(FileBrowser browser, String name) {
        return browser.typeName(entry(browser, name));
    }

    /** The icon the browser shows for {@code name} at {@code size} pixels (at 100 %). */
    public static org.eclipse.swt.graphics.Image icon(FileBrowser browser, String name, int size) {
        return browser.icons().get(entry(browser, name), size);
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
        org.eclipse.swt.widgets.Control control = browser.view().control();
        org.eclipse.swt.widgets.Menu menu = classic ? browser.createClassicMenu(control, onItems) : browser.createContextMenu(control, onItems);
        try {
            return java.util.Arrays.stream(menu.getItems())
                    .map(i -> (i.getStyle() & org.eclipse.swt.SWT.SEPARATOR) != 0 ? "-" : i.getText().split("\t")[0].replace("&", "")).toList();
        } finally {
            menu.dispose();
        }
    }

    /** Selects the entries named {@code names} in the file view, as a click would. */
    public static void select(FileBrowser browser, String... names) {
        List<String> wanted = List.of(names);
        browser.view().select(browser.view().rows().stream().filter(e -> wanted.contains(e.name())).toList());
        browser.view().control().notifyListeners(org.eclipse.swt.SWT.Selection, new org.eclipse.swt.widgets.Event());
        browser.focused(false);
    }

    /** The labels of the column headers' menu, with "[x]" before those shown. */
    public static List<String> columnsMenu(FileBrowser browser) {
        org.eclipse.swt.widgets.Menu menu = browser.createColumnsMenu();
        try {
            return java.util.Arrays.stream(menu.getItems()).map(i -> (i.getSelection() ? "[x] " : "[ ] ") + i.getText()).toList();
        } finally {
            menu.dispose();
        }
    }

    /** Picks {@code label} in the column headers' menu, toggling that column. */
    public static void toggleColumn(FileBrowser browser, String label) {
        org.eclipse.swt.widgets.Menu menu = browser.createColumnsMenu();
        try {
            for (org.eclipse.swt.widgets.MenuItem item : menu.getItems()) {
                if (item.getText().equals(label)) {
                    item.setSelection(!item.getSelection());
                    item.notifyListeners(org.eclipse.swt.SWT.Selection, new org.eclipse.swt.widgets.Event());
                }
            }
        } finally {
            menu.dispose();
        }
    }

    /** Selects the place at {@code index} in the navigation pane, giving the pane the focus. */
    public static void selectPlace(FileBrowser browser, int index) {
        browser.navigationTree().selectPlace(index);
        browser.focused(true);
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
        InlineEditor editor = browser.view().editor() != null ? browser.view().editor() : browser.navigationTree().editor();
        if (editor != null) {
            if (name != null) {
                editor.text().setText(name);
            }
            editor.finish(name != null);
        } else if (name != null && browser.navigationTreeHasFocus() && browser.navigationTree().selectedPlace() != null) {
            browser.renamePlace(browser.navigationTree().selectedPlace(), name);
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
        List<FileEntry> entries = source.getVisibleEntries().stream().filter(e -> names.contains(e.name())).toList();
        FileDragAndDrop dnd = target.dragAndDrop();
        FilePath into = target.getView() == FileView.DETAILS ? dnd.folderAt(onto == null ? null : rowItem(target, onto))
                : folderInGrid(target, onto);
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
        FileEntry entry = name == null ? null : entry(browser, name);
        browser.view().setDropTarget(entry);
    }

    private static FileEntry entry(FileBrowser browser, String name) {
        return browser.getVisibleEntries().stream().filter(e -> e.name().equals(name)).findFirst().orElse(null);
    }

    private static org.eclipse.swt.widgets.TableItem rowItem(FileBrowser browser, String name) {
        FileEntry entry = entry(browser, name);
        return entry == null ? null : browser.fileTable().itemOf(entry);
    }

    /** Where a drop on the grid's item {@code onto} (null: empty space) goes. */
    private static FilePath folderInGrid(FileBrowser browser, String onto) {
        FileEntry entry = onto == null ? null : entry(browser, onto);
        return entry != null && entry.isContainer() ? entry.path() : browser.getCurrentPath();
    }

    // ------------------------------------------------------------------ step 5: layouts, groups, panes

    /** The group headers shown, as "label (count)", in order (empty when not grouped). */
    public static List<String> groupHeaders(FileBrowser browser) {
        return browser.groupHeaders();
    }

    /** Collapses or expands group {@code label}, as a click on its chevron does. */
    public static void toggleGroup(FileBrowser browser, String label) {
        browser.toggleGroup(label);
    }

    /** The bounds of the item named {@code name} in the grid (a layout other than Details), or null. */
    public static org.eclipse.swt.graphics.Rectangle itemBounds(FileBrowser browser, String name) {
        FileEntry entry = entry(browser, name);
        return entry == null || browser.getView() == FileView.DETAILS ? null : browser.fileGrid().boundsOf(entry);
    }

    /** The file view's control: the Details table, or the grid of the other layouts. */
    public static org.eclipse.swt.widgets.Control viewControl(FileBrowser browser) {
        return browser.view().control();
    }

    /** What the details pane lists: "label: value" lines, or the plain value of the name and type lines. */
    public static List<String> detailsPane(FileBrowser browser) {
        return browser.sidePane().details().stream().map(l -> l[0] == null ? l[1] : l[0] + ": " + l[1]).toList();
    }

    /** The text the preview pane shows, or null if it shows none (a picture, a message). */
    public static String previewText(FileBrowser browser) {
        SidePane pane = browser.sidePane();
        return pane.text().isVisible() ? pane.text().getText() : null;
    }

    /** Whether the preview pane shows a picture. */
    public static boolean previewShowsPicture(FileBrowser browser) {
        return browser.sidePane().showsPicture();
    }

    /** The line the preview pane shows instead of a preview ("No preview available."), or null. */
    public static String previewMessage(FileBrowser browser) {
        return browser.sidePane().previewMessage();
    }

    /** Thumbnails read so far. */
    public static int thumbnails(FileBrowser browser) {
        return browser.thumbnails().size();
    }
}
