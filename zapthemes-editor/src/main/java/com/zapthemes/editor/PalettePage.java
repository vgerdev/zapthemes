package com.zapthemes.editor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.FileDialog;

import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.editor.EditorModel.Page;
import com.zapthemes.editor.palette.Harmony;
import com.zapthemes.editor.palette.Hsl;
import com.zapthemes.editor.palette.ImagePalette;
import com.zapthemes.editor.palette.Palette;
import com.zapthemes.editor.palette.PaletteStore;
import com.zapthemes.editor.palette.Swatch;
import com.zapthemes.editor.palette.TintsAndShades;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Group;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Scale;
import com.zapthemes.swt.widgets.Spinner;

/**
 * Page 1: the palette. Duplicate a theme, use a saved palette or a theme's swatches, or generate
 * one: a harmony rule from a base color, 5 to 8 swatches, light or dark, Regenerate for the
 * unlocked ones, or colors from an image. A selected swatch can be changed by hue, saturation and
 * lightness, or set to one of its tints and shades.
 */
final class PalettePage extends EditorPage {

    static final int TINTS = 9;

    private final PaletteStore store;
    private Combo duplicateFrom;
    private Combo paletteFrom;
    private Button deleteSaved;
    private Combo rule;
    private ColorChip baseChip;
    private Spinner size;
    private Button dark;
    private Button light;
    private Composite strip;
    private final List<ColorChip> chips = new ArrayList<>();
    private final List<Label> hexes = new ArrayList<>();
    private final List<Button> locks = new ArrayList<>();
    private Group selectedGroup;
    private Scale hue;
    private Scale saturation;
    private Scale lightness;
    private Label hueValue;
    private Label saturationValue;
    private Label lightnessValue;
    private Composite tints;
    private final List<ColorChip> tintChips = new ArrayList<>();
    private String selected;
    /** The slider values for the selected swatch, kept while its color is the one they give. */
    private Hsl sliders;
    /** Choices of {@link #paletteFrom}: a saved palette's name, or a theme. */
    private final List<Object> paletteChoices = new ArrayList<>();

    PalettePage(EditorModel model, PaletteStore store) {
        super("palette", "Palette", "Duplicate a theme, use a palette, or generate one. Lock the swatches you like "
                + "and regenerate the rest.", model, Page.PALETTE);
        this.store = store;
    }

    @Override
    protected void createContent(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Group start = group(parent, "Start from", 4);
        new Label(start, SWT.NONE).setText("Duplicate a theme:");
        duplicateFrom = new Combo(start, SWT.READ_ONLY);
        model.themes().forEach(t -> duplicateFrom.add(t.name()));
        duplicateFrom.select(0);
        duplicateFrom.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Button duplicate = button(start, "Duplicate", e -> tryEdit(() -> model.duplicate(model.themes().get(duplicateFrom.getSelectionIndex()))));
        duplicate.setToolTipText("Starts from the theme: its palette, its names and their colors, and every rule it has");
        button(start, "Open theme file…", e -> openThemeFile());

        new Label(start, SWT.NONE).setText("Use a palette:");
        paletteFrom = new Combo(start, SWT.READ_ONLY);
        paletteFrom.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        paletteFrom.addListener(SWT.Selection, e -> updateDeleteSaved());
        button(start, "Use", e -> useChosenPalette());
        deleteSaved = button(start, "Delete", e -> deleteChosenPalette());
        fillPaletteChoices();

        Group generate = group(parent, "Generate", 8);
        new Label(generate, SWT.NONE).setText("Rule:");
        rule = new Combo(generate, SWT.READ_ONLY);
        for (Harmony harmony : Harmony.values()) {
            rule.add(capitalized(harmony.id()));
        }
        rule.addListener(SWT.Selection, e -> model.setRule(Harmony.values()[rule.getSelectionIndex()]));
        new Label(generate, SWT.NONE).setText("Base:");
        baseChip = new ColorChip(generate, 28, 22);
        baseChip.setToolTipText("The base color the rule's hues start from; click to pick another");
        baseChip.onChoose(chip -> pick(chip.getRGB()).ifPresent(model::setBaseColor));
        new Label(generate, SWT.NONE).setText("Swatches:");
        size = new Spinner(generate, SWT.BORDER);
        size.setValues(6, Palette.MIN_SIZE, Palette.MAX_SIZE, 0, 1, 1);
        size.addListener(SWT.Selection, e -> {
            if (size.getSelection() != model.palette().swatches().size()) {
                model.setSize(size.getSelection());
            }
        });
        dark = new Button(generate, SWT.RADIO);
        dark.setText("Dark");
        dark.addListener(SWT.Selection, e -> {
            if (dark.getSelection()) {
                model.setDark(true);
            }
        });
        light = new Button(generate, SWT.RADIO);
        light.setText("Light");
        light.addListener(SWT.Selection, e -> {
            if (light.getSelection()) {
                model.setDark(false);
            }
        });
        Composite actions = new Composite(generate, SWT.NONE);
        actions.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 8, 1));
        GridLayout actionLayout = new GridLayout(4, false);
        actionLayout.marginWidth = 0;
        actions.setLayout(actionLayout);
        Button regenerate = button(actions, "Regenerate", e -> model.regenerate());
        regenerate.setToolTipText("Rolls every unlocked swatch again");
        button(actions, "From image…", e -> fromImage());
        button(actions, "Save palette…", e -> savePalette());

        strip = new Composite(parent, SWT.NONE);
        strip.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        selectedGroup = group(parent, "Swatch", 3);
        hue = slider(selectedGroup, "Hue", 360);
        hueValue = valueLabel(selectedGroup);
        saturation = slider(selectedGroup, "Saturation", 100);
        saturationValue = valueLabel(selectedGroup);
        lightness = slider(selectedGroup, "Lightness", 100);
        lightnessValue = valueLabel(selectedGroup);
        hue.addListener(SWT.Selection, e -> slid());
        saturation.addListener(SWT.Selection, e -> slid());
        lightness.addListener(SWT.Selection, e -> slid());
        Label tintsLabel = new Label(selectedGroup, SWT.NONE);
        tintsLabel.setText("Tints and shades:");
        tints = ColorChip.row(selectedGroup);
        tints.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        for (int i = 0; i < TINTS; i++) {
            ColorChip chip = new ColorChip(tints, 30, 24);
            chip.onChoose(c -> {
                if (selected != null) {
                    model.setSwatchColor(selected, c.getRGB());
                }
            });
            tintChips.add(chip);
        }
    }

    @Override
    protected void refresh() {
        Palette palette = model.palette();
        rule.select(palette.rule() == null ? -1 : palette.rule().ordinal());
        baseChip.setRGB(palette.base() != null ? palette.base() : palette.swatches().getFirst().color());
        size.setSelection(Math.clamp(palette.swatches().size(), Palette.MIN_SIZE, Palette.MAX_SIZE));
        dark.setSelection(palette.dark());
        light.setSelection(!palette.dark());
        rebuildStrip(palette);
        if (selected == null || palette.swatch(selected).isEmpty()) {
            selected = palette.swatches().getFirst().name();
            sliders = null;
        }
        for (int i = 0; i < chips.size(); i++) {
            Swatch swatch = palette.swatches().get(i);
            chips.get(i).setRGB(swatch.color());
            chips.get(i).setCaption(swatch.name());
            chips.get(i).setLocked(swatch.locked());
            chips.get(i).setSelected(swatch.name().equals(selected));
            hexes.get(i).setText(swatch.hex());
            locks.get(i).setSelection(swatch.locked());
        }
        refreshSelected(palette.swatch(selected).orElseThrow());
        strip.getParent().layout(true, true);
    }

    String selectedSwatch() {
        return selected;
    }

    void select(String swatch) {
        selected = swatch;
        sliders = null;
        refresh();
    }

    List<ColorChip> chips() {
        return chips;
    }

    List<ColorChip> tintChips() {
        return tintChips;
    }

    private void refreshSelected(Swatch swatch) {
        selectedGroup.setText("Swatch “" + swatch.name() + "”");
        if (sliders == null || !sliders.toRgb().equals(swatch.color())) {
            sliders = Hsl.of(swatch.color());
        }
        hue.setSelection((int) Math.round(sliders.h()) % 360);
        saturation.setSelection((int) Math.round(sliders.s() * 100));
        lightness.setSelection((int) Math.round(sliders.l() * 100));
        hueValue.setText(hue.getSelection() + "°");
        saturationValue.setText(saturation.getSelection() + "%");
        lightnessValue.setText(lightness.getSelection() + "%");
        List<RGB> row = TintsAndShades.of(swatch.color(), TINTS);
        for (int i = 0; i < TINTS; i++) {
            tintChips.get(i).setRGB(row.get(i));
            tintChips.get(i).setSelected(row.get(i).equals(swatch.color()));
        }
    }

    private void slid() {
        if (selected == null) {
            return;
        }
        sliders = new Hsl(hue.getSelection(), saturation.getSelection() / 100.0, lightness.getSelection() / 100.0);
        hueValue.setText(hue.getSelection() + "°");
        saturationValue.setText(saturation.getSelection() + "%");
        lightnessValue.setText(lightness.getSelection() + "%");
        model.setSwatchColor(selected, sliders.toRgb());
    }

    /** One cell per swatch: the chip (click to select), its hex value, and Lock. */
    private void rebuildStrip(Palette palette) {
        if (chips.size() == palette.swatches().size()) {
            return;
        }
        for (org.eclipse.swt.widgets.Control child : strip.getChildren()) {
            child.dispose();
        }
        chips.clear();
        hexes.clear();
        locks.clear();
        GridLayout layout = new GridLayout(palette.swatches().size(), true);
        layout.marginWidth = 0;
        strip.setLayout(layout);
        for (int i = 0; i < palette.swatches().size(); i++) {
            Composite cell = new Composite(strip, SWT.NONE);
            GridLayout cellLayout = new GridLayout(1, false);
            cellLayout.marginWidth = 0;
            cellLayout.marginHeight = 0;
            cell.setLayout(cellLayout);
            cell.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            ColorChip chip = new ColorChip(cell, 76, 56);
            chip.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            int index = i;
            chip.onChoose(c -> select(model.palette().swatches().get(index).name()));
            chips.add(chip);
            Label hex = new Label(cell, SWT.NONE);
            hex.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            hexes.add(hex);
            Button lock = new Button(cell, SWT.CHECK);
            lock.setText("Lock");
            lock.addListener(SWT.Selection, e -> model.setLocked(model.palette().swatches().get(index).name(), lock.getSelection()));
            locks.add(lock);
        }
    }

    private void fillPaletteChoices() {
        paletteFrom.removeAll();
        paletteChoices.clear();
        for (String saved : store.list()) {
            paletteFrom.add(saved + " (saved)");
            paletteChoices.add(saved);
        }
        for (Theme theme : model.themes()) {
            paletteFrom.add(theme.name() + "’s colors");
            paletteChoices.add(theme);
        }
        paletteFrom.select(0);
        updateDeleteSaved();
    }

    private void updateDeleteSaved() {
        int index = paletteFrom.getSelectionIndex();
        deleteSaved.setEnabled(index >= 0 && paletteChoices.get(index) instanceof String);
    }

    private void useChosenPalette() {
        int index = paletteFrom.getSelectionIndex();
        if (index < 0) {
            return;
        }
        Object choice = paletteChoices.get(index);
        tryEdit(() -> {
            if (choice instanceof String saved) {
                model.usePalette("Use " + saved, store.load(saved));
            } else {
                Theme theme = (Theme) choice;
                model.usePalette("Use " + theme.name() + "’s colors", Palette.fromTheme(theme));
            }
        });
    }

    private void deleteChosenPalette() {
        int index = paletteFrom.getSelectionIndex();
        if (index >= 0 && paletteChoices.get(index) instanceof String saved) {
            tryEdit(() -> store.delete(saved));
            fillPaletteChoices();
        }
    }

    private void savePalette() {
        InputDialog dialog = new InputDialog(getShell(), "Save palette", "Save the palette as:", "", text -> {
            if (text.isBlank()) {
                return "Give the palette a name.";
            }
            return text.matches("[\\p{L}\\p{N} _.-]+") ? null : "Use letters, digits, spaces, dots, dashes and underscores.";
        });
        if (dialog.open() == Window.OK) {
            String name = dialog.getValue().strip();
            if (tryEdit(() -> store.save(name, model.palette()))) {
                fillPaletteChoices();
                paletteFrom.select(paletteChoices.indexOf(name));
                updateDeleteSaved();
                setMessage("Saved the palette “" + name + "” in " + store.directory() + ".");
            }
        }
    }

    private void openThemeFile() {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        dialog.setFilterExtensions(new String[] { "*.json", "*.*" });
        dialog.setFilterNames(new String[] { "Themes (*.json)", "All files" });
        String file = dialog.open();
        if (file == null) {
            return;
        }
        tryEdit(() -> {
            ThemeRegistry registry = new ThemeRegistry();
            model.themes().forEach(registry::register);
            Theme loaded = registry.loadFile(Path.of(file));
            model.duplicate(registry.resolve(loaded.id()));
        });
    }

    private void fromImage() {
        FileDialog dialog = new FileDialog(getShell(), SWT.OPEN);
        dialog.setFilterExtensions(new String[] { "*.png;*.jpg;*.jpeg;*.gif;*.bmp", "*.*" });
        dialog.setFilterNames(new String[] { "Images", "All files" });
        String file = dialog.open();
        if (file != null) {
            tryEdit(() -> useImage(new ImageData(file), Path.of(file).getFileName().toString()));
        }
    }

    /** The image's dominant colors as the palette, as many as the palette has swatches. */
    void useImage(ImageData image, String name) {
        List<RGB> colors = ImagePalette.extract(image, size.getSelection(), Objects.hash(name));
        if (colors.isEmpty()) {
            throw new IllegalArgumentException("The image has no opaque pixels.");
        }
        model.usePalette("Colors of " + name, Palette.of(model.palette().dark(), colors));
    }

    private java.util.Optional<RGB> pick(RGB initial) {
        ColorDialog dialog = new ColorDialog(getShell());
        dialog.setRGB(initial);
        return java.util.Optional.ofNullable(dialog.open());
    }

    private static Group group(org.eclipse.swt.widgets.Composite parent, String title, int columns) {
        Group group = new Group(parent, SWT.NONE);
        group.setText(title);
        group.setLayout(new GridLayout(columns, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return group;
    }

    private static Button button(org.eclipse.swt.widgets.Composite parent, String text, org.eclipse.swt.widgets.Listener listener) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, listener);
        return button;
    }

    private static Scale slider(org.eclipse.swt.widgets.Composite parent, String text, int max) {
        new Label(parent, SWT.NONE).setText(text + ":");
        Scale scale = new Scale(parent, SWT.HORIZONTAL);
        scale.setMaximum(max);
        scale.setPageIncrement(max / 10);
        scale.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return scale;
    }

    private static Label valueLabel(org.eclipse.swt.widgets.Composite parent) {
        Label label = new Label(parent, SWT.NONE);
        GridData data = new GridData(SWT.END, SWT.CENTER, false, false);
        data.widthHint = 40;
        label.setLayoutData(data);
        return label;
    }

    static String capitalized(String id) {
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }
}
