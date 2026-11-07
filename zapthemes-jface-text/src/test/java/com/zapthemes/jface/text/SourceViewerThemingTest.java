package com.zapthemes.jface.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ContextInformationValidator;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.AnnotationRulerColumn;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.IAnnotationAccess;
import org.eclipse.jface.text.source.ISharedTextColors;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.text.source.OverviewRuler;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.jface.JFaceTheming;
import com.zapthemes.themes.BundledThemes;

/**
 * The forked JFace Text builds its source viewer, rulers, hovers and content assist from proxies,
 * and its patches give them the theme's {@code ruler}, {@code popup} and {@code frame} classes in
 * place of upstream's system colors, also after a theme switch.
 */
class SourceViewerThemingTest {

    private static Display display;
    private static Theme light;
    private static Theme dark;
    private ThemeManager manager;
    private Shell shell;
    private SourceViewer viewer;
    private TestAssistant assistant;
    private LineNumberRulerColumn lineNumbers;

    @BeforeAll
    static void loadThemes() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        light = registry.resolve("zap-light");
        dark = registry.resolve("zap-dark");
    }

    @BeforeEach
    void setUp() {
        manager = ThemeManager.get(display);
        manager.setTheme(dark);
    }

    @AfterEach
    void tearDown() {
        for (Shell open : display.getShells()) {
            open.dispose();
        }
        manager.setTheme(dark);
    }

    @Test
    void everyControlIsAProxy() {
        open(null);

        // Anonymous subclasses (the ruler columns' canvases) extend proxies.
        assertThat(findAll(shell, Control.class)).hasSizeGreaterThan(5).allSatisfy(c -> assertThat(isProxy(c.getClass())).as(c.toString()).isTrue());
    }

    @Test
    void rulersPaintInTheThemesRulerColorsAcrossSwitches() {
        open(null);
        List<Control> rulers = findAll(shell, Canvas.class).stream().filter(c -> !(c instanceof StyledText) && !(c instanceof Shell))
                .map(Control.class::cast).filter(c -> c != viewer.getControl()).toList();
        assertThat(rulers).as("composite ruler, its two columns, overview ruler and header").hasSize(5)
                .allSatisfy(c -> assertThat(c.getData(ThemeKeys.THEME_CLASS)).isEqualTo(TextTheming.RULER));

        assertRuler(dark);
        manager.setTheme(light);
        dispatch();
        assertRuler(light);
    }

    @Test
    void syntaxColorKeysFollowTheTheme() {
        assertThat(JFaceResources.getColorRegistry().getRGB(TextTheming.KEYWORD)).isEqualTo(new RGB(0xCF, 0x8E, 0x6D));
        manager.setTheme(light);
        assertThat(JFaceResources.getColorRegistry().getRGB(TextTheming.KEYWORD)).isEqualTo(new RGB(0x00, 0x33, 0xB3));
    }

    @Test
    void hoversUseThePopupClassAcrossSwitches() {
        open(null);
        Shell hover = opened(() -> {
            DefaultInformationControl control = new DefaultInformationControl(shell, "Press 'F2' for focus");
            control.setInformation("A hover");
            control.setSize(300, 80);
            control.setVisible(true);
        });

        assertPopup(hover, dark);
        Label status = findAll(hover, Label.class).stream().filter(l -> l.getText().startsWith("Press")).findFirst().orElseThrow();
        assertThat(status.getData(ThemeKeys.THEME_CLASS)).isEqualTo(JFaceTheming.POPUP + " " + JFaceTheming.MUTED);
        manager.setTheme(light);
        assertPopup(hover, light);
    }

    @Test
    void contentAssistAndContextInformationUseThePopupClass() {
        open(null);
        Shell proposals = assist(ISourceViewer.CONTENTASSIST_PROPOSALS);
        Table table = findAll(proposals, Table.class).getFirst();
        assertThat(table.getData(ThemeKeys.THEME_CLASS)).isEqualTo(JFaceTheming.POPUP);
        assertThat(table.getBackground().getRGB()).isEqualTo(palette(dark, "surface"));
        assertThat(table.getItems()).as("no stripes in the popup list")
                .allSatisfy(item -> assertThat(item.getBackground().getRGB()).isEqualTo(palette(dark, "surface")));
        assistant.close();
        dispatch();

        Shell context = assist(ISourceViewer.CONTENTASSIST_CONTEXT_INFORMATION);
        assertThat(context.getData(ThemeKeys.THEME_CLASS)).isEqualTo(TextTheming.FRAME);
        assertThat(context.getBackground().getRGB()).as("frame").isEqualTo(palette(dark, "border"));
        StyledText text = findAll(context, StyledText.class).getFirst();
        assertThat(text.getBackground().getRGB()).isEqualTo(palette(dark, "surface"));
        assertThat(text.getForeground().getRGB()).isEqualTo(palette(dark, "fg"));
    }

    @Test
    void proposalColorsTheApplicationChoseAreKept() {
        open(new RGB(200, 0, 0));
        Shell proposals = assist(ISourceViewer.CONTENTASSIST_PROPOSALS);
        Table table = findAll(proposals, Table.class).getFirst();

        assertThat(table.getData(ThemeKeys.THEME_CLASS)).isNull();
        assertThat(table.getBackground().getRGB()).isEqualTo(new RGB(200, 0, 0));
    }

    @Test
    void withoutAThemeHoversAndRulersLookAsUpstream() {
        manager.setTheme(Theme.EMPTY);
        open(null);
        Shell hover = opened(() -> new DefaultInformationControl(shell, "status").setVisible(true));

        assertThat(hover.getData(ThemeKeys.THEME_CLASS)).isEqualTo(JFaceTheming.POPUP);
        assertThat(hover.getBackground().getRGB()).isEqualTo(display.getSystemColor(
                "win32".equals(SWT.getPlatform()) || "cocoa".equals(SWT.getPlatform()) ? SWT.COLOR_INFO_BACKGROUND : SWT.COLOR_LIST_BACKGROUND).getRGB());
        Canvas canvas = (Canvas) lineNumbers.getControl();
        assertThat(painted(canvas)).isEqualTo(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND).getRGB());
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isProxy(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            if (c.getName().startsWith("com.zapthemes.")) {
                return true;
            }
        }
        return false;
    }

    private void assertRuler(Theme theme) {
        Canvas canvas = (Canvas) lineNumbers.getControl();
        assertThat(canvas.getForeground().getRGB()).as("line numbers").isEqualTo(palette(theme, "qualifier"));
        assertThat(painted(canvas)).as("painted ruler background").isEqualTo(palette(theme, "surface"));
    }

    /** The color the canvas paints at its bottom-left corner, below the last line number. */
    private static RGB painted(Canvas canvas) {
        var size = canvas.getSize();
        Image image = new Image(display, size.x, size.y);
        GC gc = new GC(image);
        canvas.print(gc);
        gc.dispose();
        var data = image.getImageData();
        image.dispose();
        return data.palette.getRGB(data.getPixel(1, size.y - 2));
    }

    private void assertPopup(Shell popup, Theme theme) {
        assertThat(popup.getBackground().getRGB()).as("shell").isEqualTo(palette(theme, "surface"));
        StyledText text = findAll(popup, StyledText.class).getFirst();
        assertThat(text.getBackground().getRGB()).as("text").isEqualTo(palette(theme, "surface"));
        assertThat(text.getForeground().getRGB()).as("text").isEqualTo(palette(theme, "fg"));
    }

    /** A source viewer with line numbers, an annotation column, an overview ruler and content assist. */
    private void open(RGB proposalBackground) {
        shell = new com.zapthemes.swt.widgets.Shell(display);
        shell.setLayout(new FillLayout());
        IAnnotationAccess access = new IAnnotationAccess() {
            @Override
            @SuppressWarnings("deprecation")
            public Object getType(Annotation annotation) {
                return annotation.getType();
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean isMultiLine(Annotation annotation) {
                return false;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean isTemporary(Annotation annotation) {
                return true;
            }
        };
        ISharedTextColors colors = new ISharedTextColors() {
            @Override
            public Color getColor(RGB rgb) {
                return new Color(rgb);
            }

            @Override
            public void dispose() {
            }
        };
        CompositeRuler ruler = new CompositeRuler();
        ruler.addDecorator(0, new AnnotationRulerColumn(12, access));
        lineNumbers = new LineNumberRulerColumn();
        ruler.addDecorator(1, lineNumbers);
        viewer = new SourceViewer(shell, ruler, new OverviewRuler(access, 10, colors), true, SWT.BORDER | SWT.V_SCROLL);
        assistant = new TestAssistant();
        if (proposalBackground != null) {
            assistant.setProposalSelectorBackground(new Color(proposalBackground));
        }
        assistant.setContentAssistProcessor(new Processor(), IDocument.DEFAULT_CONTENT_TYPE);
        viewer.configure(new SourceViewerConfiguration() {
            @Override
            public IContentAssistant getContentAssistant(ISourceViewer sourceViewer) {
                return assistant;
            }
        });
        viewer.setDocument(new Document("one\ntwo\nprint(\n"));
        shell.setSize(420, 300);
        shell.open();
        // Content assist opens only for the focus control; Xvfb has no window manager to activate the shell.
        shell.forceActive();
        dispatch();
        viewer.getTextWidget().setFocus();
        viewer.setSelectedRange(viewer.getDocument().getLength() - 1, 0);
        dispatch();
    }

    private static final class TestAssistant extends ContentAssistant {
        void close() {
            hide();
        }
    }

    private static final class Processor implements IContentAssistProcessor {
        @Override
        public ICompletionProposal[] computeCompletionProposals(ITextViewer textViewer, int offset) {
            return new ICompletionProposal[] { new CompletionProposal("alpha", offset, 0, 5),
                new CompletionProposal("beta", offset, 0, 4), new CompletionProposal("gamma", offset, 0, 5) };
        }

        @Override
        public IContextInformation[] computeContextInformation(ITextViewer textViewer, int offset) {
            return new IContextInformation[] { new ContextInformation("print", "void print(String text)") };
        }

        @Override
        public char[] getCompletionProposalAutoActivationCharacters() {
            return null;
        }

        @Override
        public char[] getContextInformationAutoActivationCharacters() {
            return null;
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

    /**
     * Opens a content assist popup. It opens only for the focus control, which the window manager
     * may hand over late (Xvfb has none): retried with the editor focused again.
     */
    private Shell assist(int operation) {
        for (int attempt = 0; attempt < 5; attempt++) {
            shell.forceActive();
            viewer.getTextWidget().setFocus();
            dispatch();
            try {
                return opened(() -> viewer.doOperation(operation));
            } catch (AssertionError notYet) {
                // Try again.
            }
        }
        throw new AssertionError("content assist did not open");
    }

    private static Shell opened(Runnable open) {
        Set<Shell> before = Set.of(display.getShells());
        open.run();
        // A busy CI machine can take a while to show the popup: look for it for up to 2 s.
        long end = System.currentTimeMillis() + 2000;
        do {
            dispatch();
            for (Shell candidate : display.getShells()) {
                if (!before.contains(candidate) && candidate.isVisible()) {
                    return candidate;
                }
            }
        } while (System.currentTimeMillis() < end);
        throw new AssertionError("nothing opened");
    }

    private static RGB palette(Theme theme, String name) {
        return com.zapthemes.core.style.ThemeNames.of(theme).color(name).orElseThrow().rgb;
    }

    private static <T> List<T> findAll(Control root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                found.addAll(findAll(child, type));
            }
        }
        return found;
    }

    private static void dispatch() {
        long end = System.currentTimeMillis() + 300;
        while (System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
    }
}
