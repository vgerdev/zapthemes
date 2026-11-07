package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntConsumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.Images;

/**
 * An in-ribbon gallery, like Office's Styles: a strip of item previews drawn by the app, with
 * arrows that scroll it a row at a time and an expand button that opens every item as a popup
 * grid (with optional commands under it). As its group shrinks it shows fewer columns, then
 * becomes a large drop-down button.
 *
 * <pre>{@code
 * group.add(new RibbonGallery("home.styles", "Styles", (gc, bounds, index, selected, hovered) -> ...)
 *         .items("Normal", "No Spacing", "Heading 1").itemSize(72, 56).columns(5, 3));
 * }</pre>
 */
public class RibbonGallery extends RibbonItem {

    /** Paints one item's preview into {@code bounds} (the cell, inside its selection border). */
    @FunctionalInterface
    public interface Renderer {
        void paint(GC gc, Rectangle bounds, int index, boolean selected, boolean hovered);
    }

    private final String label;
    private final Renderer renderer;
    private final List<String> items = new ArrayList<>();
    private final List<String> popupCommands = new ArrayList<>();
    private final List<IntConsumer> listeners = new CopyOnWriteArrayList<>();
    private Images image = Images.NONE;
    private String description;
    private int itemWidth = 72;
    private int itemHeight = 56;
    private int maxColumns = 5;
    private int minColumns = 3;
    private int popupColumns;
    private int selection = -1;
    private int firstRow;

    @SuppressWarnings("this-escape")
    public RibbonGallery(String id, String label, Renderer renderer) {
        super(id);
        this.label = Objects.requireNonNull(label, "label");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        defaultSizes(Size.LARGE, Size.SMALL);
    }

    @Override
    public String label() {
        return label;
    }

    /** The items' labels: their accessible names and tooltips. Their count is the item count. */
    public RibbonGallery items(String... labels) {
        return items(List.of(labels));
    }

    public RibbonGallery items(List<String> labels) {
        items.clear();
        items.addAll(labels);
        selection = Math.min(selection, items.size() - 1);
        firstRow = 0;
        changed();
        return this;
    }

    public int itemCount() {
        return items.size();
    }

    String itemLabel(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : "";
    }

    /** A cell's size at 100 %, in pixels (default 72 × 56). */
    public RibbonGallery itemSize(int width, int height) {
        itemWidth = width;
        itemHeight = height;
        changed();
        return this;
    }

    /** The columns shown when the group is large and when it is medium (default 5 and 3). */
    public RibbonGallery columns(int max, int min) {
        maxColumns = Math.max(1, max);
        minColumns = Math.max(1, Math.min(min, max));
        changed();
        return this;
    }

    /** The popup grid's columns (default: the large column count). */
    public RibbonGallery popupColumns(int columns) {
        popupColumns = columns;
        return this;
    }

    int popupColumns() {
        return popupColumns > 0 ? popupColumns : maxColumns;
    }

    /** Commands listed under the popup grid (<em>New Style…</em>, <em>Clear Formatting</em>). */
    public RibbonGallery popupCommands(String... commandIds) {
        popupCommands.clear();
        popupCommands.addAll(List.of(commandIds));
        return this;
    }

    List<Command> popupCommands() {
        Ribbon ribbon = ribbon();
        return popupCommands.stream().map(id -> ribbon.commands().find(id).orElse(null)).filter(Objects::nonNull).toList();
    }

    /** The image of the drop-down button the gallery becomes when its group is small. */
    public RibbonGallery image(Images images) {
        image = images == null ? Images.NONE : images;
        return this;
    }

    public RibbonGallery description(String text) {
        description = text;
        return this;
    }

    @Override
    public String description() {
        return description;
    }

    public int selection() {
        return selection;
    }

    /** Selects an item without telling listeners; -1 selects none. */
    public void setSelection(int index) {
        selection = index < 0 || index >= items.size() ? -1 : index;
        repaint();
    }

    /** Told the index of the item the user picks. */
    public RibbonGallery onSelect(IntConsumer listener) {
        listeners.add(Objects.requireNonNull(listener));
        return this;
    }

    /** Picks {@code index} as the user does: selects it and tells listeners. */
    public void select(int index) {
        setSelection(index);
        if (selection >= 0) {
            listeners.forEach(l -> l.accept(selection));
        }
    }

    /** The first row shown in the ribbon. */
    public int firstRow() {
        return firstRow;
    }

    Renderer renderer() {
        return renderer;
    }

    // ------------------------------------------------------------------ layout

    int cellWidth(RibbonMetrics m) {
        return m.px(itemWidth) + m.px(4);
    }

    int cellHeight(RibbonMetrics m) {
        return m.px(itemHeight) + m.px(4);
    }

    int rows(RibbonMetrics m) {
        return Math.max(1, (m.contentHeight - m.px(4)) / cellHeight(m));
    }

    int columns(Size size) {
        return size == Size.LARGE ? maxColumns : minColumns;
    }

    int rowCount(Size size) {
        int columns = columns(size);
        return (items.size() + columns - 1) / columns;
    }

    /** Scrolls the in-ribbon strip by {@code delta} rows at {@code size}. */
    void scroll(int delta, Size size, int visibleRows) {
        int max = Math.max(0, rowCount(size) - visibleRows);
        int next = Math.max(0, Math.min(max, firstRow + delta));
        if (next != firstRow) {
            firstRow = next;
            Ribbon ribbon = ribbon();
            if (ribbon != null) {
                ribbon.invalidateAll();
            }
        }
    }

    @Override
    boolean hasPopup() {
        return true;
    }

    @Override
    int accessibleRole() {
        return ACC.ROLE_LIST;
    }

    @Override
    Point measure(RibbonMetrics m, Size size) {
        if (size == Size.SMALL) {
            String[] lines = m.lines(label, true);
            int width = m.largeIcon;
            for (int i = 0; i < lines.length; i++) {
                width = Math.max(width, m.text(lines[i]) + (i == lines.length - 1 ? m.px(4) + m.arrow : 0));
            }
            return new Point(width + m.px(10), m.contentHeight);
        }
        return new Point(columns(size) * cellWidth(m) + m.px(4) + scrollWidth(m), m.contentHeight);
    }

    private int scrollWidth(RibbonMetrics m) {
        return m.px(16);
    }

    Rectangle cell(RibbonMetrics m, ItemBox box, int index) {
        Rectangle b = box.bounds();
        int columns = columns(box.size());
        int visible = index - firstRow * columns;
        int row = visible / columns;
        int column = visible % columns;
        int rows = rows(m);
        int top = b.y + (b.height - rows * cellHeight(m)) / 2;
        return new Rectangle(b.x + m.px(2) + column * cellWidth(m), top + row * cellHeight(m), cellWidth(m), cellHeight(m));
    }

    void addHots(RibbonMetrics m, ItemBox box, List<Hot> out) {
        if (box.size() == Size.SMALL) {
            return;
        }
        int columns = columns(box.size());
        int first = firstRow * columns;
        int last = Math.min(items.size(), first + rows(m) * columns);
        for (int i = first; i < last; i++) {
            out.add(new Hot(Hot.Kind.GALLERY_CELL, this, box, cell(m, box, i), null, i));
        }
        Rectangle b = box.bounds();
        int width = scrollWidth(m);
        int x = b.x + b.width - width;
        int third = b.height / 3;
        out.add(new Hot(Hot.Kind.GALLERY_UP, this, box, new Rectangle(x, b.y, width, third), null, -1));
        out.add(new Hot(Hot.Kind.GALLERY_DOWN, this, box, new Rectangle(x, b.y + third, width, third), null, -1));
        out.add(new Hot(Hot.Kind.GALLERY_MORE, this, box, new Rectangle(x, b.y + 2 * third, width, b.height - 2 * third), null, -1));
    }

    // ------------------------------------------------------------------ painting

    @Override
    void paint(RibbonSurface surface, GC gc, ItemBox box) {
        RibbonMetrics m = surface.metrics();
        RibbonColors colors = surface.colors();
        Rectangle b = box.bounds();
        if (box.size() == Size.SMALL) {
            surface.paintItemBackground(gc, box, null, false);
            int iconX = b.x + (b.width - m.largeIcon) / 2;
            int iconY = b.y + m.px(3);
            if (image.image(m.largeIcon) != null) {
                surface.drawImage(gc, image, m.largeIcon, iconX, iconY, isEnabled());
            } else if (!items.isEmpty()) {
                renderer.paint(gc, new Rectangle(iconX, iconY, m.largeIcon, m.largeIcon), Math.max(0, selection), false, false);
            }
            String[] lines = m.lines(label, true);
            int y = iconY + m.largeIcon + m.px(3);
            gc.setFont(m.font);
            for (int i = 0; i < lines.length; i++) {
                boolean last = i == lines.length - 1;
                int width = m.text(lines[i]);
                int total = width + (last ? (width > 0 ? m.px(4) : 0) + m.arrow : 0);
                int x = b.x + (b.width - total) / 2;
                gc.setForeground(colors.foreground);
                gc.drawText(lines[i], x, y, SWT.DRAW_TRANSPARENT);
                if (last) {
                    surface.drawArrow(gc, x + total - m.arrow, y + (m.fontHeight - m.arrow / 2) / 2, m.arrow, colors.foreground);
                }
                y += m.fontHeight;
            }
            return;
        }
        gc.setBackground(colors.galleryBackground);
        gc.fillRectangle(b);
        Rectangle clip = gc.getClipping();
        int width = scrollWidth(m);
        gc.setClipping(b.x + 1, b.y + 1, b.width - width - 2, b.height - 2);
        int columns = columns(box.size());
        int first = firstRow * columns;
        int last = Math.min(items.size(), first + rows(m) * columns);
        for (int i = first; i < last; i++) {
            Rectangle cell = cell(m, box, i);
            boolean hovered = surface.isHovered(hotCell(surface, i));
            Rectangle inner = new Rectangle(cell.x + m.px(2), cell.y + m.px(2), cell.width - m.px(4), cell.height - m.px(4));
            renderer.paint(gc, inner, i, i == selection, hovered);
            if (i == selection || hovered) {
                gc.setForeground(i == selection ? colors.gallerySelection : colors.checkedBorder);
                gc.setLineWidth(i == selection ? Math.max(2, m.px(2)) : 1);
                gc.drawRectangle(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2);
                gc.setLineWidth(1);
            }
        }
        gc.setClipping(clip);
        gc.setForeground(colors.galleryBorder);
        gc.drawRectangle(b.x, b.y, b.width - 1, b.height - 1);
        int x = b.x + b.width - width;
        gc.drawLine(x, b.y, x, b.y + b.height - 1);
        int third = b.height / 3;
        int rows = rows(m);
        paintScrollPart(surface, gc, m, new Rectangle(x, b.y, width, third), 0, firstRow > 0);
        paintScrollPart(surface, gc, m, new Rectangle(x, b.y + third, width, third), 1, firstRow + rows < rowCount(box.size()));
        paintScrollPart(surface, gc, m, new Rectangle(x, b.y + 2 * third, width, b.height - 2 * third), 2, true);
    }

    private Hot hotCell(RibbonSurface surface, int index) {
        for (Hot hot : surface.hots()) {
            if (hot.kind() == Hot.Kind.GALLERY_CELL && hot.target() == this && hot.index() == index) {
                return hot;
            }
        }
        return new Hot(Hot.Kind.GALLERY_CELL, this, null, new Rectangle(0, 0, 0, 0), null, index);
    }

    private void paintScrollPart(RibbonSurface surface, GC gc, RibbonMetrics m, Rectangle r, int part, boolean enabled) {
        RibbonColors colors = surface.colors();
        Hot.Kind kind = part == 0 ? Hot.Kind.GALLERY_UP : part == 1 ? Hot.Kind.GALLERY_DOWN : Hot.Kind.GALLERY_MORE;
        boolean hovered = false;
        for (Hot hot : surface.hots()) {
            if (hot.kind() == kind && hot.target() == this && surface.isHovered(hot)) {
                hovered = true;
            }
        }
        if (hovered && enabled) {
            gc.setBackground(colors.hover);
            gc.fillRectangle(r.x + 1, r.y + 1, r.width - 2, r.height - 2);
        }
        gc.setForeground(enabled && isEnabled() ? colors.foreground : colors.disabled);
        int w = m.arrow;
        int half = w / 2;
        int x = r.x + (r.width - w) / 2;
        int y = r.y + (r.height - half) / 2;
        if (part == 0) {
            gc.drawPolyline(new int[] { x, y + half, x + half, y, x + 2 * half, y + half });
        } else {
            gc.drawPolyline(new int[] { x, y, x + half, y + half, x + 2 * half, y });
            if (part == 2) {
                gc.drawLine(x, y - m.px(2), x + 2 * half, y - m.px(2));
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    void activate(RibbonSurface surface, ItemBox box, boolean arrow, Event event) {
        if (isEnabled()) {
            openPopup(surface, box);
        }
    }

    void activatePart(RibbonSurface surface, Hot hot) {
        if (!isEnabled()) {
            return;
        }
        int rows = surface.measure(this::rows);
        switch (hot.kind()) {
            case GALLERY_UP -> scroll(-1, hot.box().size(), rows);
            case GALLERY_DOWN -> scroll(1, hot.box().size(), rows);
            case GALLERY_MORE -> openPopup(surface, hot.box());
            case GALLERY_CELL -> {
                surface.ribbon().closePopups();
                surface.ribbon().keyTips().hide();
                select(hot.index());
            }
            default -> {
            }
        }
    }

    void openPopup(RibbonSurface surface, ItemBox box) {
        Rectangle anchor = surface.toDisplay(box.bounds());
        anchor.height = 0;
        surface.ribbon().openPopup(new GalleryPopup(surface, this, anchor), this);
    }
}
