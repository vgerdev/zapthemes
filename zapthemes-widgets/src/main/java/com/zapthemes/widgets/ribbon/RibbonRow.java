package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;

import com.zapthemes.widgets.command.Command;

/**
 * A horizontal run of small items in a group, like the rows of Office's Font and Paragraph
 * groups. Consecutive rows stack, up to three to a column. Items in a row default to small.
 */
public final class RibbonRow {

    private final RibbonGroup group;
    private final List<RibbonItem> items = new ArrayList<>();

    RibbonRow(RibbonGroup group) {
        this.group = group;
    }

    public RibbonGroup group() {
        return group;
    }

    /** Adds a button for the command {@code id}, looked up in the ribbon's registry. */
    public RibbonRow add(String commandId) {
        return add(new RibbonButton(group.ribbon().commands().get(commandId)));
    }

    /** Adds a button for {@code command}, registering it. */
    public RibbonRow add(Command command) {
        return add(new RibbonButton(group.ribbon().commands().add(command)));
    }

    public RibbonRow add(RibbonItem item) {
        item.defaultSizes(Size.SMALL, Size.SMALL);
        items.add(item);
        group.attach(item);
        return this;
    }

    public RibbonRow separator() {
        return add(new RibbonSeparator());
    }

    public List<RibbonItem> items() {
        return List.copyOf(items);
    }

    void restore(List<RibbonItem> snapshot) {
        items.clear();
        items.addAll(snapshot);
    }

    void removeItem(RibbonItem item) {
        items.remove(item);
    }
}
