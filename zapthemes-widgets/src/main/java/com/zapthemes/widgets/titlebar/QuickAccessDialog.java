package com.zapthemes.widgets.titlebar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;

import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;

/**
 * "More Commands...": the commands the app offers on the left, the quick access toolbar on the
 * right, with Add, Remove, Move Up, Move Down and Reset, and a choice to show the toolbar below the
 * bar. OK applies it to the bar (which saves its preferences file).
 */
final class QuickAccessDialog {

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

    QuickAccessDialog(TitleBar bar) {
        this.bar = bar;
        this.model = new Model(bar, bar.getCustomization());
    }

    /** Builds the dialog; {@link #open()} also shows it. */
    Shell create() {
        shell = new Shell(bar.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText("Customize Quick Access Toolbar");
        shell.setLayout(new GridLayout(3, false));

        Label left = new Label(shell, SWT.NONE);
        left.setText("&Commands:");
        new Label(shell, SWT.NONE);
        Label right = new Label(shell, SWT.NONE);
        right.setText("&Quick Access Toolbar:");

        availableList = new com.zapthemes.swt.widgets.List(shell, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData listData = new GridData(SWT.FILL, SWT.FILL, true, true);
        listData.widthHint = 200;
        listData.heightHint = 220;
        availableList.setLayoutData(listData);

        Composite buttons = new Composite(shell, SWT.NONE);
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

        shownList = new com.zapthemes.swt.widgets.List(shell, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        GridData shownData = new GridData(SWT.FILL, SWT.FILL, true, true);
        shownData.widthHint = 200;
        shownData.heightHint = 220;
        shownList.setLayoutData(shownData);

        belowCheck = new Button(shell, SWT.CHECK);
        belowCheck.setText("Show the Quick Access Toolbar &below the title bar");
        belowCheck.setSelection(model.below);
        belowCheck.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        belowCheck.addListener(SWT.Selection, e -> model.below = belowCheck.getSelection());

        Composite actions = new Composite(shell, SWT.NONE);
        actions.setLayout(new GridLayout(2, true));
        actions.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 3, 1));
        ok = button(actions, "OK", () -> {
            bar.setCustomization(model.result());
            shell.close();
        });
        button(actions, "Cancel", shell::close);
        shell.setDefaultButton(ok);

        availableList.addListener(SWT.DefaultSelection, e -> add());
        shownList.addListener(SWT.DefaultSelection, e -> remove());
        Runnable enable = () -> {
            add.setEnabled(availableList.getSelectionIndex() >= 0);
            boolean selected = shownList.getSelectionIndex() >= 0;
            remove.setEnabled(selected);
            up.setEnabled(selected && shownList.getSelectionIndex() > 0);
            down.setEnabled(selected && shownList.getSelectionIndex() < shownList.getItemCount() - 1);
        };
        availableList.addListener(SWT.Selection, e -> enable.run());
        shownList.addListener(SWT.Selection, e -> enable.run());
        shell.setData("zapthemes.titleBar.enable", enable);
        refresh(null);
        shell.pack();
        Rectangle parent = bar.getShell().getBounds();
        Point size = shell.getSize();
        shell.setLocation(parent.x + (parent.width - size.x) / 2, parent.y + Math.max(0, (parent.height - size.y) / 3));
        return shell;
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
        if (shell.getData("zapthemes.titleBar.enable") instanceof Runnable enable) {
            enable.run();
        }
    }
}
