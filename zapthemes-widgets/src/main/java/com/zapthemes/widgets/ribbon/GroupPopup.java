package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;

/** A collapsed group, opened at full size under its button; or a collapsed ribbon's tab. */
final class GroupPopup extends PopupShell {

    private final RibbonGroup group;
    private final RibbonTab tab;
    private final int width;
    private PanelSurface surface;

    GroupPopup(RibbonSurface origin, RibbonGroup group, Rectangle anchor) {
        super(origin, anchor);
        this.group = group;
        this.tab = null;
        this.width = 0;
    }

    /** The overlay of a collapsed ribbon: {@code tab} at {@code width} pixels. */
    GroupPopup(RibbonSurface origin, RibbonTab tab, Rectangle anchor, int width) {
        super(origin, anchor);
        this.group = null;
        this.tab = tab;
        this.width = width;
    }

    @Override
    Control createContent() {
        surface = tab != null ? new PanelSurface(shell, this, tab, width) : new PanelSurface(shell, this, group);
        return surface;
    }

    PanelSurface surface() {
        return surface;
    }

    RibbonGroup group() {
        return group;
    }

    RibbonTab tab() {
        return tab;
    }
}
