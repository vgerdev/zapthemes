package com.zapthemes.files.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.widgets.Control;

import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FilePath;

/**
 * What the browser needs of a file view: the Details table ({@link FileTable}) or the owner-drawn
 * layouts ({@link FileGrid}). A view sends {@code SWT.Selection} when the user changes the selection
 * and {@code SWT.DefaultSelection} when the user opens it.
 */
interface ItemView {

    /** A group of the view: a header ({@code label}, null for no header) and the entries shown under it. */
    record Section(String label, List<FileEntry> entries, int count, boolean collapsed) {
        public Section {
            entries = List.copyOf(entries);
        }

        /** All of {@code entries}, without a header. */
        static List<Section> plain(List<FileEntry> entries) {
            return List.of(new Section(null, entries, entries.size(), false));
        }

        /** The entries of {@code sections}, in order. */
        static List<FileEntry> entriesOf(List<Section> sections) {
            List<FileEntry> all = new ArrayList<>();
            sections.forEach(s -> all.addAll(s.entries()));
            return List.copyOf(all);
        }
    }

    Control control();

    /** Shows {@code sections}, with the entries at {@code select} selected (the first one scrolled to). */
    void setSections(List<Section> sections, List<FilePath> select);

    /** The entries shown, in order (not those of collapsed groups). */
    List<FileEntry> rows();

    List<FileEntry> selection();

    /** Selects {@code entries} (those shown), without an event. */
    void select(List<FileEntry> entries);

    /** Clears the entries and shows {@code text} instead ("Loading…", an error). */
    void showMessage(String text);

    /** The line shown when there are no entries, or null. */
    String message();

    /** Opens a field over {@code entry}'s name; false if it isn't shown. */
    boolean startRename(FileEntry entry, Consumer<String> commit);

    /** The open rename field, or null. */
    InlineEditor editor();

    /** Forgets a pending slow second click (a drag started from it). */
    void cancelSlowClick();

    /** Paints the entries again (zoom, theme, cut state, extensions). */
    void refreshRows();

    /** The entry at {@code (x, y)} of the control, or null. */
    FileEntry entryAt(int x, int y);

    /** Highlights {@code entry} (a folder) as where a drop goes, or nothing. */
    void setDropTarget(FileEntry entry);

    /** The entry highlighted as where a drop goes, or null. */
    FileEntry dropTarget();

    /** Where the view is scrolled to, to {@link #setScroll} back after the folder is listed again. */
    int[] scroll();

    /** Scrolls back to a position {@link #scroll} gave, as far as the items reach. */
    void setScroll(int[] position);
}
