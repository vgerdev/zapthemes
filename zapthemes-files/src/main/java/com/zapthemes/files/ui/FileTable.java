package com.zapthemes.files.ui;

import java.time.ZoneId;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.TableItem;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ThemeContext;
import com.zapthemes.files.Capability;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileTypes;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;

/**
 * The Details view: a virtual table with Name, Date modified, Type and Size, plus Permissions,
 * Owner and Group when the filesystem has them. Folders come first; a header click sorts by that
 * column, a second one reverses it. With nothing to list it shows a line of text ("This folder is
 * empty.", "Loading…", an error).
 */
final class FileTable {

    /** A column: what it shows and sorts by. */
    record Column(FileOrder.Key key, String title, int width, int align) {
    }

    private static final DateTimeFormatter DATES = dateFormat(Locale.getDefault());

    final Table table;
    private final FileBrowser browser;
    private List<Column> columns = List.of();
    private List<FileEntry> rows = List.of();
    private FileOrder.Key sortKey = FileOrder.Key.NAME;
    private boolean ascending = true;
    private String message;

    FileTable(FileBrowser browser, Composite parent) {
        this.browser = browser;
        table = new Table(parent, SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.addListener(SWT.SetData, this::setData);
        table.addListener(SWT.Paint, this::paintMessage);
        table.addListener(SWT.MouseDown, this::slowClick);
        table.addListener(SWT.MouseDoubleClick, e -> slowClickGeneration++);
        table.addListener(SWT.KeyDown, e -> slowClickGeneration++);
        ItemPainter.install(table, item -> {
            FileEntry entry = rowOf(item);
            return entry != null ? browser.icons().get(entry, browser.iconSize(), browser.isCut(entry)) : null;
        }, item -> {
            FileEntry entry = rowOf(item);
            return entry != null ? name(entry) : "";
        });
    }

    private FileEntry rowOf(org.eclipse.swt.widgets.Item item) {
        int index = table.indexOf((TableItem) item);
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    private String name(FileEntry entry) {
        String name = entry.displayName();
        if (!browser.isShowExtensions() && !entry.isContainer() && entry.label() == null) {
            String extension = FileTypes.extension(name);
            if (!extension.isEmpty()) {
                name = name.substring(0, name.length() - extension.length() - 1);
            }
        }
        return name + (entry.link() ? "  →" : "");
    }

    /**
     * Gives row {@code index} the theme's stripe as its background, as the Table styler would on its
     * first paint: set while painting, it makes GTK render the row twice, and the drawn names look bold.
     */
    private void stripe(TableItem item, int index) {
        ThemeManager manager = ThemeManager.get(table.getDisplay());
        org.eclipse.swt.graphics.Color stripe = new ThemeContext(manager.resources()).color(manager.styleOf(table), "alternateRowBackground");
        item.setBackground(stripe != null && index % 2 == 1 ? stripe : null);
    }

    /**
     * A click on the name of the one item already selected, not part of a double click, renames it
     * in place (after the double-click time, as Explorer does).
     */
    private void slowClick(Event event) {
        int generation = ++slowClickGeneration;
        if (event.button != 1 || (event.stateMask & SWT.MODIFIER_MASK) != 0 || table.getSelectionCount() != 1) {
            return;
        }
        TableItem item = table.getItem(new org.eclipse.swt.graphics.Point(event.x, event.y));
        if (item == null || table.indexOf(item) != table.getSelectionIndex() || !item.getBounds(0).contains(event.x, event.y)) {
            return;
        }
        FileEntry entry = rowOf(item);
        table.getDisplay().timerExec(table.getDisplay().getDoubleClickTime() + 50, () -> {
            if (generation == slowClickGeneration && !table.isDisposed() && table.getSelectionCount() == 1 && entry != null
                    && entry.equals(selection().stream().findFirst().orElse(null))) {
                browser.actions().rename();
            }
        });
    }

    private int slowClickGeneration;
    private InlineEditor editor;

    /** Opens a field over {@code entry}'s name; false if it isn't shown. */
    boolean startRename(FileEntry entry, java.util.function.Consumer<String> commit) {
        int index = rows.indexOf(entry);
        if (index < 0) {
            return false;
        }
        if (editor != null && editor.isOpen()) {
            editor.finish(false);
        }
        table.showItem(table.getItem(index));
        Rectangle cell = table.getItem(index).getBounds(0);
        int offset = ThemeManager.get(table.getDisplay()).resources().zoomedPixels(2 + 6) + browser.iconSize();
        Rectangle bounds = new Rectangle(cell.x + offset, cell.y, Math.max(40, cell.width - offset), cell.height);
        editor = InlineEditor.open(table, bounds, entry.name(), entry.isContainer(), commit, () -> table.setFocus());
        return true;
    }

    /** The open rename field, or null (tests). */
    InlineEditor editor() {
        return editor != null && editor.isOpen() ? editor : null;
    }

    /** Explorer's short date and time, with a four-digit year: {@code 4/8/2024 1:45 PM}. */
    static DateTimeFormatter dateFormat(Locale locale) {
        String date = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale);
        if (!date.contains("yyyy") && !date.contains("y".repeat(3))) {
            date = date.replace("yy", "yyyy");
            if (!date.contains("yyyy")) {
                date = date.replace("y", "yyyy");
            }
        }
        String time = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale);
        // Some JDK patterns use a narrow no-break space before AM/PM; a plain one reads the same everywhere.
        return DateTimeFormatter.ofPattern(date + " " + time.replace(' ', ' '), locale).withZone(ZoneId.systemDefault());
    }

    /** Sets the columns for a filesystem with {@code capabilities}. */
    void setColumns(Set<Capability> capabilities) {
        List<Column> list = new ArrayList<>();
        list.add(new Column(FileOrder.Key.NAME, "Name", 260, SWT.LEFT));
        list.add(new Column(FileOrder.Key.MODIFIED, "Date modified", 150, SWT.LEFT));
        list.add(new Column(FileOrder.Key.TYPE, "Type", 150, SWT.LEFT));
        list.add(new Column(FileOrder.Key.SIZE, "Size", 90, SWT.RIGHT));
        if (capabilities.contains(Capability.PERMISSIONS)) {
            list.add(new Column(FileOrder.Key.PERMISSIONS, "Permissions", 95, SWT.LEFT));
        }
        if (capabilities.contains(Capability.OWNERS)) {
            list.add(new Column(FileOrder.Key.OWNER, "Owner", 90, SWT.LEFT));
            list.add(new Column(null, "Group", 90, SWT.LEFT));
        }
        if (list.equals(columns)) {
            return;
        }
        columns = List.copyOf(list);
        for (org.eclipse.swt.widgets.TableColumn column : table.getColumns()) {
            column.dispose();
        }
        int zoomed = ThemeManager.get(table.getDisplay()).resources().zoomedPixels(100);
        for (Column column : columns) {
            TableColumn item = new TableColumn(table, column.align());
            item.setText(column.title());
            item.setWidth(column.width() * zoomed / 100);
            item.setMoveable(true);
            item.setData(column);
            item.addListener(SWT.Selection, e -> {
                if (column.key() != null) {
                    sortBy(column.key(), column.key() == sortKey ? !ascending : true);
                }
            });
        }
        showSort();
    }

    List<Column> columns() {
        return columns;
    }

    FileOrder.Key sortKey() {
        return sortKey;
    }

    boolean ascending() {
        return ascending;
    }

    /** Sorts by {@code key}, keeping the selection. */
    void sortBy(FileOrder.Key key, boolean ascendingOrder) {
        sortKey = key;
        ascending = ascendingOrder;
        showSort();
        List<FilePath> selected = selection().stream().map(FileEntry::path).toList();
        setRows(rows, selected);
        browser.sortChanged();
    }

    private void showSort() {
        for (org.eclipse.swt.widgets.TableColumn column : table.getColumns()) {
            if (column.getData() instanceof Column c && c.key() == sortKey) {
                table.setSortColumn(column);
                table.setSortDirection(ascending ? SWT.UP : SWT.DOWN);
                return;
            }
        }
        table.setSortColumn(null);
        table.setSortDirection(SWT.NONE);
    }

    /** Shows {@code entries}, sorted, with the entries at {@code select} selected (the first shown). */
    void setRows(List<FileEntry> entries, List<FilePath> select) {
        List<FileEntry> sorted = new ArrayList<>(entries);
        sorted.sort(FileOrder.by(sortKey, ascending));
        rows = List.copyOf(sorted);
        message = null;
        table.setRedraw(false);
        table.deselectAll();
        table.setItemCount(rows.size());
        table.clearAll();
        if (!select.isEmpty()) {
            Set<FilePath> wanted = new HashSet<>(select);
            List<Integer> indices = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                if (wanted.contains(rows.get(i).path())) {
                    indices.add(i);
                }
            }
            if (!indices.isEmpty()) {
                table.select(indices.stream().mapToInt(Integer::intValue).toArray());
                table.showItem(table.getItem(indices.getFirst()));
            }
        } else if (!rows.isEmpty()) {
            table.setTopIndex(0);
        }
        fillFirstRows();
        table.setRedraw(true);
        table.redraw();
    }

    /** Clears the rows and shows {@code text} instead ("Loading…", an error). */
    void showMessage(String text) {
        rows = List.of();
        table.setItemCount(0);
        message = text;
        table.redraw();
    }

    /** Re-stripes the rows filled so far (after a theme change). */
    void restripe() {
        // Cleared rows are filled (and striped) again as they are painted; the first ones at once.
        table.clearAll();
        fillFirstRows();
    }

    /**
     * Fills the first rows now: a virtual table fills rows as it paints them, and Control.print
     * (screenshots) of a table that wasn't shown yet would leave them blank.
     */
    private void fillFirstRows() {
        for (int i = 0; i < Math.min(rows.size(), 500); i++) {
            TableItem item = table.getItem(i);
            item.setText(texts(rows.get(i)));
            stripe(item, i);
        }
    }

    /** Repaints every row (after a zoom: the icons are drawn at the new size). */
    void refreshRows() {
        table.redraw();
    }

    List<FileEntry> rows() {
        return rows;
    }

    /** The line shown when there are no rows, or null. */
    String message() {
        return rows.isEmpty() ? message != null ? message : "This folder is empty." : null;
    }

    List<FileEntry> selection() {
        List<FileEntry> list = new ArrayList<>();
        for (int index : table.getSelectionIndices()) {
            if (index < rows.size()) {
                list.add(rows.get(index));
            }
        }
        return list;
    }

    /** The texts of row {@code index}, column by column. */
    String[] texts(FileEntry entry) {
        String[] texts = new String[columns.size()];
        for (int i = 0; i < texts.length; i++) {
            Column column = columns.get(i);
            texts[i] = column.key() == null ? nonNull(entry.group()) : switch (column.key()) {
                case NAME -> name(entry);
                case MODIFIED -> entry.modified() == null ? "" : DATES.format(entry.modified());
                case TYPE -> FileTypes.typeName(entry);
                case SIZE -> entry.isContainer() ? "" : FileTypes.detailsSize(entry.size());
                case PERMISSIONS -> nonNull(entry.permissions());
                case OWNER -> nonNull(entry.owner());
            };
        }
        return texts;
    }

    private static String nonNull(String text) {
        return text == null ? "" : text;
    }

    private void setData(Event event) {
        TableItem item = (TableItem) event.item;
        int index = table.indexOf(item);
        if (index < 0 || index >= rows.size()) {
            return;
        }
        FileEntry entry = rows.get(index);
        item.setText(texts(entry));
        stripe(item, index);
    }

    private void paintMessage(Event event) {
        String text = message();
        if (text == null) {
            return;
        }
        GC gc = event.gc;
        Rectangle area = table.getClientArea();
        int top = area.y + (table.getHeaderVisible() ? table.getHeaderHeight() : 0) + ThemeManager.get(table.getDisplay()).resources().zoomedPixels(16);
        gc.setForeground(new org.eclipse.swt.graphics.Color(BarColors.blend(table.getForeground().getRGB(), table.getBackground().getRGB(), 0.7)));
        int width = gc.textExtent(text).x;
        gc.drawText(text, area.x + Math.max(0, (area.width - width) / 2), top, SWT.DRAW_TRANSPARENT);
    }
}
