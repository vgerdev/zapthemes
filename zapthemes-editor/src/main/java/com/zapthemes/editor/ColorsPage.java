package com.zapthemes.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.FontDialog;

import com.zapthemes.core.model.FontSpec;
import com.zapthemes.editor.EditorModel.NameInfo;
import com.zapthemes.editor.EditorModel.Page;
import com.zapthemes.editor.palette.ContrastCheck;
import com.zapthemes.editor.palette.Oklch;
import com.zapthemes.editor.palette.Palette;
import com.zapthemes.editor.palette.Swatch;
import com.zapthemes.editor.palette.TintsAndShades;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Group;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Spinner;
import com.zapthemes.swt.widgets.Table;
import com.zapthemes.swt.widgets.TableColumn;
import com.zapthemes.swt.widgets.TableItem;
import com.zapthemes.swt.widgets.Text;

/**
 * Page 3: a color and a font for each name. A color is a swatch (written as a reference, so it
 * follows the palette), one of a swatch's tints and shades, or a custom color; a font is a list of
 * families (the first one installed wins), a size and a style, optionally starting from another
 * name's font. Every contrast pair the name is part of shows its WCAG ratio and level.
 */
final class ColorsPage extends EditorPage {

    private static final String NOTHING = "(nothing)";

    private TableViewer viewer;
    private final ChipImages images = new ChipImages();
    private Group colorGroup;
    private Label colorSource;
    private Composite swatchRow;
    private final List<ColorChip> swatchChips = new ArrayList<>();
    private Label tintsLabel;
    private final List<ColorChip> tintChips = new ArrayList<>();
    private Group fontGroup;
    private Label fontSource;
    private Text families;
    private Spinner height;
    private Button bold;
    private Button italic;
    private Combo inherit;
    private Table contrast;
    private String selected = "foreground";
    /** The swatch whose tints and shades are shown. */
    private String tintsOf;
    private boolean updating;

    ColorsPage(EditorModel model) {
        super("values", "Colors and fonts", "A color and a font for each name. Text should reach 4.5:1 on the colors it "
                + "is drawn on (3:1 for disabled text).", model, Page.VALUES);
    }

    @Override
    protected void createContent(Composite parent) {
        parent.setLayout(new GridLayout(2, false));
        viewer = new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL);
        viewer.getTable().setHeaderVisible(true);
        viewer.getTable().setLinesVisible(true);
        GridData listData = new GridData(SWT.FILL, SWT.FILL, false, true);
        listData.widthHint = 300;
        viewer.getTable().setLayoutData(listData);
        viewer.setContentProvider(ArrayContentProvider.getInstance());
        viewer.getTable().addDisposeListener(e -> images.dispose());
        column("Name", 112, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((NameInfo) element).name();
            }
        });
        column("Color", 84, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return model.color(((NameInfo) element).name()).map(EditorModel::hex).orElse("");
            }

            @Override
            public Image getImage(Object element) {
                return model.color(((NameInfo) element).name()).map(images::of).orElse(null);
            }
        });
        column("Contrast", 90, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return worst(((NameInfo) element).name()).map(ColorsPage::badge).orElse("");
            }
        });
        viewer.addSelectionChangedListener(e -> {
            if (!updating && viewer.getStructuredSelection().getFirstElement() instanceof NameInfo info) {
                selected = info.name();
                tintsOf = null;
                refreshEditor();
            }
        });

        Composite editor = new Composite(parent, SWT.NONE);
        editor.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout editorLayout = new GridLayout(1, false);
        editorLayout.marginWidth = 0;
        editorLayout.marginHeight = 0;
        editor.setLayout(editorLayout);

        colorGroup = new Group(editor, SWT.NONE);
        colorGroup.setLayout(new GridLayout(1, false));
        colorGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        colorSource = new Label(colorGroup, SWT.WRAP);
        colorSource.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(colorGroup, SWT.NONE).setText("Swatches:");
        swatchRow = ColorChip.row(colorGroup);
        swatchRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        tintsLabel = new Label(colorGroup, SWT.NONE);
        tintsLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        Composite tintRow = ColorChip.row(colorGroup);
        tintRow.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        for (int i = 0; i < PalettePage.TINTS; i++) {
            ColorChip chip = new ColorChip(tintRow, 24, 20);
            chip.onChoose(c -> tryEdit(() -> model.setColor(selected, ColorChoice.of(c.getRGB()))));
            tintChips.add(chip);
        }
        Composite colorButtons = new Composite(colorGroup, SWT.NONE);
        GridLayout colorButtonsLayout = new GridLayout(2, false);
        colorButtonsLayout.marginWidth = 0;
        colorButtons.setLayout(colorButtonsLayout);
        button(colorButtons, "Custom…", e -> customColor());
        button(colorButtons, "Fill from palette", e -> tryEdit(() -> model.setColor(selected, null)))
                .setToolTipText("Forgets this name's color, so that it follows the palette again");

        fontGroup = new Group(editor, SWT.NONE);
        fontGroup.setLayout(new GridLayout(4, false));
        fontGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        fontSource = new Label(fontGroup, SWT.WRAP);
        fontSource.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
        new Label(fontGroup, SWT.NONE).setText("Families:");
        families = new Text(fontGroup, SWT.BORDER);
        families.setToolTipText("Font families in order of preference, separated by commas; the first one installed is used");
        families.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        families.addListener(SWT.DefaultSelection, e -> fontEdited());
        families.addListener(SWT.FocusOut, e -> fontEdited());
        new Label(fontGroup, SWT.NONE).setText("Size:");
        height = new Spinner(fontGroup, SWT.BORDER);
        height.setValues(0, 0, 96, 0, 1, 4);
        height.setToolTipText("Points; 0 takes the inherited size");
        height.addListener(SWT.Selection, e -> fontEdited());
        bold = new Button(fontGroup, SWT.CHECK);
        bold.setText("Bold");
        bold.addListener(SWT.Selection, e -> fontEdited());
        italic = new Button(fontGroup, SWT.CHECK);
        italic.setText("Italic");
        italic.addListener(SWT.Selection, e -> fontEdited());
        new Label(fontGroup, SWT.NONE).setText("Starts from:");
        inherit = new Combo(fontGroup, SWT.READ_ONLY);
        inherit.setToolTipText("Another name's font, which this one changes only in what is set here");
        inherit.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
        inherit.addListener(SWT.Selection, e -> fontEdited());
        Composite fontButtons = new Composite(fontGroup, SWT.NONE);
        fontButtons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
        GridLayout fontButtonsLayout = new GridLayout(2, false);
        fontButtonsLayout.marginWidth = 0;
        fontButtons.setLayout(fontButtonsLayout);
        button(fontButtons, "Choose…", e -> chooseFont());
        button(fontButtons, "Use the base theme’s", e -> tryEdit(() -> model.setFont(selected, null)))
                .setToolTipText("Forgets this name's font, so that it is the base theme's again");

        Group contrastGroup = new Group(editor, SWT.NONE);
        contrastGroup.setText("Contrast");
        contrastGroup.setLayout(new GridLayout(1, false));
        contrastGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        contrast = new Table(contrastGroup, SWT.BORDER | SWT.FULL_SELECTION | SWT.V_SCROLL);
        contrast.setHeaderVisible(true);
        contrast.setLinesVisible(true);
        contrast.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        for (String title : new String[] { "Text", "On", "Ratio", "Level" }) {
            TableColumn column = new TableColumn(contrast, SWT.NONE);
            column.setText(title);
            column.setWidth(title.equals("Ratio") ? 50 : title.equals("Level") ? 62 : 86);
        }
    }

    @Override
    protected void refresh() {
        updating = true;
        try {
            List<NameInfo> names = model.names().stream().filter(n -> n.hasColor() || n.hasFont()).toList();
            if (viewer.getInput() == null || !names.equals(viewer.getInput())) {
                viewer.setInput(names);
            } else {
                viewer.refresh();
            }
            if (model.name(selected).isEmpty()) {
                selected = names.getFirst().name();
            }
            viewer.setSelection(new StructuredSelection(model.name(selected).orElseThrow()), true);
        } finally {
            updating = false;
        }
        refreshEditor();
    }

    String selectedName() {
        return selected;
    }

    void select(String name) {
        selected = name;
        tintsOf = null;
        refresh();
    }

    List<ColorChip> swatchChips() {
        return swatchChips;
    }

    List<ColorChip> tintChips() {
        return tintChips;
    }

    Table contrastTable() {
        return contrast;
    }

    Text families() {
        return families;
    }

    Spinner height() {
        return height;
    }

    private void refreshEditor() {
        NameInfo info = model.name(selected).orElseThrow();
        updating = true;
        try {
            refreshColor(info);
            refreshFont(info);
            refreshContrast(info);
        } finally {
            updating = false;
        }
        colorGroup.getParent().layout(true, true);
    }

    private void refreshColor(NameInfo info) {
        colorGroup.setText("Color of " + info.name());
        setEnabled(colorGroup, info.hasColor());
        Palette palette = model.palette();
        if (swatchChips.size() != palette.swatches().size()) {
            swatchChips.forEach(ColorChip::dispose);
            swatchChips.clear();
            for (int i = 0; i < palette.swatches().size(); i++) {
                ColorChip chip = new ColorChip(swatchRow, 38, 28);
                int index = i;
                chip.onChoose(c -> {
                    String swatch = model.palette().swatches().get(index).name();
                    tintsOf = swatch;
                    tryEdit(() -> model.setColor(selected, ColorChoice.ref(swatch)));
                });
                swatchChips.add(chip);
            }
        }
        Optional<ColorChoice> choice = model.colorChoice(info.name());
        RGB current = model.color(info.name()).orElse(null);
        for (int i = 0; i < swatchChips.size(); i++) {
            Swatch swatch = palette.swatches().get(i);
            swatchChips.get(i).setRGB(swatch.color());
            swatchChips.get(i).setCaption(swatch.name());
            swatchChips.get(i).setSelected(choice.filter(c -> c.equals(ColorChoice.ref(swatch.name()))).isPresent());
        }
        if (!info.hasColor() || current == null) {
            colorSource.setText(info.name() + " holds no color.");
            tintsLabel.setText("");
            tintChips.forEach(c -> c.setVisible(false));
            return;
        }
        colorSource.setText(EditorModel.hex(current) + ": " + switch (model.colorSource(info.name())) {
            case AUTO -> info.added() ? "the accent's, until you set it." : "filled from the palette, until you set it.";
            case THEME -> "from " + model.duplicated().map(t -> t.name()).orElse("the theme") + ".";
            case SET -> choice.orElseThrow() instanceof ColorChoice.Ref ref ? "swatch " + ref.name() + ", set by you." : "set by you.";
        });
        if (tintsOf == null || palette.swatch(tintsOf).isEmpty()) {
            tintsOf = choice.filter(c -> c instanceof ColorChoice.Ref).map(c -> ((ColorChoice.Ref) c).name())
                    .filter(n -> palette.swatch(n).isPresent())
                    .orElseGet(() -> palette.swatches().stream()
                            .min(Comparator.comparingDouble(s -> Oklch.distance(s.color(), current))).orElseThrow().name());
        }
        tintsLabel.setText("Tints and shades of " + tintsOf + ":");
        List<RGB> row = TintsAndShades.of(palette.swatch(tintsOf).orElseThrow().color(), PalettePage.TINTS);
        for (int i = 0; i < tintChips.size(); i++) {
            tintChips.get(i).setVisible(true);
            tintChips.get(i).setRGB(row.get(i));
            tintChips.get(i).setSelected(row.get(i).equals(current) && choice.filter(c -> c instanceof ColorChoice.Literal).isPresent());
        }
    }

    private void refreshFont(NameInfo info) {
        fontGroup.setText("Font of " + info.name());
        setEnabled(fontGroup, info.hasFont());
        List<String> others = model.names().stream().filter(NameInfo::hasFont).map(NameInfo::name)
                .filter(n -> !n.equals(info.name())).toList();
        List<String> items = new ArrayList<>();
        items.add(NOTHING);
        items.addAll(others);
        if (!Arrays.asList(inherit.getItems()).equals(items)) {
            inherit.setItems(items.toArray(String[]::new));
        }
        if (!info.hasFont()) {
            fontSource.setText(info.name() + " holds no font.");
            families.setText("");
            height.setSelection(0);
            bold.setSelection(false);
            italic.setSelection(false);
            inherit.select(0);
            return;
        }
        Optional<FontChoice> choice = model.fontChoice(info.name());
        FontSpec font = model.font(info.name()).orElse(new FontSpec(List.<String>of(), 0, SWT.NORMAL));
        fontSource.setText(NamesPage.describe(font) + ": " + (choice.isPresent() ? "set by you." : "the base theme's, until you set it."));
        // Shown: the user's own values where set, else the font the name gets.
        List<String> shownFamilies = choice.map(c -> c.families().isEmpty() && c.inherit() != null ? List.<String>of() : c.families())
                .orElse(font.names());
        if (!families.isFocusControl()) {
            families.setText(String.join(", ", shownFamilies));
        }
        height.setSelection(choice.map(FontChoice::height).orElse(font.height()));
        int style = choice.map(FontChoice::style).orElse(font.style());
        bold.setSelection((style & SWT.BOLD) != 0);
        italic.setSelection((style & SWT.ITALIC) != 0);
        String from = choice.map(FontChoice::inherit).orElse(null);
        inherit.select(from == null ? 0 : Math.max(0, items.indexOf(from)));
    }

    private void refreshContrast(NameInfo info) {
        contrast.removeAll();
        for (ContrastCheck.Pair pair : model.contrastOf(info.name())) {
            TableItem item = new TableItem(contrast, SWT.NONE);
            item.setText(new String[] { pair.name(), pair.against(), String.format("%.1f:1", pair.ratio()),
                    (pair.ok() ? "" : "⚠ ") + level(pair) });
            model.color(pair.name()).ifPresent(rgb -> item.setImage(0, images.of(rgb)));
            model.color(pair.against()).ifPresent(rgb -> item.setImage(1, images.of(rgb)));
        }
        if (contrast.getItemCount() == 0) {
            TableItem item = new TableItem(contrast, SWT.NONE);
            item.setText(new String[] { "No pairs", "", "", "" });
        }
    }

    /** Builds the name's font from the fields: a font of the user's own from now on. */
    private void fontEdited() {
        if (updating || !model.name(selected).map(NameInfo::hasFont).orElse(false)) {
            return;
        }
        int style = (bold.getSelection() ? SWT.BOLD : 0) | (italic.getSelection() ? SWT.ITALIC : 0);
        String from = inherit.getSelectionIndex() <= 0 ? null : inherit.getItem(inherit.getSelectionIndex());
        List<String> list = Arrays.stream(families.getText().split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        FontChoice choice = new FontChoice(list, height.getSelection(), style, from);
        if (!choice.equals(model.fontChoice(selected).orElse(null))) {
            tryEdit(() -> model.setFont(selected, choice));
        }
    }

    private void chooseFont() {
        FontDialog dialog = new FontDialog(getShell());
        model.font(selected).ifPresent(f -> dialog.setFontList(new FontData[] {
                new FontData(f.name() != null ? f.name() : getShell().getFont().getFontData()[0].getName(),
                        f.height() > 0 ? f.height() : 9, f.style()) }));
        FontData chosen = dialog.open();
        if (chosen != null) {
            tryEdit(() -> model.setFont(selected, new FontChoice(List.of(chosen.getName()), chosen.getHeight(),
                    chosen.getStyle() & (SWT.BOLD | SWT.ITALIC), null)));
        }
    }

    private void customColor() {
        ColorDialog dialog = new ColorDialog(getShell());
        model.color(selected).ifPresent(dialog::setRGB);
        RGB rgb = dialog.open();
        if (rgb != null) {
            tryEdit(() -> model.setColor(selected, ColorChoice.of(rgb)));
        }
    }

    /** The lowest ratio of the pairs where {@code name} is the text. */
    private Optional<ContrastCheck.Pair> worst(String name) {
        return model.contrast().stream().filter(p -> p.name().equals(name)).min(Comparator.comparingDouble(p -> p.ratio() / p.minimum()));
    }

    static String badge(ContrastCheck.Pair pair) {
        return (pair.ok() ? "" : "⚠ ") + String.format("%.1f:1 ", pair.ratio()) + level(pair);
    }

    static String level(ContrastCheck.Pair pair) {
        return switch (pair.level()) {
            case AAA -> "AAA";
            case AA -> "AA";
            case AA_LARGE -> pair.ok() ? "AA large" : "large only";
            case FAIL -> "fails";
        };
    }

    private static void setEnabled(org.eclipse.swt.widgets.Composite composite, boolean enabled) {
        for (org.eclipse.swt.widgets.Control child : composite.getChildren()) {
            if (child instanceof org.eclipse.swt.widgets.Composite inner && !(child instanceof ColorChip)) {
                setEnabled(inner, enabled);
            }
            child.setEnabled(enabled);
        }
    }

    private void column(String title, int width, ColumnLabelProvider labels) {
        TableViewerColumn column = new TableViewerColumn(viewer, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(labels);
    }

    private static Button button(org.eclipse.swt.widgets.Composite parent, String text, org.eclipse.swt.widgets.Listener listener) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, listener);
        return button;
    }
}
