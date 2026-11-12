package com.zapthemes.editor;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.editor.palette.Contrast;
import com.zapthemes.swt.widgets.Canvas;
import com.zapthemes.swt.widgets.Composite;

/**
 * A square of color that can be clicked or chosen with Space or Enter, with an optional caption
 * drawn on it (in black or white, whichever reads) and a selected outline.
 */
public final class ColorChip extends Canvas {

    private RGB rgb;
    private String caption;
    private boolean selected;
    private boolean locked;
    private final int width;
    private final int height;
    private Consumer<ColorChip> onChoose = chip -> { };

    public ColorChip(org.eclipse.swt.widgets.Composite parent, int width, int height) {
        super(parent, SWT.DOUBLE_BUFFERED);
        this.width = width;
        this.height = height;
        this.rgb = new RGB(0, 0, 0);
        addPaintListener(e -> paint(e.gc));
        addListener(SWT.MouseUp, e -> {
            if (e.button == 1 && getClientArea().contains(e.x, e.y)) {
                choose();
            }
        });
        addListener(SWT.KeyDown, e -> {
            if (e.character == ' ' || e.character == '\r') {
                choose();
            }
        });
        addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_TAB_NEXT || e.detail == SWT.TRAVERSE_TAB_PREVIOUS) {
                e.doit = true;
            }
        });
        addListener(SWT.FocusIn, e -> redraw());
        addListener(SWT.FocusOut, e -> redraw());
        getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                e.result = (caption != null ? caption + " " : "") + EditorModel.hex(rgb);
            }
        });
    }

    public void setRGB(RGB color) {
        if (!color.equals(rgb)) {
            rgb = color;
            updateToolTip();
            redraw();
        }
    }

    public RGB getRGB() {
        return rgb;
    }

    public void setCaption(String text) {
        caption = text;
        updateToolTip();
        redraw();
    }

    public void setSelected(boolean isSelected) {
        if (selected != isSelected) {
            selected = isSelected;
            redraw();
        }
    }

    public boolean isSelected() {
        return selected;
    }

    /** Draws a small lock in the corner. */
    public void setLocked(boolean isLocked) {
        if (locked != isLocked) {
            locked = isLocked;
            redraw();
        }
    }

    /** Called when the chip is clicked, or Space or Enter is pressed on it. */
    public void onChoose(Consumer<ColorChip> listener) {
        onChoose = listener;
    }

    /** As a click does. */
    public void choose() {
        setFocus();
        onChoose.accept(this);
        Event event = new Event();
        notifyListeners(SWT.Selection, event);
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        return new Point(wHint == SWT.DEFAULT ? width : wHint, hHint == SWT.DEFAULT ? height : hHint);
    }

    private void updateToolTip() {
        setToolTipText(caption != null ? caption + " · " + EditorModel.hex(rgb) : EditorModel.hex(rgb));
    }

    private void paint(GC gc) {
        Rectangle area = getClientArea();
        RGB ink = Contrast.ratio(rgb, new RGB(0, 0, 0)) >= Contrast.ratio(rgb, new RGB(255, 255, 255))
                ? new RGB(0, 0, 0) : new RGB(255, 255, 255);
        Color fill = new Color(rgb);
        Color text = new Color(ink);
        gc.setBackground(getBackground());
        gc.fillRectangle(area);
        int inset = selected ? 3 : 1;
        gc.setBackground(fill);
        gc.fillRectangle(area.x + inset, area.y + inset, area.width - 2 * inset, area.height - 2 * inset);
        gc.setForeground(getForeground());
        gc.drawRectangle(area.x + inset - 1, area.y + inset - 1, area.width - 2 * inset + 1, area.height - 2 * inset + 1);
        if (selected) {
            gc.setLineWidth(2);
            gc.drawRectangle(area.x + 1, area.y + 1, area.width - 2, area.height - 2);
            gc.setLineWidth(1);
        }
        gc.setForeground(text);
        if (caption != null) {
            // Only where it fits; the tool tip always has it.
            Point extent = gc.textExtent(caption);
            if (extent.x <= area.width - 10 && extent.y <= area.height - 6) {
                gc.drawText(caption, area.x + 6, area.y + area.height - extent.y - 4, true);
            }
        }
        if (locked) {
            // A small padlock: a shackle over a body.
            int x = area.x + area.width - 14;
            int y = area.y + 5;
            gc.setBackground(text);
            gc.drawArc(x + 1, y, 6, 7, 0, 180);
            gc.fillRectangle(x, y + 4, 9, 6);
        }
        if (isFocusControl()) {
            gc.setForeground(text);
            gc.drawFocus(area.x + inset + 2, area.y + inset + 2, area.width - 2 * inset - 4, area.height - 2 * inset - 4);
        }
    }

    /** A composite of chips, as a parent to give a row of them. */
    static Composite row(org.eclipse.swt.widgets.Composite parent) {
        Composite row = new Composite(parent, SWT.NONE);
        org.eclipse.swt.layout.RowLayout layout = new org.eclipse.swt.layout.RowLayout(SWT.HORIZONTAL);
        layout.marginLeft = 0;
        layout.marginTop = 0;
        layout.spacing = 4;
        layout.wrap = true;
        row.setLayout(layout);
        return row;
    }
}
