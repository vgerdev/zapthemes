package zapnotes;

import org.eclipse.core.databinding.Binding;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.databinding.fieldassist.ControlDecorationSupport;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.SimpleContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.widgets.WidgetFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

/**
 * The editor of one note, in a tab of the editor area: a form (title, notebook, tags with content
 * assist) over the body. Changes are kept until {@link #save()}; the tab shows {@code *} meanwhile.
 * The title is bound with JFace data binding: a validator rejects a blank one, and
 * {@link ControlDecorationSupport} shows its message on the field.
 */
final class NoteEditor extends com.zapthemes.swt.widgets.Composite {

    private static final String[] TAGS = { "1.0", "books", "ci", "design", "home", "ideas", "outdoors", "release", "swt", "themes" };

    final Model.Note note;
    private final Model model;
    private final Runnable dirtyChanged;
    private final Text title;
    private final ComboViewer notebook;
    private final Text tags;
    private final StyledText body;
    private final WritableValue<String> titleValue = new WritableValue<>("", String.class);
    private final Binding titleBinding;
    private final ContentProposalAdapter tagAssist;
    private boolean dirty;
    private boolean loading;

    NoteEditor(Composite parent, Model model, Model.Note note, boolean wrap, Runnable dirtyChanged) {
        super(parent, SWT.NONE);
        this.model = model;
        this.note = note;
        this.dirtyChanged = dirtyChanged;
        GridLayoutFactory.swtDefaults().numColumns(2).margins(12, 10).spacing(10, 8).applyTo(this);

        WidgetFactory.label(SWT.NONE).text("&Title:").create(this);
        title = WidgetFactory.text(SWT.BORDER | SWT.SINGLE).onModify(e -> edited()).create(this);
        GridDataFactory.fillDefaults().grab(true, false).indent(8, 0).applyTo(title);
        DataBindingContext bindings = new DataBindingContext();
        titleBinding = bindings.bindValue(WidgetProperties.text(SWT.Modify).observe(title), titleValue,
                new UpdateValueStrategy<String, String>()
                        .setAfterGetValidator(v -> v.isBlank() ? ValidationStatus.error("A note needs a title.") : ValidationStatus.ok()),
                null);
        ControlDecorationSupport.create(titleBinding, SWT.TOP | SWT.LEFT);
        addDisposeListener(e -> bindings.dispose());

        WidgetFactory.label(SWT.NONE).text("&Notebook:").create(this);
        notebook = new ComboViewer(this, SWT.READ_ONLY);
        notebook.setContentProvider(ArrayContentProvider.getInstance());
        notebook.setInput(model.notebooks);
        notebook.addSelectionChangedListener(e -> edited());
        GridDataFactory.fillDefaults().indent(8, 0).applyTo(notebook.getControl());

        WidgetFactory.label(SWT.NONE).text("T&ags:").create(this);
        tags = WidgetFactory.text(SWT.BORDER | SWT.SINGLE).message("comma-separated; Ctrl+Space for suggestions").onModify(e -> edited()).create(this);
        GridDataFactory.fillDefaults().grab(true, false).indent(8, 0).applyTo(tags);
        SimpleContentProposalProvider proposals = new SimpleContentProposalProvider(TAGS);
        proposals.setFiltering(true);
        tagAssist = new ContentProposalAdapter(tags, new TextContentAdapter(), proposals, ctrlSpace(), null);
        tagAssist.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_INSERT);

        body = new com.zapthemes.swt.custom.StyledText(this, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | (wrap ? SWT.WRAP : SWT.H_SCROLL));
        body.setMargins(8, 6, 8, 6);
        body.addModifyListener(e -> edited());
        GridDataFactory.fillDefaults().grab(true, true).span(2, 1).applyTo(body);
        load();
    }

    private static org.eclipse.jface.bindings.keys.KeyStroke ctrlSpace() {
        try {
            return org.eclipse.jface.bindings.keys.KeyStroke.getInstance("Ctrl+Space");
        } catch (org.eclipse.jface.bindings.keys.ParseException e) {
            throw new IllegalStateException(e);
        }
    }

    private void load() {
        loading = true;
        titleValue.setValue(note.title);
        notebook.setSelection(new StructuredSelection(note.notebook));
        tags.setText(note.tags);
        body.setText(note.body);
        loading = false;
        setDirty(false);
    }

    private void edited() {
        if (!loading) {
            setDirty(true);
        }
    }

    private void setDirty(boolean dirty) {
        this.dirty = dirty;
        dirtyChanged.run();
    }

    boolean isDirty() {
        return dirty;
    }

    /** Whether the form can be saved: the title must not be blank. */
    boolean isValid() {
        return ((IStatus) titleBinding.getValidationStatus().getValue()).isOK();
    }

    String title() {
        return title.getText().isBlank() ? "(untitled)" : title.getText();
    }

    void save() {
        if (!isValid()) {
            return;
        }
        note.title = titleValue.getValue().strip();
        note.notebook = (String) notebook.getStructuredSelection().getFirstElement();
        note.tags = tags.getText().strip();
        note.body = body.getText();
        setDirty(false);
        model.changed();
    }

    /** Finds {@code text} in the body from the caret on, selecting it; false when it is not there. */
    boolean find(String text, boolean caseSensitive) {
        String haystack = caseSensitive ? body.getText() : body.getText().toLowerCase();
        String needle = caseSensitive ? text : text.toLowerCase();
        int from = body.getSelection().y;
        int at = haystack.indexOf(needle, from);
        if (at < 0) {
            at = haystack.indexOf(needle);
        }
        if (at < 0) {
            return false;
        }
        body.setSelection(at, at + needle.length());
        body.setFocus();
        return true;
    }

    // ---------------------------------------------------------------- for the smoke test

    /** Clears the title so the required-field decoration shows, and opens the tag suggestions. */
    void showAssists() {
        title.setText("");
        tags.setFocus();
        tags.setText("re");
        tags.setSelection(2);
        tagAssist.openProposalPopup();
    }

    void reload() {
        load();
    }
}
