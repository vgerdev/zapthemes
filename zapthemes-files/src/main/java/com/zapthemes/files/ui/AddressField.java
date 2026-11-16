package com.zapthemes.files.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.Location;
import com.zapthemes.swt.widgets.Text;

/**
 * The address bar's text field, while the path is edited: the path, selected; a drop-down of
 * suggestions as you type (the recent locations that start with the text, then the folders whose
 * names complete its last part, listed in the background); and the whole list of recent locations
 * with Down, F4 or the bar's drop-down button.
 *
 * <p>Keys: Down and Up move through the suggestions, putting each in the field; Tab completes with
 * the selected or first one; Enter goes to the selected one or to the text; Escape closes the
 * drop-down, then stops editing.
 */
final class AddressField {

    /**
     * Whether editing stops when the field loses the focus. Tests turn it off: without a window
     * manager, focus comes and goes on its own.
     */
    static boolean stopOnFocusOut = true;

    /** At most this many folder completions. */
    private static final int MAX_FOLDERS = 100;

    private record Target(FileSystem fs, FilePath folder, String parentText, String prefix) {
    }

    final Text text;
    private final NavigationBar bar;
    private final FileBrowser browser;
    private FolderPopup suggestions;
    private boolean showingAll;
    private boolean settingText;
    /** What was typed, put back when Up goes above the first suggestion. */
    private String typed = "";
    private boolean committing;
    // The subfolders of the last folder listed for completion, and the folder being listed.
    private FileSystem listedFs;
    private FilePath listedFolder;
    private List<FileEntry> listedFolders = List.of();
    private FilePath listingFolder;

    AddressField(NavigationBar bar, FileBrowser browser) {
        this.bar = bar;
        this.browser = browser;
        text = new Text(bar, SWT.SINGLE);
        text.addListener(SWT.Modify, e -> {
            if (!settingText) {
                typed = text.getText();
                showingAll = false;
                suggest();
            }
        });
        text.addListener(SWT.KeyDown, this::keyDown);
        text.addListener(SWT.Traverse, this::traverse);
        text.addListener(SWT.DefaultSelection, e -> commit());
        text.addListener(SWT.FocusOut, e -> text.getDisplay().asyncExec(() -> {
            if (stopOnFocusOut && !text.isDisposed() && !text.isFocusControl() && !focusInSuggestions()) {
                bar.stopEditing();
            }
        }));
        text.addListener(SWT.Dispose, e -> closeSuggestions());
    }

    /** Shows {@code value} selected, with no drop-down. */
    void start(String value) {
        closeSuggestions();
        settingText = true;
        text.setText(value);
        settingText = false;
        typed = value;
        text.selectAll();
        text.setFocus();
    }

    private boolean focusInSuggestions() {
        Control focus = text.getDisplay().getFocusControl();
        return suggestions != null && focus != null && focus.getShell() == suggestions.shell;
    }

    // ------------------------------------------------------------------ keys

    private void keyDown(Event event) {
        boolean all = event.keyCode == SWT.F4 || event.keyCode == SWT.ARROW_DOWN && (event.stateMask & SWT.ALT) != 0;
        if (all) {
            toggleAll();
            event.doit = false;
        } else if (event.keyCode == SWT.ARROW_DOWN) {
            if (suggestions == null) {
                showAll();
            } else {
                move(1);
            }
            event.doit = false;
        } else if (event.keyCode == SWT.ARROW_UP && suggestions != null) {
            move(-1);
            event.doit = false;
        }
    }

    private void traverse(Event event) {
        switch (event.detail) {
            case SWT.TRAVERSE_ESCAPE -> {
                event.doit = false;
                event.detail = SWT.TRAVERSE_NONE;
                if (suggestions != null) {
                    closeSuggestions();
                    settingText = true;
                    text.setText(typed);
                    text.setSelection(typed.length());
                    settingText = false;
                } else {
                    bar.stopEditing();
                }
            }
            case SWT.TRAVERSE_RETURN -> {
                event.doit = false;
                event.detail = SWT.TRAVERSE_NONE;
                commit();
            }
            case SWT.TRAVERSE_TAB_NEXT -> {
                if (complete()) {
                    event.doit = false;
                    event.detail = SWT.TRAVERSE_NONE;
                }
            }
            default -> {
            }
        }
    }

    /** Moves the selection of the suggestions by {@code delta}, putting the selected one in the field. */
    void move(int delta) {
        if (suggestions == null) {
            return;
        }
        int index = Math.clamp(suggestions.selectedIndex() + (long) delta, -1, suggestions.size() - 1);
        suggestions.select(index);
        String value = index < 0 ? typed : suggestions.label(index);
        settingText = true;
        text.setText(value);
        text.setSelection(value.length());
        settingText = false;
    }

    /** Puts the selected (or the first) suggestion in the field and suggests from there; false if none. */
    boolean complete() {
        if (suggestions == null || suggestions.size() == 0) {
            return false;
        }
        int index = Math.max(0, suggestions.selectedIndex());
        String value = suggestions.label(index);
        text.setText(value); // Fires Modify: the suggestions follow.
        text.setSelection(value.length());
        return true;
    }

    /** Goes to the selected suggestion, else to what the field says. */
    void commit() {
        if (committing || text.isDisposed()) {
            return;
        }
        committing = true;
        text.getDisplay().asyncExec(() -> committing = false);
        if (suggestions != null && suggestions.selectedIndex() >= 0) {
            suggestions.pick(suggestions.selectedIndex());
            return;
        }
        closeSuggestions();
        if (browser.navigateText(text.getText())) {
            bar.stopEditing();
        } else if (!text.isDisposed()) {
            text.selectAll();
        }
    }

    // ------------------------------------------------------------------ the drop-down

    /** Whether the drop-down shows every recent location (the bar's button, F4). */
    boolean showingAll() {
        return showingAll && suggestions != null;
    }

    /** Opens the list of every recent location, or closes it if it is open. */
    void toggleAll() {
        if (showingAll()) {
            closeSuggestions();
        } else {
            showAll();
        }
    }

    private void showAll() {
        showingAll = true;
        show(locationItems(""));
        bar.redraw();
    }

    /** The suggestions shown, or null. */
    FolderPopup suggestions() {
        return suggestions;
    }

    void closeSuggestions() {
        if (suggestions != null) {
            FolderPopup popup = suggestions;
            suggestions = null;
            popup.close();
        }
        showingAll = false;
        if (!bar.isDisposed()) {
            bar.redraw();
        }
    }

    private void show(List<FolderPopup.Item> items) {
        if (text.isDisposed()) {
            return;
        }
        if (items.isEmpty()) {
            closeSuggestions();
            return;
        }
        if (suggestions != null && suggestions.isOpen()) {
            suggestions.setItems(bar, bar.boxBounds(), items);
            return;
        }
        FolderPopup[] opened = new FolderPopup[1];
        opened[0] = FolderPopup.suggest(bar, bar.boxBounds(), items, () -> {
            if (suggestions == opened[0]) {
                suggestions = null;
                showingAll = false;
                if (!bar.isDisposed()) {
                    bar.redraw();
                }
            }
        });
        suggestions = opened[0];
    }

    /** The recent locations whose text starts with {@code value}. */
    private List<FolderPopup.Item> locationItems(String value) {
        List<FolderPopup.Item> items = new ArrayList<>();
        FileSystem fs = browser.getFileSystem();
        for (Location location : browser.getLocationHistory().matching(value, fs)) {
            items.add(new FolderPopup.Item(location.text(fs), browser.locationIcon(location), () -> {
                bar.stopEditing();
                browser.openLocation(location);
            }));
        }
        return items;
    }

    /** Suggests from what the field says: recent locations, then folder names (listed in the background). */
    private void suggest() {
        String value = text.getText();
        if (value.isBlank()) {
            closeSuggestions();
            return;
        }
        List<FolderPopup.Item> recent = locationItems(value);
        Target target = target(value);
        if (target == null) {
            show(recent);
            return;
        }
        if (target.fs() == listedFs && target.folder().equals(listedFolder)) {
            show(merge(recent, folderItems(target)));
            return;
        }
        show(recent);
        if (target.folder().equals(listingFolder)) {
            return; // Already being listed: shown when it comes.
        }
        listingFolder = target.folder();
        browser.listFolderEntries(target.folder(), (folders, failure) -> {
            if (text.isDisposed() || browser.getFileSystem() != target.fs()) {
                return;
            }
            if (Objects.equals(listingFolder, target.folder())) {
                listingFolder = null;
            }
            listedFs = target.fs();
            listedFolder = target.folder();
            listedFolders = folders != null ? folders : List.of();
            if (!showingAll) {
                suggest();
            }
        });
    }

    /** The folder whose subfolders complete {@code value}, and the parts of it, or null. */
    private Target target(String value) {
        FileSystem fs = browser.getFileSystem();
        FilePath current = browser.getCurrentPath();
        if (fs == null || current == null) {
            return null;
        }
        int cut = Math.max(Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\')), value.lastIndexOf(fs.separator()));
        String parentText = value.substring(0, cut + 1);
        String prefix = value.substring(cut + 1);
        FilePath folder;
        if (cut < 0) {
            folder = current; // A name: the current folder's subfolders.
        } else {
            try {
                folder = fs.parse(parentText, current);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return new Target(fs, folder, parentText, prefix);
    }

    private List<FolderPopup.Item> folderItems(Target target) {
        String prefix = target.prefix().toLowerCase(Locale.ROOT);
        List<FolderPopup.Item> items = new ArrayList<>();
        for (FileEntry entry : listedFolders) {
            if (items.size() >= MAX_FOLDERS) {
                break;
            }
            if (entry.name().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                String value = target.parentText() + entry.name();
                items.add(new FolderPopup.Item(value, browser.icons().get(entry, browser.iconSize()), () -> {
                    bar.stopEditing();
                    browser.navigateText(value);
                }));
            }
        }
        return items;
    }

    /** {@code first}, then those of {@code second} with another label. */
    private static List<FolderPopup.Item> merge(List<FolderPopup.Item> first, List<FolderPopup.Item> second) {
        List<FolderPopup.Item> items = new ArrayList<>(first);
        for (FolderPopup.Item item : second) {
            if (first.stream().noneMatch(i -> i.label().equalsIgnoreCase(item.label()))) {
                items.add(item);
            }
        }
        return items;
    }
}
