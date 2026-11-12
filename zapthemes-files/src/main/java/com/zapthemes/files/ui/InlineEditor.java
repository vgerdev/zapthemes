package com.zapthemes.files.ui;

import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;

import com.zapthemes.swt.widgets.Text;

/**
 * A text field over an item of a table or tree, for renaming it in place: Enter or a click
 * elsewhere commits, Escape cancels. The name is selected without its extension, as Explorer does.
 */
final class InlineEditor {

    private final Text text;
    private final Consumer<String> commit;
    private final Runnable cancel;
    private boolean done;

    private InlineEditor(Composite parent, Rectangle bounds, String name, int selectionEnd, Consumer<String> commit, Runnable cancel) {
        this.commit = commit;
        this.cancel = cancel;
        text = new Text(parent, SWT.SINGLE | SWT.BORDER);
        text.setText(name);
        Point size = text.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        int width = Math.max(bounds.width, size.x + 24);
        int height = Math.max(bounds.height, size.y);
        text.setBounds(bounds.x, bounds.y + (bounds.height - height) / 2, Math.min(width, parent.getClientArea().width - bounds.x), height);
        text.setSelection(0, selectionEnd);
        text.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
                e.doit = false;
                finish(false);
            } else if (e.detail == SWT.TRAVERSE_RETURN) {
                e.doit = false;
                finish(true);
            }
        });
        text.addListener(SWT.FocusOut, e -> finish(true));
        text.setFocus();
    }

    /** Opens an editor for {@code name} over {@code bounds} of {@code parent}. */
    static InlineEditor open(Composite parent, Rectangle bounds, String name, boolean folder, Consumer<String> commit, Runnable cancel) {
        String extension = folder ? "" : com.zapthemes.files.FileTypes.extension(name);
        int end = extension.isEmpty() ? name.length() : name.length() - extension.length() - 1;
        return new InlineEditor(parent, bounds, name, end, commit, cancel);
    }

    /** Commits ({@code true}) or cancels, once, and closes. */
    void finish(boolean accept) {
        if (done) {
            return;
        }
        done = true;
        String value = text.isDisposed() ? null : text.getText();
        if (!text.isDisposed()) {
            // Disposed later: this may run inside the field's own event.
            text.getDisplay().asyncExec(() -> {
                if (!text.isDisposed()) {
                    text.dispose();
                }
            });
            text.setVisible(false);
        }
        if (accept && value != null) {
            commit.accept(value);
        } else {
            cancel.run();
        }
    }

    boolean isOpen() {
        return !done;
    }

    Text text() {
        return text;
    }
}
