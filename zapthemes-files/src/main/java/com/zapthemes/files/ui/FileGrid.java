package com.zapthemes.files.ui;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.ScrollBar;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileTypes;

/**
 * The owner-drawn layouts of the file view: Extra large, Large, Medium and Small icons, List,
 * Tiles and Content ({@link FileView}). Items flow in rows (List: in columns), under group
 * headers when grouped; image files show their picture in the larger layouts.
 *
 * <p>Selection as in Explorer: a click selects, Ctrl toggles, Shift selects a range; a drag on
 * empty space draws a rubber band; the arrow keys move by the layout, Home, End, Page Up and Page
 * Down too, Shift extends, Ctrl moves without selecting (Ctrl+Space toggles), and typing a name's
 * first letters goes to it. Enter or a double click opens.
 *
 * <p>Theme type {@code FileItemView}: {@code background}, {@code foreground},
 * {@code selectionBackground}, {@code selectionForeground}, {@code hoverBackground},
 * {@code mutedForeground}; keys it leaves out come from the Details view's {@code Table} rule.
 */
final class FileGrid extends Canvas implements ItemView {

    private static final int MARGIN = 8;
    private static final int GAP = 4;
    private static final int HEADER = 30;
    private static final int CHEVRON = 16;

    private final FileBrowser browser;
    private FileView view = FileView.LARGE_ICONS;
    private List<Section> sections = List.of();
    private List<FileEntry> rows = List.of();
    private Map<FilePath, Integer> indexOf = Map.of();
    private String message;

    // Layout, in content coordinates (the origin scrolls).
    private Rectangle[] cells = new Rectangle[0];
    private final List<Rectangle> headers = new ArrayList<>();
    private int contentWidth;
    private int contentHeight;
    private int originX;
    private int originY;

    // Selection.
    private final BitSet selected = new BitSet();
    private int focus = -1;
    private int anchor = -1;
    private int hover = -1;
    private int hoverHeader = -1;
    private FileEntry dropTarget;

    // Mouse.
    private Point bandStart;
    private Rectangle band;
    private BitSet bandBase;
    private int pressed = -1;
    private boolean pressedSelected;
    private int slowClickGeneration;

    private String typed = "";
    private long typedAt;
    private InlineEditor editor;

    @SuppressWarnings("this-escape")
    FileGrid(FileBrowser browser, Composite parent) {
        super(parent, SWT.V_SCROLL | SWT.H_SCROLL | SWT.DOUBLE_BUFFERED);
        this.browser = browser;
        getVerticalBar().setVisible(false);
        getHorizontalBar().setVisible(false);
        getVerticalBar().addListener(SWT.Selection, e -> scrollTo(originX, getVerticalBar().getSelection()));
        getHorizontalBar().addListener(SWT.Selection, e -> scrollTo(getHorizontalBar().getSelection(), originY));
        addListener(SWT.Paint, this::paint);
        addListener(SWT.Resize, e -> relayout());
        addListener(SWT.MouseDown, this::mouseDown);
        addListener(SWT.MouseMove, this::mouseMove);
        addListener(SWT.MouseUp, this::mouseUp);
        addListener(SWT.MouseDoubleClick, this::doubleClick);
        addListener(SWT.MouseExit, e -> setHover(-1, -1));
        addListener(SWT.MouseVerticalWheel, this::wheel);
        addListener(SWT.KeyDown, this::keyDown);
        addListener(SWT.Traverse, e -> {
            switch (e.detail) {
                case SWT.TRAVERSE_ARROW_NEXT, SWT.TRAVERSE_ARROW_PREVIOUS, SWT.TRAVERSE_RETURN, SWT.TRAVERSE_PAGE_NEXT,
                        SWT.TRAVERSE_PAGE_PREVIOUS -> e.doit = false;
                default -> e.doit = true;
            }
        });
        addListener(SWT.FocusIn, e -> redraw());
        addListener(SWT.FocusOut, e -> redraw());
        accessibility();
        ThemeManager.applyOnCreate(this, "FileItemView");
    }

    @Override
    public Control control() {
        return this;
    }

    private int px(int pixels) {
        return ThemeManager.get(getDisplay()).resources().zoomedPixels(pixels);
    }

    // ------------------------------------------------------------------ content

    /** Shows the entries in {@code view} (any but Details). */
    void setView(FileView view) {
        if (view != this.view) {
            this.view = view;
            finishEditing();
            originX = originY = 0;
            relayout();
        }
    }

    FileView view() {
        return view;
    }

    @Override
    public void setSections(List<Section> sections, List<FilePath> select) {
        finishEditing();
        this.sections = List.copyOf(sections);
        rows = Section.entriesOf(sections);
        Map<FilePath, Integer> index = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            index.put(rows.get(i).path(), i);
        }
        indexOf = index;
        message = null;
        selected.clear();
        hover = hoverHeader = -1;
        dropTarget = null;
        Set<FilePath> wanted = new HashSet<>(select);
        for (int i = 0; i < rows.size(); i++) {
            if (wanted.contains(rows.get(i).path())) {
                selected.set(i);
            }
        }
        focus = selected.isEmpty() ? (rows.isEmpty() ? -1 : 0) : selected.nextSetBit(0);
        anchor = focus;
        if (select.isEmpty()) {
            originX = originY = 0;
        }
        relayout();
        if (!selected.isEmpty()) {
            showIndex(selected.nextSetBit(0));
        }
    }

    @Override
    public void showMessage(String text) {
        finishEditing();
        sections = List.of();
        rows = List.of();
        indexOf = Map.of();
        selected.clear();
        focus = anchor = hover = hoverHeader = -1;
        message = text;
        originX = originY = 0;
        relayout();
    }

    @Override
    public String message() {
        return rows.isEmpty() && sections.stream().allMatch(s -> s.label() == null) ? message != null ? message : "This folder is empty." : null;
    }

    @Override
    public List<FileEntry> rows() {
        return rows;
    }

    @Override
    public List<FileEntry> selection() {
        List<FileEntry> list = new ArrayList<>();
        for (int i = selected.nextSetBit(0); i >= 0 && i < rows.size(); i = selected.nextSetBit(i + 1)) {
            list.add(rows.get(i));
        }
        return list;
    }

    @Override
    public void select(List<FileEntry> entries) {
        selected.clear();
        int first = -1;
        for (FileEntry entry : entries) {
            Integer i = indexOf.get(entry.path());
            if (i != null) {
                selected.set(i);
                first = first < 0 ? i : Math.min(first, i);
            }
        }
        if (first >= 0) {
            focus = anchor = first;
            showIndex(first);
        }
        redraw();
    }

    @Override
    public void refreshRows() {
        relayout();
    }

    @Override
    public FileEntry entryAt(int x, int y) {
        int i = indexAt(x, y);
        return i < 0 ? null : rows.get(i);
    }

    @Override
    public void setDropTarget(FileEntry entry) {
        if (entry != dropTarget) {
            dropTarget = entry;
            redraw();
        }
    }

    @Override
    public FileEntry dropTarget() {
        return dropTarget;
    }

    /** The focused entry (the keyboard's), or null. */
    FileEntry focused() {
        return focus >= 0 && focus < rows.size() ? rows.get(focus) : null;
    }

    /** The bounds of {@code entry} in the control, or null if it isn't shown (tests). */
    Rectangle boundsOf(FileEntry entry) {
        Integer i = indexOf.get(entry.path());
        return i == null ? null : toControl(cells[i]);
    }

    /** The bounds of group {@code label}'s header in the control, or null (tests). */
    Rectangle headerBounds(String label) {
        int h = 0;
        for (Section section : sections) {
            if (section.label() != null) {
                if (section.label().equals(label)) {
                    return toControl(headers.get(h));
                }
                h++;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ layout

    private record Metrics(int icon, int cellWidth, int cellHeight, int line, int pad) {
    }

    private Metrics metrics() {
        GC gc = new GC(this);
        try {
            gc.setFont(getFont());
            int line = gc.getFontMetrics().getHeight();
            int icon = px(view.iconSize());
            int pad = px(view.namesBelow() ? 6 : 4);
            return switch (view) {
                case EXTRA_LARGE_ICONS -> new Metrics(icon, icon + 2 * pad, pad + icon + px(4) + 2 * line + pad, line, pad);
                case LARGE_ICONS -> new Metrics(icon, icon + px(28), pad + icon + px(4) + 2 * line + pad, line, pad);
                case MEDIUM_ICONS -> new Metrics(icon, icon + px(44), pad + icon + px(4) + 2 * line + pad, line, pad);
                case SMALL_ICONS -> new Metrics(icon, px(220), Math.max(icon, line) + px(6), line, pad);
                case LIST -> new Metrics(icon, listWidth(gc, icon), Math.max(icon, line) + px(6), line, pad);
                case TILES -> new Metrics(icon, px(260), Math.max(icon, 3 * line) + 2 * px(8), line, pad);
                case CONTENT, DETAILS -> new Metrics(icon, 0, Math.max(icon, 2 * line) + 2 * px(8), line, px(8));
            };
        } finally {
            gc.dispose();
        }
    }

    /** List's column width: the widest name, within limits. */
    private int listWidth(GC gc, int icon) {
        int widest = px(120);
        for (int i = 0; i < Math.min(rows.size(), 2000); i++) {
            widest = Math.max(widest, gc.textExtent(browser.displayName(rows.get(i))).x);
        }
        return Math.min(px(360), icon + px(6) + widest + px(16));
    }

    /** Lays the items out for the client area, showing the scroll bars that are needed. */
    private void relayout() {
        if (isDisposed()) {
            return;
        }
        Rectangle area = getClientArea();
        boolean sideways = view == FileView.LIST && sections.stream().allMatch(s -> s.label() == null);
        ScrollBar vertical = getVerticalBar();
        ScrollBar horizontal = getHorizontalBar();
        // Twice at most: a scroll bar that appears takes room.
        for (int pass = 0; pass < 2; pass++) {
            area = getClientArea();
            layoutFor(area.width, area.height, sideways);
            boolean needV = !sideways && contentHeight > area.height;
            boolean needH = sideways && contentWidth > area.width;
            if (vertical.getVisible() == needV && horizontal.getVisible() == needH) {
                break;
            }
            vertical.setVisible(needV);
            horizontal.setVisible(needH);
        }
        area = getClientArea();
        configure(vertical, contentHeight, area.height, px(40));
        configure(horizontal, contentWidth, area.width, px(40));
        originY = vertical.getVisible() ? Math.max(0, Math.min(originY, contentHeight - area.height)) : 0;
        originX = horizontal.getVisible() ? Math.max(0, Math.min(originX, contentWidth - area.width)) : 0;
        vertical.setSelection(originY);
        horizontal.setSelection(originX);
        redraw();
    }

    private static void configure(ScrollBar bar, int content, int visible, int step) {
        if (bar.getVisible()) {
            bar.setValues(bar.getSelection(), 0, Math.max(content, visible), Math.max(1, visible), step, Math.max(1, visible - step));
        }
    }

    private void layoutFor(int width, int height, boolean sideways) {
        Metrics m = metrics();
        int margin = px(MARGIN);
        int available = Math.max(1, width - 2 * margin);
        int cellWidth = view == FileView.CONTENT ? available : m.cellWidth();
        int cellHeight = m.cellHeight();
        cells = new Rectangle[rows.size()];
        headers.clear();
        int index = 0;
        int y = margin;
        int widest = 0;
        if (sideways) {
            int perColumn = Math.max(1, (height - 2 * margin) / cellHeight);
            for (int i = 0; i < rows.size(); i++) {
                cells[i] = new Rectangle(margin + i / perColumn * cellWidth, margin + i % perColumn * cellHeight, cellWidth, cellHeight);
                widest = Math.max(widest, cells[i].x + cellWidth);
            }
            contentWidth = widest + margin;
            contentHeight = height;
            return;
        }
        int columns = Math.max(1, available / cellWidth);
        for (Section section : sections) {
            if (section.label() != null) {
                headers.add(new Rectangle(margin, y, available, px(HEADER)));
                y += px(HEADER) + px(GAP);
            }
            int count = section.entries().size();
            int lines = (count + columns - 1) / columns;
            for (int i = 0; i < count; i++) {
                int column;
                int row;
                if (view == FileView.LIST) {
                    // Down the columns, as List does.
                    column = i / Math.max(1, lines);
                    row = i % Math.max(1, lines);
                } else {
                    column = i % columns;
                    row = i / columns;
                }
                cells[index++] = new Rectangle(margin + column * cellWidth, y + row * cellHeight, cellWidth, cellHeight);
            }
            y += lines * cellHeight;
            if (section.label() != null) {
                y += px(12);
            }
        }
        contentWidth = width;
        contentHeight = y + margin;
    }

    private Rectangle toControl(Rectangle content) {
        return new Rectangle(content.x - originX, content.y - originY, content.width, content.height);
    }

    private void scrollTo(int x, int y) {
        Rectangle area = getClientArea();
        int nx = Math.max(0, Math.min(x, contentWidth - area.width));
        int ny = Math.max(0, Math.min(y, contentHeight - area.height));
        if (nx != originX || ny != originY) {
            finishEditing();
            originX = nx;
            originY = ny;
            getVerticalBar().setSelection(originY);
            getHorizontalBar().setSelection(originX);
            redraw();
        }
    }

    /** Scrolls so item {@code i} shows. */
    private void showIndex(int i) {
        if (i < 0 || i >= cells.length) {
            return;
        }
        Rectangle cell = cells[i];
        Rectangle area = getClientArea();
        int x = originX;
        int y = originY;
        if (cell.y < y) {
            y = cell.y - px(MARGIN);
        } else if (cell.y + cell.height > y + area.height) {
            y = cell.y + cell.height - area.height + px(MARGIN);
        }
        if (cell.x < x) {
            x = cell.x - px(MARGIN);
        } else if (cell.x + cell.width > x + area.width) {
            x = cell.x + cell.width - area.width + px(MARGIN);
        }
        scrollTo(x, y);
    }

    private int indexAt(int x, int y) {
        int cx = x + originX;
        int cy = y + originY;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].contains(cx, cy)) {
                return i;
            }
        }
        return -1;
    }

    private int headerAt(int x, int y) {
        int cx = x + originX;
        int cy = y + originY;
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).contains(cx, cy)) {
                return i;
            }
        }
        return -1;
    }

    private Section headerSection(int header) {
        int h = 0;
        for (Section section : sections) {
            if (section.label() != null && h++ == header) {
                return section;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ painting

    /** The colors the view paints with. */
    record Colors(Color background, Color foreground, Color selection, Color selectionText, Color hover, Color muted, Color line,
            Color focus) {
    }

    Colors colors() {
        ThemeManager manager = ThemeManager.get(getDisplay());
        ThemeContext context = new ThemeContext(manager.resources());
        ResolvedStyle own = manager.styleOf(this);
        Control table = browser.fileTable().table;
        ResolvedStyle details = manager.styleOf(table);
        if (getDisplay().getHighContrast()) {
            var d = getDisplay();
            Color bg = d.getSystemColor(SWT.COLOR_LIST_BACKGROUND);
            Color fg = d.getSystemColor(SWT.COLOR_LIST_FOREGROUND);
            Color sel = d.getSystemColor(SWT.COLOR_LIST_SELECTION);
            return new Colors(bg, fg, sel, d.getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT), sel, fg, fg, fg);
        }
        Color background = own.has("background") ? context.color(own, "background") : table.getBackground();
        Color foreground = own.has("foreground") ? context.color(own, "foreground") : table.getForeground();
        RGB bg = background.getRGB();
        RGB fg = foreground.getRGB();
        RGB system = getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION).getRGB();
        Color selection = pick(context, own, details, "selectionBackground", null);
        if (selection == null) {
            selection = new Color(BarColors.blend(system, bg, 0.32));
        }
        Color selectionText = pick(context, own, details, "selectionForeground", null);
        if (selectionText == null || !own.has("selectionBackground") && !details.has("selectionBackground")) {
            selectionText = foreground;
        }
        Color hover = own.has("hoverBackground") ? context.color(own, "hoverBackground") : null;
        if (hover == null && details.states().contains("hover")) {
            hover = context.color(details.state("hover"), "background");
        }
        if (hover == null) {
            hover = new Color(BarColors.blend(system, bg, 0.14));
        }
        Color muted = own.has("mutedForeground") ? context.color(own, "mutedForeground") : new Color(BarColors.blend(fg, bg, 0.65));
        return new Colors(background, foreground, selection, selectionText, hover, muted, new Color(BarColors.blend(fg, bg, 0.18)),
                new Color(BarColors.blend(system, bg, 0.8)));
    }

    private static Color pick(ThemeContext context, ResolvedStyle own, ResolvedStyle details, String key, Color fallback) {
        Color color = own.has(key) ? context.color(own, key) : null;
        if (color == null && details.has(key)) {
            color = context.color(details, key);
        }
        return color != null ? color : fallback;
    }

    private void paint(Event event) {
        GC gc = event.gc;
        Colors colors = colors();
        Rectangle area = getClientArea();
        gc.setBackground(colors.background());
        gc.fillRectangle(event.x, event.y, event.width, event.height);
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        gc.setInterpolation(SWT.HIGH);
        gc.setFont(getFont());
        String text = message();
        if (text != null) {
            gc.setForeground(colors.muted());
            int width = gc.textExtent(text).x;
            gc.drawText(text, Math.max(0, (area.width - width) / 2), px(16), true);
            return;
        }
        Rectangle damage = new Rectangle(event.x + originX, event.y + originY, event.width, event.height);
        Metrics m = metrics();
        int h = 0;
        for (Section section : sections) {
            if (section.label() != null) {
                Rectangle header = headers.get(h);
                if (header.intersects(damage)) {
                    paintHeader(gc, colors, section, toControl(header), h == hoverHeader);
                }
                h++;
            }
        }
        for (int i = 0; i < cells.length; i++) {
            if (cells[i].intersects(damage)) {
                paintItem(gc, colors, m, i, toControl(cells[i]));
            }
        }
        if (band != null) {
            Rectangle r = toControl(band);
            gc.setAlpha(60);
            gc.setBackground(colors.focus());
            gc.fillRectangle(r);
            gc.setAlpha(255);
            gc.setForeground(colors.focus());
            gc.drawRectangle(r.x, r.y, r.width - 1, r.height - 1);
        }
    }

    /** "Today (3)": a group header's text. */
    static String headerText(Section section) {
        return section.label() + " (" + section.count() + ")";
    }

    /** A chevron pointing down (open) or right (collapsed), {@code size} wide. */
    static void drawChevron(GC gc, int x, int y, int size, boolean collapsed, Color color) {
        gc.setForeground(color);
        gc.setLineWidth(Math.max(1, size / 12));
        int c = size / 2;
        int d = Math.max(2, size / 5);
        if (collapsed) {
            gc.drawPolyline(new int[] { x + c - d / 2, y + c - d, x + c + d / 2, y + c, x + c - d / 2, y + c + d });
        } else {
            gc.drawPolyline(new int[] { x + c - d, y + c - d / 2, x + c, y + c + d / 2, x + c + d, y + c - d / 2 });
        }
        gc.setLineWidth(1);
    }

    private void paintHeader(GC gc, Colors colors, Section section, Rectangle r, boolean hot) {
        int arc = px(4);
        if (hot) {
            gc.setBackground(colors.hover());
            gc.fillRoundRectangle(r.x, r.y, r.width, r.height, arc, arc);
        }
        int x = r.x + px(4);
        drawChevron(gc, x, r.y + (r.height - px(CHEVRON)) / 2, px(CHEVRON), section.collapsed(), colors.foreground());
        x += px(CHEVRON + 6);
        String text = headerText(section);
        Point extent = gc.textExtent(text);
        gc.setForeground(colors.foreground());
        gc.drawText(text, x, r.y + (r.height - extent.y) / 2, true);
        x += extent.x + px(10);
        if (x < r.x + r.width) {
            gc.setForeground(colors.line());
            gc.drawLine(x, r.y + r.height / 2, r.x + r.width - px(4), r.y + r.height / 2);
        }
    }

    private void paintItem(GC gc, Colors colors, Metrics m, int i, Rectangle r) {
        FileEntry entry = rows.get(i);
        boolean isSelected = selected.get(i);
        boolean isDrop = entry.equals(dropTarget);
        int arc = px(4);
        Rectangle box = new Rectangle(r.x + px(1), r.y + px(1), r.width - px(2), r.height - px(2));
        if (isSelected || isDrop || i == hover) {
            gc.setBackground(isSelected ? colors.selection() : colors.hover());
            gc.fillRoundRectangle(box.x, box.y, box.width, box.height, arc, arc);
        }
        if (i == focus && isFocusControl() && !isSelected) {
            gc.setForeground(colors.focus());
            gc.drawRoundRectangle(box.x, box.y, box.width - 1, box.height - 1, arc, arc);
        }
        Color text = isSelected ? colors.selectionText() : colors.foreground();
        String name = browser.displayName(entry);
        int icon = m.icon();
        switch (view) {
            case EXTRA_LARGE_ICONS, LARGE_ICONS, MEDIUM_ICONS -> {
                drawIcon(gc, entry, r.x + (r.width - icon) / 2, r.y + m.pad(), icon);
                int top = r.y + m.pad() + icon + px(4);
                int width = r.width - 2 * px(4);
                gc.setForeground(text);
                List<String> lines = wrap(gc, name, width, 2);
                for (String line : lines) {
                    int w = gc.textExtent(line).x;
                    gc.drawText(line, r.x + (r.width - w) / 2, top, true);
                    top += m.line();
                }
            }
            case SMALL_ICONS, LIST -> {
                int x = r.x + px(4);
                drawIcon(gc, entry, x, r.y + (r.height - icon) / 2, icon);
                x += icon + px(6);
                gc.setForeground(text);
                gc.drawText(ellipsize(gc, name, r.x + r.width - px(4) - x), x, r.y + (r.height - m.line()) / 2, true);
            }
            case TILES -> {
                int x = r.x + px(8);
                drawIcon(gc, entry, x, r.y + (r.height - icon) / 2, icon);
                x += icon + px(10);
                int width = r.x + r.width - px(6) - x;
                List<String> lines = new ArrayList<>(List.of(name, browser.typeName(entry)));
                if (!entry.isContainer() && entry.size() >= 0) {
                    lines.add(FileTypes.shortSize(entry.size()));
                }
                int top = r.y + (r.height - lines.size() * m.line()) / 2;
                for (int l = 0; l < lines.size(); l++) {
                    gc.setForeground(l == 0 ? text : isSelected ? text : colors.muted());
                    gc.drawText(ellipsize(gc, lines.get(l), width), x, top, true);
                    top += m.line();
                }
            }
            case CONTENT, DETAILS -> {
                int x = r.x + px(8);
                drawIcon(gc, entry, x, r.y + (r.height - icon) / 2, icon);
                x += icon + px(12);
                int split = r.x + r.width * 55 / 100;
                int top = r.y + (r.height - 2 * m.line()) / 2;
                gc.setForeground(text);
                gc.drawText(ellipsize(gc, name, split - px(12) - x), x, top, true);
                gc.setForeground(isSelected ? text : colors.muted());
                gc.drawText(ellipsize(gc, browser.typeName(entry), split - px(12) - x), x, top + m.line(), true);
                int right = r.x + r.width - px(8);
                drawField(gc, colors, isSelected, "Date modified: ", FileTable.formatDate(entry.modified()), split, top, right - split);
                if (!entry.isContainer() && entry.size() >= 0) {
                    drawField(gc, colors, isSelected, "Size: ", FileTypes.shortSize(entry.size()), split, top + m.line(), right - split);
                }
                gc.setForeground(colors.line());
                gc.drawLine(r.x + px(4), r.y + r.height - 1, r.x + r.width - px(4), r.y + r.height - 1);
            }
        }
    }

    private void drawField(GC gc, Colors colors, boolean isSelected, String label, String value, int x, int y, int width) {
        if (value.isEmpty()) {
            return;
        }
        gc.setForeground(isSelected ? colors.selectionText() : colors.muted());
        gc.drawText(label, x, y, true);
        int w = gc.textExtent(label).x;
        gc.setForeground(isSelected ? colors.selectionText() : colors.foreground());
        gc.drawText(ellipsize(gc, value, width - w), x + w, y, true);
    }

    /** The icon of {@code entry}, or its picture for an image in the larger layouts, {@code size} wide at {@code (x, y)}. */
    private void drawIcon(GC gc, FileEntry entry, int x, int y, int size) {
        if (view.thumbnails()) {
            Image picture = browser.thumbnails().get(entry, size, this::redrawSoon);
            if (picture != null) {
                Rectangle b = picture.getBounds();
                double scale = Math.min((double) size / b.width, (double) size / b.height);
                int w = Math.max(1, (int) Math.round(b.width * scale));
                int h = Math.max(1, (int) Math.round(b.height * scale));
                int dx = x + (size - w) / 2;
                int dy = y + (size - h) / 2;
                if (browser.isCut(entry)) {
                    gc.setAlpha(128);
                }
                gc.drawImage(picture, 0, 0, b.width, b.height, dx, dy, w, h);
                gc.setAlpha(255);
                return;
            }
        }
        Image image = browser.icons().get(entry, size, browser.isCut(entry));
        gc.drawImage(image, x, y);
    }

    private boolean redrawPending;

    private void redrawSoon() {
        if (!redrawPending && !isDisposed()) {
            redrawPending = true;
            getDisplay().timerExec(30, () -> {
                redrawPending = false;
                if (!isDisposed()) {
                    redraw();
                }
            });
        }
    }

    /** {@code text} in at most {@code max} lines of {@code width}, broken after spaces, hyphens, underscores and dots where it can, the last one cut with "…". */
    static List<String> wrap(GC gc, String text, int width, int max) {
        List<String> lines = new ArrayList<>();
        String rest = text;
        while (!rest.isEmpty() && lines.size() < max) {
            if (lines.size() == max - 1 || gc.textExtent(rest).x <= width) {
                lines.add(ellipsize(gc, rest, width));
                break;
            }
            int end = fit(gc, rest, width);
            // After a space, hyphen, underscore or dot where one fits, as Explorer breaks names.
            int cut = Math.max(1, end);
            // A space may sit right after what fits; a hyphen must fit itself.
            for (int i = Math.min(end, rest.length() - 1); i > 0; i--) {
                char c = rest.charAt(i);
                if (c == ' ' || (c == '-' || c == '_' || c == '.') && i + 1 <= end) {
                    cut = c == ' ' ? i : i + 1;
                    break;
                }
            }
            lines.add(rest.substring(0, cut).stripTrailing());
            rest = rest.substring(cut).stripLeading();
        }
        return lines;
    }

    /** How many characters of {@code text} fit in {@code width}. */
    private static int fit(GC gc, String text, int width) {
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (gc.textExtent(text.substring(0, mid)).x <= width) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /** {@code text}, cut with "…" to fit {@code width}. */
    static String ellipsize(GC gc, String text, int width) {
        if (width <= 0) {
            return "";
        }
        if (gc.textExtent(text).x <= width) {
            return text;
        }
        String dots = "…";
        int n = fit(gc, text, Math.max(0, width - gc.textExtent(dots).x));
        return text.substring(0, n) + dots;
    }

    // ------------------------------------------------------------------ mouse

    private void setHover(int item, int header) {
        if (item != hover || header != hoverHeader) {
            hover = item;
            hoverHeader = header;
            setToolTipText(item >= 0 ? tip(rows.get(item)) : null);
            redraw();
        }
    }

    /** The tooltip of an item: its name, type, size and date, as Explorer's. */
    private String tip(FileEntry entry) {
        StringBuilder text = new StringBuilder(entry.displayName()).append("\nType: ").append(browser.typeName(entry));
        if (!entry.isContainer() && entry.size() >= 0) {
            text.append("\nSize: ").append(FileTypes.shortSize(entry.size()));
        }
        if (entry.modified() != null) {
            text.append("\nDate modified: ").append(FileTable.formatDate(entry.modified()));
        }
        return text.toString();
    }

    private void mouseDown(Event event) {
        setFocus();
        int generation = ++slowClickGeneration;
        boolean ctrl = (event.stateMask & SWT.MOD1) != 0;
        boolean shift = (event.stateMask & SWT.SHIFT) != 0;
        int header = headerAt(event.x, event.y);
        if (header >= 0) {
            if (event.button != 1) {
                return;
            }
            Section section = headerSection(header);
            Rectangle r = toControl(headers.get(header));
            if (event.x < r.x + px(CHEVRON + 10)) {
                browser.toggleGroup(section.label());
            } else {
                select(section.entries());
                changed();
            }
            return;
        }
        int i = indexAt(event.x, event.y);
        pressed = i;
        pressedSelected = i >= 0 && selected.get(i);
        if (i < 0) {
            if (event.button == 1) {
                bandStart = new Point(event.x + originX, event.y + originY);
                bandBase = ctrl ? (BitSet) selected.clone() : new BitSet();
            }
            if (!ctrl && !selected.isEmpty()) {
                selected.clear();
                redraw();
                changed();
            }
            return;
        }
        if (event.button != 1 && pressedSelected) {
            return; // A right click on the selection keeps it.
        }
        if (shift && anchor >= 0) {
            if (!ctrl) {
                selected.clear();
            }
            selected.set(Math.min(anchor, i), Math.max(anchor, i) + 1);
            focus = i;
        } else if (ctrl) {
            selected.flip(i);
            focus = anchor = i;
        } else if (!pressedSelected) {
            selected.clear();
            selected.set(i);
            focus = anchor = i;
        } else {
            focus = i;
            // A second click on the one selected item, not part of a double click, renames it.
            if (event.button == 1 && selected.cardinality() == 1 && onName(i, event.x, event.y)) {
                FileEntry entry = rows.get(i);
                getDisplay().timerExec(getDisplay().getDoubleClickTime() + 50, () -> {
                    if (generation == slowClickGeneration && !isDisposed() && selection().equals(List.of(entry))) {
                        browser.actions().rename();
                    }
                });
            }
        }
        redraw();
        changed();
    }

    /** Whether {@code (x, y)} is on item {@code i}'s name. */
    private boolean onName(int i, int x, int y) {
        Rectangle name = nameBounds(i);
        return name != null && name.contains(x, y);
    }

    private void mouseMove(Event event) {
        if (bandStart != null && (event.stateMask & SWT.BUTTON1) != 0) {
            Point now = new Point(event.x + originX, event.y + originY);
            band = new Rectangle(Math.min(bandStart.x, now.x), Math.min(bandStart.y, now.y), Math.abs(now.x - bandStart.x),
                    Math.abs(now.y - bandStart.y));
            BitSet next = (BitSet) bandBase.clone();
            for (int i = 0; i < cells.length; i++) {
                if (cells[i].intersects(band)) {
                    next.set(i);
                }
            }
            if (!next.equals(selected)) {
                selected.clear();
                selected.or(next);
                changed();
            }
            // Scroll along at the edges.
            Rectangle area = getClientArea();
            if (event.y > area.height) {
                scrollTo(originX, originY + px(20));
            } else if (event.y < 0) {
                scrollTo(originX, originY - px(20));
            }
            redraw();
            return;
        }
        int header = headerAt(event.x, event.y);
        setHover(header >= 0 ? -1 : indexAt(event.x, event.y), header);
    }

    private void mouseUp(Event event) {
        if (band != null || bandStart != null) {
            band = null;
            bandStart = null;
            bandBase = null;
            redraw();
            return;
        }
        int i = indexAt(event.x, event.y);
        // A click (not a drag) on one of several selected items selects it alone.
        if (event.button == 1 && i >= 0 && i == pressed && pressedSelected && (event.stateMask & (SWT.MOD1 | SWT.SHIFT)) == 0
                && selected.cardinality() > 1) {
            selected.clear();
            selected.set(i);
            focus = anchor = i;
            redraw();
            changed();
        }
        pressed = -1;
    }

    private void doubleClick(Event event) {
        slowClickGeneration++;
        if (event.button == 1 && indexAt(event.x, event.y) >= 0) {
            notifyListeners(SWT.DefaultSelection, new Event());
        }
    }

    private void wheel(Event event) {
        if (!getVerticalBar().getVisible() && getHorizontalBar().getVisible()) {
            scrollTo(originX - event.count * px(40), originY);
            event.doit = false;
        }
    }

    /** Ends a rubber band (a drag starts instead: the band never began). */
    void cancelBand() {
        band = null;
        bandStart = null;
        bandBase = null;
    }

    /** Scrolls while a drag is held near an edge, so a drop can reach items out of view. */
    void scrollForDrop(Point point) {
        Rectangle area = getClientArea();
        int edge = px(24);
        int step = px(20);
        if (getVerticalBar().getVisible()) {
            scrollTo(originX, point.y < edge ? originY - step : point.y > area.height - edge ? originY + step : originY);
        } else if (getHorizontalBar().getVisible()) {
            scrollTo(point.x < edge ? originX - step : point.x > area.width - edge ? originX + step : originX, originY);
        }
    }

    /** Whether a drag starting at {@code (x, y)} would take items (not a rubber band on empty space). */
    boolean dragsItems(int x, int y) {
        int i = indexAt(x, y);
        return i >= 0 && selected.get(i);
    }

    @Override
    public void cancelSlowClick() {
        slowClickGeneration++;
    }

    private void changed() {
        getAccessible().setFocus(focus >= 0 ? focus : ACC.CHILDID_SELF);
        notifyListeners(SWT.Selection, new Event());
    }

    // ------------------------------------------------------------------ keys

    private void keyDown(Event event) {
        slowClickGeneration++;
        if (rows.isEmpty()) {
            return;
        }
        boolean ctrl = (event.stateMask & SWT.MOD1) != 0;
        boolean shift = (event.stateMask & SWT.SHIFT) != 0;
        boolean alt = (event.stateMask & SWT.ALT) != 0;
        if (alt) {
            return;
        }
        int from = focus < 0 ? 0 : focus;
        int to = switch (event.keyCode) {
            case SWT.ARROW_LEFT -> beside(from, -1);
            case SWT.ARROW_RIGHT -> beside(from, 1);
            case SWT.ARROW_UP -> above(from, -1, 1);
            case SWT.ARROW_DOWN -> above(from, 1, 1);
            case SWT.PAGE_UP -> above(from, -1, Math.max(1, getClientArea().height));
            case SWT.PAGE_DOWN -> above(from, 1, Math.max(1, getClientArea().height));
            case SWT.HOME -> 0;
            case SWT.END -> rows.size() - 1;
            default -> -2;
        };
        if (to == -2) {
            if (event.character == ' ' && ctrl) {
                selected.flip(from);
                focus = anchor = from;
                redraw();
                changed();
                event.doit = false;
            } else if (event.character == ' ' && !shift) {
                selected.set(from);
                redraw();
                changed();
                event.doit = false;
            } else if (event.keyCode == SWT.CR || event.keyCode == SWT.KEYPAD_CR) {
                notifyListeners(SWT.DefaultSelection, new Event());
                event.doit = false;
            } else if (!ctrl && event.character > ' ' && event.character != SWT.DEL) {
                typeAhead(event.character, event.time);
                event.doit = false;
            }
            return;
        }
        event.doit = false;
        if (to < 0) {
            return;
        }
        if (shift) {
            if (anchor < 0) {
                anchor = from;
            }
            if (!ctrl) {
                selected.clear();
            }
            selected.set(Math.min(anchor, to), Math.max(anchor, to) + 1);
        } else if (!ctrl) {
            selected.clear();
            selected.set(to);
            anchor = to;
        }
        focus = to;
        showIndex(to);
        redraw();
        changed();
    }

    /** The item beside {@code i} on its line ({@code step} -1 left, 1 right), else the one before or after it. */
    private int beside(int i, int step) {
        Rectangle cell = cells[i];
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int j = 0; j < cells.length; j++) {
            Rectangle c = cells[j];
            if (j == i || c.y != cell.y) {
                continue;
            }
            int d = (c.x - cell.x) * step;
            if (d > 0 && d < bestDistance) {
                best = j;
                bestDistance = d;
            }
        }
        if (best >= 0) {
            return best;
        }
        // At the end of a line Explorer goes on to the next item (List: to the next column's).
        return view == FileView.LIST ? above(i, step, 1) : Math.max(0, Math.min(rows.size() - 1, i + step));
    }

    /** The item at least {@code distance} above ({@code step} -1) or below (1) {@code i}, nearest its column; i itself if none. */
    private int above(int i, int step, int distance) {
        Rectangle cell = cells[i];
        int target = cell.y + step * distance;
        int best = i;
        long bestScore = Long.MAX_VALUE;
        for (int j = 0; j < cells.length; j++) {
            Rectangle c = cells[j];
            int dy = (c.y - cell.y) * step;
            if (j == i || dy <= 0) {
                continue;
            }
            // The line nearest the target first, then the item nearest the column.
            long score = Math.abs((long) c.y - target) * 100_000L + Math.abs(c.x - cell.x);
            if (score < bestScore) {
                best = j;
                bestScore = score;
            }
        }
        return best;
    }

    private void typeAhead(char c, int time) {
        long now = time & 0xFFFFFFFFL;
        typed = now - typedAt < 1000 ? typed + c : String.valueOf(c);
        typedAt = now;
        // The same letter again goes on to the next name with it, as Explorer does.
        boolean same = typed.chars().allMatch(ch -> ch == typed.charAt(0));
        String prefix = (same ? typed.substring(0, 1) : typed).toLowerCase(Locale.ROOT);
        int start = same ? focus + 1 : Math.max(0, focus);
        for (int k = 0; k < rows.size(); k++) {
            int j = (start + k) % rows.size();
            if (rows.get(j).displayName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                selected.clear();
                selected.set(j);
                focus = anchor = j;
                showIndex(j);
                redraw();
                changed();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ rename

    /** Where item {@code i}'s name is drawn, in the control. */
    private Rectangle nameBounds(int i) {
        if (i < 0 || i >= cells.length) {
            return null;
        }
        Metrics m = metrics();
        Rectangle r = toControl(cells[i]);
        return switch (view) {
            case EXTRA_LARGE_ICONS, LARGE_ICONS, MEDIUM_ICONS -> new Rectangle(r.x + px(2), r.y + m.pad() + m.icon() + px(2), r.width - px(4),
                    m.line() + px(6));
            case SMALL_ICONS, LIST -> {
                int x = r.x + px(4) + m.icon() + px(4);
                yield new Rectangle(x, r.y + (r.height - m.line() - px(6)) / 2, r.x + r.width - x - px(2), m.line() + px(6));
            }
            case TILES -> {
                int x = r.x + px(8) + m.icon() + px(8);
                yield new Rectangle(x, r.y + (r.height - 3 * m.line()) / 2 - px(3), r.x + r.width - x - px(4), m.line() + px(6));
            }
            case CONTENT, DETAILS -> {
                int x = r.x + px(8) + m.icon() + px(10);
                yield new Rectangle(x, r.y + (r.height - 2 * m.line()) / 2 - px(3), r.x + r.width * 55 / 100 - x, m.line() + px(6));
            }
        };
    }

    @Override
    public boolean startRename(FileEntry entry, Consumer<String> commit) {
        Integer i = indexOf.get(entry.path());
        if (i == null) {
            return false;
        }
        finishEditing();
        showIndex(i);
        Rectangle bounds = nameBounds(i);
        editor = InlineEditor.open(this, bounds, entry.name(), entry.isContainer(), commit, this::setFocus);
        return true;
    }

    @Override
    public int[] scroll() {
        return new int[] { originX, originY };
    }

    @Override
    public void setScroll(int[] position) {
        if (position.length == 2) {
            scrollTo(position[0], position[1]);
        }
    }

    @Override
    public InlineEditor editor() {
        return editor != null && editor.isOpen() ? editor : null;
    }

    private void finishEditing() {
        if (editor != null && editor.isOpen()) {
            editor.finish(true);
        }
    }

    // ------------------------------------------------------------------ accessibility

    private void accessibility() {
        getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                if (e.childID >= 0 && e.childID < rows.size()) {
                    e.result = rows.get(e.childID).displayName();
                } else if (e.childID == ACC.CHILDID_SELF) {
                    e.result = "Items View";
                }
            }

            @Override
            public void getDescription(AccessibleEvent e) {
                if (e.childID >= 0 && e.childID < rows.size()) {
                    e.result = tip(rows.get(e.childID)).replace('\n', ',');
                }
            }
        });
        getAccessible().addAccessibleControlListener(new AccessibleControlAdapter() {
            @Override
            public void getRole(AccessibleControlEvent e) {
                e.detail = e.childID == ACC.CHILDID_SELF ? ACC.ROLE_LIST : ACC.ROLE_LISTITEM;
            }

            @Override
            public void getChildCount(AccessibleControlEvent e) {
                e.detail = rows.size();
            }

            @Override
            public void getChildren(AccessibleControlEvent e) {
                Object[] children = new Object[rows.size()];
                for (int i = 0; i < children.length; i++) {
                    children[i] = i;
                }
                e.children = children;
            }

            @Override
            public void getFocus(AccessibleControlEvent e) {
                e.childID = !isFocusControl() ? ACC.CHILDID_NONE : focus >= 0 ? focus : ACC.CHILDID_SELF;
            }

            @Override
            public void getSelection(AccessibleControlEvent e) {
                int count = selected.cardinality();
                if (count == 0) {
                    e.childID = ACC.CHILDID_NONE;
                } else if (count == 1) {
                    e.childID = selected.nextSetBit(0);
                } else {
                    e.childID = ACC.CHILDID_MULTIPLE;
                    e.children = selected.stream().boxed().toArray();
                }
            }

            @Override
            public void getState(AccessibleControlEvent e) {
                if (e.childID == ACC.CHILDID_SELF) {
                    e.detail = ACC.STATE_NORMAL | (isFocusControl() ? ACC.STATE_FOCUSED : 0);
                    return;
                }
                int state = ACC.STATE_SELECTABLE | ACC.STATE_FOCUSABLE;
                if (e.childID >= 0 && selected.get(e.childID)) {
                    state |= ACC.STATE_SELECTED;
                }
                if (e.childID == focus && isFocusControl()) {
                    state |= ACC.STATE_FOCUSED;
                }
                e.detail = state;
            }

            @Override
            public void getChildAtPoint(AccessibleControlEvent e) {
                Point p = toControl(e.x, e.y);
                int i = indexAt(p.x, p.y);
                e.childID = i >= 0 ? i : getClientArea().contains(p) ? ACC.CHILDID_SELF : ACC.CHILDID_NONE;
            }

            @Override
            public void getLocation(AccessibleControlEvent e) {
                Rectangle r = e.childID >= 0 && e.childID < cells.length ? toControl(cells[e.childID]) : getClientArea();
                Point p = toDisplay(r.x, r.y);
                e.x = p.x;
                e.y = p.y;
                e.width = r.width;
                e.height = r.height;
            }
        });
    }
}
