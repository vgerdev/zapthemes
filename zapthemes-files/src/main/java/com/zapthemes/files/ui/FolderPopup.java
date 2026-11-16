package com.zapthemes.files.ui;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;

/**
 * The drop-down of a breadcrumb chevron: a borderless shell under it with a scrolling list (the
 * subfolders of that level, or the places). Picking an item runs its action and closes it; Escape,
 * a click elsewhere or another window closes it too.
 *
 * <p>The address field's suggestions use one too, opened without taking the focus
 * ({@link #suggest}): the field keeps the keyboard and moves the selection with the arrow keys.
 */
final class FolderPopup {

    /** One row. */
    record Item(String label, Image image, Runnable action) {
    }

    /**
     * Whether a popup closes when its window loses activation. Tests turn it off: without a window
     * manager (CI), activation comes and goes on its own.
     */
    static boolean closeOnDeactivate = true;

    private static final int MAX_ROWS = 14;

    final Shell shell;
    final Table table;
    private final boolean keepFocus;
    private List<Item> items;

    private FolderPopup(Control anchor, List<Item> items, boolean keepFocus, Runnable closed) {
        this.items = List.copyOf(items);
        this.keepFocus = keepFocus;
        shell = new Shell(anchor.getShell(), keepFocus ? SWT.NO_TRIM | SWT.ON_TOP | SWT.TOOL | SWT.NO_FOCUS : SWT.NO_TRIM | SWT.ON_TOP);
        FillLayout layout = new FillLayout();
        layout.marginWidth = 1;
        layout.marginHeight = 1;
        shell.setLayout(layout);
        shell.setData(ThemeKeys.ZOOM_IMAGES, Boolean.FALSE);
        table = new Table(shell, SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL);
        // One column as wide as the list, so a row's background (the theme's stripes) spans it.
        TableColumn column = new TableColumn(table, SWT.LEFT);
        table.addListener(SWT.Resize, e -> column.setWidth(table.getClientArea().width));
        fill();
        table.addListener(SWT.MouseUp, e -> {
            org.eclipse.swt.widgets.TableItem hit = table.getItem(new Point(e.x, e.y));
            if (hit != null && e.button == 1) {
                pick(table.indexOf(hit));
            }
        });
        table.addListener(SWT.DefaultSelection, e -> pick(table.getSelectionIndex()));
        table.addListener(SWT.MouseMove, e -> {
            org.eclipse.swt.widgets.TableItem hit = table.getItem(new Point(e.x, e.y));
            if (hit != null && table.getSelectionIndex() != table.indexOf(hit)) {
                table.setSelection(hit);
            }
        });
        table.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
                e.doit = false;
                close();
            }
        });
        shell.addListener(SWT.Deactivate, e -> shell.getDisplay().asyncExec(() -> {
            if (closeOnDeactivate && !keepFocus && !shell.isDisposed() && shell.getDisplay().getActiveShell() != shell) {
                close();
            }
        }));
        shell.addListener(SWT.Dispose, e -> closed.run());
        // The outline: the border color of the bar it hangs from.
        shell.setBackground(BarColors.of(anchor).border());
    }

    /** Opens a popup of {@code items} under {@code anchor} (in the anchor's coordinates); {@code closed} runs when it goes. */
    static FolderPopup open(Control anchor, Rectangle area, List<Item> items, Runnable closed) {
        FolderPopup popup = new FolderPopup(anchor, items, false, closed);
        popup.show(anchor, area);
        return popup;
    }

    /**
     * Opens a popup of suggestions under {@code area} of {@code anchor}, as wide as it, leaving the
     * focus where it is; the owner moves its selection ({@link #select}) and closes it.
     */
    static FolderPopup suggest(Control anchor, Rectangle area, List<Item> items, Runnable closed) {
        FolderPopup popup = new FolderPopup(anchor, items, true, closed);
        popup.show(anchor, area);
        return popup;
    }

    private void fill() {
        table.removeAll();
        for (Item item : items) {
            TableItem row = new TableItem(table, SWT.NONE);
            row.setText(item.label());
            row.setImage(item.image());
        }
    }

    /** Shows {@code items} instead, at the size they need, under the same {@code area} of {@code anchor}. */
    void setItems(Control anchor, Rectangle area, List<Item> items) {
        if (shell.isDisposed() || labels().equals(items.stream().map(Item::label).toList())) {
            return;
        }
        this.items = List.copyOf(items);
        table.setRedraw(false);
        fill();
        table.setRedraw(true);
        show(anchor, area);
    }

    private void show(Control anchor, Rectangle area) {
        int rows = Math.max(1, Math.min(items.size(), MAX_ROWS));
        int textWidth = 0;
        GC gc = new GC(table);
        for (Item item : items) {
            textWidth = Math.max(textWidth, gc.textExtent(item.label()).x);
        }
        gc.dispose();
        int px = ThemeManager.get(anchor.getDisplay()).resources().zoomedPixels(1);
        int image = items.isEmpty() || items.getFirst().image() == null ? 0 : items.getFirst().image().getBounds().width + 8 * px;
        Rectangle trim = table.computeTrim(0, 0, 0, rows * table.getItemHeight());
        int scroll = items.size() > MAX_ROWS ? table.getVerticalBar().getSize().x : 0;
        int width = Math.clamp(textWidth + image + 40 * px + scroll, 160 * px, 420 * px);
        if (keepFocus) {
            width = Math.max(area.width, Math.min(width, 640 * px));
        }
        int height = trim.height + 2;
        Point origin = anchor.toDisplay(area.x, area.y + area.height);
        Display display = anchor.getDisplay();
        Rectangle screen = display.getPrimaryMonitor().getClientArea();
        for (Monitor monitor : display.getMonitors()) {
            if (monitor.getBounds().contains(origin)) {
                screen = monitor.getClientArea();
            }
        }
        int x = Math.max(screen.x, Math.min(origin.x, screen.x + screen.width - width));
        int y = origin.y;
        if (y + height > screen.y + screen.height) {
            y = Math.max(screen.y, anchor.toDisplay(area.x, area.y).y - height);
        }
        shell.setBounds(x, y, width, height);
        if (keepFocus) {
            shell.setVisible(true);
        } else {
            shell.open();
            table.setFocus();
        }
    }

    /** The selected row, or -1. */
    int selectedIndex() {
        return shell.isDisposed() ? -1 : table.getSelectionIndex();
    }

    /** Selects row {@code index} and shows it, or nothing for -1. */
    void select(int index) {
        if (shell.isDisposed()) {
            return;
        }
        if (index < 0 || index >= items.size()) {
            table.deselectAll();
        } else {
            table.setSelection(index);
            table.showSelection();
        }
    }

    /** The label of row {@code index}. */
    String label(int index) {
        return items.get(index).label();
    }

    int size() {
        return items.size();
    }

    /** Runs row {@code index}'s action and closes. */
    void pick(int index) {
        if (index < 0 || index >= items.size()) {
            return;
        }
        Runnable action = items.get(index).action();
        close();
        action.run();
    }

    boolean isOpen() {
        return !shell.isDisposed();
    }

    List<String> labels() {
        return items.stream().map(Item::label).toList();
    }

    void close() {
        if (!shell.isDisposed()) {
            shell.dispose();
        }
    }
}
