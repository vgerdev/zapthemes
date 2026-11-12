package com.zapthemes.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.model.Theme;
import com.zapthemes.editor.palette.PaletteStore;

/**
 * The theme editor as a JFace wizard: a palette, the common names, their colors and fonts, the
 * controls' properties, then where to save it. Each page has a live preview of the theme being
 * built. Any ZapThemes application can open it:
 *
 * <pre>{@code
 * ThemeWizard wizard = new ThemeWizard(themes);   // resolved themes to duplicate and build on
 * if (ThemeWizard.dialog(shell, wizard).open() == Window.OK) {
 *     Theme theme = wizard.result();              // saved in wizard.savedFile()
 * }
 * }</pre>
 *
 * {@link #edit} opens a saved theme to change it. The wizard runs under the application's theme;
 * only the preview panes show the theme being edited ({@link ThemeManager#preview}). Finish writes
 * a full theme, with no {@code extends}, so that it does not change when the library's themes do.
 * Cancel discards the edits.
 */
public final class ThemeWizard extends Wizard {

    /** A size that fits the pages and the preview side by side. */
    public static final Point PAGE_SIZE = new Point(1120, 660);

    private final EditorModel model;
    private final PaletteStore store;
    private final PropertyCatalog catalog;
    private PalettePage palettePage;
    private NamesPage namesPage;
    private ColorsPage colorsPage;
    private ControlsPage controlsPage;
    private FinishPage finishPage;
    private Theme result;
    private Path saved;

    /** A wizard over {@code themes} (resolved), saving palettes in {@code ~/.zapthemes/palettes}. */
    public ThemeWizard(List<Theme> themes) {
        this(new EditorModel(themes, System.nanoTime()), PaletteStore.user());
    }

    /** A wizard on {@code model}, saving palettes in {@code store}. */
    public ThemeWizard(EditorModel model, PaletteStore store) {
        this(model, store, PropertyCatalog.load());
    }

    /** A wizard on {@code model}, with the types and properties {@code catalog} describes. */
    public ThemeWizard(EditorModel model, PaletteStore store, PropertyCatalog catalog) {
        this.model = model;
        this.store = store;
        this.catalog = catalog;
        setWindowTitle(model.file().isPresent() ? "Edit Theme" : "New Theme");
        setNeedsProgressMonitor(false);
        setHelpAvailable(false);
    }

    /**
     * A wizard that edits the theme in {@code file}, saving it back there unless another file is
     * chosen. A theme that extends one of {@code themes} is read over it.
     */
    public static ThemeWizard edit(List<Theme> themes, Path file) {
        ThemeRegistry registry = new ThemeRegistry();
        themes.forEach(registry::register);
        Theme loaded = registry.loadFile(file);
        EditorModel model = new EditorModel(themes, System.nanoTime());
        model.open(registry.resolve(loaded.id()), file);
        model.clearHistory();
        return new ThemeWizard(model, PaletteStore.user());
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
        controlsPage = new ControlsPage(model, catalog);
        finishPage = new FinishPage(model);
        addPage(palettePage);
        addPage(namesPage);
        addPage(colorsPage);
        addPage(controlsPage);
        addPage(finishPage);
    }

    public EditorModel model() {
        return model;
    }

    /** The theme Finish saved; null before Finish, and after Cancel. */
    public Theme result() {
        return result;
    }

    /** Where Finish saved the theme; null before Finish, and after Cancel. */
    public Path savedFile() {
        return saved;
    }

    @Override
    public boolean performFinish() {
        Path target = finishPage.target();
        try {
            model.save(target);
        } catch (IOException | RuntimeException e) {
            getContainer().showPage(finishPage);
            finishPage.setErrorMessage("Could not save the theme in " + target + ": " + e.getMessage());
            return false;
        }
        result = model.theme();
        saved = target;
        if (finishPage.applyWhenDone()) {
            Display display = getShell() != null ? getShell().getDisplay() : Display.getCurrent();
            Theme theme = result;
            display.asyncExec(() -> ThemeManager.get(display).setTheme(theme));
        }
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

    ControlsPage controlsPage() {
        return controlsPage;
    }

    FinishPage finishPage() {
        return finishPage;
    }
}
