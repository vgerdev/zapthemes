package com.zapthemes.widgets.ribbon;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.ScrollBar;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.widgets.command.Command;

/**
 * A gallery's expanded view: every item in a grid (scrolling past six rows), and the gallery's
 * commands under it. Arrows move, Enter picks, Esc closes.
 */
final class GalleryPopup extends PopupShell {

    private static final int MAX_ROWS = 6;
    private final RibbonGallery gallery;
    private Grid grid;

    GalleryPopup(RibbonSurface origin, RibbonGallery gallery, Rectangle anchor) {
        super(origin, anchor);
        this.gallery = gallery;
    }

    @Override
    Control createContent() {
        grid = new Grid(shell);
        return grid;
    }

    Grid grid() {
        return grid;
    }

    /** The grid canvas. Index {@code itemCount() + i} is the i-th command. */
    final class Grid extends Canvas {

        private final List<Command> commands = gallery.popupCommands();
        private int hover = -1;
        private int focus;
        private int top;

        Grid(Composite parent) {
            super(parent, SWT.DOUBLE_BUFFERED | SWT.V_SCROLL);
            ThemeManager.applyOnCreate(this, "RibbonPopup");
            focus = Math.max(0, gallery.selection());
            addListener(SWT.Paint, this::paint);
            addListener(SWT.MouseMove, e -> {
                int index = indexAt(e.x, e.y);
                if (index != hover) {
                    hover = index;
                    redraw();
                }
            });
            addListener(SWT.MouseExit, e -> {
                hover = -1;
                redraw();
            });
            addListener(SWT.MouseUp, e -> {
                int index = indexAt(e.x, e.y);
                if (index >= 0 && e.button == 1) {
                    choose(index);
                }
            });
            addListener(SWT.MouseVerticalWheel, e -> scrollTo(top - Integer.signum(e.count)));
            addListener(SWT.KeyDown, this::key);
            addListener(SWT.Traverse, e -> {
                if (e.detail == SWT.TRAVERSE_ESCAPE) {
                    e.doit = false;
                    close();
                    if (!origin.isDisposed()) {
                        origin.setFocus();
                    }
                } else if (e.detail != SWT.TRAVERSE_MNEMONIC) {
                    e.doit = false;
                }
            });
            ScrollBar bar = getVerticalBar();
            bar.setVisible(rowCount() > MAX_ROWS);
            bar.setMaximum(rowCount());
            bar.setThumb(MAX_ROWS);
            bar.setPageIncrement(MAX_ROWS);
            bar.addListener(SWT.Selection, e -> {
                top = bar.getSelection();
                redraw();
            });
            initAccessible();
            ensureVisible(focus);
        }

        private RibbonMetrics metrics(GC gc) {
            RibbonColors colors = ribbon.colors();
            return new RibbonMetrics(gc, ribbon.getFont(), colors.tabFont, colors.groupLabelFont);
        }

        private <T> T measure(java.util.function.Function<RibbonMetrics, T> f) {
            GC gc = new GC(this);
            try {
                return f.apply(metrics(gc));
            } finally {
                gc.dispose();
            }
        }

        int columns() {
            return gallery.popupColumns();
        }

        int rowCount() {
            return (gallery.itemCount() + columns() - 1) / columns();
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            return measure(m -> {
                int width = columns() * gallery.cellWidth(m) + m.px(8);
                int rows = Math.min(MAX_ROWS, rowCount());
                int height = rows * gallery.cellHeight(m) + m.px(8) + commands.size() * m.row + (commands.isEmpty() ? 0 : m.px(4));
                if (rowCount() > MAX_ROWS) {
                    width += getVerticalBar().getSize().x;
                }
                return new Point(width, height);
            });
        }

        Rectangle bounds(RibbonMetrics m, int index) {
            int count = gallery.itemCount();
            if (index < count) {
                int row = index / columns() - top;
                int column = index % columns();
                return new Rectangle(m.px(4) + column * gallery.cellWidth(m), m.px(4) + row * gallery.cellHeight(m),
                        gallery.cellWidth(m), gallery.cellHeight(m));
            }
            int rows = Math.min(MAX_ROWS, rowCount());
            int y = m.px(4) + rows * gallery.cellHeight(m) + m.px(4) + (index - count) * m.row;
            return new Rectangle(1, y, getClientArea().width - 2, m.row);
        }

        int indexAt(int x, int y) {
            return measure(m -> {
                for (int i = 0; i < gallery.itemCount() + commands.size(); i++) {
                    boolean shown = i >= gallery.itemCount() || (i / columns() >= top && i / columns() < top + MAX_ROWS);
                    if (shown && bounds(m, i).contains(x, y)) {
                        return i;
                    }
                }
                return -1;
            });
        }

        void choose(int index) {
            if (index < gallery.itemCount()) {
                close();
                gallery.select(index);
            } else {
                Command command = commands.get(index - gallery.itemCount());
                close();
                origin.run(command, null, false, null);
            }
        }

        private void scrollTo(int row) {
            top = Math.max(0, Math.min(Math.max(0, rowCount() - MAX_ROWS), row));
            getVerticalBar().setSelection(top);
            redraw();
        }

        private void ensureVisible(int index) {
            if (index < gallery.itemCount()) {
                int row = index / columns();
                if (row < top) {
                    scrollTo(row);
                } else if (row >= top + MAX_ROWS) {
                    scrollTo(row - MAX_ROWS + 1);
                }
            }
        }

        private void key(Event e) {
            int count = gallery.itemCount();
            int total = count + commands.size();
            int next = focus;
            switch (e.keyCode) {
                case SWT.ARROW_RIGHT -> next = Math.min(total - 1, focus + 1);
                case SWT.ARROW_LEFT -> next = Math.max(0, focus - 1);
                case SWT.ARROW_DOWN -> next = focus < count ? (focus + columns() < count ? focus + columns() : Math.min(total - 1, count))
                        : Math.min(total - 1, focus + 1);
                case SWT.ARROW_UP -> next = focus < count ? Math.max(0, focus - columns()) : focus == count ? Math.max(0, count - 1) : focus - 1;
                case SWT.HOME -> next = 0;
                case SWT.END -> next = total - 1;
                case SWT.CR, SWT.KEYPAD_CR -> {
                    choose(focus);
                    return;
                }
                default -> {
                    if (e.character == ' ') {
                        choose(focus);
                    }
                    return;
                }
            }
            focus = next;
            ensureVisible(focus);
            getAccessible().setFocus(focus);
            redraw();
        }

        int focusIndex() {
            return focus;
        }

        private void paint(Event event) {
            GC gc = event.gc;
            RibbonMetrics m = metrics(gc);
            RibbonColors colors = ribbon.colors();
            Themed theme = Themed.of(this);
            Rectangle area = getClientArea();
            gc.setBackground(theme.color("background", colors.background));
            gc.fillRectangle(area);
            int count = gallery.itemCount();
            for (int i = top * columns(); i < Math.min(count, (top + MAX_ROWS) * columns()); i++) {
                Rectangle cell = bounds(m, i);
                boolean hovered = i == hover || (i == focus && isFocusControl());
                Rectangle inner = new Rectangle(cell.x + m.px(2), cell.y + m.px(2), cell.width - m.px(4), cell.height - m.px(4));
                gallery.renderer().paint(gc, inner, i, i == gallery.selection(), hovered);
                if (i == gallery.selection() || hovered) {
                    gc.setForeground(i == gallery.selection() ? colors.gallerySelection : colors.checkedBorder);
                    gc.setLineWidth(i == gallery.selection() ? Math.max(2, m.px(2)) : 1);
                    gc.drawRectangle(cell.x + 1, cell.y + 1, cell.width - 2, cell.height - 2);
                    gc.setLineWidth(1);
                }
            }
            if (!commands.isEmpty()) {
                int y = bounds(m, count).y - m.px(2);
                gc.setForeground(colors.separator);
                gc.drawLine(1, y, area.width - 2, y);
            }
            for (int i = 0; i < commands.size(); i++) {
                Command command = commands.get(i);
                Rectangle b = bounds(m, count + i);
                if (count + i == hover || (count + i == focus && isFocusControl())) {
                    gc.setBackground(colors.hover);
                    gc.fillRectangle(b);
                }
                Rectangle icon = new Rectangle(b.x + m.px(6), b.y + (b.height - m.smallIcon) / 2, m.smallIcon, m.smallIcon);
                org.eclipse.swt.graphics.Image image = command.images().image(m.smallIcon);
                if (image != null) {
                    Rectangle ib = image.getBounds();
                    gc.drawImage(image, 0, 0, ib.width, ib.height, icon.x, icon.y, icon.width, icon.height);
                }
                gc.setForeground(command.isEnabled() ? colors.foreground : colors.disabled);
                gc.setFont(m.font);
                gc.drawText(command.plainLabel(), icon.x + icon.width + m.px(8), b.y + (b.height - m.fontHeight) / 2, SWT.DRAW_TRANSPARENT);
            }
            gc.setForeground(theme.color("borderColor", colors.border));
            gc.drawRectangle(0, 0, area.width - 1, area.height - 1);
        }

        private void initAccessible() {
            getAccessible().addAccessibleListener(new AccessibleAdapter() {
                @Override
                public void getName(AccessibleEvent e) {
                    if (e.childID == ACC.CHILDID_SELF) {
                        e.result = gallery.label();
                    } else if (e.childID < gallery.itemCount()) {
                        e.result = gallery.itemLabel(e.childID);
                    } else if (e.childID - gallery.itemCount() < commands.size()) {
                        e.result = commands.get(e.childID - gallery.itemCount()).plainLabel();
                    }
                }
            });
            getAccessible().addAccessibleControlListener(new AccessibleControlAdapter() {
                @Override
                public void getRole(AccessibleControlEvent e) {
                    e.detail = e.childID == ACC.CHILDID_SELF ? ACC.ROLE_LIST
                            : e.childID < gallery.itemCount() ? ACC.ROLE_LISTITEM : ACC.ROLE_PUSHBUTTON;
                }

                @Override
                public void getChildCount(AccessibleControlEvent e) {
                    e.detail = gallery.itemCount() + commands.size();
                }

                @Override
                public void getFocus(AccessibleControlEvent e) {
                    e.childID = isFocusControl() ? focus : ACC.CHILDID_NONE;
                }

                @Override
                public void getState(AccessibleControlEvent e) {
                    int state = ACC.STATE_FOCUSABLE;
                    if (e.childID == focus && isFocusControl()) {
                        state |= ACC.STATE_FOCUSED;
                    }
                    if (e.childID == gallery.selection()) {
                        state |= ACC.STATE_SELECTED;
                    }
                    e.detail = state;
                }
            });
        }
    }
}
