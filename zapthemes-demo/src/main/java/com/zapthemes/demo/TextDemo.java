package com.zapthemes.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ContextInformationValidator;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.NumberRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationModel;
import org.eclipse.jface.text.source.AnnotationPainter;
import org.eclipse.jface.text.source.AnnotationRulerColumn;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationAccess;
import org.eclipse.jface.text.source.IAnnotationAccessExtension;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISharedTextColors;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.OverviewRuler;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionSupport;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.jface.text.TextTheming;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Shell;

/**
 * A source editor on the ZapThemes JFace Text fork: a {@link ProjectionViewer} with syntax colors,
 * line numbers, an annotation ruler and an overview ruler with an error and a warning, folding,
 * content assist (Ctrl+Space), context information (Ctrl+Shift+Space) and hovers. The app code is
 * plain JFace Text; the syntax colors come from the theme's {@link TextTheming} registry keys.
 *
 * <p>{@code --smoke} opens the content assist popup, the context information and a hover in every
 * bundled theme, checks that each control is tagged and has the theme's background, and exits
 * non-zero on failure. {@code --screenshots <dir>} also saves the editor and the popups as PNG.
 */
public final class TextDemo {

    private static final String ERROR = "demo.error";
    private static final String WARNING = "demo.warning";
    private static final List<String> KEYWORDS = List.of("class", "public", "private", "static", "final", "void",
            "return", "new", "int", "if", "else", "import", "package");

    private static final String SOURCE = """
            package com.example;

            import java.util.List;

            // A small class to show the editor in each theme.
            public class Greeter {

                private static final int TIMES = 3;

                public void greet(List<String> names) {
                    for (String name : names) {
                        System.out.println("Hello, " + name);
                    }
                    if (names.isEmpty()) {
                        return undefined;
                    }
                }

                private int unused = 42;
            }
            """;

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available = DemoSupport.bundledThemes();
    private final Map<String, Token> tokens = new HashMap<>();
    private final SharedColors colors = new SharedColors();
    private Shell shell;
    private ProjectionViewer viewer;
    private DemoAssistant assistant;

    private TextDemo(Display display) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        themes.setTheme(available.getFirst());
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        Display display = new Display();
        TextDemo demo = new TextDemo(display);
        demo.open();
        int exit = 0;
        int shots = options.indexOf("--screenshots");
        if (options.contains("--smoke") || shots >= 0) {
            exit = demo.smokeTest(shots >= 0 ? Path.of(options.get(shots + 1)) : null);
            demo.shell.dispose();
        }
        while (!demo.shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        demo.colors.dispose();
        display.dispose();
        System.exit(exit);
    }

    private void open() {
        shell = new Shell(display);
        shell.setText("ZapThemes — JFace Text source viewer");
        shell.setLayout(new FillLayout());
        Composite parent = new Composite(shell, SWT.NONE);
        parent.setLayout(new FillLayout());

        Access access = new Access();
        CompositeRuler ruler = new CompositeRuler();
        AnnotationRulerColumn annotations = new AnnotationRulerColumn(16, access);
        annotations.addAnnotationType(ERROR);
        annotations.addAnnotationType(WARNING);
        ruler.addDecorator(0, annotations);
        ruler.addDecorator(1, new LineNumberRulerColumn());
        OverviewRuler overview = new OverviewRuler(access, 12, colors);
        for (String type : List.of(ERROR, WARNING)) {
            overview.addAnnotationType(type);
            overview.addHeaderAnnotationType(type);
            overview.setAnnotationTypeColor(type, annotationColor(type));
        }

        viewer = new ProjectionViewer(parent, ruler, overview, true, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
        viewer.getTextWidget().setFont(JFaceResources.getTextFont());
        viewer.configure(new Configuration());
        ProjectionSupport projection = new ProjectionSupport(viewer, access, colors);
        projection.install();

        IDocument document = new Document(SOURCE);
        AnnotationModel model = new AnnotationModel();
        mark(model, document, ERROR, "undefined", "undefined cannot be resolved to a variable");
        mark(model, document, WARNING, "unused", "The value of the field Greeter.unused is not used");
        viewer.setDocument(document, model);
        viewer.doOperation(ProjectionViewer.TOGGLE);
        fold(document, "    public void greet", "        }\n    }\n");

        AnnotationPainter painter = new AnnotationPainter(viewer, access);
        painter.addTextStyleStrategy("squiggles", new AnnotationPainter.UnderlineStrategy(SWT.UNDERLINE_SQUIGGLE));
        for (String type : List.of(ERROR, WARNING)) {
            painter.addAnnotationType(type, "squiggles");
            painter.setAnnotationTypeColor(type, annotationColor(type));
        }
        viewer.addPainter(painter);

        // The theme sets the syntax colors in JFace's ColorRegistry; recolor when it changes.
        IPropertyChangeListener recolor = e -> {
            updateTokens();
            viewer.invalidateTextPresentation();
        };
        JFaceResources.getColorRegistry().addListener(recolor);
        shell.addDisposeListener(e -> JFaceResources.getColorRegistry().removeListener(recolor));

        shell.setSize(760, 520);
        shell.open();
    }

    private static void mark(AnnotationModel model, IDocument document, String type, String word, String message) {
        int offset = document.get().indexOf(word);
        model.addAnnotation(new Annotation(type, false, message), new Position(offset, word.length()));
    }

    /** Folds the region from {@code start} to the end of {@code end}; expanded, so the ruler shows the "-" handle. */
    private void fold(IDocument document, String start, String end) {
        int offset = document.get().indexOf(start);
        int last = document.get().indexOf(end, offset) + end.length();
        viewer.getProjectionAnnotationModel().addAnnotation(new ProjectionAnnotation(), new Position(offset, last - offset));
    }

    private Color annotationColor(String type) {
        return colors.getColor(type.equals(ERROR) ? new RGB(0xE5, 0x48, 0x4D) : new RGB(0xE0, 0x9A, 0x1E));
    }

    // ------------------------------------------------------------------ syntax colors

    private Token token(String key) {
        return tokens.computeIfAbsent(key, k -> new Token(attribute(k)));
    }

    private static TextAttribute attribute(String key) {
        Color color = JFaceResources.getColorRegistry().get(key);
        return new TextAttribute(color, null, key.equals(TextTheming.KEYWORD) ? SWT.BOLD : SWT.NONE);
    }

    private void updateTokens() {
        tokens.forEach((key, token) -> token.setData(attribute(key)));
    }

    private RuleBasedScanner scanner() {
        RuleBasedScanner scanner = new RuleBasedScanner();
        WordRule words = new WordRule(new IWordDetector() {
            @Override
            public boolean isWordStart(char c) {
                return Character.isJavaIdentifierStart(c);
            }

            @Override
            public boolean isWordPart(char c) {
                return Character.isJavaIdentifierPart(c);
            }
        }, Token.UNDEFINED);
        KEYWORDS.forEach(keyword -> words.addWord(keyword, token(TextTheming.KEYWORD)));
        scanner.setRules(new IRule[] {
            new EndOfLineRule("//", token(TextTheming.COMMENT)),
            new SingleLineRule("\"", "\"", token(TextTheming.STRING), '\\'),
            new NumberRule(token(TextTheming.NUMBER)),
            words,
        });
        return scanner;
    }

    private final class Configuration extends SourceViewerConfiguration {
        @Override
        public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
            PresentationReconciler reconciler = new PresentationReconciler();
            DefaultDamagerRepairer repairer = new DefaultDamagerRepairer(scanner());
            reconciler.setDamager(repairer, IDocument.DEFAULT_CONTENT_TYPE);
            reconciler.setRepairer(repairer, IDocument.DEFAULT_CONTENT_TYPE);
            return reconciler;
        }

        @Override
        public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
            assistant = new DemoAssistant();
            assistant.setContentAssistProcessor(new Processor(), IDocument.DEFAULT_CONTENT_TYPE);
            assistant.setInformationControlCreator(getInformationControlCreator(sourceViewer));
            assistant.enableAutoActivation(true);
            return assistant;
        }

        @Override
        public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
            return parent -> new DefaultInformationControl(parent, "Press 'F2' for focus");
        }

        @Override
        public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
            return new Hover();
        }

        @Override
        public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
            return (sourceViewer1, line) -> "Line " + (line + 1);
        }
    }

    /** Exposes {@link ContentAssistant#hide()} for the smoke test. */
    private static final class DemoAssistant extends ContentAssistant {
        void close() {
            hide();
        }
    }

    private static final class Processor implements IContentAssistProcessor {
        @Override
        public ICompletionProposal[] computeCompletionProposals(ITextViewer textViewer, int offset) {
            List<ICompletionProposal> proposals = new ArrayList<>();
            for (String keyword : KEYWORDS) {
                proposals.add(new CompletionProposal(keyword, offset, 0, keyword.length(), null, keyword, null,
                        "The Java keyword <b>" + keyword + "</b>."));
            }
            return proposals.toArray(ICompletionProposal[]::new);
        }

        @Override
        public IContextInformation[] computeContextInformation(ITextViewer textViewer, int offset) {
            return new IContextInformation[] { new ContextInformation("println", "void println(String x)") };
        }

        @Override
        public char[] getCompletionProposalAutoActivationCharacters() {
            return new char[] { '.' };
        }

        @Override
        public char[] getContextInformationAutoActivationCharacters() {
            return new char[] { '(' };
        }

        @Override
        public String getErrorMessage() {
            return null;
        }

        @Override
        public IContextInformationValidator getContextInformationValidator() {
            return new ContextInformationValidator(this);
        }
    }

    private static final class Hover implements ITextHover {
        @Override
        public String getHoverInfo(ITextViewer textViewer, IRegion region) {
            try {
                return "Hover for '" + textViewer.getDocument().get(region.getOffset(), region.getLength()) + "'";
            } catch (BadLocationException e) {
                return null;
            }
        }

        @Override
        public IRegion getHoverRegion(ITextViewer textViewer, int offset) {
            return new Region(offset, 1);
        }
    }

    /** Annotation types and their ruler icons: a filled circle in the annotation's color. */
    private final class Access implements IAnnotationAccess, IAnnotationAccessExtension {
        @Override
        @SuppressWarnings("deprecation")
        public Object getType(Annotation annotation) {
            return annotation.getType();
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isMultiLine(Annotation annotation) {
            return true;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isTemporary(Annotation annotation) {
            return !annotation.isPersistent();
        }

        @Override
        public String getTypeLabel(Annotation annotation) {
            return annotation.getType();
        }

        @Override
        public int getLayer(Annotation annotation) {
            return 0;
        }

        @Override
        public void paint(Annotation annotation, GC gc, Canvas canvas, Rectangle bounds) {
            if (annotation instanceof ProjectionAnnotation projection) {
                projection.paint(gc, canvas, bounds);
                return;
            }
            int size = Math.min(bounds.width, bounds.height) - 4;
            gc.setAntialias(SWT.ON);
            gc.setBackground(annotationColor(annotation.getType()));
            gc.fillOval(bounds.x + (bounds.width - size) / 2, bounds.y + (bounds.height - size) / 2, size, size);
        }

        @Override
        public boolean isPaintable(Annotation annotation) {
            return true;
        }

        @Override
        public boolean isSubtype(Object annotationType, Object potentialSupertype) {
            return annotationType.equals(potentialSupertype);
        }

        @Override
        public Object[] getSupertypes(Object annotationType) {
            return new Object[0];
        }
    }

    private static final class SharedColors implements ISharedTextColors {
        private final Map<RGB, Color> colors = new HashMap<>();

        @Override
        public Color getColor(RGB rgb) {
            return colors.computeIfAbsent(rgb, Color::new);
        }

        @Override
        public void dispose() {
            colors.clear();
        }
    }

    // ------------------------------------------------------------------ smoke test

    private int smokeTest(Path screenshots) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Theme theme : available) {
            themes.setTheme(theme);
            // Content assist opens only for the focus control; Xvfb has no window manager to activate the shell.
            shell.forceActive();
            viewer.getTextWidget().setFocus();
            viewer.setSelectedRange(SOURCE.indexOf("names.isEmpty") + 6, 0);
            DemoSupport.settle(display);
            checked += check("editor", shell, problems);
            shot(shell, screenshots, "text-editor-" + theme.id());

            checked += popups(() -> viewer.doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS), "content assist", screenshots, "text-assist-" + theme.id(), problems);
            assistant.close();
            DemoSupport.settle(display);

            viewer.setSelectedRange(SOURCE.indexOf("println(") + 8, 0);
            checked += popups(() -> viewer.doOperation(ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION), "context information", screenshots, "text-context-" + theme.id(), problems);
            assistant.close();
            DemoSupport.settle(display);

            // A text hover's control, shown directly: a real hover needs the mouse over the text.
            DefaultInformationControl hover = new DefaultInformationControl(shell, "Press 'F2' for focus");
            hover.setInformation("A hover: DefaultInformationControl, as text hovers use.");
            hover.setSize(360, 80);
            hover.setLocation(shell.toDisplay(120, 120));
            checked += popups(() -> hover.setVisible(true), "hover", screenshots, "text-hover-" + theme.id(), problems);
            hover.dispose();
        }
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: " + checked + " controls in the editor, content assist, context information and hover"
                    + " tagged and themed, in " + available.size() + " themes");
            return 0;
        }
        problems.stream().distinct().forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    /**
     * Runs {@code open}, then checks and saves every shell open besides the editor (the proposals and
     * their additional info). Content assist opens only for the focus control, which a window manager
     * may hand over late (Xvfb): retried a few times with the editor focused again.
     */
    private int popups(Runnable opener, String where, Path screenshots, String name, List<String> problems) {
        for (int attempt = 0; attempt < 5 && !popupOpen(); attempt++) {
            shell.forceActive();
            viewer.getTextWidget().setFocus();
            DemoSupport.settle(display);
            opener.run();
            DemoSupport.settle(display);
        }
        int checked = 0;
        int index = 0;
        for (org.eclipse.swt.widgets.Shell open : display.getShells()) {
            if (open != shell && open.isVisible()) {
                checked += check(where, open, problems);
                // Control.print leaves the popups' StyledText blank on Windows: copy the screen there.
                String file = index == 0 ? name : name + "-" + (index + 1);
                if (screenshots != null && SWT.getPlatform().equals("win32")) {
                    DemoSupport.screenCopy(open, screenshots, file);
                } else {
                    shot(open, screenshots, file);
                }
                index++;
            }
        }
        if (index == 0) {
            problems.add(where + ": nothing opened");
        }
        return checked;
    }

    private boolean popupOpen() {
        for (org.eclipse.swt.widgets.Shell open : display.getShells()) {
            if (open != shell && open.isVisible()) {
                return true;
            }
        }
        return false;
    }

    private static void shot(org.eclipse.swt.widgets.Shell shell, Path dir, String name) {
        if (dir != null && shell != null && !shell.isDisposed()) {
            DemoSupport.screenshot(shell, dir, name);
        }
    }

    private int check(String where, Control control, List<String> problems) {
        if (control == null) {
            problems.add(where + ": nothing opened");
            return 0;
        }
        int count = 1;
        if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            problems.add(where + ": " + control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
        }
        themes.styleOf(control).color("background").ifPresent(expected -> {
            if (!control.getBackground().getRGB().equals(expected.rgb)) {
                problems.add(where + ": " + control.getClass().getName() + " background " + control.getBackground().getRGB()
                        + ", theme says " + expected.rgb);
            }
        });
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                count += check(where, child, problems);
            }
        }
        return count;
    }
}
