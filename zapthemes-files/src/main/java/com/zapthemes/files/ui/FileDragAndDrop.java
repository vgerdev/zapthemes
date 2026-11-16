package com.zapthemes.files.ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;
import com.zapthemes.files.Capability;
import com.zapthemes.files.EntryKind;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FilePath;
import com.zapthemes.files.FileSystem;
import com.zapthemes.files.LocalFileSystem;
import com.zapthemes.files.TransferRequest;
import com.zapthemes.files.ui.FileEntryTransfer.Dragged;

/**
 * Drag and drop for a {@link FileBrowser}'s file view and navigation pane, as in Explorer.
 * <ul>
 * <li><b>Out:</b> the selected items (or the folder dragged in the pane) as a
 * {@link FileEntryTransfer}, and as a {@code FileTransfer} too when they are local files, so other
 * applications take them.</li>
 * <li><b>In:</b> onto a folder (highlighted while the mouse is over it), else into the folder shown.
 * Within one volume a drop moves, between volumes or filesystems it copies; Ctrl copies, Shift
 * moves. A folder can't go into itself, and a move into the folder it is in does nothing. Files
 * from other applications are copied (Shift moves them, the browser doing the move, so the other
 * application never deletes them itself).</li>
 * <li>Every drop becomes a {@link TransferRequest} for the browser's {@code FileTransferHandler},
 * as a paste does.</li>
 * </ul>
 */
final class FileDragAndDrop {

    /** The drag this process has going, if any: lets a drop in this process see what comes during the drag. */
    private static Dragged dragging;
    private static FileBrowser draggedFrom;

    private final FileBrowser browser;
    /** The row or tree item highlighted as where a drop goes, or null. */
    private Item dropItem;
    /** Whether the last drop of this browser's own drag landed in this process (so the drop refreshed). */
    private static boolean droppedHere;

    FileDragAndDrop(FileBrowser browser) {
        this.browser = browser;
        Table table = browser.fileTable().table;
        Tree tree = browser.navigationTree().tree;
        dragSource(table, false);
        dragSource(tree, true);
        dropTarget(table);
        dropTarget(tree);
        table.addListener(SWT.EraseItem, this::paintDropItem);
        tree.addListener(SWT.EraseItem, this::paintDropItem);
    }

    // ------------------------------------------------------------------ out

    /** Sets the browser a drag in progress comes from (tests, which drop without a native drag). */
    static void setDragFrom(FileBrowser from) {
        draggedFrom = from;
    }

    /** What a drag starting at {@code (x, y)} of the file view or pane takes, or empty if nothing can be dragged there. */
    List<FileEntry> draggable(boolean fromTree, int x, int y) {
        if (browser.getFileSystem() == null) {
            return List.of();
        }
        List<FileEntry> entries;
        if (fromTree) {
            TreeItem item = browser.navigationTree().tree.getItem(new Point(x, y));
            FileEntry entry = item == null ? null : browser.navigationTree().folderEntry(item);
            entries = entry == null ? List.of() : List.of(entry);
        } else {
            entries = browser.fileTable().selection();
        }
        boolean fixed = entries.stream().anyMatch(e -> e.kind() == EntryKind.DRIVE || e.path().isTop());
        return fixed ? List.of() : entries;
    }

    private void dragSource(Control control, boolean fromTree) {
        DragSource source = new DragSource(control, DND.DROP_COPY | DND.DROP_MOVE);
        source.setTransfer(FileEntryTransfer.getInstance(), FileTransfer.getInstance());
        source.addDragListener(new DragSourceListener() {
            @Override
            public void dragStart(DragSourceEvent event) {
                List<FileEntry> entries = draggable(fromTree, event.x, event.y);
                if (entries.isEmpty() || browser.actions().renaming()) {
                    event.doit = false;
                    return;
                }
                browser.fileTable().cancelSlowClick();
                FileSystem fs = browser.getFileSystem();
                dragging = new Dragged(fs, entries);
                draggedFrom = browser;
                droppedHere = false;
                // Other applications get files only when they are on disk.
                boolean local = entries.stream().allMatch(e -> LocalFileSystem.diskPath(fs, e.path()).isPresent());
                source.setTransfer(local ? new Transfer[] { FileEntryTransfer.getInstance(), FileTransfer.getInstance() }
                        : new Transfer[] { FileEntryTransfer.getInstance() });
            }

            @Override
            public void dragSetData(DragSourceEvent event) {
                Dragged dragged = dragging;
                if (dragged == null) {
                    return;
                }
                if (FileEntryTransfer.getInstance().isSupportedType(event.dataType)) {
                    event.data = dragged;
                } else if (FileTransfer.getInstance().isSupportedType(event.dataType)) {
                    event.data = dragged.entries().stream()
                            .map(e -> LocalFileSystem.diskPath(dragged.fileSystem(), e.path()).map(Path::toString).orElse(null))
                            .filter(Objects::nonNull).toArray(String[]::new);
                }
            }

            @Override
            public void dragFinished(DragSourceEvent event) {
                boolean here = droppedHere;
                dragging = null;
                draggedFrom = null;
                droppedHere = false;
                // Another application took the files (and may have moved them): list the folder again.
                if (!here && event.detail != DND.DROP_NONE && !browser.isDisposed()) {
                    browser.refresh();
                }
            }
        });
    }

    // ------------------------------------------------------------------ in

    private void dropTarget(Control control) {
        DropTarget target = new DropTarget(control, DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_DEFAULT);
        target.setTransfer(FileEntryTransfer.getInstance(), FileTransfer.getInstance());
        target.addDropListener(new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetEvent event) {
                over(event);
            }

            @Override
            public void dragOver(DropTargetEvent event) {
                over(event);
            }

            @Override
            public void dragOperationChanged(DropTargetEvent event) {
                over(event);
            }

            @Override
            public void dragLeave(DropTargetEvent event) {
                highlight(null);
            }

            @Override
            public void dropAccept(DropTargetEvent event) {
                over(event);
            }

            @Override
            public void drop(DropTargetEvent event) {
                highlight(null);
                FilePath into = folderAt(event.item);
                if (event.data instanceof Dragged dragged) {
                    int operation = operation(dragged, into, event.detail);
                    event.detail = FileDragAndDrop.this.drop(dragged, into, operation) ? operation : DND.DROP_NONE;
                } else if (event.data instanceof String[] files && files.length > 0) {
                    int operation = operation(null, into, event.detail);
                    if (operation == DND.DROP_NONE) {
                        event.detail = DND.DROP_NONE;
                        return;
                    }
                    dropFiles(files, into, operation == DND.DROP_MOVE);
                    // The browser moves them itself: the other application must not delete them.
                    event.detail = DND.DROP_COPY;
                } else {
                    event.detail = DND.DROP_NONE;
                }
            }
        });
    }

    /** Picks the data to take and the operation the mouse shows, and highlights the folder under it. */
    private void over(DropTargetEvent event) {
        TransferData entries = find(event.dataTypes, FileEntryTransfer.getInstance());
        TransferData files = find(event.dataTypes, FileTransfer.getInstance());
        Dragged dragged = null;
        if (entries != null && dragging != null) {
            event.currentDataType = entries;
            dragged = dragging;
        } else if (files != null) {
            event.currentDataType = files;
        } else {
            event.detail = DND.DROP_NONE;
            highlight(null);
            return;
        }
        FilePath into = folderAt(event.item);
        int operation = operation(dragged, into, event.detail);
        if ((event.operations & operation) == 0) {
            operation = (event.operations & DND.DROP_COPY) != 0 && operation != DND.DROP_NONE ? DND.DROP_COPY : DND.DROP_NONE;
        }
        event.detail = operation;
        event.feedback = DND.FEEDBACK_SCROLL | (event.widget instanceof DropTarget t && t.getControl() instanceof Tree ? DND.FEEDBACK_EXPAND : 0);
        highlight(operation != DND.DROP_NONE && intoItem(event.item) ? (Item) event.item : null);
    }

    private static TransferData find(TransferData[] types, Transfer transfer) {
        if (types != null) {
            for (TransferData type : types) {
                if (transfer.isSupportedType(type)) {
                    return type;
                }
            }
        }
        return null;
    }

    /** Whether a drop on {@code item} goes into it (a folder), rather than into the folder shown. */
    private boolean intoItem(org.eclipse.swt.widgets.Widget item) {
        if (item instanceof TableItem row) {
            int index = row.getParent().indexOf(row);
            List<FileEntry> rows = browser.fileTable().rows();
            return index >= 0 && index < rows.size() && rows.get(index).isContainer();
        }
        return item instanceof TreeItem node && browser.navigationTree().folderOf(node) != null;
    }

    /**
     * Where a drop on {@code item} goes: the folder of that row or tree item, else (a file, empty
     * space) the folder shown; null where nothing can go (the drives of "This PC", a separator).
     */
    FilePath folderAt(org.eclipse.swt.widgets.Widget item) {
        FilePath folder;
        if (item instanceof TreeItem node) {
            folder = browser.navigationTree().folderOf(node);
        } else if (item instanceof TableItem && intoItem(item)) {
            TableItem row = (TableItem) item;
            folder = browser.fileTable().rows().get(row.getParent().indexOf(row)).path();
        } else if (item == null || item instanceof TableItem) {
            folder = browser.getCurrentPath();
        } else {
            folder = null;
        }
        FileSystem fs = browser.getFileSystem();
        if (folder == null || fs == null || fs instanceof LocalFileSystem && LocalFileSystem.diskPath(fs, folder).isEmpty()) {
            return null;
        }
        return folder;
    }

    /**
     * What dropping {@code dragged} (null: files of another application) into {@code into} does, for
     * {@code requested} ({@code DND.DROP_DEFAULT} when no key is held): a {@code DND} operation, or
     * {@code DROP_NONE}.
     */
    int operation(Dragged dragged, FilePath into, int requested) {
        FileSystem fs = browser.getFileSystem();
        if (into == null || fs == null || !(fs.has(Capability.WRITE) || fs.has(Capability.CREATE_DIRECTORY))) {
            return DND.DROP_NONE;
        }
        if (dragged != null && dragged.entries().isEmpty()) {
            return DND.DROP_NONE;
        }
        boolean same = dragged != null && sameFileSystem(dragged.fileSystem(), fs);
        if (same) {
            for (FileEntry entry : dragged.entries()) {
                if (inside(fs, into, dragged.fileSystem(), entry.path())) {
                    return DND.DROP_NONE; // A folder into itself.
                }
            }
        }
        int operation;
        if (requested == DND.DROP_COPY || requested == DND.DROP_MOVE) {
            operation = requested;
        } else {
            operation = dragged != null && sameVolume(dragged, fs, into) ? DND.DROP_MOVE : DND.DROP_COPY;
        }
        if (operation == DND.DROP_MOVE && dragged != null) {
            FileSystem from = dragged.fileSystem();
            if (!(from.has(Capability.MOVE) && same || from.has(Capability.DELETE))) {
                operation = DND.DROP_COPY;
            } else if (same && dragged.entries().stream().allMatch(e -> disk(from, e.path().parent()).equals(disk(fs, into)))) {
                return DND.DROP_NONE; // Already there.
            }
        }
        return operation;
    }

    /** The same filesystem, or two views of the local disk (a rooted folder and the whole disk). */
    private static boolean sameFileSystem(FileSystem a, FileSystem b) {
        return a == b || isDisk(a) && isDisk(b);
    }

    /** Whether {@code fs} is the local disk or a folder of it. */
    private static boolean isDisk(FileSystem fs) {
        return fs instanceof LocalFileSystem || fs instanceof com.zapthemes.files.RootedFileSystem rooted && isDisk(rooted.base());
    }

    /** Whether {@code into} of {@code fs} is {@code folder} of {@code other}, or inside it (on disk, across two views of it). */
    private static boolean inside(FileSystem fs, FilePath into, FileSystem other, FilePath folder) {
        Optional<Path> target = LocalFileSystem.diskPath(fs, into);
        Optional<Path> source = LocalFileSystem.diskPath(other, folder);
        if (target.isPresent() && source.isPresent()) {
            return target.get().normalize().startsWith(source.get().normalize());
        }
        return fs == other && into.startsWith(folder);
    }

    /** {@code path} of {@code fs} on disk when it has one, else itself: for comparing folders across two views of the disk. */
    private static Object disk(FileSystem fs, FilePath path) {
        Optional<Path> disk = path == null ? Optional.empty() : LocalFileSystem.diskPath(fs, path);
        return disk.<Object>map(Path::normalize).orElse(path);
    }

    /** Whether a drop moves by default: within one filesystem, and for disk files within one drive. */
    private static boolean sameVolume(Dragged dragged, FileSystem fs, FilePath into) {
        Optional<Path> target = LocalFileSystem.diskPath(fs, into);
        if (target.isPresent()) {
            Path root = target.get().getRoot();
            return dragged.entries().stream().allMatch(e -> LocalFileSystem.diskPath(dragged.fileSystem(), e.path())
                    .map(p -> Objects.equals(p.getRoot(), root)).orElse(false));
        }
        return dragged.fileSystem() == fs;
    }

    /** Drops {@code dragged} into {@code into} with {@code operation}; false if that does nothing. */
    boolean drop(Dragged dragged, FilePath into, int operation) {
        if (operation != DND.DROP_COPY && operation != DND.DROP_MOVE || into == null) {
            return false;
        }
        FileSystem fs = browser.getFileSystem();
        boolean move = operation == DND.DROP_MOVE;
        FileBrowser from = draggedFrom;
        droppedHere = true;
        TransferRequest request = onDisk(new TransferRequest(dragged.fileSystem(), dragged.entries(), fs, into, move));
        browser.actions().transfer(request, false, move ? "Can't move" : "Can't copy", () -> {
            if (move && from != null && from != browser && !from.isDisposed()) {
                from.navigationTree().deleted(dragged.entries().stream().filter(FileEntry::isContainer).toList());
                from.refresh();
            } else if (move && from == browser) {
                browser.navigationTree().deleted(dragged.entries().stream().filter(FileEntry::isContainer).toList());
            }
            if (!into.equals(browser.getCurrentPath())) {
                browser.navigationTree().relist(into);
            }
        });
        return true;
    }

    /**
     * Within one disk seen through two filesystems (a rooted folder and the whole disk), the same
     * transfer on the local filesystem itself, so a move is a rename on disk rather than a copy.
     */
    private static TransferRequest onDisk(TransferRequest request) {
        if (request.withinOneFileSystem()) {
            return request;
        }
        Optional<Path> target = LocalFileSystem.diskPath(request.target(), request.targetFolder());
        List<FileEntry> sources = new ArrayList<>();
        LocalFileSystem local = LocalFileSystem.get();
        for (FileEntry entry : request.sources()) {
            Optional<Path> disk = LocalFileSystem.diskPath(request.source(), entry.path());
            if (disk.isEmpty() || target.isEmpty()) {
                return request;
            }
            sources.add(entry.withPath(local.fromPath(disk.get())));
        }
        return new TransferRequest(local, sources, local, local.fromPath(target.get()), request.move());
    }

    /** Copies (or moves) files another application dropped into {@code into}. */
    private void dropFiles(String[] files, FilePath into, boolean move) {
        FileSystem fs = browser.getFileSystem();
        LocalFileSystem local = LocalFileSystem.get();
        browser.loader().run(() -> {
            List<FileEntry> entries = new ArrayList<>();
            for (String file : files) {
                local.stat(local.fromPath(Path.of(file))).ifPresent(entries::add);
            }
            return entries;
        }, (entries, failure) -> {
            if (failure != null) {
                browser.reportFailure(move ? "Can't move" : "Can't copy", failure);
            } else if (!entries.isEmpty() && fs == browser.getFileSystem()) {
                Dragged dragged = new Dragged(local, entries);
                int operation = operation(dragged, into, move ? DND.DROP_MOVE : DND.DROP_COPY);
                drop(dragged, into, operation);
            }
        });
    }

    // ------------------------------------------------------------------ highlight

    /** The item highlighted as the drop's folder, or null (tests). */
    Item dropItem() {
        return dropItem != null && !dropItem.isDisposed() ? dropItem : null;
    }

    /** Highlights {@code item} (a folder row or tree item) as where a drop goes, or nothing. */
    void highlight(Item item) {
        if (item == dropItem) {
            return;
        }
        Item previous = dropItem;
        if (previous != null && !previous.isDisposed()) {
            // Back to its own color (a stripe, or none).
            setBackground(previous, dropItemBackground);
        }
        dropItem = item;
        dropItemBackground = null;
        if (item != null) {
            // As the item's color too: Windows paints the other cells' text over it, GTK the name.
            dropItemBackground = item instanceof TableItem row ? row.getBackground() : ((TreeItem) item).getBackground();
            setBackground(item, dropColor(item instanceof TableItem row ? row.getParent() : ((TreeItem) item).getParent()));
        }
        redraw(previous);
        redraw(item);
    }

    private Color dropItemBackground;

    private static void setBackground(Item item, Color color) {
        if (item instanceof TableItem row) {
            row.setBackground(color);
        } else if (item instanceof TreeItem node) {
            node.setBackground(color);
        }
    }

    private static void redraw(Item item) {
        if (item instanceof TableItem row && !row.isDisposed()) {
            Rectangle bounds = row.getBounds();
            Table table = row.getParent();
            table.redraw(0, bounds.y, table.getClientArea().width, bounds.height, false);
        } else if (item instanceof TreeItem node && !node.isDisposed()) {
            Rectangle bounds = node.getBounds();
            Tree tree = node.getParent();
            tree.redraw(0, bounds.y, tree.getClientArea().width, bounds.height, false);
        }
    }

    /** Paints the drop folder's row as the theme paints a row under the mouse. */
    private void paintDropItem(Event event) {
        if (event.item == null || event.item != dropItem || (event.detail & SWT.SELECTED) != 0) {
            return;
        }
        Control control = (Control) event.widget;
        Color fill = dropColor(control);
        int columns = control instanceof Table table ? table.getColumnCount() : ((Tree) control).getColumnCount();
        Rectangle area = control instanceof Table table ? table.getClientArea() : ((Tree) control).getClientArea();
        int width = columns == 0 || event.index == columns - 1 ? Math.max(event.width, area.x + area.width - event.x) : event.width;
        event.gc.setBackground(fill);
        event.gc.fillRectangle(event.x, event.y, width, event.height);
        event.detail &= ~(SWT.BACKGROUND | SWT.HOT);
    }

    private static Color dropColor(Control control) {
        ThemeManager manager = ThemeManager.get(control.getDisplay());
        ResolvedStyle style = manager.styleOf(control);
        if (style.states().contains("hover")) {
            Color hover = new ThemeContext(manager.resources()).color(style.state("hover"), "background");
            if (hover != null) {
                return hover;
            }
        }
        Color selection = control.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION);
        return new Color(BarColors.blend(selection.getRGB(), control.getBackground().getRGB(), 0.3));
    }
}
