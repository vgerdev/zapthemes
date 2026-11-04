package com.zapthemes.samples;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

import com.zapthemes.core.ThemeManager;

/**
 * An application's own canvas widget made theme-aware, the way Nebula widgets are: its look is
 * exposed through setters ({@code setNeedleColor}, ...), it tags itself with a control type on
 * creation, and {@link GaugeStylers} maps the theme's {@code "Gauge"} rule onto the setters.
 */
public class Gauge extends Canvas {

    private Color trackColor;
    private Color needleColor;
    private Color labelColor;
    private int thickness = 6;
    private int value;

    @SuppressWarnings("this-escape")
    public Gauge(Composite parent, int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        trackColor = getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
        needleColor = getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
        labelColor = getForeground();
        addPaintListener(e -> paint(e.gc));
        // Last in the constructor: tags the widget "Gauge" and applies the current theme now.
        ThemeManager.applyOnCreate(this, "Gauge");
    }

    private void paint(GC gc) {
        Rectangle area = getClientArea();
        int size = Math.min(area.width, area.height) - thickness - 4;
        int x = (area.width - size) / 2;
        int y = (area.height - size) / 2;
        gc.setAntialias(SWT.ON);
        gc.setLineWidth(thickness);
        gc.setLineCap(SWT.CAP_ROUND);
        gc.setForeground(trackColor);
        gc.drawArc(x, y, size, size, -45, 270);
        gc.setForeground(needleColor);
        gc.drawArc(x, y, size, size, 225, -270 * value / 100);
        gc.setForeground(labelColor);
        String text = value + "%";
        Point extent = gc.textExtent(text);
        gc.drawString(text, (area.width - extent.x) / 2, (area.height - extent.y) / 2, true);
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        return new Point(wHint == SWT.DEFAULT ? 120 : wHint, hHint == SWT.DEFAULT ? 120 : hHint);
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = Math.clamp(value, 0, 100);
        redraw();
    }

    public Color getTrackColor() {
        return trackColor;
    }

    public void setTrackColor(Color color) {
        trackColor = color != null ? color : getDisplay().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
    }

    public Color getNeedleColor() {
        return needleColor;
    }

    public void setNeedleColor(Color color) {
        needleColor = color != null ? color : getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
    }

    public Color getLabelColor() {
        return labelColor;
    }

    public void setLabelColor(Color color) {
        labelColor = color != null ? color : getForeground();
    }

    public int getThickness() {
        return thickness;
    }

    public void setThickness(int thickness) {
        this.thickness = thickness;
    }
}
