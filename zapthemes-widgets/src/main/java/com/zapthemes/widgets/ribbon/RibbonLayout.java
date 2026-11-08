package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

/**
 * Lays out groups, as a pure function of the groups, a width and the {@link RibbonMetrics}:
 * column flow inside a group, and the adaptive reduction of a tab's groups to fit a width.
 */
final class RibbonLayout {

    /** Where a group is drawn, at what size, with its items; {@code button} is set when collapsed. */
    record GroupBox(RibbonGroup group, Size size, Rectangle bounds, List<ItemBox> items, Rectangle launcher, Rectangle button) {

        GroupBox translated(int dx) {
            List<ItemBox> moved = new ArrayList<>(items.size());
            items.forEach(i -> moved.add(i.translated(dx)));
            return new GroupBox(group, size, move(bounds, dx), moved, launcher == null ? null : move(launcher, dx),
                    button == null ? null : move(button, dx));
        }

        private static Rectangle move(Rectangle r, int dx) {
            return new Rectangle(r.x + dx, r.y, r.width, r.height);
        }
    }

    /** A panel's groups, their total width, and whether they overflow the available width. */
    record PanelLayout(List<GroupBox> groups, int width, boolean overflow) {
    }

    private RibbonLayout() {
    }

    /** The height of a group (and of the panel's content row, labels included). */
    static int groupHeight(RibbonMetrics m) {
        return m.contentTop + m.contentHeight + m.px(2) + m.groupLabelHeight;
    }

    static int width(RibbonGroup group, Size size, RibbonMetrics m) {
        return layoutGroup(group, size, m, 0, 0).bounds().width;
    }

    /** Lays out {@code group} at {@code size} with its top left corner at {@code (x, top)}. */
    static GroupBox layoutGroup(RibbonGroup group, Size size, RibbonMetrics m, int x, int top) {
        int height = groupHeight(m);
        if (size == Size.COLLAPSED) {
            String[] lines = m.lines(group.label(), true);
            int width = m.largeIcon;
            for (int i = 0; i < lines.length; i++) {
                width = Math.max(width, m.text(lines[i]) + (i == lines.length - 1 ? m.px(4) + m.arrow : 0));
            }
            width += m.px(10);
            Rectangle button = new Rectangle(x + m.px(2), top + m.contentTop, width, m.contentHeight);
            return new GroupBox(group, size, new Rectangle(x, top, width + m.px(4), height), List.of(), null, button);
        }
        int contentTop = top + m.contentTop;
        List<ItemBox> boxes = new ArrayList<>();
        int cursor = x + m.groupPad;
        int columnGap = m.px(2);
        List<RibbonItem> stack = new ArrayList<>();
        List<List<RibbonItem>> rows = new ArrayList<>();
        boolean any = false;
        for (Object element : group.elements()) {
            if (element instanceof RibbonRow row) {
                cursor = flushStack(stack, size, m, cursor, contentTop, boxes, columnGap);
                List<RibbonItem> visible = row.items().stream().filter(RibbonItem::isVisible).toList();
                if (!visible.isEmpty()) {
                    rows.add(visible);
                }
                if (rows.size() == 3) {
                    cursor = flushRows(rows, size, m, cursor, contentTop, boxes, columnGap);
                }
            } else {
                RibbonItem item = (RibbonItem) element;
                if (!item.isVisible()) {
                    continue;
                }
                cursor = flushRows(rows, size, m, cursor, contentTop, boxes, columnGap);
                Size itemSize = item.resolve(size);
                Point extent = item.measure(m, itemSize);
                if (itemSize == Size.LARGE || extent.y > m.row) {
                    cursor = flushStack(stack, size, m, cursor, contentTop, boxes, columnGap);
                    boxes.add(new ItemBox(item, itemSize, new Rectangle(cursor, contentTop, extent.x, Math.min(extent.y, m.contentHeight))));
                    cursor += extent.x + columnGap;
                } else {
                    stack.add(item);
                    if (stack.size() == 3) {
                        cursor = flushStack(stack, size, m, cursor, contentTop, boxes, columnGap);
                    }
                }
            }
            any = true;
        }
        cursor = flushStack(stack, size, m, cursor, contentTop, boxes, columnGap);
        cursor = flushRows(rows, size, m, cursor, contentTop, boxes, columnGap);
        if (any && !boxes.isEmpty()) {
            cursor -= columnGap;
        }
        int contentWidth = cursor - x + m.groupPad;
        int launcherWidth = group.dialogLauncher() != null ? m.px(15) : 0;
        int labelWidth = m.groupText(group.label()) + 2 * launcherWidth + 2 * m.groupPad;
        int width = Math.max(contentWidth, labelWidth);
        if (width > contentWidth) {
            int dx = (width - contentWidth) / 2;
            boxes.replaceAll(b -> b.translated(dx));
        }
        Rectangle launcher = null;
        if (launcherWidth > 0) {
            int labelTop = contentTop + m.contentHeight + m.px(2);
            launcher = new Rectangle(x + width - launcherWidth - m.px(1), labelTop, launcherWidth, m.groupLabelHeight - m.px(1));
        }
        return new GroupBox(group, size, new Rectangle(x, top, width, height), boxes, launcher, null);
    }

    private static int flushStack(List<RibbonItem> stack, Size size, RibbonMetrics m, int x, int top, List<ItemBox> boxes, int gap) {
        if (stack.isEmpty()) {
            return x;
        }
        int width = 0;
        List<Point> extents = new ArrayList<>();
        for (RibbonItem item : stack) {
            Point extent = item.measure(m, item.resolve(size));
            extents.add(extent);
            width = Math.max(width, extent.x);
        }
        for (int i = 0; i < stack.size(); i++) {
            RibbonItem item = stack.get(i);
            // Medium items line up as a column of equal width; small ones keep their own.
            Size itemSize = item.resolve(size);
            int itemWidth = itemSize == Size.MEDIUM ? width : extents.get(i).x;
            boxes.add(new ItemBox(item, itemSize, new Rectangle(x, top + i * m.row, itemWidth, m.row)));
        }
        stack.clear();
        return x + width + gap;
    }

    private static int flushRows(List<List<RibbonItem>> rows, Size size, RibbonMetrics m, int x, int top, List<ItemBox> boxes, int gap) {
        if (rows.isEmpty()) {
            return x;
        }
        int count = rows.size();
        int spacing = (m.contentHeight - count * m.row) / (count + 1);
        int width = 0;
        for (int r = 0; r < count; r++) {
            int y = top + spacing * (r + 1) + r * m.row;
            int cx = x;
            for (RibbonItem item : rows.get(r)) {
                Size itemSize = item.resolve(size);
                Point extent = item.measure(m, itemSize);
                boxes.add(new ItemBox(item, itemSize, new Rectangle(cx, y, extent.x, m.row)));
                cx += extent.x;
            }
            width = Math.max(width, cx - x);
        }
        rows.clear();
        return x + width + gap;
    }

    /**
     * The size of each group of {@code tab} so that they fit {@code available} pixels: every group
     * starts at its largest size, then steps of the tab's reduction order apply until they fit.
     */
    static Map<RibbonGroup, Size> reduce(RibbonTab tab, int available, RibbonMetrics m) {
        List<RibbonGroup> groups = tab.groups();
        Map<RibbonGroup, Size> sizes = new LinkedHashMap<>();
        Map<RibbonGroup, Integer> widths = new LinkedHashMap<>();
        int total = 0;
        for (RibbonGroup group : groups) {
            sizes.put(group, group.maxSize());
            int width = width(group, group.maxSize(), m);
            widths.put(group, width);
            total += width;
        }
        total += Math.max(0, groups.size() - 1) * m.groupGap;
        for (RibbonTab.Step step : tab.effectiveReductionOrder()) {
            if (total <= available) {
                break;
            }
            RibbonGroup group = step.group();
            Size current = sizes.get(group);
            if (current == null || !step.size().smallerThan(current) || step.size().smallerThan(group.minSize())) {
                continue;
            }
            int width = width(group, step.size(), m);
            if (width >= widths.get(group)) {
                continue;
            }
            total += width - widths.get(group);
            sizes.put(group, step.size());
            widths.put(group, width);
        }
        return sizes;
    }

    /** Lays the groups out left to right from {@code x}, at the given sizes. */
    static PanelLayout layoutPanel(List<RibbonGroup> groups, Map<RibbonGroup, Size> sizes, RibbonMetrics m, int x, int top, int available) {
        List<GroupBox> boxes = new ArrayList<>();
        int cursor = x;
        for (RibbonGroup group : groups) {
            GroupBox box = layoutGroup(group, sizes.getOrDefault(group, group.maxSize()), m, cursor, top);
            boxes.add(box);
            cursor += box.bounds().width + m.groupGap;
        }
        int width = boxes.isEmpty() ? 0 : cursor - m.groupGap - x;
        return new PanelLayout(boxes, width, width > available);
    }
}
