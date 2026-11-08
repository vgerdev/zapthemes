package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.graphics.Rectangle;

/**
 * A part of a surface that the mouse, the keyboard, KeyTips and screen readers can reach: a tab,
 * an item, a dialog launcher, a collapsed group's button, the collapse button, ...
 *
 * @param target the {@link RibbonTab}, {@link RibbonItem}, {@link RibbonGroup} or
 *        {@link com.zapthemes.widgets.command.Command} it stands for
 * @param box the item's box, for {@link Kind#ITEM}
 * @param arrow the arrow part of a split item, or {@code null}
 * @param index a gallery cell's index, else -1
 */
record Hot(Kind kind, Object target, ItemBox box, Rectangle bounds, Rectangle arrow, int index) {

    enum Kind {
        FILE_TAB, TAB, RIGHT, ITEM, LAUNCHER, GROUP_BUTTON, GROUP, COLLAPSE, PIN, SCROLL_LEFT, SCROLL_RIGHT,
        GALLERY_UP, GALLERY_DOWN, GALLERY_MORE, GALLERY_CELL
    }

    Hot(Kind kind, Object target, Rectangle bounds) {
        this(kind, target, null, bounds, null, -1);
    }

    /** Whether keyboard focus can rest on it. */
    boolean focusable() {
        return switch (kind) {
            case GROUP, SCROLL_LEFT, SCROLL_RIGHT, GALLERY_UP, GALLERY_DOWN, GALLERY_MORE, GALLERY_CELL -> false;
            case ITEM -> box.item().isInteractive();
            default -> true;
        };
    }

    boolean inTabRow() {
        return kind == Kind.FILE_TAB || kind == Kind.TAB || kind == Kind.RIGHT;
    }

    boolean sameAs(Hot other) {
        return other != null && kind == other.kind && target == other.target && index == other.index;
    }
}
