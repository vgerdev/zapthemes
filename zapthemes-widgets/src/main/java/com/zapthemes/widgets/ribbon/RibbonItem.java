package com.zapthemes.widgets.ribbon;

import java.util.Objects;

import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.widgets.command.Command;

/**
 * One thing in a {@link RibbonGroup}: a button, a combo box, a gallery, a separator. Items are
 * model objects that the ribbon lays out and paints; they are not widgets.
 *
 * <p>Each item has an id, unique in its ribbon (a button's is its command's id), the sizes it may
 * take ({@link #sizes(Size, Size)}), and an optional KeyTip.
 */
public abstract class RibbonItem {

    private final String id;
    private Size maxSize = Size.MEDIUM;
    private Size minSize = Size.SMALL;
    private boolean sizesSet;
    private String keyTip;
    RibbonGroup group;

    protected RibbonItem(String id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public String id() {
        return id;
    }

    public RibbonGroup group() {
        return group;
    }

    /** The ribbon this item is in, or {@code null} before it is added. */
    public Ribbon ribbon() {
        return group == null ? null : group.ribbon();
    }

    /**
     * The largest and the smallest size this item may take as its group shrinks. Buttons default
     * to medium and small (small and small in a {@link RibbonRow}).
     */
    public RibbonItem sizes(Size max, Size min) {
        if (max == Size.COLLAPSED || min == Size.COLLAPSED || max.smallerThan(min)) {
            throw new IllegalArgumentException("Invalid sizes " + max + ", " + min);
        }
        maxSize = max;
        minSize = min;
        sizesSet = true;
        changed();
        return this;
    }

    /** Large, then medium: shorthand for {@code sizes(LARGE, MEDIUM)}. */
    public RibbonItem large() {
        return sizes(Size.LARGE, Size.MEDIUM);
    }

    public Size maxSize() {
        return maxSize;
    }

    public Size minSize() {
        return minSize;
    }

    boolean sizesSet() {
        return sizesSet;
    }

    final void defaultSizes(Size max, Size min) {
        if (!sizesSet) {
            maxSize = max;
            minSize = min;
        }
    }

    /** Overrides the assigned KeyTip (one or two letters). */
    public RibbonItem keyTip(String letters) {
        keyTip = letters == null ? null : letters.toUpperCase();
        return this;
    }

    public String keyTip() {
        return keyTip;
    }

    /** The command this item shows, or {@code null}. */
    public Command command() {
        return null;
    }

    /** The label users see: in KeyTip assignment, tooltips, accessibility and customization. */
    public abstract String label();

    /** The super tooltip's body, or {@code null}. */
    public String description() {
        Command command = command();
        return command == null ? null : command.description();
    }

    public boolean isEnabled() {
        Command command = command();
        return command == null || command.isEnabled();
    }

    public boolean isVisible() {
        Command command = command();
        return command == null || command.isVisible();
    }

    /** Whether a user may focus and run it (separators may not). */
    boolean isInteractive() {
        return true;
    }

    /** The size this item is drawn at when its group is {@code groupSize}. */
    Size resolve(Size groupSize) {
        return groupSize.clamp(maxSize, minSize);
    }

    /** The item's width and height at {@code size}. */
    abstract Point measure(RibbonMetrics metrics, Size size);

    /** Paints the item into {@code box.bounds()}. */
    abstract void paint(RibbonSurface surface, GC gc, ItemBox box);

    /** The arrow part of a split item at {@code box}, or {@code null}. */
    Rectangle arrowBounds(RibbonMetrics metrics, ItemBox box) {
        return null;
    }

    /** What a click, Enter or Space does: on the arrow part when {@code arrow}. */
    abstract void activate(RibbonSurface surface, ItemBox box, boolean arrow, Event event);

    /** Whether Down opens something (a menu, list or palette). */
    boolean hasPopup() {
        return false;
    }

    int accessibleRole() {
        return ACC.ROLE_PUSHBUTTON;
    }

    /** Relays out and repaints the ribbon. */
    protected void changed() {
        Ribbon ribbon = ribbon();
        if (ribbon != null && !ribbon.isDisposed()) {
            ribbon.modelChanged();
        }
    }

    /** Repaints the ribbon. */
    protected void repaint() {
        Ribbon ribbon = ribbon();
        if (ribbon != null && !ribbon.isDisposed()) {
            ribbon.repaintAll();
        }
    }

    void attached() {
    }

    void detached() {
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + id + "]";
    }
}
