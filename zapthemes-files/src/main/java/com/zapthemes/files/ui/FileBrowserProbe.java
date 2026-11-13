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
}
