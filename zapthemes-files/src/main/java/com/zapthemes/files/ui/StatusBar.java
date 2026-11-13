package com.zapthemes.files.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.files.ui.FileIcons.Glyph;

/**
 * The browser's status bar: the item count and the selection at the left ("9 items", "2 items
 * selected 1.2 MB", "Loading…", an error), and the view toggles at the right (Details; Large icons
 * comes with the icon views).
 *
 * <p>Theme type {@code FileStatusBar}: {@code background}, {@code foreground}, {@code font},
 * {@code hoverBackground}, {@code pressedBackground} (the current view's toggle),
 * {@code disabledForeground}, {@code errorForeground}.
 */
final class StatusBar extends Canvas {

    private static final int HEIGHT = 24;
    private static final int PAD = 10;
    private static final int TOGGLE = 22;
    private static final int GLYPH = 14;

    private String text = "";
    private String selection = "";
    private boolean error;
    private int hoverToggle = -1;

    @SuppressWarnings("this-escape")
    StatusBar(FileBrowser browser) {
        super(browser, SWT.DOUBLE_BUFFERED);
        addListener(SWT.Paint, this::paint);
        addListener(SWT.MouseMove, e -> setHoverToggle(toggleAt(e.x, e.y)));
        addListener(SWT.MouseExit, e -> setHoverToggle(-1));
        ThemeManager.applyOnCreate(this, "FileStatusBar");
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        GC gc = new GC(this);
        gc.setFont(getFont());
        int textHeight = gc.getFontMetrics().getHeight();
        gc.dispose();
        int height = Math.max(px(HEIGHT), textHeight + px(6));
        return new Point(wHint != SWT.DEFAULT ? wHint : px(300), hHint != SWT.DEFAULT ? hHint : height);
    }

    private int px(int pixels) {
        return ThemeManager.get(getDisplay()).resources().zoomedPixels(pixels);
    }

    /** Sets the left text ("9 items") and the selection part ("1 item selected 51 KB", or empty). */
    void setStatus(String text, String selection, boolean error) {
        this.text = text;
        this.selection = selection;
        this.error = error;
        redraw();
    }

    /** What the bar shows, as one line (tests). */
    String getText() {
        return selection.isEmpty() ? text : text + "  |  " + selection;
    }

    Rectangle toggleBounds(int index) {
        Rectangle area = getClientArea();
        int size = px(TOGGLE);
        int x = area.width - px(6) - (2 - index) * (size + px(2));
        return new Rectangle(x, (area.height - size) / 2, size, size);
    }

    private int toggleAt(int x, int y) {
        for (int i = 0; i < 2; i++) {
            if (toggleBounds(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    private void setHoverToggle(int index) {
        if (index != hoverToggle) {
            hoverToggle = index;
            setToolTipText(index == 0 ? "Details" : index == 1 ? "Large icons (coming with the icon views)" : null);
            redraw();
        }
    }

    private void paint(Event event) {
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        BarColors colors = BarColors.of(this, false);
        Rectangle area = getClientArea();
        gc.setBackground(colors.background());
        gc.fillRectangle(area);
        int textHeight = gc.getFontMetrics().getHeight();
        int y = (area.height - textHeight) / 2;
        int x = px(PAD);
        gc.setForeground(error ? colors.error() : colors.foreground());
        gc.drawText(text, x, y, SWT.DRAW_TRANSPARENT);
        if (!selection.isEmpty()) {
            x += gc.textExtent(text).x + px(PAD);
            gc.setForeground(colors.border());
            gc.drawLine(x, y + px(2), x, y + textHeight - px(2));
            x += px(PAD);
            gc.setForeground(colors.foreground());
            gc.drawText(selection, x, y, SWT.DRAW_TRANSPARENT);
        }
        int arc = px(4);
        int glyph = px(GLYPH);
        for (int i = 0; i < 2; i++) {
            Rectangle r = toggleBounds(i);
            boolean enabled = i == 0;
            if (i == 0) {
                gc.setBackground(colors.pressed());
                gc.fillRoundRectangle(r.x, r.y, r.width, r.height, arc, arc);
            } else if (hoverToggle == i && enabled) {
                gc.setBackground(colors.hover());
                gc.fillRoundRectangle(r.x, r.y, r.width, r.height, arc, arc);
            }
            FileIcons.draw(gc, i == 0 ? Glyph.DETAILS_VIEW : Glyph.LARGE_ICONS_VIEW, r.x + (r.width - glyph) / 2, r.y + (r.height - glyph) / 2,
                    glyph, enabled ? colors.foreground() : colors.disabled());
        }
    }
}
