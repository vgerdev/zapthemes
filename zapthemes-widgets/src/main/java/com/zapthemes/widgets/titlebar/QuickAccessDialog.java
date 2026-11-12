package com.zapthemes.widgets.titlebar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;

import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.widgets.options.OptionsPage;

/**
 * "More Commands...": the commands the app offers on the left, the quick access toolbar on the
 * right, with Add, Remove, Move Up, Move Down and Reset, and a choice to show the toolbar below the
 * bar. Commands can also be dragged: within the toolbar list to reorder them, from the commands
 * list into it to add one at that place, and back out to remove one. OK applies it to the bar
 * (which saves its preferences file).
 */
final class QuickAccessDialog implements OptionsPage {

    /** The dialog's state, apart from its widgets. */
    static final class Model {
        final Map<String, String> names = new LinkedHashMap<>();
        final List<String> shown = new ArrayList<>();
        boolean below;

        Model(TitleBar bar, TitleBarCustomization state) {
            bar.getQuickAccessItems().forEach(item -> names.put(item.getId(), item.plainText()));
            // The shell's other commands (the ribbon's, say): adding one creates its item.
            bar.otherCommands().forEach(command -> names.putIfAbsent(command.id(), command.plainLabel()));
            reset(state);
        }

        void reset(TitleBarCustomization state) {
            shown.clear();
            state.shown().stream().filter(names::containsKey).forEach(shown::add);
            below = state.below();
        }

        List<String> available() {
            return names.keySet().stream().filter(id -> !shown.contains(id)).toList();
        }

        /** Adds {@code id} after the {@code index}th shown command (at the end for -1). */
        void add(String id, int index) {
            if (names.containsKey(id) && !shown.contains(id)) {
                shown.add(index < 0 || index >= shown.size() ? shown.size() : index + 1, id);
            }
        }

        void remove(String id) {
            shown.remove(id);
        }

        /** Moves {@code id} by {@code delta} places; returns its new index. */
        int move(String id, int delta) {
            int index = shown.indexOf(id);
            if (index < 0) {
                return -1;
            }
            int target = Math.max(0, Math.min(shown.size() - 1, index + delta));
            shown.remove(index);
            shown.add(target, id);
            return target;
        }

        /**
         * Puts {@code id} at gap {@code gap} of the toolbar list (0 before the first command,
         * {@code shown.size()} after the last): moves a shown command, adds another one. Returns
         * its new index, or -1 for an id the dialog doesn't offer.
         */
        int insert(String id, int gap) {
            if (!names.containsKey(id)) {
                return -1;
            }
            int target = Math.max(0, Math.min(shown.size(), gap));
            int index = shown.indexOf(id);
            if (index >= 0) {
                shown.remove(index);
                if (index < target) {
                    target--;
                }
            }
            shown.add(target, id);
            return target;
        }

        TitleBarCustomization result() {
            return new TitleBarCustomization(shown, new LinkedHashSet<>(names.keySet()), below);
        }
    }

    private final TitleBar bar;
    final Model model;
    Shell shell;
    com.zapthemes.swt.widgets.List availableList;
    com.zapthemes.swt.widgets.List shownList;
    Button belowCheck;
    Button ok;
    private Runnable enable;
    /** The command being dragged, and whether it comes from the toolbar list. */
    private String dragged;
    private boolean draggedShown;
    /** Where a drop in the toolbar list would land: a line between its rows. */
    private Composite marker;

    QuickAccessDialog(TitleBar bar) {
        this.bar = bar;
        this.model = new Model(bar, bar.getCustomization());
    }

    /** Builds the dialog; {@link #open()} also shows it. */
    Shell create() {
        shell = new Shell(bar.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText("Customize Quick Access Toolbar");
        shell.setLayout(new GridLayout(1, false));
        Composite body = new Composite(shell, SWT.NONE);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        create(body);

        Composite actions = new Composite(shell, SWT.NONE);
        actions.setLayout(new GridLayout(2, true));
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));
        ok = button(actions, "OK", () -> {
            apply();
            shell.close();
        });
        button(actions, "Cancel", shell::close);
        shell.setDefaultButton(ok);
        shell.pack();
        Rectangle parent = bar.getShell().getBounds();
        Point size = shell.getSize();
        shell.setLocation(parent.x + (parent.width - size.x) / 2, parent.y + Math.max(0, (parent.height - size.y) / 3));
        return shell;
    }

    /**
     * Builds the dialog's contents into {@code parent}, without OK and Cancel: the Quick Access
     * Toolbar page of an {@link com.zapthemes.widgets.options.OptionsDialog}.
     */
    @Override
    public void create(org.eclipse.swt.widgets.Composite parent) {
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        parent.setLayout(layout);
        Label left = new Label(parent, SWT.NONE);
        left.setText("&Commands:");
        new Label(parent, SWT.NONE);
        Label right = new Label(parent, SWT.NONE);
        right.setText("&Quick Access Toolbar:");

        availableList = new com.zapthemes.swt.widgets.List(parent, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData listData = new GridData(SWT.FILL, SWT.FILL, true, true);
        listData.widthHint = 200;
        listData.heightHint = 220;
        availableList.setLayoutData(listData);

        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayout(new GridLayout(1, true));
        buttons.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
        Button add = button(buttons, "&Add >>", this::add);
        Button remove = button(buttons, "<< &Remove", this::remove);
        Button up = button(buttons, "Move &Up", () -> move(-1));
        Button down = button(buttons, "Move &Down", () -> move(1));
        button(buttons, "Re&set", () -> {
            model.reset(bar.defaults());
            belowCheck.setSelection(model.below);
            refresh(null);
        });

        shownList = new com.zapthemes.swt.widgets.List(parent, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData shownData = new GridData(SWT.FILL, SWT.FILL, true, true);
        shownData.widthHint = 200;
        shownData.heightHint = 220;
        shownList.setLayoutData(shownData);

        belowCheck = new Button(parent, SWT.CHECK);
        belowCheck.setText("Show the Quick Access Toolbar &below the title bar");
        belowCheck.setSelection(model.below);
        belowCheck.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        belowCheck.addListener(SWT.Selection, e -> model.below = belowCheck.getSelection());

        availableList.addListener(SWT.DefaultSelection, e -> add());
        shownList.addListener(SWT.DefaultSelection, e -> remove());
        enable = () -> {
            add.setEnabled(availableList.getSelectionIndex() >= 0);
            boolean selected = shownList.getSelectionIndex() >= 0;
            remove.setEnabled(selected);
            up.setEnabled(selected && shownList.getSelectionIndex() > 0);
            down.setEnabled(selected && shownList.getSelectionIndex() < shownList.getItemCount() - 1);
        };
        availableList.addListener(SWT.Selection, e -> enable.run());
        shownList.addListener(SWT.Selection, e -> enable.run());
        dragAndDrop(parent);
        refresh(null);
    }

    // ------------------------------------------------------------------ drag and drop

    /*
     * As in the ribbon's dialog: on Windows the listeners run inside the native drag loop, so the
     * lists are not refilled while it runs (the drop's change waits until the loop has returned),
     * and no exception escapes a listener (one is thrown again from the event loop instead).
     */
    private void dragAndDrop(org.eclipse.swt.widgets.Composite parent) {
        marker = new Composite(parent, SWT.NONE);
        GridData markerData = new GridData();
        markerData.exclude = true;
        marker.setLayoutData(markerData);
        marker.setVisible(false);
        // Disabled, the line is not a window the drag can be over: it stays on the list's.
        marker.setEnabled(false);
        Transfer[] transfers = { TextTransfer.getInstance() };
        dragSource(availableList, false, transfers);
        dragSource(shownList, true, transfers);

        DropTarget intoToolbar = new DropTarget(shownList, DND.DROP_MOVE | DND.DROP_COPY);
        intoToolbar.setTransfer(transfers);
        intoToolbar.addDropListener(new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetEvent event) {
                dragOver(event);
            }

            @Override
            public void dragOperationChanged(DropTargetEvent event) {
                dragOver(event);
            }

            @Override
            public void dragOver(DropTargetEvent event) {
                event.feedback = DND.FEEDBACK_SCROLL;
                event.detail = DND.DROP_NONE;
                guarded(() -> {
                    if (dragged != null) {
                        event.detail = draggedShown ? DND.DROP_MOVE : DND.DROP_COPY;
                        showMarker(gapAt(event.y));
                    }
                });
            }

            @Override
            public void dragLeave(DropTargetEvent event) {
                guarded(() -> marker.setVisible(false));
            }

            @Override
            public void drop(DropTargetEvent event) {
                guarded(() -> marker.setVisible(false));
                String id = dragged;
                int[] gap = { -1 };
                guarded(() -> gap[0] = gapAt(event.y));
                if (id == null || gap[0] < 0) {
                    event.detail = DND.DROP_NONE;
                    return;
                }
                later(() -> {
                    model.insert(id, gap[0]);
                    refresh(id);
                });
            }
        });

        DropTarget outOfToolbar = new DropTarget(availableList, DND.DROP_MOVE);
        outOfToolbar.setTransfer(transfers);
        outOfToolbar.addDropListener(new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetEvent event) {
                dragOver(event);
            }

            @Override
            public void dragOperationChanged(DropTargetEvent event) {
                dragOver(event);
            }

            @Override
            public void dragOver(DropTargetEvent event) {
                event.feedback = DND.FEEDBACK_NONE;
                event.detail = dragged != null && draggedShown ? DND.DROP_MOVE : DND.DROP_NONE;
            }

            @Override
            public void drop(DropTargetEvent event) {
                String id = dragged;
                if (id == null || !draggedShown) {
                    event.detail = DND.DROP_NONE;
                    return;
                }
                later(() -> {
                    model.remove(id);
                    refresh(null);
                });
            }
        });
    }

    private void dragSource(com.zapthemes.swt.widgets.List list, boolean fromToolbar, Transfer[] transfers) {
        DragSource source = new DragSource(list, DND.DROP_MOVE | DND.DROP_COPY);
        source.setTransfer(transfers);
        source.addDragListener(new DragSourceAdapter() {
            @Override
            public void dragStart(DragSourceEvent event) {
                event.doit = false;
                guarded(() -> {
                    int index = list.getSelectionIndex();
                    List<String> ids = fromToolbar ? model.shown : model.available();
                    dragged = index >= 0 && index < ids.size() ? ids.get(index) : null;
                    draggedShown = fromToolbar;
                    event.doit = dragged != null;
                });
            }

            @Override
            public void dragSetData(DragSourceEvent event) {
                // The command stays in the dialog; the id is only what other apps would get.
                // TextTransfer refuses empty text, so there is always some.
                event.data = dragged != null ? dragged : "zapthemes.quickAccess";
            }

            @Override
            public void dragFinished(DragSourceEvent event) {
                dragged = null;
                guarded(() -> marker.setVisible(false));
            }
        });
    }

    /** The gap of the toolbar list nearest display y {@code y}: 0 above the first row, the row count below the last. */
    int gapAt(int y) {
        int itemHeight = Math.max(1, shownList.getItemHeight());
        int local = shownList.toControl(0, y).y;
        int gap = shownList.getTopIndex() + Math.round((float) local / itemHeight);
        return Math.max(0, Math.min(model.shown.size(), gap));
    }

    private void showMarker(int gap) {
        Rectangle area = shownList.getClientArea();
        int itemHeight = shownList.getItemHeight();
        Rectangle list = shownList.getBounds();
        int border = shownList.getBorderWidth();
        int y = list.y + border + (gap - shownList.getTopIndex()) * itemHeight;
        y = Math.max(list.y + border, Math.min(list.y + border + area.height - 2, y - 1));
        marker.setBackground(shownList.getForeground());
        marker.setBounds(list.x + border + 1, y, Math.max(0, area.width - 2), 2);
        marker.moveAbove(shownList);
        marker.setVisible(true);
    }

    /** Runs a drop's change once the native drag loop has returned. */
    private void later(Runnable change) {
        shownList.getDisplay().asyncExec(() -> {
            if (!shownList.isDisposed()) {
                change.run();
            }
        });
    }

    /** Runs a drag listener's work; an exception is thrown again later, outside the native drag loop. */
    private void guarded(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException | org.eclipse.swt.SWTError e) {
            shownList.getDisplay().asyncExec(() -> {
                throw e;
            });
        }
    }

    /** Applies the toolbar to the bar, which saves its preferences file. */
    @Override
    public void apply() {
        bar.setCustomization(model.result());
    }

    void open() {
        create().open();
    }

    private static Button button(Composite parent, String text, Runnable run) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        button.addListener(SWT.Selection, e -> run.run());
        return button;
    }

    private void add() {
        int index = availableList.getSelectionIndex();
        if (index < 0) {
            return;
        }
        String id = model.available().get(index);
        model.add(id, shownList.getSelectionIndex());
        refresh(id);
    }

    private void remove() {
        int index = shownList.getSelectionIndex();
        if (index >= 0) {
            model.remove(model.shown.get(index));
            refresh(null);
        }
    }

    private void move(int delta) {
        int index = shownList.getSelectionIndex();
        if (index >= 0) {
            String id = model.shown.get(index);
            model.move(id, delta);
            refresh(id);
        }
    }

    /** Refills both lists, selecting {@code select} in the toolbar list. */
    private void refresh(String select) {
        availableList.removeAll();
        model.available().forEach(id -> availableList.add(model.names.get(id)));
        shownList.removeAll();
        model.shown.forEach(id -> shownList.add(model.names.get(id)));
        if (select != null && model.shown.contains(select)) {
            shownList.setSelection(model.shown.indexOf(select));
        }
        if (enable != null) {
            enable.run();
        }
    }
}
