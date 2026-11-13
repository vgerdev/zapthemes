package com.zapthemes.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Scale;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.editor.EditorModel.Page;
import com.zapthemes.editor.palette.PaletteStore;
import com.zapthemes.themes.BundledThemes;

/** The wizard driven through its controls, as a user would. */
class ThemeWizardTest {

    private static Display display;
    private static List<Theme> bundled;
    private ThemeManager themes;
    private Shell parent;
    private ThemeWizard wizard;
    private WizardDialog dialog;
    private EditorModel model;

    @TempDir
    Path palettes;

    @BeforeAll
    static void load() {
        display = Display.getDefault();
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        bundled = BundledThemes.IDS.stream().map(registry::resolve).toList();
    }

    @BeforeEach
    void open() {
        themes = ThemeManager.get(display);
        themes.setTheme(bundled.getFirst());
        parent = new com.zapthemes.swt.widgets.Shell(display);
        model = new EditorModel(bundled, 3);
        wizard = new ThemeWizard(model, new PaletteStore(palettes));
        dialog = ThemeWizard.dialog(parent, wizard);
        dialog.setBlockOnOpen(false);
        dialog.open();
        settle();
    }

    @AfterEach
    void close() {
        if (dialog.getShell() != null && !dialog.getShell().isDisposed()) {
            dialog.close();
        }
        parent.dispose();
        settle();
    }

    @Test
    void thePreviewShowsTheEditedThemeAndTheWizardKeepsTheWindowsTheme() {
        PreviewPane preview = wizard.palettePage().preview();
        assertThat(preview.shown()).isSameAs(model.theme());
        assertThat(themes.themeOf(preview)).isSameAs(model.theme());
        assertThat(themes.themeOf(wizard.palettePage().getControl())).isSameAs(bundled.getFirst());
        assertThat(preview.getBackground().getRGB()).isEqualTo(model.color("background").orElseThrow());
    }

    @Test
    void regenerateLockAndSlidersEditThePaletteAndUndoTakesThemBack() {
        PalettePage page = wizard.palettePage();
        var first = model.palette();

        click(button(page, "Lock"));
        assertThat(model.palette().swatches().getFirst().locked()).isTrue();
        click(button(page, "Regenerate"));
        assertThat(model.palette().swatches().getFirst()).isEqualTo(first.swatches().getFirst().withLocked(true));
        assertThat(model.palette().swatches().get(3).color()).isNotEqualTo(first.swatches().get(3).color());
        assertThat(wizard.palettePage().preview().shown()).isSameAs(model.theme());

        page.chips().get(3).choose();
        settle();
        assertThat(page.selectedSwatch()).isEqualTo("accent1");
        Scale hue = find(page.getControl(), Scale.class, s -> true);
        RGB before = model.palette().swatch("accent1").orElseThrow().color();
        for (int step = 1; step <= 5; step++) {
            hue.setSelection((hue.getSelection() + 20) % 360);
            hue.notifyListeners(SWT.Selection, new Event());
        }
        settle();
        assertThat(model.palette().swatch("accent1").orElseThrow().color()).isNotEqualTo(before);
        assertThat(button(page, "Undo Change accent1").isEnabled()).isTrue();

        click(button(page, "Undo Change accent1"));
        assertThat(model.palette().swatch("accent1").orElseThrow().color()).as("the drag undoes as one").isEqualTo(before);

        page.tintChips().get(2).choose();
        settle();
        assertThat(model.palette().swatch("accent1").orElseThrow().color()).isEqualTo(page.tintChips().get(2).getRGB());
    }

    @Test
    void duplicatingAThemeAndColorsFromAnImage() {
        PalettePage page = wizard.palettePage();
        click(button(page, "Duplicate"));
        assertThat(model.duplicated()).contains(bundled.getFirst());
        assertThat(page.preview().getBackground().getRGB()).isEqualTo(new RGB(0xF7, 0xF8, 0xFA));

        ImageData image = new ImageData(60, 20, 24, new PaletteData(0xFF0000, 0xFF00, 0xFF));
        for (int x = 0; x < 60; x++) {
            RGB color = x < 30 ? new RGB(0x10, 0x20, 0x50) : x < 45 ? new RGB(0xE8, 0xD8, 0xB0) : new RGB(0xF0, 0x71, 0x78);
            for (int y = 0; y < 20; y++) {
                image.setPixel(x, y, image.palette.getPixel(color));
            }
        }
        page.useImage(image, "stripes.png");
        settle();
        assertThat(model.palette().colors()).contains(new RGB(0x10, 0x20, 0x50), new RGB(0xF0, 0x71, 0x78));
        assertThat(model.history(Page.PALETTE).undoLabel()).contains("Colors of stripes.png");
    }

    @Test
    void savedPalettesCanBeUsedAndDeleted() {
        dialog.close();
        PaletteStore store = new PaletteStore(palettes);
        var sea = new EditorModel(bundled, 99).palette();
        store.save("Sea", sea);
        wizard = new ThemeWizard(model, store);
        dialog = ThemeWizard.dialog(parent, wizard);
        dialog.setBlockOnOpen(false);
        dialog.open();
        settle();
        PalettePage page = wizard.palettePage();
        org.eclipse.swt.widgets.Combo choices = find(page.getControl(), org.eclipse.swt.widgets.Combo.class,
                c -> c.getItemCount() > 0 && c.getItem(0).equals("Sea (saved)"));
        choices.select(0);
        choices.notifyListeners(SWT.Selection, new Event());

        click(button(page, "Use"));
        assertThat(model.palette().colors()).isEqualTo(sea.colors());

        click(button(page, "Delete"));
        assertThat(store.list()).isEmpty();
        assertThat(choices.getItem(0)).isNotEqualTo("Sea (saved)");
    }

    @Test
    void namesAreAddedAndRenamedInPlace() {
        dialog.showPage(wizard.namesPage());
        settle();
        NamesPage page = wizard.namesPage();
        // Whether renaming starts is counted as it happens: without a window manager (CI's Xvfb), the
        // field can lose focus right after, and a field that loses focus ends the rename, as it should.
        int[] started = { 0 };
        page.viewer().getColumnViewerEditor().addEditorActivationListener(new org.eclipse.jface.viewers.ColumnViewerEditorActivationListener() {
            @Override
            public void beforeEditorActivated(org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent event) {
            }

            @Override
            public void afterEditorActivated(org.eclipse.jface.viewers.ColumnViewerEditorActivationEvent event) {
                started[0]++;
            }

            @Override
            public void beforeEditorDeactivated(org.eclipse.jface.viewers.ColumnViewerEditorDeactivationEvent event) {
            }

            @Override
            public void afterEditorDeactivated(org.eclipse.jface.viewers.ColumnViewerEditorDeactivationEvent event) {
            }
        });
        dialog.getShell().forceActive();
        page.viewer().getTree().setFocus();
        settle();

        click(button(page, "Add name"));
        assertThat(model.name("newName")).isPresent();
        assertThat(started[0]).as("renaming starts in place").isEqualTo(1);
        // Type at once, with no events in between that could move the focus.
        if (!page.viewer().isCellEditorActive()) {
            page.viewer().editElement(model.name("newName").orElseThrow(), 0);
        }
        Text editor = find(page.viewer().getTree(), Text.class, t -> t.isVisible());

        editor.setText("2bad");
        editor.notifyListeners(SWT.Modify, new Event());
        assertThat(page.getErrorMessage()).contains("starts with a letter");
        editor.setText("badgeBackground");
        editor.notifyListeners(SWT.Modify, new Event());
        assertThat(page.getErrorMessage()).isNull();
        editor.notifyListeners(SWT.DefaultSelection, new Event());
        settle();

        assertThat(model.name("badgeBackground")).isPresent();
        assertThat(model.name("newName")).isEmpty();
        assertThat(page.viewer().getStructuredSelection().getFirstElement()).isEqualTo(model.name("badgeBackground").orElseThrow());

        // F2 renames too; Rename is only for names you added.
        page.viewer().setSelection(new StructuredSelection(model.name("foreground").orElseThrow()));
        settle();
        assertThat(button(page, "Rename").isEnabled()).isFalse();
        page.viewer().setSelection(new StructuredSelection(model.name("badgeBackground").orElseThrow()));
        settle();
        int before = started[0];
        click(button(page, "Rename"));
        assertThat(started[0]).as("Rename starts renaming").isEqualTo(before + 1);
        page.viewer().cancelEditing();

        // A click that selects a row does not rename it; a second, slow click does. The clicks must land
        // on the row: only its group is left open, so that it is in view on every platform.
        page.viewer().collapseAll();
        page.viewer().expandToLevel(new NamesPage.GroupNode(EditorModel.ADDED), 1);
        page.viewer().setSelection(new StructuredSelection(model.name("foreground").orElseThrow()));
        settle();
        before = started[0];
        clickName(page, "badgeBackground");
        assertThat(started[0]).as("the selecting click").isEqualTo(before);
        try {
            Thread.sleep(display.getDoubleClickTime() + 150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        clickName(page, "badgeBackground");
        assertThat(started[0]).as("the second, slow click").isEqualTo(before + 1);
        page.viewer().cancelEditing();

        click(button(page, "Remove"));
        assertThat(model.name("badgeBackground")).isEmpty();
        click(button(page, "Undo Remove badgeBackground"));
        assertThat(model.name("badgeBackground")).isPresent();
    }

    @Test
    void aColorAndAFontPerNameWithContrast() {
        dialog.showPage(wizard.colorsPage());
        settle();
        ColorsPage page = wizard.colorsPage();
        page.select("accent");
        settle();

        page.swatchChips().get(4).choose();
        settle();
        String swatch = model.palette().swatches().get(4).name();
        assertThat(model.colorChoice("accent")).contains(ColorChoice.ref(swatch));
        assertThat(model.theme().names().get("accent").getAsJsonObject().get("color").getAsString()).isEqualTo("$" + swatch);
        assertThat(page.swatchChips().get(4).isSelected()).isTrue();
        assertThat(rows(page)).anySatisfy(row -> assertThat(row).startsWith("onAccent accent"));

        page.tintChips().get(1).choose();
        settle();
        assertThat(model.colorChoice("accent")).contains(ColorChoice.of(page.tintChips().get(1).getRGB()));

        page.select("heading1");
        settle();
        page.families().setText("Georgia, DejaVu Serif");
        page.families().notifyListeners(SWT.DefaultSelection, new Event());
        page.height().setSelection(18);
        page.height().notifyListeners(SWT.Selection, new Event());
        settle();
        assertThat(model.fontChoice("heading1").orElseThrow().families()).containsExactly("Georgia", "DejaVu Serif");
        assertThat(model.font("heading1").orElseThrow().height()).isEqualTo(18);

        click(button(page, "Use the base theme’s"));
        assertThat(model.fontChoice("heading1")).isEmpty();
        assertThat(page.preview().shown()).isSameAs(model.theme());
    }

    @Test
    void finishSavesTheFullThemeWhereTheFinishPageSaysAndCancelSavesNothing() throws Exception {
        model.addName("badgeBackground", EditorModel.COLOR);
        dialog.showPage(wizard.finishPage());
        settle();
        FinishPage page = wizard.finishPage();
        assertThat(page.idField().getText()).isEqualTo("untitled");
        assertThat(page.fileField().getText()).endsWith("untitled.json");

        type(page.idField(), "ocean-dark");
        assertThat(model.id()).isEqualTo("ocean-dark");
        assertThat(page.fileField().getText()).as("the file follows the id").endsWith("ocean-dark.json");
        type(page.idField(), "no spaces");
        assertThat(page.getErrorMessage()).contains("only letters");
        assertThat(dialogButton("Finish").isEnabled()).isFalse();
        type(page.idField(), "ocean-dark");
        Path file = palettes.resolve("themes").resolve("ocean.json");
        type(page.fileField(), file.toString());
        assertThat(page.checks().getItemCount()).as("no problems").isZero();

        click(dialogButton("Finish"));

        assertThat(wizard.result()).isNotNull();
        assertThat(wizard.savedFile()).isEqualTo(file);
        Theme saved = new ThemeRegistry().loadFile(file);
        assertThat(saved.id()).isEqualTo("ocean-dark");
        assertThat(saved.parent()).isNull();
        assertThat(saved.names()).containsKey("badgeBackground");
        assertThat(saved.controls()).isEqualTo(wizard.result().controls());

        ThemeWizard other = new ThemeWizard(new EditorModel(bundled, 4), new PaletteStore(palettes));
        WizardDialog second = ThemeWizard.dialog(parent, other);
        second.setBlockOnOpen(false);
        second.open();
        settle();
        dialog = second;
        click(dialogButton("Cancel"));
        assertThat(other.result()).isNull();
    }

    @Test
    void anErrorKeepsFinishOffAndEditingASavedThemeSavesItBack() throws Exception {
        assertThatThrownBy(() -> model.setRuleValue(EditorModel.RuleKey.of("Table"), "headerBackground",
                new com.google.gson.JsonPrimitive("$nothing"))).hasMessageContaining("not a swatch");
        // A broken theme file can still be opened: its error shows, and Finish stays off.
        model.open(EditorModelTest.broken(bundled.get(1)), null);
        settle();
        dialog.showPage(wizard.finishPage());
        settle();
        assertThat(rowsOf(wizard.finishPage().checks())).anyMatch(r -> r.startsWith("Error") && r.contains("nothing"));
        assertThat(dialogButton("Finish").isEnabled()).isFalse();
        dialog.close();

        Path file = palettes.resolve("mine.json");
        EditorModel mine = new EditorModel(bundled, 5);
        mine.setIdentity("mine", "Mine", true);
        mine.save(file);
        wizard = ThemeWizard.edit(bundled, file);
        assertThat(wizard.model().history(Page.PALETTE).canUndo()).as("opening is not an edit").isFalse();
        dialog = ThemeWizard.dialog(parent, wizard);
        dialog.setBlockOnOpen(false);
        dialog.open();
        settle();
        assertThat(wizard.getWindowTitle()).isEqualTo("Edit Theme");
        dialog.showPage(wizard.finishPage());
        settle();
        assertThat(wizard.finishPage().fileField().getText()).isEqualTo(file.toString());
        click(dialogButton("Finish"));
        assertThat(java.nio.file.Files.readString(file)).isEqualTo(mine.toJson());
    }

    @Test
    void theControlsPageSetsANameOrAnOverridePerRule() {
        dialog.showPage(wizard.controlsPage());
        settle();
        ControlsPage page = wizard.controlsPage();
        page.select("Table");
        settle();
        var table = page.properties().getTable();
        assertThat(rowsOf(table)).anyMatch(r -> r.startsWith("headerBackground"));

        // A common name, chosen in place.
        int row = rowIndex(table, "headerBackground");
        page.properties().editElement(page.properties().getElementAt(row), 2);
        org.eclipse.swt.custom.CCombo combo = find(table, org.eclipse.swt.custom.CCombo.class, c -> c.isVisible());
        int accent = java.util.Arrays.asList(combo.getItems()).indexOf("accent");
        combo.select(accent);
        combo.notifyListeners(SWT.Selection, new Event());
        combo.notifyListeners(SWT.DefaultSelection, new Event());
        settle();
        assertThat(model.ruleValue(EditorModel.RuleKey.of("Table"), "headerBackground")).contains(new com.google.gson.JsonPrimitive("$accent"));
        assertThat(table.getItem(rowIndex(table, "headerBackground")).getText(0)).startsWith("• ");

        // An override, typed in place.
        int height = rowIndex(table, "rowHeight");
        if (height >= 0) {
            page.properties().editElement(page.properties().getElementAt(height), 3);
            Text text = find(table, Text.class, t -> t.isVisible());
            text.setText("30");
            text.notifyListeners(SWT.DefaultSelection, new Event());
            settle();
            assertThat(model.theme().controls().get("Table").get("rowHeight").getAsInt()).isEqualTo(30);
        }

        // Reset takes the change back.
        page.properties().setSelection(new StructuredSelection(page.properties().getElementAt(rowIndex(table, "headerBackground"))));
        settle();
        click(button(page, "Reset"));
        assertThat(model.isEdited(EditorModel.RuleKey.of("Table"), "headerBackground")).isFalse();
        assertThat(page.preview().shown()).isSameAs(model.theme());
    }

    @Test
    void theCommonNamesAreSortedAndAPickAppliesAtOnce() {
        dialog.showPage(wizard.controlsPage());
        settle();
        ControlsPage page = wizard.controlsPage();
        page.select("Button");
        settle();
        var table = page.properties().getTable();
        int row = rowIndex(table, "background");
        org.eclipse.swt.graphics.Image before = table.getItem(row).getImage(2);
        page.properties().editElement(page.properties().getElementAt(row), 2);
        org.eclipse.swt.custom.CCombo combo = find(table, org.eclipse.swt.custom.CCombo.class, c -> c.isVisible());

        // The default map's name first, then the others in alphabetical order, ignoring case.
        List<String> items = List.of(combo.getItems());
        assertThat(items.getFirst()).endsWith(" (default)");
        assertThat(items.subList(1, items.size())).isSortedAccordingTo(String.CASE_INSENSITIVE_ORDER).contains("foreground");

        // An arrow key moves the selection without applying it.
        int current = combo.getSelectionIndex();
        int foreground = items.indexOf("foreground");
        combo.notifyListeners(SWT.KeyDown, new Event());
        combo.select(foreground);
        combo.notifyListeners(SWT.Selection, new Event());
        settle();
        assertThat(model.ruleValue(EditorModel.RuleKey.of("Button"), "background")).isEmpty();
        assertThat(page.properties().isCellEditorActive()).isTrue();
        combo.select(current);

        // A pick from the list applies at once: no Enter, no focus change.
        combo.select(foreground);
        combo.notifyListeners(SWT.Selection, new Event());
        settle();
        assertThat(model.ruleValue(EditorModel.RuleKey.of("Button"), "background")).contains(new com.google.gson.JsonPrimitive("$foreground"));
        assertThat(page.properties().isCellEditorActive()).isFalse();
        row = rowIndex(table, "background");
        assertThat(table.getItem(row).getText(0)).isEqualTo("• background");
        assertThat(table.getItem(row).getText(2)).isEqualTo("foreground");
        assertThat(table.getItem(row).getImage(2)).isNotSameAs(before);
        assertThat(model.history(Page.CONTROLS).undoLabel()).isPresent();
        assertThat(page.preview().shown()).isSameAs(model.theme());

        // One edit, so one undo takes it back.
        assertThat(model.undo(Page.CONTROLS)).isTrue();
        assertThat(model.isEdited(EditorModel.RuleKey.of("Button"), "background")).isFalse();
        assertThat(model.history(Page.CONTROLS).canUndo()).isFalse();
    }

    @Test
    void theControlsPageMarksTextThatIsHardToRead() {
        dialog.showPage(wizard.controlsPage());
        settle();
        ControlsPage page = wizard.controlsPage();
        page.select("Text");
        settle();
        assertThat(page.getMessage()).isNull();
        var tree = page.types();
        var typeNode = (ControlsPage.TypeNode) tree.getStructuredSelection().getFirstElement();
        assertThat(((org.eclipse.swt.widgets.TreeItem) tree.testFindItem(typeNode)).getImage()).isNull();

        // Text on its own color: the page says so, and the tree and both properties are marked.
        model.setRuleValue(EditorModel.RuleKey.of("Text"), "background", new com.google.gson.JsonPrimitive("$foreground"));
        settle();
        assertThat(page.getMessage()).startsWith("Hard to read: Text: foreground #").contains("is 1.0:1, below 4.5:1");
        assertThat(page.getMessageType()).isEqualTo(org.eclipse.jface.dialogs.IMessageProvider.WARNING);
        assertThat(page.problemsOf(typeNode)).hasSize(1);
        assertThat(((org.eclipse.swt.widgets.TreeItem) tree.testFindItem(typeNode)).getImage()).isNotNull();
        var library = new ControlsPage.LibraryNode(typeNode.spec().group());
        assertThat(((org.eclipse.swt.widgets.TreeItem) tree.testFindItem(library)).getImage()).isNotNull();
        var table = page.properties().getTable();
        assertThat(table.getItem(rowIndex(table, "background")).getImage(0)).isNotNull();
        assertThat(table.getItem(rowIndex(table, "foreground")).getImage(0)).isNotNull();
        assertThat(table.getItem(rowIndex(table, "font")).getImage(0)).isNull();

        // A problem in a state marks its type, and the type's message points at it.
        model.setRuleValue(EditorModel.RuleKey.of("Text"), "background", null);
        model.setRuleValue(EditorModel.RuleKey.of("Tree").withState("selected"), "background", new com.google.gson.JsonPrimitive("$foreground"));
        settle();
        assertThat(page.getMessage()).isNull();
        page.select("Tree");
        settle();
        assertThat(page.getMessage()).contains("Tree's variants, classes or states");
        assertThat(((org.eclipse.swt.widgets.TreeItem) tree.testFindItem(tree.getStructuredSelection().getFirstElement())).getImage()).isNotNull();
    }

    @Test
    void clickingAControlInThePreviewSelectsItsType() {
        dialog.showPage(wizard.controlsPage());
        settle();
        ControlsPage page = wizard.controlsPage();
        org.eclipse.swt.widgets.Tree tree = find(page.preview(), org.eclipse.swt.widgets.Tree.class, t -> true);
        Event down = new Event();
        down.button = 1;
        tree.notifyListeners(SWT.MouseDown, down);
        settle();
        assertThat(page.rule()).contains(EditorModel.RuleKey.of("Tree"));
    }

    private void type(Text field, String text) {
        field.setText(text);
        field.notifyListeners(SWT.Modify, new Event());
        settle();
    }

    private static int rowIndex(org.eclipse.swt.widgets.Table table, String property) {
        for (int i = 0; i < table.getItemCount(); i++) {
            String text = table.getItem(i).getText(0);
            if (text.equals(property) || text.equals("• " + property)) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> rowsOf(org.eclipse.swt.widgets.Table table) {
        List<String> rows = new ArrayList<>();
        for (var item : table.getItems()) {
            rows.add(item.getText(0) + " " + item.getText(1));
        }
        return rows;
    }

    @Test
    void undoKeysUndoThePageShowingOnly() {
        click(button(wizard.palettePage(), "Regenerate"));
        model.setColor("background", ColorChoice.of(new RGB(1, 2, 3)));
        var palette = model.palette();

        dialog.showPage(wizard.namesPage());
        settle();
        wizard.namesPage().viewer().getTree().setFocus();
        key(wizard.namesPage().viewer().getTree(), 'z');
        assertThat(model.palette()).as("the names page has nothing to undo").isEqualTo(palette);

        dialog.showPage(wizard.colorsPage());
        settle();
        Control focus = wizard.colorsPage().contrastTable();
        focus.setFocus();
        key(focus, 'z');
        assertThat(model.colorChoice("background")).isEmpty();
        key(focus, 'y');
        assertThat(model.color("background")).contains(new RGB(1, 2, 3));
        assertThat(model.palette()).isEqualTo(palette);
    }

    private void key(Control control, char key) {
        Event event = new Event();
        event.type = SWT.KeyDown;
        event.keyCode = key;
        event.character = (char) (key - 'a' + 1);
        event.stateMask = SWT.MOD1;
        // Through the widget, so that the display's filters see it, as they see a real key.
        control.notifyListeners(SWT.KeyDown, event);
        settle();
    }

    /** A left click on {@code name}'s cell, as the tree reports one: it selects the row, then the viewer hears it. */
    private void clickName(NamesPage page, String name) {
        org.eclipse.swt.widgets.Tree tree = page.viewer().getTree();
        org.eclipse.swt.widgets.TreeItem item = find(tree, name);
        tree.showItem(item);
        settle();
        org.eclipse.swt.graphics.Rectangle bounds = item.getBounds(0);
        assertThat(tree.getClientArea().contains(bounds.x + 4, bounds.y + bounds.height / 2)).as(name + " is in view").isTrue();
        if (tree.getSelectionCount() != 1 || tree.getSelection()[0] != item) {
            tree.setSelection(item);
            Event selection = new Event();
            selection.item = item;
            tree.notifyListeners(SWT.Selection, selection);
        }
        Event down = new Event();
        down.button = 1;
        down.x = bounds.x + 4;
        down.y = bounds.y + bounds.height / 2;
        down.count = 1;
        tree.notifyListeners(SWT.MouseDown, down);
        Event up = new Event();
        up.button = 1;
        up.x = down.x;
        up.y = down.y;
        up.count = 1;
        tree.notifyListeners(SWT.MouseUp, up);
        settle();
    }

    private static org.eclipse.swt.widgets.TreeItem find(org.eclipse.swt.widgets.Tree tree, String text) {
        for (org.eclipse.swt.widgets.TreeItem group : tree.getItems()) {
            for (org.eclipse.swt.widgets.TreeItem item : group.getItems()) {
                if (item.getText(0).equals(text)) {
                    return item;
                }
            }
        }
        throw new AssertionError("No row " + text);
    }

    private List<String> rows(ColorsPage page) {
        List<String> rows = new ArrayList<>();
        for (var item : page.contrastTable().getItems()) {
            rows.add(item.getText(0) + " " + item.getText(1) + " " + item.getText(2));
        }
        return rows;
    }

    private Button button(EditorPage page, String text) {
        return find(page.getControl(), Button.class, b -> b.getText().equals(text));
    }

    private Button dialogButton(String text) {
        return find(dialog.getShell(), Button.class, b -> b.getText().replace("&", "").equals(text));
    }

    private void click(Button button) {
        assertThat(button.isEnabled()).as(button.getText() + " enabled").isTrue();
        if ((button.getStyle() & (SWT.CHECK | SWT.RADIO)) != 0) {
            button.setSelection(!button.getSelection());
        }
        button.notifyListeners(SWT.Selection, new Event());
        settle();
    }

    private static <T extends Control> T find(Control root, Class<T> type, Predicate<T> test) {
        List<T> found = new ArrayList<>();
        collect(root, type, test, found);
        assertThat(found).as(type.getSimpleName()).isNotEmpty();
        return found.getFirst();
    }

    private static <T extends Control> void collect(Control control, Class<T> type, Predicate<T> test, List<T> found) {
        if (type.isInstance(control) && test.test(type.cast(control))) {
            found.add(type.cast(control));
        }
        if (control instanceof org.eclipse.swt.widgets.Composite composite) {
            for (Control child : composite.getChildren()) {
                collect(child, type, test, found);
            }
        }
    }

    private static void settle() {
        for (int i = 0; i < 5; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
        }
    }
}
