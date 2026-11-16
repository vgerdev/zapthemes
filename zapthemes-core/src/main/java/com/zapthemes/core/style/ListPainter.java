package com.zapthemes.core.style;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.List;

import com.zapthemes.core.ThemeManager;

/**
 * The selected items of a {@link List}, painted in the theme's {@code selectionBackground} and
 * {@code selectionForeground}; also the drop-down list of a {@code CCombo}, which is a List.
 *
 * <p>Only on Windows, where a list box is not owner-drawn and paints its selection in the system's
 * highlight colors whatever the list's own colors are. In the explorer dark mode
 * ({@code platform.win32.darkScrollbars}) that highlight is white and so is the text on it: the
 * selected item cannot be read at all. After the list box has painted, the selected items are
 * painted again, row by row, in the theme's colors. A list box also repaints an item it selects
 * without a paint message, so the list is redrawn after every mouse and key event that can change
 * the selection. Properties the theme does not set are left to the platform. Installed once per
 * List by its styler.
 */
final class ListPainter {

    static final String INSTALLED = "zapthemes.listPainter";

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
    }

    /** Redraws after the list box has handled the event: an invalidation is painted later. */
    private static void changed(Event event) {
        List list = (List) event.widget;
        if (styleOf(list).has("selectionBackground")) {
            list.redraw();
        }
    }

    private static ResolvedStyle styleOf(List list) {
        return ThemeManager.get(list.getDisplay()).styleOf(list);
    }

    private static void paint(Event event) {
        List list = (List) event.widget;
        ResolvedStyle style = styleOf(list);
        if (!style.has("selectionBackground")) {
            return;
        }
        ThemeContext context = new ThemeContext(ThemeManager.get(list.getDisplay()).resources());
        Color fill = context.color(style, "selectionBackground");
        Color text = style.has("selectionForeground") ? context.color(style, "selectionForeground") : list.getForeground();
        int height = list.getItemHeight();
        if (fill == null || height <= 0) {
            return;
        }
        Rectangle area = list.getClientArea();
        int top = list.getTopIndex();
        GC gc = event.gc;
        gc.setFont(list.getFont());
        for (int index : list.getSelectionIndices()) {
            int y = (index - top) * height;
            if (y + height <= area.y || y >= area.y + area.height) {
                continue;
            }
            gc.setBackground(fill);
            gc.fillRectangle(area.x, y, area.width, height);
            gc.setForeground(text);
            String item = list.getItem(index);
            int textHeight = gc.textExtent(item, SWT.DRAW_TAB).y;
            gc.drawText(item, area.x + TEXT_MARGIN, y + (height - textHeight) / 2, SWT.DRAW_TAB | SWT.DRAW_TRANSPARENT);
        }
    }
}
