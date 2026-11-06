package com.zapthemes.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;

/**
 * A horizontal progress bar drawn on a canvas, with the same value API as {@code ProgressBar}
 * ({@code minimum}, {@code maximum}, {@code selection}, {@code state}).
 *
 * <p>Theme type {@code "FlatProgressBar"}. Properties: {@code background} (track),
 * {@code barColor}, {@code errorColor} ({@link SWT#ERROR} state), {@code pausedColor}
 * ({@link SWT#PAUSED} state), {@code arc}.
 */
public class FlatProgressBar extends Canvas {

    private int minimum;
    private int maximum = 100;
    private int selection;
    private int state = SWT.NORMAL;

    @SuppressWarnings("this-escape")
    public FlatProgressBar(Composite parent, int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        addListener(SWT.Paint, this::paint);
        getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                e.result = percent() + "%";
            }
        });
        getAccessible().addAccessibleControlListener(new AccessibleControlAdapter() {
            @Override
            public void getRole(AccessibleControlEvent e) {
                e.detail = ACC.ROLE_PROGRESSBAR;
            }

            @Override
            public void getValue(AccessibleControlEvent e) {
                e.result = Integer.toString(selection);
            }
        });
        ThemeManager.applyOnCreate(this, "FlatProgressBar");
    }

    public int getMinimum() {
        checkWidget();
        return minimum;
    }

    public void setMinimum(int value) {
        checkWidget();
        if (value >= 0 && value < maximum) {
            minimum = value;
            selection = Math.max(selection, minimum);
            redraw();
        }
    }

    public int getMaximum() {
        checkWidget();
        return maximum;
    }

    public void setMaximum(int value) {
        checkWidget();
        if (value > minimum) {
            maximum = value;
            selection = Math.min(selection, maximum);
            redraw();
        }
    }

    public int getSelection() {
        checkWidget();
        return selection;
    }

    public void setSelection(int value) {
        checkWidget();
        selection = Math.max(minimum, Math.min(maximum, value));
        redraw();
    }

    /** {@link SWT#NORMAL}, {@link SWT#ERROR} or {@link SWT#PAUSED}. */
    public int getState() {
        checkWidget();
        return state;
    }

    public void setState(int state) {
        checkWidget();
        this.state = state;
        redraw();
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        return new Point(wHint == SWT.DEFAULT ? FlatPalette.px(this, 160) : wHint, hHint == SWT.DEFAULT ? FlatPalette.px(this, 8) : hHint);
    }

    private int percent() {
        return (int) Math.round(100.0 * (selection - minimum) / (maximum - minimum));
    }

    private void paint(Event event) {
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        Rectangle area = getClientArea();
        FlatPalette colors = FlatPalette.of(this, isEnabled() ? null : "disabled");
        int arc = colors.sizeOr("arc", area.height);

        gc.setBackground(getParent().getBackground());
        gc.fillRectangle(area);
        gc.setBackground(colors.background());
        gc.fillRoundRectangle(area.x, area.y, area.width, area.height, arc, arc);

        Color accent = getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
        Color bar = switch (state) {
            case SWT.ERROR -> colors.color("errorColor", getDisplay().getSystemColor(SWT.COLOR_RED));
            case SWT.PAUSED -> colors.color("pausedColor", getDisplay().getSystemColor(SWT.COLOR_YELLOW));
            default -> colors.color("barColor", accent);
        };
        int width = (int) Math.round(area.width * (selection - minimum) / (double) (maximum - minimum));
        if (width > 0) {
            gc.setBackground(bar);
            gc.fillRoundRectangle(area.x, area.y, width, area.height, arc, arc);
        }
    }
}
