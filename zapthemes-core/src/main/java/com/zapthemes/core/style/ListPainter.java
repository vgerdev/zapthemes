package com.zapthemes.core.style;

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.List;

import com.zapthemes.core.ThemeManager;

/**
 * The selected items of a {@link List}, painted in the theme's {@code selectionBackground} and
 * {@code selectionForeground}, and the item under the pointer in its {@code hover} state's
 * {@code background} (and {@code foreground}, if set); also the drop-down list of a
 * {@code CCombo}, which is a List.
 *
 * <p>Only on Windows, where a list box is not owner-drawn and paints its selection in the system's
 * highlight colors whatever the list's own colors are. In the explorer dark mode
 * ({@code platform.win32.darkScrollbars}) that highlight is white and so is the text on it: the
 * selected item cannot be read at all. After the list box has painted, the selected items are
 * painted again, row by row, in the theme's colors. A list box also repaints an item it selects
 * without a paint message, so the list is redrawn after a mouse or key event that changed the
 * selection or scrolled it. Only then: every redraw has the list box paint the system highlight
 * before the theme's colors go over it, which shows as a white flash on the selected item. A
 * pointer moving over the list redraws only the unselected rows it enters and leaves, for the
 * hover highlight (the list box has none of its own); a selected row is never redrawn for it.
 * Properties the theme does not set are left to the platform. Installed once per List by its
 * styler.
 */
final class ListPainter {

    static final String INSTALLED = "zapthemes.listPainter";

    /** What the last paint showed: the selected indices, then the top index. */
    static final String PAINTED = "zapthemes.listPainter.painted";

    /** The index of the row under the pointer, or -1. */
    static final String HOVER = "zapthemes.listPainter.hover";

    static final boolean ACTIVE = "win32".equals(SWT.getPlatform());

    /** Left margin of a list box item's text, in points (the list box's own, 2 pixels at 100 %). */
    private static final int TEXT_MARGIN = 2;

    private ListPainter() {
    }

    static void install(List list) {
        if (!ACTIVE || list.getData(INSTALLED) != null) {
            return;
        }
        list.setData(INSTALLED, Boolean.TRUE);
        list.addListener(SWT.Paint, ListPainter::paint);
        for (int type : new int[] { SWT.Selection, SWT.MouseDown, SWT.MouseUp, SWT.MouseMove, SWT.KeyDown, SWT.KeyUp,
                SWT.FocusIn, SWT.FocusOut, SWT.MouseVerticalWheel }) {
            list.addListener(type, ListPainter::changed);
        }
        list.addListener(SWT.MouseExit, event -> hover((List) event.widget, -1));
    }

    /**
     * The pointer is over row {@code index} (-1: none): redraws the rows it left and entered, to
     * paint or clear the hover highlight there, unless they are selected.
     */
    private static void hover(List list, int index) {
        int previous = hovered(list);
        if (previous == index) {
            return;
        }
        list.setData(HOVER, index);
        if (styleOf(list).states().contains("hover")) {
            redrawRow(list, previous);
            redrawRow(list, index);
        }
    }

    /** The row under the pointer, or -1. */
    static int hovered(List list) {
        return list.getData(HOVER) instanceof Integer row ? row : -1;
    }

    private static void redrawRow(List list, int index) {
        if (index < 0 || index >= list.getItemCount() || list.isSelected(index)) {
            return;
        }
        int height = list.getItemHeight();
        Rectangle area = list.getClientArea();
        list.redraw(area.x, area.y + (index - list.getTopIndex()) * height, area.width, height, false);
    }

    /** The row at {@code y} in the list, or -1 below the last one. */
    private static int rowAt(List list, int y) {
        int height = list.getItemHeight();
        if (height <= 0 || y < 0) {
            return -1;
        }
        int index = list.getTopIndex() + y / height;
        return index < list.getItemCount() ? index : -1;
    }

    /**
     * Redraws after the list box has handled the event, if it changed what the last paint showed
     * (focus changes always redraw: the list box repaints its selection then). An invalidation is
     * painted later.
     */
    private static void changed(Event event) {
        List list = (List) event.widget;
        if (event.type == SWT.MouseMove && (event.stateMask & SWT.BUTTON_MASK) == 0) {
            hover(list, rowAt(list, event.y));
            return;
        }
        boolean focus = event.type == SWT.FocusIn || event.type == SWT.FocusOut;
        if ((focus || !Arrays.equals(shown(list), list.getData(PAINTED) instanceof int[] painted ? painted : null))
                && styleOf(list).has("selectionBackground")) {
            list.redraw();
        }
    }

    /** The selected indices, then the top index. */
    static int[] shown(List list) {
        int[] selection = list.getSelectionIndices();
        int[] shown = Arrays.copyOf(selection, selection.length + 1);
        shown[selection.length] = list.getTopIndex();
        return shown;
    }

    private static ResolvedStyle styleOf(List list) {
        return ThemeManager.get(list.getDisplay()).styleOf(list);
    }

    private static void paint(Event event) {
        List list = (List) event.widget;
        list.setData(PAINTED, shown(list));
        ResolvedStyle style = styleOf(list);
        if (list.getItemHeight() <= 0) {
            return;
        }
        ThemeContext context = new ThemeContext(ThemeManager.get(list.getDisplay()).resources());
        event.gc.setFont(list.getFont());
        int hover = hovered(list);
        if (hover >= 0 && hover < list.getItemCount() && !list.isSelected(hover) && style.states().contains("hover")) {
            ResolvedStyle hot = style.state("hover");
            Color fill = hot.has("background") ? context.color(hot, "background") : null;
            Color text = hot.has("foreground") ? context.color(hot, "foreground") : list.getForeground();
            paintRow(event.gc, list, hover, fill, text);
        }
        if (style.has("selectionBackground")) {
            Color fill = context.color(style, "selectionBackground");
            Color text = style.has("selectionForeground") ? context.color(style, "selectionForeground") : list.getForeground();
            for (int index : list.getSelectionIndices()) {
                paintRow(event.gc, list, index, fill, text);
            }
        }
    }

    /** Paints row {@code index} in {@code fill} with its text in {@code text}; nothing without a fill. */
    private static void paintRow(GC gc, List list, int index, Color fill, Color text) {
        int height = list.getItemHeight();
        Rectangle area = list.getClientArea();
        int y = (index - list.getTopIndex()) * height;
        if (fill == null || y + height <= area.y || y >= area.y + area.height) {
            return;
        }
        gc.setBackground(fill);
        gc.fillRectangle(area.x, y, area.width, height);
        gc.setForeground(text);
        String item = list.getItem(index);
        int textHeight = gc.textExtent(item, SWT.DRAW_TAB).y;
        gc.drawText(item, area.x + TEXT_MARGIN, y + (height - textHeight) / 2, SWT.DRAW_TAB | SWT.DRAW_TRANSPARENT);
    }
}
