package com.zapthemes.demo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.model.Theme;
import com.zapthemes.editor.ColorChoice;
import com.zapthemes.editor.EditorModel;
import com.zapthemes.editor.PreviewPane;
import com.zapthemes.editor.ThemeWizard;
import com.zapthemes.editor.palette.PaletteStore;
import com.zapthemes.swt.widgets.Shell;

/**
 * The theme wizard ({@link ThemeWizard}): palette, common names, colors and fonts, each page with
 * a preview of the theme being built.
 *
 * <p>{@code --smoke}, for every bundled theme as the window's theme: on every page, every control
 * outside the preview has the window's theme and every control in it the edited theme; edits on
 * each page (regenerate, a duplicated theme, an added name, a color) reach the preview, and undo
 * takes them back. Exits non-zero on failure. {@code --screenshots <dir>} also saves each page.
 */
public final class ThemeWizardDemo {

    private static final RGB PLUM = new RGB(0x24, 0x1B, 0x2E);

    private final Display display;
    private final ThemeManager themes;
    private final List<Theme> available = DemoSupport.bundledThemes();
    private final Shell parent;

    private ThemeWizardDemo(Display display) {
        this.display = display;
        this.themes = ThemeManager.get(display);
        themes.setTheme(available.getFirst());
        parent = new Shell(display);
    }

    public static void main(String[] args) {
        List<String> options = List.of(args);
        Display display = new Display();
        ThemeWizardDemo demo = new ThemeWizardDemo(display);
        int exit = 0;
        int shots = options.indexOf("--screenshots");
        if (options.contains("--smoke") || shots >= 0) {
            exit = demo.smokeTest(shots >= 0 ? Path.of(options.get(shots + 1)) : null);
        } else {
            ThemeWizard wizard = new ThemeWizard(demo.available);
            if (ThemeWizard.dialog(demo.parent, wizard).open() == org.eclipse.jface.window.Window.OK) {
                System.out.println("Finished: " + wizard.result().id());
            }
        }
        display.dispose();
        System.exit(exit);
    }

    private int smokeTest(Path screenshots) {
        List<String> problems = new ArrayList<>();
        int checked = 0;
        Path palettes = Path.of(System.getProperty("java.io.tmpdir"), "zapthemes-wizard-demo-palettes");
        for (Theme window : available) {
            themes.setTheme(window);
            EditorModel model = new EditorModel(available, 11);
            ThemeWizard wizard = new ThemeWizard(model, new PaletteStore(palettes));
            WizardDialog dialog = ThemeWizard.dialog(parent, wizard);
            dialog.setBlockOnOpen(false);
            dialog.open();
            DemoSupport.settle(display);

            for (IWizardPage page : wizard.getPages()) {
                dialog.showPage(page);
                DemoSupport.settle(display);
                checked += check(dialog, model, window, page.getName(), problems);
                if (screenshots != null) {
                    DemoSupport.screenshot(dialog.getShell(), screenshots, "wizard-" + page.getName() + "-" + window.id());
                }
            }

            // Edits reach the preview, on whichever page shows it.
            dialog.showPage(wizard.getPage("palette"));
            Theme before = model.theme();
            model.regenerate();
            DemoSupport.settle(display);
            checked += expectShown(dialog, model, "regenerate", problems);
            model.duplicate(available.get(1));
            DemoSupport.settle(display);
            checked += expectShown(dialog, model, "duplicate", problems);
            model.undo(EditorModel.Page.PALETTE);
            model.undo(EditorModel.Page.PALETTE);
            DemoSupport.settle(display);
            if (!model.theme().swatches().equals(before.swatches())) {
                problems.add(window.id() + ": undo did not bring the palette back");
            }

            dialog.showPage(wizard.getPage("names"));
            model.addName("badgeBackground", EditorModel.COLOR);
            DemoSupport.settle(display);
            checked += expectShown(dialog, model, "add name", problems);

            dialog.showPage(wizard.getPage("values"));
            model.setColor("background", ColorChoice.of(PLUM));
            DemoSupport.settle(display);
            checked += expectShown(dialog, model, "set color", problems);
            PreviewPane preview = previewOf(dialog, wizard.getPage("values"));
            if (!preview.getBackground().getRGB().equals(PLUM)) {
                problems.add(window.id() + ": the preview's background is " + preview.getBackground().getRGB() + " after setting " + PLUM);
            }
            if (screenshots != null) {
                DemoSupport.screenshot(dialog.getShell(), screenshots, "wizard-edited-" + window.id());
            }
            checked += check(dialog, model, window, "values (edited)", problems);

            dialog.close();
            DemoSupport.settle(display);
        }
        if (problems.isEmpty()) {
            System.out.println("SMOKE OK: theme wizard, " + checked + " control checks over " + available.size()
                    + " window themes, 3 pages, edits and undo");
            return 0;
        }
        problems.forEach(p -> System.out.println("SMOKE FAIL: " + p));
        return 1;
    }

    /** Every control of the page: themed; the preview's with the edited theme, the rest with the window's. */
    private int check(WizardDialog dialog, EditorModel model, Theme window, String page, List<String> problems) {
        int[] count = { 0 };
        PreviewPane preview = previewOf(dialog, dialog.getCurrentPage());
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
                if (!(control instanceof com.zapthemes.editor.ColorChip) && !control.getBackground().getRGB().equals(rgba.rgb)) {
                    problems.add(window.id() + " " + page + ": " + control + " background " + control.getBackground().getRGB()
                            + ", its style says " + rgba.rgb);
                }
            });
        });
        return count[0];
    }

    private int expectShown(WizardDialog dialog, EditorModel model, String edit, List<String> problems) {
        PreviewPane preview = previewOf(dialog, dialog.getCurrentPage());
        if (preview.shown() != model.theme()) {
            problems.add(edit + " on " + dialog.getCurrentPage().getName() + ": the preview does not show the edit");
        }
        RGB expected = model.color("background").orElseThrow();
        if (!preview.getBackground().getRGB().equals(expected)) {
            problems.add(edit + ": preview background " + preview.getBackground().getRGB() + ", the theme's is " + expected);
        }
        return 1;
    }

    private static PreviewPane previewOf(WizardDialog dialog, IWizardPage page) {
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
}
