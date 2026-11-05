package com.zapthemes.core.style;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TreeColumn;

import com.zapthemes.core.ThemeManager;

/**
 * Owner-draw for Table and Tree rows, driven by the theme on every paint:
 * <ul>
 *   <li>{@code selectionBackground} / {@code selectionForeground}: selected rows</li>
 *   <li>{@code states.hover.background}: the row under the mouse</li>
 *   <li>{@code alternateRowBackground}: every second row, as the item background (Table only;
 *       Tree rows have no stable index)</li>
 *   <li>{@code gridLineColor}: grid lines painted instead of the native ones, when the application
 *       asked for lines and the control is a {@link ThemedLines} proxy</li>
 * </ul>
 * Properties the theme does not set are left to the platform. Installed once per control by the
 * Table and Tree stylers.
 */
final class ItemPainter {

    static final String INSTALLED = "zapthemes.itemPainter";

    private static final boolean GTK = "gtk".equals(SWT.getPlatform());
    private static final boolean COCOA = "cocoa".equals(SWT.getPlatform());

    private ItemPainter() {
    }

    static void install(Composite control) {
        if (control.getData(INSTALLED) != null) {
            return;
        }
        control.setData(INSTALLED, Boolean.TRUE);
        control.addListener(SWT.EraseItem, ItemPainter::eraseItem);
        control.addListener(SWT.Paint, ItemPainter::paintGrid);
    }

    /** Applies the theme's grid choice; called by the stylers on every (re)theme. */
    static void updateLines(Composite control, ResolvedStyle style) {
        if (control instanceof ThemedLines lines) {
            boolean painted = style.has("gridLineColor");
            lines.setNativeLinesVisible(lines.requestedLinesVisible() && !painted);
        }
        control.redraw();
    }

    private static void eraseItem(Event event) {
        Composite control = (Composite) event.widget;
        ThemeManager manager = ThemeManager.get(control.getDisplay());
        ResolvedStyle style = manager.styleOf(control);
        ThemeContext context = new ThemeContext(manager.resources());
        GC gc = event.gc;

        Color fill = null;
        Color text = null;
        if ((event.detail & SWT.SELECTED) != 0 && style.has("selectionBackground")) {
            fill = context.color(style, "selectionBackground");
            text = context.color(style, "selectionForeground");
            event.detail &= ~(SWT.SELECTED | SWT.HOT);
        } else if ((event.detail & SWT.HOT) != 0 && style.states().contains("hover")) {
            fill = context.color(style.state("hover"), "background");
            event.detail &= ~SWT.HOT;
        } else if (control instanceof Table table && style.has("alternateRowBackground")
                && event.item instanceof TableItem item) {
            stripe(table, item, context.color(style, "alternateRowBackground"));
            return;
        }
        if (fill == null) {
            return;
        }
        Rectangle cell = cellBounds(control, event);
        gc.setBackground(fill);
        gc.fillRectangle(cell);
        if (text != null) {
            gc.setForeground(text);
        }
        event.detail &= ~SWT.BACKGROUND;
    }

    /**
     * Stripes by giving odd rows the stripe as their item background: on Windows the list view paints
     * text backgrounds in the item color after {@code EraseItem}, so a painted stripe would be covered.
     * Checked on every paint, so inserts and sorting keep the pattern; rows with a color of the
     * application's own (neither the table background nor the stripe) are left alone.
     */
    private static void stripe(Table table, TableItem item, Color stripe) {
        Color current = item.getBackground();
        Color base = table.getBackground();
        if (!current.equals(base) && !current.equals(stripe)) {
            return;
        }
        Color wanted = table.indexOf(item) % 2 == 1 ? stripe : base;
        if (!current.equals(wanted)) {
            item.setBackground(wanted);
        }
    }

    /** The cell, extended to the right edge for the last column so whole rows are filled. */
    private static Rectangle cellBounds(Composite control, Event event) {
        int columns = control instanceof Table table ? table.getColumnCount() : ((Tree) control).getColumnCount();
        Rectangle area = control.getClientArea();
        if (columns == 0 || event.index == columns - 1) {
            return new Rectangle(event.x, event.y, Math.max(event.width, area.x + area.width - event.x), event.height);
        }
        return new Rectangle(event.x, event.y, event.width, event.height);
    }

    private static void paintGrid(Event event) {
        Composite control = (Composite) event.widget;
        if (!(control instanceof ThemedLines lines) || !lines.requestedLinesVisible()) {
            return;
        }
        ThemeManager manager = ThemeManager.get(control.getDisplay());
        ResolvedStyle style = manager.styleOf(control);
        Color color = new ThemeContext(manager.resources()).color(style, "gridLineColor");
        if (color == null) {
            return;
        }
        Rectangle area = control.getClientArea();
        int itemHeight;
        int header;
        Rectangle topRow;
        int[] columnEdges;
        if (control instanceof Table table) {
            itemHeight = table.getItemHeight();
            header = table.getHeaderVisible() ? table.getHeaderHeight() : 0;
            topRow = table.getItemCount() > 0 ? table.getItem(table.getTopIndex()).getBounds() : null;
            columnEdges = edges(table.getColumnOrder(), table.getColumns(), null, scrollX(table));
        } else {
            Tree tree = (Tree) control;
            itemHeight = tree.getItemHeight();
            header = tree.getHeaderVisible() ? tree.getHeaderHeight() : 0;
            topRow = tree.getTopItem() != null ? tree.getTopItem().getBounds() : null;
            columnEdges = edges(tree.getColumnOrder(), null, tree.getColumns(), scrollX(tree));
        }
        // Item bounds are client coordinates; where the GC draws depends on the platform:
        //   win32: client area and GC both start at the top of the header.
        //   GTK:   the client area starts above the header (y = -header) but the GC at the header's
        //          top, and cell bounds start a pixel below the row.
        //   Cocoa: the client area starts above the header too, but the header is a separate view,
        //          so the GC starts at the first row.
        int shift = GTK ? -area.y : 0;
        int rowsTop = COCOA ? 0 : header;
        int rowTop = topRow == null ? rowsTop : topRow.y + shift - (GTK ? 1 : 0);
        int bottom = area.y + area.height + shift;
        GC gc = event.gc;
        gc.setForeground(color);
        // Rows continue below the last item, like native grid lines.
        for (int y = rowTop + itemHeight - 1; y < bottom; y += itemHeight) {
            if (y >= rowsTop) {
                gc.drawLine(area.x, y, area.x + area.width, y);
            }
        }
        for (int x : columnEdges) {
            gc.drawLine(x, rowsTop, x, bottom);
        }
    }

    private static int scrollX(Composite control) {
        var bar = control.getHorizontalBar();
        return bar != null && bar.isVisible() ? bar.getSelection() : 0;
    }

    /** The x of each column's right edge, in display order. */
    private static int[] edges(int[] order, TableColumn[] tableColumns, TreeColumn[] treeColumns, int scrollX) {
        int[] edges = new int[order.length];
        int x = -scrollX;
        for (int i = 0; i < order.length; i++) {
            x += tableColumns != null ? tableColumns[order[i]].getWidth() : treeColumns[order[i]].getWidth();
            edges[i] = x - 1;
        }
        return edges;
    }
}
