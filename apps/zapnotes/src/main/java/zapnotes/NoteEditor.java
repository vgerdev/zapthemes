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
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.widgets.WidgetFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.zapthemes.jface.text.TextTheming;

/**
 * The editor of one note, in a tab of the editor area: a form (title, notebook, tags with content
 * assist) over the body, a JFace Text {@link SourceViewer} with line numbers and light Markdown
 * highlighting (headings, {@code `code`}, quotes) in the theme's syntax colors ({@link TextTheming}). Changes are kept until {@link #save()}; the tab shows {@code *} meanwhile.
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
    private final SourceViewer viewer;
    private final StyledText body;
    private final Token heading = new Token(null);
    private final Token code = new Token(null);
    private final Token quote = new Token(null);
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

        CompositeRuler ruler = new CompositeRuler();
        ruler.addDecorator(0, new LineNumberRulerColumn());
        viewer = new SourceViewer(this, ruler, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | (wrap ? SWT.WRAP : SWT.H_SCROLL));
        viewer.configure(new SourceViewerConfiguration() {
            @Override
            public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
                PresentationReconciler reconciler = new PresentationReconciler();
                DefaultDamagerRepairer repairer = new DefaultDamagerRepairer(markdown());
                reconciler.setDamager(repairer, IDocument.DEFAULT_CONTENT_TYPE);
                reconciler.setRepairer(repairer, IDocument.DEFAULT_CONTENT_TYPE);
                return reconciler;
            }
        });
        viewer.setDocument(new Document());
        body = viewer.getTextWidget();
        // Side margins only: with a top margin, upstream's line number column paints no numbers.
        body.setLeftMargin(8);
        body.setRightMargin(8);
        body.addModifyListener(e -> edited());
        GridDataFactory.fillDefaults().grab(true, true).span(2, 1).applyTo(viewer.getControl());
        // The theme sets the syntax colors in JFace's ColorRegistry on every switch.
        recolor();
        IPropertyChangeListener recolor = e -> {
            recolor();
            viewer.invalidateTextPresentation();
        };
        JFaceResources.getColorRegistry().addListener(recolor);
        addDisposeListener(e -> JFaceResources.getColorRegistry().removeListener(recolor));
        load();
    }

    private RuleBasedScanner markdown() {
        EndOfLineRule headings = new EndOfLineRule("#", heading);
        headings.setColumnConstraint(0);
        EndOfLineRule quotes = new EndOfLineRule(">", quote);
        quotes.setColumnConstraint(0);
        RuleBasedScanner scanner = new RuleBasedScanner();
        scanner.setRules(new IRule[] { headings, quotes, new SingleLineRule("`", "`", code) });
        return scanner;
    }

    private void recolor() {
        var colors = JFaceResources.getColorRegistry();
        heading.setData(new TextAttribute(colors.get(TextTheming.KEYWORD), null, SWT.BOLD));
        code.setData(new TextAttribute(colors.get(TextTheming.STRING)));
        quote.setData(new TextAttribute(colors.get(TextTheming.COMMENT), null, SWT.ITALIC));
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
        viewer.getDocument().set(note.body);
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
