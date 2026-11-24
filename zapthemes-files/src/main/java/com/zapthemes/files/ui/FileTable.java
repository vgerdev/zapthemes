package com.zapthemes.files.ui;

import java.time.ZoneId;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
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
 * Owner and Group when the filesystem has them. A header click sorts by that column, a second one
 * reverses it (the browser keeps the order). Grouped, a header row starts each group: its chevron
 * collapses the group, a click elsewhere on it selects the group's items. With nothing to list it
 * shows a line of text ("This folder is empty.", "Loading…", an error).
 */
final class FileTable implements ItemView {

    /** A column: what it shows and sorts by. */
    record Column(FileOrder.Key key, String title, int width, int align) {
    }

    private static final DateTimeFormatter DATES = dateFormat(Locale.getDefault());
    private static final int CHEVRON = 16;

    final Table table;
    private final FileBrowser browser;
    private List<Column> columns = List.of();
    /** What each row of the table is: a {@link FileEntry}, or the {@link Section} a header row starts. */
    private List<Object> lines = List.of();
    private List<FileEntry> rows = List.of();
    private Map<FilePath, Integer> lineOf = Map.of();
    private String message;
    private Point mouseDown;

    FileTable(FileBrowser browser, Composite parent) {
        this.browser = browser;
        table = new Table(parent, SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.addListener(SWT.SetData, this::setData);
        table.addListener(SWT.Paint, this::paintMessage);
        table.addListener(SWT.MouseDown, e -> mouseDown = new Point(e.x, e.y));
        table.addListener(SWT.MouseDown, this::slowClick);
        table.addListener(SWT.MouseDoubleClick, e -> slowClickGeneration++);
        table.addListener(SWT.KeyDown, e -> slowClickGeneration++);
        // Before the browser's: a header row is never selected itself.
        table.addListener(SWT.Selection, this::headerSelected);
        table.addListener(SWT.DefaultSelection, e -> {
            if (e.item instanceof TableItem item && headerOf(item) != null) {
                e.type = SWT.None;
            }
        });
        table.addListener(SWT.EraseItem, this::eraseHeader);
        table.addListener(SWT.PaintItem, this::paintHeader);
        ItemPainter.install(table, item -> {
            FileEntry entry = entryOf((TableItem) item);
            return entry != null ? browser.icons().get(entry, browser.iconSize(), browser.isCut(entry)) : null;
        }, item -> {
            FileEntry entry = entryOf((TableItem) item);
            return entry != null ? browser.displayName(entry) : "";
        });
    }

    @Override
    public Control control() {
        return table;
    }

    /** The entry of {@code item}, or null (a header row). */
    FileEntry entryOf(TableItem item) {
        int index = table.indexOf(item);
        return index >= 0 && index < lines.size() && lines.get(index) instanceof FileEntry entry ? entry : null;
    }

    /** The group a header row {@code item} starts, or null. */
    Section headerOf(TableItem item) {
        int index = table.indexOf(item);
        return index >= 0 && index < lines.size() && lines.get(index) instanceof Section section ? section : null;
    }

    /** The row of {@code entry}, or null if it isn't shown. */
    TableItem itemOf(FileEntry entry) {
        Integer line = lineOf.get(entry.path());
        return line == null ? null : table.getItem(line);
    }

    /** The header row of the group {@code label}, or null (tests). */
    TableItem headerItem(String label) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) instanceof Section section && label.equals(section.label())) {
                return table.getItem(i);
            }
        }
        return null;
    }

    /**
     * Gives row {@code index} the theme's stripe as its background, as the Table styler would on its
     * first paint: set while painting, it makes GTK render the row twice, and the drawn names look bold.
     */
    private void stripe(TableItem item, int index) {
        ThemeManager manager = ThemeManager.get(table.getDisplay());
        Color stripe = new ThemeContext(manager.resources()).color(manager.styleOf(table), "alternateRowBackground");
        item.setBackground(stripe != null && index % 2 == 1 && lines.get(index) instanceof FileEntry ? stripe : null);
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
        TableItem item = table.getItem(new Point(event.x, event.y));
        if (item == null || table.indexOf(item) != table.getSelectionIndex() || !item.getBounds(0).contains(event.x, event.y)) {
            return;
        }
        FileEntry entry = entryOf(item);
        table.getDisplay().timerExec(table.getDisplay().getDoubleClickTime() + 50, () -> {
            if (generation == slowClickGeneration && !table.isDisposed() && table.getSelectionCount() == 1 && entry != null
                    && entry.equals(selection().stream().findFirst().orElse(null))) {
                browser.actions().rename();
            }
        });
    }

    private int slowClickGeneration;

    @Override
    public void cancelSlowClick() {
        slowClickGeneration++;
    }

    private InlineEditor editor;

    @Override
    public boolean startRename(FileEntry entry, Consumer<String> commit) {
        TableItem item = itemOf(entry);
        if (item == null) {
            return false;
        }
        if (editor != null && editor.isOpen()) {
            editor.finish(false);
        }
        table.showItem(item);
        Rectangle cell = item.getBounds(0);
        int offset = ThemeManager.get(table.getDisplay()).resources().zoomedPixels(2 + 6) + browser.iconSize();
        Rectangle bounds = new Rectangle(cell.x + offset, cell.y, Math.max(40, cell.width - offset), cell.height);
        editor = InlineEditor.open(table, bounds, entry.name(), entry.isContainer(), commit, () -> table.setFocus());
        return true;
    }

    @Override
    public InlineEditor editor() {
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
        return DateTimeFormatter.ofPattern(date + " " + time.replace(' ', ' '), locale).withZone(ZoneId.systemDefault());
    }

    /** A date as the Details view shows it. */
    static String formatDate(java.time.Instant instant) {
        return instant == null ? "" : DATES.format(instant);
    }

    /** The columns for a filesystem with {@code capabilities}, without those of {@code hidden} (Name always). */
    static List<Column> columnsFor(Set<Capability> capabilities, Set<FileOrder.Key> hidden) {
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
            list.add(new Column(FileOrder.Key.GROUP, "Group", 90, SWT.LEFT));
        }
        list.removeIf(column -> column.key() != FileOrder.Key.NAME && hidden.contains(column.key()));
        return list;
    }

    /** Whether {@code point} (in the table) is on the column headers. */
    boolean onHeader(org.eclipse.swt.graphics.Point point) {
        Rectangle area = table.getClientArea();
        return table.getHeaderVisible() && point.y >= area.y && point.y < area.y + table.getHeaderHeight();
    }

    /** Sets the columns for a filesystem with {@code capabilities}, leaving out those of {@code hidden}. */
    void setColumns(Set<Capability> capabilities, Set<FileOrder.Key> hidden) {
        List<Column> list = columnsFor(capabilities, hidden);
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
                    browser.setSort(column.key(), column.key() == browser.getSortKey() ? !browser.isSortAscending() : true);
                }
            });
        }
        showSort(browser.getSortKey(), browser.isSortAscending());
    }

    List<Column> columns() {
        return columns;
    }

    /** Shows the sort arrow on {@code key}'s column. */
    void showSort(FileOrder.Key key, boolean ascending) {
        for (org.eclipse.swt.widgets.TableColumn column : table.getColumns()) {
            if (column.getData() instanceof Column c && c.key() == key) {
                table.setSortColumn(column);
                table.setSortDirection(ascending ? SWT.UP : SWT.DOWN);
                return;
            }
        }
        table.setSortColumn(null);
        table.setSortDirection(SWT.NONE);
    }

    @Override
    public void setSections(List<Section> sections, List<FilePath> select) {
        List<Object> list = new ArrayList<>();
        Map<FilePath, Integer> index = new HashMap<>();
        for (Section section : sections) {
            if (section.label() != null) {
                list.add(section);
            }
            for (FileEntry entry : section.entries()) {
                index.put(entry.path(), list.size());
                list.add(entry);
            }
        }
        lines = List.copyOf(list);
        lineOf = index;
        rows = Section.entriesOf(sections);
        message = null;
        // Not for a hidden table: on Windows turning its redraw back on would show it.
        boolean redraw = table.getVisible();
        if (redraw) {
            table.setRedraw(false);
        }
        table.deselectAll();
        table.setItemCount(lines.size());
        table.clearAll();
        if (!select.isEmpty()) {
            Set<FilePath> wanted = new HashSet<>(select);
            List<Integer> indices = new ArrayList<>();
            for (FileEntry entry : rows) {
                if (wanted.contains(entry.path())) {
                    indices.add(lineOf.get(entry.path()));
                }
            }
            if (!indices.isEmpty()) {
                table.select(indices.stream().mapToInt(Integer::intValue).toArray());
                table.showItem(table.getItem(indices.getFirst()));
            }
        } else if (!lines.isEmpty()) {
            table.setTopIndex(0);
        }
        fillFirstRows();
        if (redraw) {
            table.setRedraw(true);
        }
        table.redraw();
    }

    @Override
    public void showMessage(String text) {
        lines = List.of();
        rows = List.of();
        lineOf = Map.of();
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
        for (int i = 0; i < Math.min(lines.size(), 500); i++) {
            fill(table.getItem(i), i);
        }
    }

    @Override
    public void refreshRows() {
        restripe();
        table.redraw();
    }

    @Override
    public List<FileEntry> rows() {
        return rows;
    }

    @Override
    public String message() {
        return lines.isEmpty() ? message != null ? message : "This folder is empty." : null;
    }

    @Override
    public List<FileEntry> selection() {
        List<FileEntry> list = new ArrayList<>();
        for (int index : table.getSelectionIndices()) {
            if (index < lines.size() && lines.get(index) instanceof FileEntry entry) {
                list.add(entry);
            }
        }
        return list;
    }

    @Override
    public void select(List<FileEntry> entries) {
        List<Integer> indices = new ArrayList<>();
        for (FileEntry entry : entries) {
            Integer line = lineOf.get(entry.path());
            if (line != null) {
                indices.add(line);
            }
        }
        table.deselectAll();
        table.select(indices.stream().mapToInt(Integer::intValue).toArray());
        if (!indices.isEmpty()) {
            table.showItem(table.getItem(indices.getFirst()));
        }
    }

    @Override
    public FileEntry entryAt(int x, int y) {
        TableItem item = table.getItem(new Point(x, y));
        return item == null ? null : entryOf(item);
    }

    private FileEntry dropTarget;

    @Override
    public void setDropTarget(FileEntry entry) {
        TableItem item = entry == null ? null : itemOf(entry);
        dropTarget = item == null ? null : entry;
        browser.dragAndDrop().highlight(item);
    }

    @Override
    public FileEntry dropTarget() {
        return dropTarget;
    }

    /** The texts of {@code entry}, column by column. */
    String[] texts(FileEntry entry) {
        String[] texts = new String[columns.size()];
        for (int i = 0; i < texts.length; i++) {
            Column column = columns.get(i);
            texts[i] = switch (column.key()) {
                case NAME -> browser.displayName(entry);
                case MODIFIED -> formatDate(entry.modified());
                case TYPE -> FileTypes.typeName(entry);
                case SIZE -> entry.isContainer() ? "" : FileTypes.detailsSize(entry.size());
                case PERMISSIONS -> nonNull(entry.permissions());
                case OWNER -> nonNull(entry.owner());
                case GROUP -> nonNull(entry.group());
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
        if (index >= 0 && index < lines.size()) {
            fill(item, index);
        }
    }

    private void fill(TableItem item, int index) {
        if (lines.get(index) instanceof FileEntry entry) {
            item.setText(texts(entry));
        } else {
            item.setText(new String[columns.size()]);
        }
        stripe(item, index);
    }

    // ------------------------------------------------------------------ group headers

    private int px(int pixels) {
        return ThemeManager.get(table.getDisplay()).resources().zoomedPixels(pixels);
    }

    /** A header row selected: its chevron collapses the group, elsewhere it selects the group's items. */
    private void headerSelected(Event event) {
        if (!(event.item instanceof TableItem item)) {
            return;
        }
        Section section = headerOf(item);
        if (section == null) {
            // Keys or a range may take headers along: they aren't selectable.
            deselectHeaders();
            return;
        }
        Point down = mouseDown;
        mouseDown = null;
        boolean onChevron = down != null && item.getBounds(0).contains(down) && down.x < item.getBounds(0).x + px(CHEVRON + 6);
        if (onChevron) {
            event.type = SWT.None;
            browser.toggleGroup(section.label());
            return;
        }
        List<FileEntry> entries = section.entries();
        if (entries.isEmpty()) {
            table.deselect(table.indexOf(item));
        } else {
            select(entries);
        }
    }

    private void deselectHeaders() {
        for (int index : table.getSelectionIndices()) {
            if (index < lines.size() && lines.get(index) instanceof Section) {
                table.deselect(index);
            }
        }
    }

    private void eraseHeader(Event event) {
        if (event.item instanceof TableItem item && headerOf(item) != null) {
            Rectangle area = table.getClientArea();
            int width = event.index == table.getColumnCount() - 1 ? Math.max(event.width, area.x + area.width - event.x) : event.width;
            event.gc.setBackground(table.getBackground());
            event.gc.fillRectangle(event.x, event.y, width, event.height);
            event.detail &= ~(SWT.BACKGROUND | SWT.FOREGROUND | SWT.SELECTED | SWT.HOT | SWT.FOCUSED);
        }
    }

    /** The group's name and count in the Name column, a line through the rest of the row. */
    private void paintHeader(Event event) {
        if (!(event.item instanceof TableItem item)) {
            return;
        }
        Section section = headerOf(item);
        if (section == null) {
            return;
        }
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        Rectangle cell = item.getBounds(event.index);
        int middle = cell.y + cell.height / 2;
        Color line = new Color(BarColors.blend(table.getForeground().getRGB(), table.getBackground().getRGB(), 0.2));
        int x = cell.x;
        if (event.index == 0) {
            x += px(4);
            FileGrid.drawChevron(gc, x, cell.y + (cell.height - px(CHEVRON)) / 2, px(CHEVRON), section.collapsed(), table.getForeground());
            x += px(CHEVRON + 6);
            String text = FileGrid.headerText(section);
            gc.setFont(table.getFont());
            gc.setForeground(table.getForeground());
            Point extent = gc.textExtent(text);
            gc.drawText(text, x, cell.y + (cell.height - extent.y) / 2, true);
            x += extent.x + px(8);
        }
        Rectangle area = table.getClientArea();
        int end = event.index == table.getColumnCount() - 1 ? area.x + area.width : cell.x + cell.width;
        if (end > x) {
            gc.setForeground(line);
            gc.drawLine(x, middle, end, middle);
        }
    }

    private void paintMessage(Event event) {
        String text = message();
        if (text == null) {
            return;
        }
        GC gc = event.gc;
        Rectangle area = table.getClientArea();
        int top = area.y + (table.getHeaderVisible() ? table.getHeaderHeight() : 0) + px(16);
        gc.setForeground(new Color(BarColors.blend(table.getForeground().getRGB(), table.getBackground().getRGB(), 0.7)));
        int width = gc.textExtent(text).x;
        gc.drawText(text, area.x + Math.max(0, (area.width - width) / 2), top, SWT.DRAW_TRANSPARENT);
    }
}
