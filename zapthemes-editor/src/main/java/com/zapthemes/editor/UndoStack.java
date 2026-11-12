package com.zapthemes.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * The undo and redo history of one wizard page. Each edit knows how to undo and redo itself, so
 * undoing on one page leaves the edits of the other pages alone.
 */
public final class UndoStack {

    /** One edit: {@code key} lets a run of small steps (a slider drag) undo as one. */
    record Edit(String label, String key, Runnable undo, Runnable redo) {
    }

    private final Deque<Edit> done = new ArrayDeque<>();
    private final Deque<Edit> undone = new ArrayDeque<>();

    /** Records an edit already made; with {@code coalesce}, it joins the last one, undoing to before it. */
    void push(Edit edit, boolean coalesce) {
        undone.clear();
        if (coalesce && !done.isEmpty()) {
            Edit last = done.pop();
            done.push(new Edit(edit.label(), edit.key(), last.undo(), edit.redo()));
        } else {
            done.push(edit);
        }
    }

    Edit top() {
        return done.peek();
    }

    public boolean canUndo() {
        return !done.isEmpty();
    }

    public boolean canRedo() {
        return !undone.isEmpty();
    }

    /** What {@link #undo} would undo, e.g. "Regenerate". */
    public Optional<String> undoLabel() {
        return Optional.ofNullable(done.peek()).map(Edit::label);
    }

    public Optional<String> redoLabel() {
        return Optional.ofNullable(undone.peek()).map(Edit::label);
    }

    boolean undo() {
        if (done.isEmpty()) {
            return false;
        }
        Edit edit = done.pop();
        edit.undo().run();
        undone.push(edit);
        return true;
    }

    boolean redo() {
        if (undone.isEmpty()) {
            return false;
        }
        Edit edit = undone.pop();
        edit.redo().run();
        done.push(edit);
        return true;
    }
}
