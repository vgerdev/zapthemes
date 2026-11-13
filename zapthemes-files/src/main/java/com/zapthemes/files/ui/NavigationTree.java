package com.zapthemes.files.ui;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.Place;
import com.zapthemes.files.ui.FileIcons.Glyph;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeItem;

/**
 * The browser's navigation pane: the places (pinned, with a pin at the right), a separator, then the
 * filesystem's top ("This PC" with its drives, or a root folder) as a tree of folders, loaded in the
 * background as it is expanded. Selecting an item opens its folder; the pane follows the folder
 * the browser shows.
 */
final class NavigationTree {

    /** What an item stands for. A placeholder child ("Loading…") has none. */
    private static final class Node {
        final FilePath path;
        final Place place;
        FileEntry entry;
        boolean loaded;
        boolean loading;
        Runnable whenLoaded;

        Node(FilePath path, Place place) {
            this.path = path;
            this.place = place;
        }
    }

    private static final Object SEPARATOR = new Object();

    final Tree tree;
    private final FileBrowser browser;
    private TreeItem topItem;
    /** Set while the tree selects an item itself, so that the selection doesn't navigate. */
    private boolean selecting;
    private int revealGeneration;

    NavigationTree(FileBrowser browser, Composite parent) {
        this.browser = browser;
        tree = new Tree(parent, SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.H_SCROLL);
        tree.addListener(SWT.Expand, e -> {
            if (e.item instanceof org.eclipse.swt.widgets.TreeItem item && item.getData() instanceof Node node) {
                load(item, node, null);
            }
        });
        tree.addListener(SWT.Selection, this::selected);
        tree.addListener(SWT.EraseItem, e -> {
            if (e.item.getData() == SEPARATOR) {
                e.detail &= ~(SWT.SELECTED | SWT.HOT | SWT.FOCUSED);
            }
        });
        tree.addListener(SWT.PaintItem, this::paintItem);
        ItemPainter.install(tree, item -> item.getData() instanceof Node node ? image(node) : null);
    }

    private org.eclipse.swt.graphics.Image image(Node node) {
        int size = browser.iconSize();
        if (node.place != null) {
            return browser.icons().place(node.place.kind(), size);
        }
        return node.entry != null ? browser.icons().get(node.entry, size) : browser.folderIcon(node.path);
    }

    /** Fills the tree for the browser's filesystem and places. */
    void rebuild() {
        revealGeneration++;
        tree.setRedraw(false);
        tree.removeAll();
        topItem = null;
        FileSystem fs = browser.getFileSystem();
        if (fs != null) {
            List<Place> places = browser.getPlaces();
            for (Place place : places) {
                TreeItem item = new TreeItem(tree, SWT.NONE);
                item.setText(place.label());
                item.setData(new Node(place.path(), place));
            }
            if (!places.isEmpty()) {
                TreeItem separator = new TreeItem(tree, SWT.NONE);
                separator.setData(SEPARATOR);
            }
            topItem = new TreeItem(tree, SWT.NONE);
            topItem.setText(fs.topName());
            topItem.setData(new Node(FilePath.TOP, null));
            new TreeItem(topItem, SWT.NONE).setText("Loading…");
        }
        tree.setRedraw(true);
    }

    /** Repaints the items (after a zoom: the icons are drawn at the new size). */
    void refreshImages() {
        tree.redraw();
    }

    /** Lists {@code node}'s subfolders into {@code item} once, then runs {@code then}. */
    private void load(org.eclipse.swt.widgets.TreeItem item, Node node, Runnable then) {
        if (node.loaded) {
            if (then != null) {
                then.run();
            }
            return;
        }
        if (then != null) {
            Runnable previous = node.whenLoaded;
            node.whenLoaded = previous == null ? then : () -> {
                previous.run();
                then.run();
            };
        }
        if (node.loading) {
            return;
        }
        node.loading = true;
        FileSystem fs = browser.getFileSystem();
        browser.listFolderEntries(node.path, (folders, error) -> {
            node.loading = false;
            if (item.isDisposed() || fs != browser.getFileSystem()) {
                return;
            }
            node.loaded = true;
            tree.setRedraw(false);
            for (org.eclipse.swt.widgets.TreeItem child : item.getItems()) {
                child.dispose();
            }
            if (folders != null) {
                for (FileEntry folder : folders) {
                    TreeItem child = new TreeItem(item, SWT.NONE);
                    child.setText(folder.displayName());
                    Node childNode = new Node(folder.path(), null);
                    childNode.entry = folder;
                    child.setData(childNode);
                    // Whether it has subfolders is known once it is expanded.
                    new TreeItem(child, SWT.NONE).setText("Loading…");
                }
            }
            tree.setRedraw(true);
            Runnable done = node.whenLoaded;
            node.whenLoaded = null;
            if (done != null) {
                done.run();
            }
        });
    }

    private void selected(Event event) {
        if (selecting || !(event.item instanceof org.eclipse.swt.widgets.TreeItem item)) {
            return;
        }
        if (item.getData() instanceof Node node) {
            browser.navigate(node.path);
        } else {
            // The separator or a placeholder: back to the folder shown.
            reveal(browser.getCurrentPath());
        }
    }

    /** Selects the item of {@code path}: a place that is exactly it, else its folder under the top, expanding the way there. */
    void reveal(FilePath path) {
        int generation = ++revealGeneration;
        for (org.eclipse.swt.widgets.TreeItem item : tree.getItems()) {
            if (item.getData() instanceof Node node && node.place != null && node.path.equals(path)) {
                select(item);
                return;
            }
        }
        if (topItem == null || topItem.isDisposed()) {
            return;
        }
        reveal(topItem, 0, path, generation, false);
    }

    private void reveal(org.eclipse.swt.widgets.TreeItem item, int depth, FilePath path, int generation, boolean relisted) {
        if (generation != revealGeneration || item.isDisposed()) {
            return;
        }
        if (depth == path.depth()) {
            select(item);
            return;
        }
        Node node = (Node) item.getData();
        load(item, node, () -> {
            if (generation != revealGeneration || item.isDisposed()) {
                return;
            }
            String segment = path.segments().get(depth);
            for (org.eclipse.swt.widgets.TreeItem child : item.getItems()) {
                if (child.getData() instanceof Node c && c.path.name().equals(segment)) {
                    item.setExpanded(true);
                    reveal(child, depth + 1, path, generation, false);
                    return;
                }
            }
            if (!relisted) {
                // Listed before the browser went there: a hidden folder on the way was left out.
                node.loaded = false;
                reveal(item, depth, path, generation, true);
                return;
            }
            // Not a listed folder (gone): the nearest one listed stays selected.
            select(item);
        });
    }

    /** Selects {@code item} without opening its folder (a right click). */
    void selectQuietly(org.eclipse.swt.widgets.TreeItem item) {
        select(item);
    }

    private void select(org.eclipse.swt.widgets.TreeItem item) {
        selecting = true;
        try {
            tree.setSelection(item);
            tree.showItem(item);
        } finally {
            selecting = false;
        }
    }

    /** The selected item as an entry (a place or the top as a folder), or null. */
    FileEntry selectedEntry() {
        org.eclipse.swt.widgets.TreeItem[] selection = tree.getSelection();
        if (selection.length != 1 || !(selection[0].getData() instanceof Node node)) {
            return null;
        }
        if (node.entry != null) {
            return node.entry;
        }
        return FileEntry.builder(node.path, com.zapthemes.files.EntryKind.DIRECTORY)
                .label(node.place != null ? node.place.label() : selection[0].getText()).build();
    }

    private InlineEditor editor;

    /** Opens a field over the selected folder's name; false for places, the top, or nothing selected. */
    boolean startRename(FileEntry entry, java.util.function.Consumer<String> commit) {
        org.eclipse.swt.widgets.TreeItem[] selection = tree.getSelection();
        if (selection.length != 1 || !(selection[0].getData() instanceof Node node) || node.place != null || node.path.isTop()
                || !node.path.equals(entry.path())) {
            return false;
        }
        org.eclipse.swt.widgets.TreeItem item = selection[0];
        Rectangle bounds = item.getBounds();
        int offset = px(2 + 6) + browser.iconSize();
        int width = Math.max(px(80), tree.getClientArea().width - bounds.x - offset - px(4));
        editor = InlineEditor.open(tree, new Rectangle(bounds.x + offset, bounds.y, width, bounds.height), entry.name(), true, commit,
                () -> tree.setFocus());
        return true;
    }

    /** The open rename field, or null (tests). */
    InlineEditor editor() {
        return editor != null && editor.isOpen() ? editor : null;
    }

    /** Follows a rename: the item of {@code from} (if listed) shows {@code to}; its subtree is listed again when expanded. */
    void renamed(FilePath from, FilePath to) {
        org.eclipse.swt.widgets.TreeItem item = find(from);
        if (item == null) {
            return;
        }
        Node old = (Node) item.getData();
        Node node = new Node(to, old.place);
        node.entry = old.entry != null ? old.entry.withPath(to) : null;
        item.setData(node);
        item.setText(to.name());
        for (org.eclipse.swt.widgets.TreeItem child : item.getItems()) {
            child.dispose();
        }
        item.setExpanded(false);
        new TreeItem(item, SWT.NONE).setText("Loading…");
    }

    /** Follows a delete: the items of {@code entries} go. */
    void deleted(List<FileEntry> entries) {
        for (FileEntry entry : entries) {
            org.eclipse.swt.widgets.TreeItem item = find(entry.path());
            if (item != null && item != topItem) {
                item.dispose();
            }
        }
    }

    /** The listed item of {@code path} under the top, or null. */
    private org.eclipse.swt.widgets.TreeItem find(FilePath path) {
        if (topItem == null || topItem.isDisposed()) {
            return null;
        }
        org.eclipse.swt.widgets.TreeItem item = topItem;
        for (String segment : path.segments()) {
            org.eclipse.swt.widgets.TreeItem next = null;
            for (org.eclipse.swt.widgets.TreeItem child : item.getItems()) {
                if (child.getData() instanceof Node c && c.path.name().equals(segment)) {
                    next = child;
                    break;
                }
            }
            if (next == null) {
                return null;
            }
            item = next;
        }
        return item;
    }

    /** The selected item's path, or null (tests). */
    FilePath selectedPath() {
        org.eclipse.swt.widgets.TreeItem[] selection = tree.getSelection();
        return selection.length == 1 && selection[0].getData() instanceof Node node ? node.path : null;
    }

    /** The labels of the items at the top level (tests). */
    List<String> rootLabels() {
        return java.util.Arrays.stream(tree.getItems()).map(item -> item.getData() == SEPARATOR ? "---" : item.getText()).toList();
    }

    private void paintItem(Event event) {
        Object data = event.item.getData();
        GC gc = event.gc;
        Rectangle area = tree.getClientArea();
        if (data == SEPARATOR) {
            Color line = new Color(BarColors.blend(tree.getForeground().getRGB(), tree.getBackground().getRGB(), 0.2));
            gc.setForeground(line);
            int y = event.y + event.height / 2;
            gc.drawLine(area.x + px(8), y, area.x + area.width - px(8), y);
        } else if (data instanceof Node node && node.place != null) {
            int size = px(12);
            Color pin = new Color(BarColors.blend(tree.getForeground().getRGB(), tree.getBackground().getRGB(), 0.6));
            FileIcons.draw(gc, Glyph.PIN, area.x + area.width - size - px(10), event.y + (event.height - size) / 2, size, pin);
        }
    }

    private int px(int pixels) {
        return ThemeManager.get(tree.getDisplay()).resources().zoomedPixels(pixels);
    }
}
