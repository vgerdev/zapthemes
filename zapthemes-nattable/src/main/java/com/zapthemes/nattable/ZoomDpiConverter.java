package com.zapthemes.nattable;

import org.eclipse.nebula.widgets.nattable.layer.AbstractDpiConverter;
import org.eclipse.nebula.widgets.nattable.layer.IDpiConverter;

/**
 * NatTable's own DPI scaling ({@code base}, the converter NatTable configured for the monitor) times
 * the ZapThemes zoom. NatTable keeps row heights and column widths unscaled and converts them with
 * this factor, so zooming in and out never drifts, and a size the user dragged is kept in
 * proportion.
 */
final class ZoomDpiConverter extends AbstractDpiConverter {

    private final IDpiConverter base;
    private final double zoom;

    ZoomDpiConverter(IDpiConverter base, double zoom) {
        this.base = base;
        this.zoom = zoom;
        readDpiFromDisplay();
    }

    @Override
    protected void readDpiFromDisplay() {
        if (base != null) {
            dpi = (int) Math.round(base.getDpi() * zoom);
        }
    }

    @Override
    public int getDpi() {
        return dpi;
    }

    @Override
    public float getCurrentDpiFactor() {
        // AbstractDpiConverter's conversions read the field once this has been called.
        scaleFactor = (float) ((base != null ? base.getCurrentDpiFactor() : 1f) * zoom);
        return scaleFactor;
    }
}
