package com.zapthemes.core;

import java.util.function.Consumer;

import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/** Depth-first walk over a control tree, including the items of item-bearing controls. */
final class WidgetTree {

    private WidgetTree() {
    }

    static void walk(Widget widget, Consumer<Widget> visitor) {
        if (widget.isDisposed()) {
            return;
        }
        visitor.accept(widget);
        switch (widget) {
            case Table table -> forEach(table.getItems(), visitor);
            case Tree tree -> walkTreeItems(tree.getItems(), visitor);
            case CTabFolder folder -> forEach(folder.getItems(), visitor);
            case ToolBar toolBar -> forEach(toolBar.getItems(), visitor);
            default -> {
            }
        }
        if (widget instanceof Composite composite) {
            for (var child : composite.getChildren()) {
                walk(child, visitor);
            }
        }
    }

    private static void walkTreeItems(TreeItem[] items, Consumer<Widget> visitor) {
        for (TreeItem item : items) {
            visitor.accept(item);
            walkTreeItems(item.getItems(), visitor);
        }
    }

    private static void forEach(Widget[] items, Consumer<Widget> visitor) {
        for (Widget item : items) {
            visitor.accept(item);
        }
    }
}
