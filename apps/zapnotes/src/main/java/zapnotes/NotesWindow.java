package zapnotes;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/**
 * One ZapNotes window: a navigator (notebooks and notes), an editor area with a tab per open note,
 * and Properties and Tasks views, over a model every window shares.
 */
final class NotesWindow extends ApplicationWindow {

    private final ZapNotes app;
    private final Model model;
    private final Runnable refresh = this::refresh;
    private TreeViewer navigator;
    private CTabFolder editors;
    private TableViewer properties;
    private CheckboxTableViewer tasks;

    final IAction newNote;
    final IAction save;
    final IAction saveAll;
    final IAction export;
    final IAction rename;
    final IAction delete;
    final IAction find;
    final IAction newWindow;
    final IAction preferences;
    final IAction about;

    NotesWindow(ZapNotes app) {
        super(null);
        this.app = app;
        this.model = app.model;
        newNote = action("&New Note...@Ctrl+N", () -> app.newNoteWizard(this).open());
        save = action("&Save@Ctrl+S", () -> activeEditor().ifPresent(NoteEditor::save));
        saveAll = action("Save A&ll@Ctrl+Shift+S", () -> editorsList().forEach(NoteEditor::save));
        export = action("&Export...", () -> app.export(getShell()));
        rename = action("&Rename...@F2", this::renameSelected);
        delete = action("&Delete@Delete", this::deleteSelected);
        find = action("&Find...@Ctrl+F", () -> app.findDialog(this).open());
        newWindow = action("New &Window", app::openWindow);
        preferences = action("&Preferences...", () -> app.preferences(getShell()).open());
        about = action("&About ZapNotes", () -> app.about(getShell()).open());
        addMenuBar();
        addToolBar(SWT.FLAT | SWT.RIGHT);
        addStatusLine();
    }

    // ---------------------------------------------------------------- actions

    private static IAction action(String text, Runnable run) {
        return new Action(text) {
            @Override
            public void run() {
                run.run();
            }
        };
    }

    @Override
    protected MenuManager createMenuManager() {
        MenuManager bar = new MenuManager();
        MenuManager file = new MenuManager("&File");
        file.add(newNote);
        file.add(new Separator());
        file.add(save);
        file.add(saveAll);
        file.add(export);
        file.add(new Separator());
        file.add(action("&Close Window", this::close));
        file.add(action("E&xit", app::exit));
        MenuManager edit = new MenuManager("&Edit");
        edit.add(rename);
        edit.add(delete);
        edit.add(new Separator());
        edit.add(find);
        MenuManager window = new MenuManager("&Window");
        window.add(newWindow);
        window.add(app.themeMenu());
        window.add(new Separator());
        window.add(preferences);
        MenuManager help = new MenuManager("&Help");
        help.add(about);
        bar.add(file);
        bar.add(edit);
        bar.add(window);
        bar.add(help);
        return bar;
    }

    @Override
    protected ToolBarManager createToolBarManager(int style) {
        ToolBarManager toolBar = new ToolBarManager(style);
        toolBar.add(newNote);
        toolBar.add(save);
        toolBar.add(new Separator());
        toolBar.add(find);
        toolBar.add(new Separator());
        toolBar.add(app.nextThemeAction());
        return toolBar;
    }

    // ---------------------------------------------------------------- contents

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("ZapNotes");
    }

    @Override
    protected Point getInitialSize() {
        return new Point(1100, 720);
    }

    @Override
    protected void initializeBounds() {
        // JFace on Windows: the menu bar resizes the shell before it opens, and Window then skips
        // getInitialSize(). Forget that resize.
        resizeHasOccurred = false;
        super.initializeBounds();
    }

    @Override
    protected Control createContents(Composite parent) {
        SashForm main = new com.zapthemes.swt.custom.SashForm(parent, SWT.HORIZONTAL);
        createNavigator(main);
        SashForm right = new com.zapthemes.swt.custom.SashForm(main, SWT.VERTICAL);
        editors = new com.zapthemes.swt.custom.CTabFolder(right, SWT.BORDER | SWT.CLOSE);
        editors.addListener(SWT.Selection, e -> updateStatus());
        editors.addCTabFolder2Listener(org.eclipse.swt.custom.CTabFolder2Listener.closeAdapter(e -> {
            NoteEditor editor = (NoteEditor) ((CTabItem) e.item).getControl();
            if (editor.isDirty() && !MessageDialog.openQuestion(getShell(), "Unsaved changes",
                    "\"" + editor.title() + "\" has unsaved changes. Close it anyway?")) {
                e.doit = false;
            }
        }));
        CTabFolder bottom = new com.zapthemes.swt.custom.CTabFolder(right, SWT.BORDER);
        createProperties(bottom);
        createTasks(bottom);
        bottom.setSelection(0);
        right.setWeights(68, 32);
        main.setWeights(24, 76);

        model.addListener(refresh);
        parent.addDisposeListener(e -> model.removeListener(refresh));
        refresh();
        updateStatus();
        return main;
    }

    private void createNavigator(Composite parent) {
        View view = new View(parent, "Notebooks", newNote, delete);
        navigator = new TreeViewer(view, SWT.SINGLE);
        navigator.setContentProvider(new ITreeContentProvider() {
            @Override
            public Object[] getElements(Object input) {
                return model.notebooks.toArray();
            }

            @Override
            public Object[] getChildren(Object element) {
                return element instanceof String notebook ? model.notesIn(notebook).toArray() : new Object[0];
            }

            @Override
            public Object getParent(Object element) {
                return element instanceof Model.Note note ? note.notebook : null;
            }

            @Override
            public boolean hasChildren(Object element) {
                return element instanceof String notebook && !model.notesIn(notebook).isEmpty();
            }
        });
        navigator.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                return element instanceof String notebook ? notebook + " (" + model.notesIn(notebook).size() + ")" : element.toString();
            }
        });
        navigator.setInput(model);
        navigator.expandAll();
        navigator.addSelectionChangedListener(e -> {
            properties.setInput(selectedNote().map(this::propertyRows).orElse(List.of()));
            updateStatus();
        });
        navigator.addDoubleClickListener(e -> selectedNote().ifPresent(this::open));
        view.content(navigator.getControl());
    }

    private void createProperties(CTabFolder folder) {
        properties = new TableViewer(folder, SWT.FULL_SELECTION);
        properties.getTable().setHeaderVisible(true);
        properties.getTable().setLinesVisible(true);
        column(properties, "Property", 140, row -> row[0]);
        column(properties, "Value", 420, row -> row[1]);
        properties.setContentProvider(ArrayContentProvider.getInstance());
        properties.setInput(List.of());
        tab(folder, "Properties", properties.getControl());
    }

    private void createTasks(CTabFolder folder) {
        tasks = CheckboxTableViewer.newCheckList(folder, SWT.FULL_SELECTION);
        tasks.getTable().setHeaderVisible(true);
        tasks.getTable().setLinesVisible(true);
        TableViewerColumn text = new TableViewerColumn(tasks, SWT.NONE);
        text.getColumn().setText("Task");
        text.getColumn().setWidth(320);
        text.setLabelProvider(ColumnLabelProvider.createTextProvider(t -> ((Model.Task) t).text));
        TableViewerColumn note = new TableViewerColumn(tasks, SWT.NONE);
        note.getColumn().setText("Note");
        note.getColumn().setWidth(200);
        note.setLabelProvider(ColumnLabelProvider.createTextProvider(t -> ((Model.Task) t).note.title));
        tasks.setContentProvider(ArrayContentProvider.getInstance());
        tasks.setCheckStateProvider(new org.eclipse.jface.viewers.ICheckStateProvider() {
            @Override
            public boolean isChecked(Object element) {
                return ((Model.Task) element).done;
            }

            @Override
            public boolean isGrayed(Object element) {
                return false;
            }
        });
        tasks.addCheckStateListener(e -> {
            ((Model.Task) e.getElement()).done = e.getChecked();
            model.changed();
        });
        tab(folder, "Tasks", tasks.getControl());
    }

    private static void column(TableViewer viewer, String title, int width, java.util.function.Function<String[], String> text) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(ColumnLabelProvider.createTextProvider(row -> text.apply((String[]) row)));
    }

    private static void tab(CTabFolder folder, String text, Control control) {
        CTabItem item = new com.zapthemes.swt.custom.CTabItem(folder, SWT.NONE);
        item.setText(text);
        item.setControl(control);
    }

    private List<String[]> propertyRows(Model.Note note) {
        long done = note.tasks.stream().filter(t -> t.done).count();
        return List.of(
                new String[] { "Title", note.title },
                new String[] { "Notebook", note.notebook },
                new String[] { "Tags", note.tags.isEmpty() ? "(none)" : note.tags },
                new String[] { "Words", Integer.toString(note.words()) },
                new String[] { "Tasks", done + " of " + note.tasks.size() + " done" });
    }

    // ---------------------------------------------------------------- behavior

    private void refresh() {
        if (navigator == null || navigator.getControl().isDisposed()) {
            return;
        }
        Object selected = navigator.getStructuredSelection().getFirstElement();
        navigator.refresh();
        navigator.expandAll();
        if (selected != null && (model.notes.contains(selected) || model.notebooks.contains(selected))) {
            navigator.setSelection(new StructuredSelection(selected));
        }
        properties.setInput(selectedNote().map(this::propertyRows).orElse(List.of()));
        tasks.setInput(model.tasks());
        for (CTabItem item : editors.getItems()) {
            NoteEditor editor = (NoteEditor) item.getControl();
            if (!model.notes.contains(editor.note)) {
                item.dispose();
            } else {
                updateTab(item);
            }
        }
        updateStatus();
    }

    java.util.Optional<Model.Note> selectedNote() {
        return navigator.getStructuredSelection().getFirstElement() instanceof Model.Note note
                ? java.util.Optional.of(note) : java.util.Optional.empty();
    }

    void select(Model.Note note) {
        navigator.setSelection(new StructuredSelection(note), true);
    }

    /** Opens {@code note} in the editor area, or brings its tab to front. */
    NoteEditor open(Model.Note note) {
        for (CTabItem item : editors.getItems()) {
            if (((NoteEditor) item.getControl()).note == note) {
                editors.setSelection(item);
                updateStatus();
                return (NoteEditor) item.getControl();
            }
        }
        CTabItem item = new com.zapthemes.swt.custom.CTabItem(editors, SWT.CLOSE);
        NoteEditor editor = new NoteEditor(editors, model, note, app.wrapLines(), () -> updateTab(item));
        item.setControl(editor);
        updateTab(item);
        editors.setSelection(item);
        updateStatus();
        return editor;
    }

    private void updateTab(CTabItem item) {
        // Also called while the editor is being built, before the tab holds it.
        if (item.isDisposed() || !(item.getControl() instanceof NoteEditor editor)) {
            return;
        }
        item.setText((editor.isDirty() ? "*" : "") + editor.title());
        save.setEnabled(activeEditor().map(NoteEditor::isDirty).orElse(false));
    }

    java.util.Optional<NoteEditor> activeEditor() {
        CTabItem item = editors == null ? null : editors.getSelection();
        return item == null ? java.util.Optional.empty() : java.util.Optional.of((NoteEditor) item.getControl());
    }

    List<NoteEditor> editorsList() {
        List<NoteEditor> list = new ArrayList<>();
        for (CTabItem item : editors.getItems()) {
            list.add((NoteEditor) item.getControl());
        }
        return list;
    }

    private void updateStatus() {
        if (getStatusLineManager() == null) {
            return;
        }
        long open = model.tasks().stream().filter(t -> !t.done).count();
        String editing = activeEditor().map(e -> "Editing \"" + e.title() + "\". ").orElse("");
        setStatus(editing + model.notes.size() + " notes, " + open + " open tasks.");
    }

    private void renameSelected() {
        selectedNote().ifPresent(note -> {
            InputDialog dialog = app.renameDialog(getShell(), note);
            if (dialog.open() == InputDialog.OK) {
                note.title = dialog.getValue().strip();
                model.changed();
                editorsList().stream().filter(e -> e.note == note).forEach(NoteEditor::reload);
            }
        });
    }

    private void deleteSelected() {
        selectedNote().ifPresent(note -> {
            if (app.deleteDialog(getShell(), note).open() == 0) {
                model.remove(note);
            }
        });
    }

    @Override
    public boolean close() {
        boolean closed = super.close();
        if (closed) {
            app.closed(this);
        }
        return closed;
    }

    /** Shows {@code message} as an error in this window's status line; null clears it. */
    void statusError(String message) {
        getStatusLineManager().setErrorMessage(message);
    }

    // ---------------------------------------------------------------- for the smoke test

    CTabFolder editorArea() {
        return editors;
    }
}
