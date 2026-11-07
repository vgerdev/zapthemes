package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.Images;

/**
 * A labeled group of items in a {@link RibbonTab}, like Office's Clipboard or Font groups. Items
 * flow into columns: large items take a column, medium and small ones stack three to a column,
 * and {@link #row() rows} stack two or three to a column.
 *
 * <p>As the window narrows the group shrinks from {@link Size#LARGE} to {@link Size#COLLAPSED}
 * (see {@link RibbonTab#setReductionOrder}); {@link #setPriority} and {@link #setSizes} steer it.
 */
public final class RibbonGroup {

    private RibbonTab tab;
    private final String id;
    private String label;
    private Images image = Images.NONE;
    private String launcherId;
    private String keyTip;
    private int priority;
    private Size maxSize = Size.LARGE;
    private Size minSize = Size.COLLAPSED;
    private final List<Object> elements = new ArrayList<>();

    RibbonGroup(RibbonTab tab, String id, String label) {
        this.tab = tab;
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
    }

    public String id() {
        return id;
    }

    public RibbonTab tab() {
        return tab;
    }

    public Ribbon ribbon() {
        return tab.ribbon();
    }

    public String label() {
        return label;
    }

    public void setLabel(String text) {
        label = Objects.requireNonNull(text);
        changed();
    }

    /** The icon of the group's button when it is collapsed. */
    public RibbonGroup setImage(Images images) {
        image = images == null ? Images.NONE : images;
        changed();
        return this;
    }

    public Images image() {
        return image;
    }

    /** Shows the ↘ dialog launcher at the label's right, running the command {@code commandId}. */
    public RibbonGroup setDialogLauncher(String commandId) {
        if (commandId != null) {
            ribbon().commands().get(commandId);
        }
        launcherId = commandId;
        changed();
        return this;
    }

    public Command dialogLauncher() {
        return launcherId == null ? null : ribbon().commands().find(launcherId).orElse(null);
    }

    public RibbonGroup setKeyTip(String letters) {
        keyTip = letters == null ? null : letters.toUpperCase();
        return this;
    }

    public String keyTip() {
        return keyTip;
    }

    /** Lower priorities shrink first (default 0); ties shrink right to left. */
    public RibbonGroup setPriority(int value) {
        priority = value;
        changed();
        return this;
    }

    public int priority() {
        return priority;
    }

    /** The largest and smallest sizes this group takes; {@code min} below COLLAPSED keeps it open. */
    public RibbonGroup setSizes(Size max, Size min) {
        if (max.smallerThan(min)) {
            throw new IllegalArgumentException("Invalid sizes " + max + ", " + min);
        }
        maxSize = max;
        minSize = min;
        changed();
        return this;
    }

    public Size maxSize() {
        return maxSize;
    }

    public Size minSize() {
        return minSize;
    }

    // ------------------------------------------------------------------ items

    /** Adds a button for the command {@code commandId}, looked up in the ribbon's registry. */
    public RibbonButton add(String commandId) {
        return add(new RibbonButton(ribbon().commands().get(commandId)));
    }

    /** Adds a button for {@code command}, registering it in the ribbon's registry. */
    public RibbonButton add(Command command) {
        return add(new RibbonButton(ribbon().commands().add(command)));
    }

    public <T extends RibbonItem> T add(T item) {
        elements.add(item);
        attach(item);
        changed();
        return item;
    }

    /** Starts a new row; rows stack up to three to a column. */
    public RibbonRow row() {
        RibbonRow row = new RibbonRow(this);
        elements.add(row);
        return row;
    }

    /** The group's items and rows, in order. */
    public List<Object> elements() {
        return List.copyOf(elements);
    }

    /** Every item, rows flattened, in order. */
    public List<RibbonItem> items() {
        List<RibbonItem> all = new ArrayList<>();
        for (Object element : elements) {
            if (element instanceof RibbonRow row) {
                all.addAll(row.items());
            } else {
                all.add((RibbonItem) element);
            }
        }
        return all;
    }

    /** Removes {@code item}, wherever it is in the group. */
    public void remove(RibbonItem item) {
        elements.remove(item);
        for (Object element : elements) {
            if (element instanceof RibbonRow row) {
                row.removeItem(item);
            }
        }
        elements.removeIf(e -> e instanceof RibbonRow row && row.items().isEmpty());
        item.detached();
        item.group = null;
        changed();
    }

    /** Replaces every item by {@code items}, as a flat flow (customization). */
    void replaceItems(List<RibbonItem> items) {
        for (RibbonItem item : items()) {
            if (!items.contains(item)) {
                item.detached();
                item.group = null;
            }
        }
        elements.clear();
        for (RibbonItem item : items) {
            elements.add(item);
            if (item.group != this) {
                attach(item);
            }
        }
        changed();
    }

    /** Moves the group to {@code newTab} (customization); the caller updates the tabs' group lists. */
    void moveTo(RibbonTab newTab) {
        tab = newTab;
    }

    /** Puts back a snapshot of the elements: items and rows, with each row's items. */
    void restore(List<Object> snapshot, java.util.Map<RibbonRow, List<RibbonItem>> rowItems) {
        List<RibbonItem> keep = new ArrayList<>();
        for (Object element : snapshot) {
            if (element instanceof RibbonRow row) {
                keep.addAll(rowItems.getOrDefault(row, List.of()));
            } else {
                keep.add((RibbonItem) element);
            }
        }
        for (RibbonItem item : items()) {
            if (!keep.contains(item)) {
                item.detached();
                item.group = null;
            }
        }
        elements.clear();
        for (Object element : snapshot) {
            elements.add(element);
            if (element instanceof RibbonRow row) {
                row.restore(rowItems.getOrDefault(row, List.of()));
            }
        }
        for (RibbonItem item : keep) {
            if (item.group != this) {
                item.group = null;
                attach(item);
            }
        }
        changed();
    }

    void attach(RibbonItem item) {
        if (item.group != null && item.group != this) {
            throw new IllegalStateException(item + " is already in " + item.group.id());
        }
        item.group = this;
        item.attached();
        ribbon().itemAttached(item);
    }

    private void changed() {
        tab.changed();
    }

    @Override
    public String toString() {
        return "RibbonGroup[" + id + "]";
    }
}
