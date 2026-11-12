package com.zapthemes.editor;

import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.zapthemes.editor.EditorModel.Page;
import com.zapthemes.swt.custom.SashForm;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;

/**
 * A page of the theme wizard: its own controls on the left, the {@link PreviewPane} on the right,
 * and Undo and Redo for this page's edits (also Ctrl+Z, and Ctrl+Y or Ctrl+Shift+Z, outside text
 * fields). The page refreshes from the model on every change while it is showing.
 */
abstract class EditorPage extends WizardPage {

    protected final EditorModel model;
    private final Page page;
    private PreviewPane preview;
    private Button undo;
    private Button redo;
    private final Runnable listener = this::modelChanged;
    private final Listener keys = this::key;
    private boolean refreshQueued;

    EditorPage(String name, String title, String description, EditorModel model, Page page) {
        super(name, title, null);
        setDescription(description);
        this.model = model;
        this.page = page;
    }

    @Override
    public final void createControl(org.eclipse.swt.widgets.Composite parent) {
        SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
        Composite left = new Composite(sash, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        left.setLayout(layout);
        Composite content = new Composite(left, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        createContent(content);

        Composite bar = new Composite(left, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout barLayout = new GridLayout(3, false);
        barLayout.marginWidth = 0;
        barLayout.marginHeight = 0;
        bar.setLayout(barLayout);
        new Label(bar, SWT.NONE).setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        undo = new Button(bar, SWT.PUSH);
        undo.addListener(SWT.Selection, e -> model.undo(page));
        redo = new Button(bar, SWT.PUSH);
        redo.addListener(SWT.Selection, e -> model.redo(page));

        Composite right = new Composite(sash, SWT.NONE);
        GridLayout rightLayout = new GridLayout(1, false);
        rightLayout.marginWidth = 0;
        right.setLayout(rightLayout);
        Label heading = new Label(right, SWT.NONE);
        heading.setText("Preview");
        preview = new PreviewPane(right);
        preview.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        sash.setWeights(58, 42);

        model.addListener(listener);
        sash.addDisposeListener(e -> {
            model.removeListener(listener);
            sash.getDisplay().removeFilter(SWT.KeyDown, keys);
        });
        setControl(sash);
        refreshAll();
    }

    /** The page's own controls, in {@code parent}. */
    protected abstract void createContent(Composite parent);

    /** Shows the model's current state in the page's controls. */
    protected abstract void refresh();

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        Control control = getControl();
        if (control == null) {
            return;
        }
        if (visible) {
            control.getDisplay().addFilter(SWT.KeyDown, keys);
            refreshAll();
        } else {
            control.getDisplay().removeFilter(SWT.KeyDown, keys);
        }
    }

    public PreviewPane preview() {
        return preview;
    }

    /** Coalesces a burst of model changes (a slider drag) into one refresh. */
    private void modelChanged() {
        Control control = getControl();
        if (control == null || control.isDisposed() || !control.isVisible() || refreshQueued) {
            return;
        }
        refreshQueued = true;
        control.getDisplay().asyncExec(() -> {
            refreshQueued = false;
            if (!control.isDisposed()) {
                refreshAll();
            }
        });
    }

    /** Refreshes now: the page, its undo buttons and the preview. */
    void refreshAll() {
        refresh();
        UndoStack history = model.history(page);
        undo.setText(history.undoLabel().map(l -> "Undo " + l).orElse("Undo"));
        undo.setEnabled(history.canUndo());
        redo.setText(history.redoLabel().map(l -> "Redo " + l).orElse("Redo"));
        redo.setEnabled(history.canRedo());
        undo.getParent().layout(true);
        preview.show(model.theme());
    }

    private void key(Event e) {
        Control control = getControl();
        if (control == null || control.isDisposed() || !control.isVisible() || (e.stateMask & SWT.MOD1) == 0) {
            return;
        }
        if (!(e.widget instanceof Control focus) || focus.getShell() != control.getShell() || takesText(focus)) {
            return;
        }
        int key = Character.toLowerCase(e.keyCode);
        if (key == 'z' && (e.stateMask & SWT.SHIFT) == 0) {
            model.undo(page);
            e.doit = false;
        } else if (key == 'y' || key == 'z') {
            model.redo(page);
            e.doit = false;
        }
    }

    private static boolean takesText(Control control) {
        return control instanceof org.eclipse.swt.widgets.Text || control instanceof org.eclipse.swt.custom.StyledText
                || control instanceof org.eclipse.swt.widgets.Spinner
                || control instanceof org.eclipse.swt.widgets.Combo combo && (combo.getStyle() & SWT.READ_ONLY) == 0;
    }

    /** Shows {@code e}'s message as the page's error, for edits the model refuses. */
    protected boolean tryEdit(Runnable edit) {
        try {
            edit.run();
            setErrorMessage(null);
            return true;
        } catch (IllegalArgumentException e) {
            setErrorMessage(e.getMessage());
            return false;
        }
    }
}
