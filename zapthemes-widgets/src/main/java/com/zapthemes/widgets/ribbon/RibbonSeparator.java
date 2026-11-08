package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;

/** A vertical line between items of a {@link RibbonRow}. */
public final class RibbonSeparator extends RibbonItem {

    private static int counter;

    public RibbonSeparator() {
        super("separator." + ++counter);
        defaultSizes(Size.SMALL, Size.SMALL);
    }

    @Override
    public String label() {
        return "";
    }

    @Override
    boolean isInteractive() {
        return false;
    }

    @Override
    int accessibleRole() {
        return ACC.ROLE_SEPARATOR;
    }

    @Override
    Point measure(RibbonMetrics metrics, Size size) {
        return new Point(metrics.px(7), metrics.row);
    }

    @Override
    void paint(RibbonSurface surface, GC gc, ItemBox box) {
        Rectangle b = box.bounds();
        gc.setForeground(surface.colors().separator);
        int x = b.x + b.width / 2;
        gc.drawLine(x, b.y + 3, x, b.y + b.height - 4);
    }

    @Override
    void activate(RibbonSurface surface, ItemBox box, boolean arrow, Event event) {
    }
}
