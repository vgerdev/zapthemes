package com.zapthemes.editor;

import java.util.List;

import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.model.Theme;
import com.zapthemes.editor.palette.PaletteStore;

/**
 * The theme editor as a JFace wizard: a palette, the common names, then their colors and fonts,
 * each page with a live preview of the theme being built. Any ZapThemes application can open it:
 *
 * <pre>{@code
 * ThemeWizard wizard = new ThemeWizard(themes);   // resolved themes to duplicate and build on
 * if (ThemeWizard.dialog(shell, wizard).open() == Window.OK) {
 *     Theme theme = wizard.result();
 * }
 * }</pre>
 *
 * The wizard runs under the application's theme; only the preview panes show the theme being
 * edited ({@link com.zapthemes.core.ThemeManager#preview}). Cancel discards the edits.
 */
public final class ThemeWizard extends Wizard {

    /** A size that fits the pages and the preview side by side. */
    public static final Point PAGE_SIZE = new Point(1120, 660);

    private final EditorModel model;
    private final PaletteStore store;
    private PalettePage palettePage;
    private NamesPage namesPage;
    private ColorsPage colorsPage;
    private Theme result;

    /** A wizard over {@code themes} (resolved), saving palettes in {@code ~/.zapthemes/palettes}. */
    public ThemeWizard(List<Theme> themes) {
        this(new EditorModel(themes, System.nanoTime()), PaletteStore.user());
    }

    /** A wizard on {@code model}, saving palettes in {@code store}. */
    public ThemeWizard(EditorModel model, PaletteStore store) {
        this.model = model;
        this.store = store;
        setWindowTitle("New Theme");
        setNeedsProgressMonitor(false);
        setHelpAvailable(false);
    }

    /** A dialog for {@code wizard} at {@link #PAGE_SIZE}. */
    public static WizardDialog dialog(Shell parent, ThemeWizard wizard) {
        WizardDialog dialog = new WizardDialog(parent, wizard);
        dialog.setPageSize(PAGE_SIZE);
        return dialog;
    }

    @Override
    public void addPages() {
        palettePage = new PalettePage(model, store);
        namesPage = new NamesPage(model);
        colorsPage = new ColorsPage(model);
        addPage(palettePage);
        addPage(namesPage);
        addPage(colorsPage);
    }

    public EditorModel model() {
        return model;
    }

    /** The theme Finish gave; null before Finish, and after Cancel. */
    public Theme result() {
        return result;
    }

    @Override
    public boolean performFinish() {
        result = model.theme();
        return true;
    }

    PalettePage palettePage() {
        return palettePage;
    }

    NamesPage namesPage() {
        return namesPage;
    }

    ColorsPage colorsPage() {
        return colorsPage;
    }
}
