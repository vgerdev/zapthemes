package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.graphics.Rectangle;

/** Where an item is drawn in one surface, and at what size. */
record ItemBox(RibbonItem item, Size size, Rectangle bounds) {

    ItemBox translated(int dx) {
        return new ItemBox(item, size, new Rectangle(bounds.x + dx, bounds.y, bounds.width, bounds.height));
    }
}
