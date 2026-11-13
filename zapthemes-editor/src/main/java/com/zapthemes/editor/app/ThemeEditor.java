package com.zapthemes.editor.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;

import com.google.gson.JsonPrimitive;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.editor.ColorChip;
import com.zapthemes.editor.ColorChoice;
import com.zapthemes.editor.EditorModel;
import com.zapthemes.editor.PreviewPane;
import com.zapthemes.editor.ThemeWizard;
import com.zapthemes.editor.palette.PaletteStore;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.themes.BundledThemes;

/**
 * The theme editor on its own: the {@link ThemeWizard} in a window of its own, under a bundled
 * theme. {@code ./gradlew :zapthemes-editor:run}, or with the class path set:
 *
 * <pre>
 * java com.zapthemes.editor.app.ThemeEditor [theme.json] [--theme zap-dark]
 * </pre>
 *
 * A theme file is opened to change it ({@link ThemeWizard#edit}); without one the wizard starts a
 * new theme. {@code --theme} picks the editor's own theme; by default it follows the desktop (high
 * contrast, dark or light).
 *
 * <p>{@code --smoke}, for every bundled theme as the window's theme: on every page, every control
 * outside the preview has the window's theme and every control in it the edited theme; edits on
 * each page (regenerate, a duplicated theme, an added name, a color, a control property) reach the
 * preview, and undo takes them back. It never finishes, so it saves nothing, and its palettes go
 * to a temporary directory. Exits non-zero on failure. {@code --screenshots <dir>} also saves each
 * page.
 */
public final class ThemeEditor {

    private static final RGB PLUM = new RGB(0x24, 0x1B, 0x2E);

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available;
    private final Shell parent;

    private ThemeEditor(Display display, String themeId) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        this.available = bundledThemes();
        String id = themeId != null ? themeId : BundledThemes.idFor(display.getHighContrast(), Display.isSystemDarkTheme());
        themes.setTheme(available.stream().filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No bundled theme " + id + "; the themes are " + BundledThemes.IDS)));
        parent = new Shell(display);
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        Display.setAppName("ZapThemes Theme Editor");
        Display display = new Display();
        int theme = options.indexOf("--theme");
        ThemeEditor editor = new ThemeEditor(display, theme >= 0 ? options.get(theme + 1) : null);
        int exit = 0;
        int shots = options.indexOf("--screenshots");
        if (options.contains("--smoke") || shots >= 0) {
            exit = editor.smokeTest(shots >= 0 ? Path.of(options.get(shots + 1)) : null);
        } else {
            exit = editor.edit(file(options));
        }
        display.dispose();
        System.exit(exit);
    }

    /** The first argument that is not an option or an option's value: the theme file to edit. */
    private static Path file(List<String> options) {
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i);
            if (option.equals("--theme") || option.equals("--screenshots")) {
                i++;
            } else if (!option.startsWith("--")) {
                return Path.of(option);
            }
        }
        return null;
    }

    private int edit(Path file) {
        ThemeWizard wizard = file != null ? ThemeWizard.edit(available, file) : new ThemeWizard(available);
        if (ThemeWizard.dialog(parent, wizard).open() == Window.OK) {
            System.out.println("Saved " + wizard.result().id() + " in " + wizard.savedFile());
        }
        return 0;
    }

    static List<Theme> bundledThemes() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        return BundledThemes.IDS.stream().map(registry::resolve).toList();
    }

    // ------------------------------------------------------------------ smoke test

    private int smokeTest(Path screenshots) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        Path palettes;
        try {
            palettes = Files.createTempDirectory("zapthemes-editor-smoke-palettes");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Theme window : available) {
            themes.setTheme(window);
            EditorModel model = new EditorModel(available, 11);
            ThemeWizard wizard = new ThemeWizard(model, new PaletteStore(palettes));
            WizardDialog dialog = ThemeWizard.dialog(parent, wizard);
            dialog.setBlockOnOpen(false);
            dialog.open();
            settle();

            for (IWizardPage page : wizard.getPages()) {
                dialog.showPage(page);
                settle();
                checked += check(dialog, model, window, page.getName(), problems);
                if (screenshots != null) {
                    screenshot(dialog.getShell(), screenshots, "wizard-" + page.getName() + "-" + window.id());
                }
            }

            // Edits reach the preview, on whichever page shows it.
            dialog.showPage(wizard.getPage("palette"));
            Theme before = model.theme();
            model.regenerate();
            settle();
            checked += expectShown(dialog, model, "regenerate", problems);
            model.duplicate(available.get(1));
            settle();
            checked += expectShown(dialog, model, "duplicate", problems);
            model.undo(EditorModel.Page.PALETTE);
            model.undo(EditorModel.Page.PALETTE);
            settle();
            if (!model.theme().swatches().equals(before.swatches())) {
                problems.add(window.id() + ": undo did not bring the palette back");
            }

            dialog.showPage(wizard.getPage("names"));
            model.addName("badgeBackground", EditorModel.COLOR);
            settle();
            checked += expectShown(dialog, model, "add name", problems);

            dialog.showPage(wizard.getPage("values"));
            model.setColor("background", ColorChoice.of(PLUM));
            settle();
            checked += expectShown(dialog, model, "set color", problems);
            PreviewPane preview = previewOf(wizard.getPage("values"));
            if (!preview.getBackground().getRGB().equals(PLUM)) {
                problems.add(window.id() + ": the preview's background is " + preview.getBackground().getRGB() + " after setting " + PLUM);
            }
            if (screenshots != null) {
                screenshot(dialog.getShell(), screenshots, "wizard-edited-" + window.id());
            }

            dialog.showPage(wizard.getPage("controls"));
            model.setRuleValue(EditorModel.RuleKey.of("Table"), "headerBackground", new JsonPrimitive("$accent"));
            settle();
            checked += expectShown(dialog, model, "set a control property", problems);
            if (!model.theme().controls().get("Table").get("headerBackground").getAsString().equals("$accent")) {
                problems.add(window.id() + ": the Table rule did not take the edit");
            }
            model.undo(EditorModel.Page.CONTROLS);
            settle();
            // A click on a control of the preview selects its type.
            Control[] table = { null };
            walk(previewOf(wizard.getPage("controls")), c -> {
                if (c instanceof org.eclipse.swt.widgets.Table && table[0] == null) {
                    table[0] = c;
                }
            });
            Event click = new Event();
            click.button = 1;
            table[0].notifyListeners(SWT.MouseDown, click);
            settle();
            if (screenshots != null) {
                screenshot(dialog.getShell(), screenshots, "wizard-controls-table-" + window.id());
            }
            checked += check(dialog, model, window, "controls (edited)", problems);

            dialog.close();
            settle();
        }
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: theme editor, " + checked + " control checks over " + available.size()
                    + " window themes, 5 pages, edits and undo");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    /** Every control of the page: themed; the preview's with the edited theme, the rest with the window's. */
    private int check(WizardDialog dialog, EditorModel model, Theme window, String page, List<String> problems) {
        int[] count = { 0 };
        PreviewPane preview = previewOf(dialog.getCurrentPage());
        if (preview.shown() != model.theme()) {
            problems.add(window.id() + " " + page + ": the preview does not show the edited theme");
        }
        walk(dialog.getShell(), control -> {
            if (!control.isVisible()) {
                return;
            }
            count[0]++;
            if (control.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
                problems.add(window.id() + " " + page + ": " + control + " has no " + ThemeKeys.THEME_CONTROL_TYPE);
            }
            Theme expected = inside(control, preview) ? model.theme() : window;
            if (themes.themeOf(control) != expected) {
                problems.add(window.id() + " " + page + ": " + control + " is styled with " + themes.themeOf(control).id()
                        + ", not " + expected.id());
            }
            themes.styleOf(control).color("background").ifPresent(rgba -> {
                if (!(control instanceof ColorChip) && !control.getBackground().getRGB().equals(rgba.rgb)) {
                    problems.add(window.id() + " " + page + ": " + control + " background " + control.getBackground().getRGB()
                            + ", its style says " + rgba.rgb);
                }
            });
        });
        return count[0];
    }

    private int expectShown(WizardDialog dialog, EditorModel model, String edit, List<String> problems) {
        PreviewPane preview = previewOf(dialog.getCurrentPage());
        if (preview.shown() != model.theme()) {
            problems.add(edit + " on " + dialog.getCurrentPage().getName() + ": the preview does not show the edit");
        }
        RGB expected = model.color("background").orElseThrow();
        if (!preview.getBackground().getRGB().equals(expected)) {
            problems.add(edit + ": preview background " + preview.getBackground().getRGB() + ", the theme's is " + expected);
        }
        return 1;
    }

    private static PreviewPane previewOf(IWizardPage page) {
        PreviewPane[] found = { null };
        walk(page.getControl(), control -> {
            if (control instanceof PreviewPane pane) {
                found[0] = pane;
            }
        });
        if (found[0] == null) {
            throw new IllegalStateException("No preview on page " + page.getName());
        }
        return found[0];
    }

    private static boolean inside(Control control, Control ancestor) {
        for (Control c = control; c != null; c = c.getParent()) {
            if (c == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static void walk(Control control, Consumer<Control> visitor) {
        visitor.accept(control);
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                walk(child, visitor);
            }
        }
    }

    /** Runs pending events for a moment, so layouts and paints are done. */
    private void settle() {
        for (int i = 0; i < 20; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
            display.timerExec(25, () -> { });
            display.sleep();
        }
    }

    /** Saves the shell's client area as PNG through {@link Control#print}, which needs no screen access. */
    private static void screenshot(org.eclipse.swt.widgets.Shell shell, Path dir, String name) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Rectangle area = shell.getClientArea();
        Image image = new Image(shell.getDisplay(), area.width, area.height);
        GC gc = new GC(image);
        shell.print(gc);
        gc.dispose();
        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { image.getImageData() };
        String file = dir.resolve(name + ".png").toString();
        loader.save(file, SWT.IMAGE_PNG);
        image.dispose();
        System.out.println("Saved " + file);
    }
}
