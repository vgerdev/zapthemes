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
}
