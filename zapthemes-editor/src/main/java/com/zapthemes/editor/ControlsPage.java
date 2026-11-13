package com.zapthemes.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColorCellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FontDialog;
import org.eclipse.swt.widgets.Listener;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zapthemes.core.ThemeKeys;
import com.zapthemes.core.catalog.ClassSpec;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.PropertyKind;
import com.zapthemes.core.catalog.PropertySpec;
import com.zapthemes.core.catalog.TypeSpec;
import com.zapthemes.editor.EditorModel.NameInfo;
import com.zapthemes.editor.EditorModel.Page;
import com.zapthemes.editor.EditorModel.RuleKey;
import com.zapthemes.editor.palette.TextContrast;
import com.zapthemes.swt.custom.SashForm;
import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Text;

/**
 * Page 4: every control type the catalog knows, grouped by library, with its variants (style
 * bits), the style classes on it, and its owner-draw states. Each property of the selected rule
 * shows the common name it uses (the default map's, unless the theme says otherwise) or an
 * override, a color, font or value of its own for this rule only. Clicking a control in the preview
 * selects its type. Text that is hard to read on its background ({@link TextContrast}) is marked
 * with a warning icon on its rule, the rule's type and library in the tree, and on the two
 * properties of the selected rule; the page's message says what is wrong.
 */
final class ControlsPage extends EditorPage {

    /** A library's types in the tree. */
    record LibraryNode(String group) {
    }

    /** A type: its plain rule. */
    record TypeNode(TypeSpec spec) {
        RuleKey key() {
            return RuleKey.of(spec.type());
        }
    }

    /** A variant, class or state rule of a type. */
    record RuleNode(TypeSpec spec, RuleKey key) {
    }

    /** One property of the selected rule. */
    record Row(PropertySpec spec) {
    }

    private static final String ALL_TYPES = "All types";
    private static final String NOT_SET = "(not set)";

    private final PropertyCatalog catalog;
    private Text filter;
    private TreeViewer types;
    private TableViewer properties;
    private Label ruleLabel;
    private Button reset;
    private Button addVariant;
    private Button addClass;
    private final ChipImages images = new ChipImages();
    private Object selected;
    private final Listener previewClicks = this::previewClicked;
    /** The theme's contrast problems by the rule they are made in ({@code Table}, {@code Tree:selected}). */
    private Map<String, List<TextContrast.Problem>> contrast = Map.of();

    ControlsPage(EditorModel model, PropertyCatalog catalog) {
        super("controls", "Controls", "Every control type, with the common name each property uses. Override a property "
                + "for one rule only, or add a rule for a variant, a style class or a state.", model, Page.CONTROLS);
        this.catalog = catalog;
    }

    @Override
    protected void createContent(Composite parent) {
        parent.setLayout(new GridLayout(1, false));
        SashForm sash = new SashForm(parent, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        Composite left = new Composite(sash, SWT.NONE);
        GridLayout leftLayout = new GridLayout(1, false);
        leftLayout.marginWidth = 0;
        left.setLayout(leftLayout);
        filter = new Text(left, SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL | SWT.BORDER);
        filter.setMessage("Filter types and properties");
        filter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        types = new TreeViewer(left, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        types.getTree().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        types.setContentProvider(new Types());
        types.setLabelProvider(new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return switch (element) {
                    case LibraryNode library -> library.group();
                    case TypeNode type -> type.spec().type();
                    case RuleNode rule -> label(rule.key());
                    default -> String.valueOf(element);
                };
            }

            @Override
            public Font getFont(Object element) {
                return element instanceof RuleNode rule && !model.hasRule(rule.key()) ? italic() : null;
            }

            @Override
            public Image getImage(Object element) {
                return problemsOf(element).isEmpty() ? null : warning();
            }

            @Override
            public String getToolTipText(Object element) {
                return tip(problemsOf(element));
            }
        });
        ColumnViewerToolTipSupport.enableFor(types);
        types.addFilter(new ViewerFilter() {
            @Override
            public boolean select(Viewer viewer, Object parentElement, Object element) {
                return matches(element);
            }
        });
        filter.addListener(SWT.Modify, e -> {
            types.refresh();
            if (!filter.getText().isBlank()) {
                types.expandAll();
            }
        });
        types.addSelectionChangedListener(e -> {
            Object element = types.getStructuredSelection().getFirstElement();
            if (element instanceof TypeNode || element instanceof RuleNode) {
                selected = element;
                refreshProperties();
            }
        });

        Composite right = new Composite(sash, SWT.NONE);
        GridLayout rightLayout = new GridLayout(1, false);
        rightLayout.marginWidth = 0;
        right.setLayout(rightLayout);
        ruleLabel = new Label(right, SWT.WRAP);
        ruleLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        properties = new TableViewer(right, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL);
        properties.getTable().setHeaderVisible(true);
        properties.getTable().setLinesVisible(true);
        properties.getTable().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        properties.setContentProvider(ArrayContentProvider.getInstance());
        properties.getTable().addDisposeListener(e -> images.dispose());
        column("Property", 125, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                Row row = (Row) element;
                return (rule().map(key -> model.isEdited(key, row.spec().key())).orElse(false) ? "• " : "") + row.spec().key();
            }

            @Override
            public Image getImage(Object element) {
                return problemsOf(element).isEmpty() ? null : warning();
            }

            @Override
            public String getToolTipText(Object element) {
                Row row = (Row) element;
                String problems = tip(problemsOf(row));
                String description = row.spec().description();
                if (problems == null) {
                    return description;
                }
                return description == null || description.isEmpty() ? problems : problems + "\n\n" + description;
            }
        }, null);
        column("Kind", 44, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return ((Row) element).spec().kind().label();
            }
        }, null);
        column("Common name", 125, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return nameText((Row) element);
            }

            @Override
            public Image getImage(Object element) {
                Row row = (Row) element;
                return row.spec().kind() == PropertyKind.COLOR && nameOf(row).isPresent()
                        ? colorOf(row).map(images::of).orElse(null) : null;
            }
        }, new NameEditing());
        column("Override", 88, new ColumnLabelProvider() {
            @Override
            public String getText(Object element) {
                return overrideText((Row) element);
            }

            @Override
            public Image getImage(Object element) {
                Row row = (Row) element;
                return row.spec().kind() == PropertyKind.COLOR && nameOf(row).isEmpty() ? colorOf(row).map(images::of).orElse(null) : null;
            }

            @Override
            public Font getFont(Object element) {
                return italic();
            }
        }, new OverrideEditing());
        ColumnViewerToolTipSupport.enableFor(properties);
        properties.addSelectionChangedListener(e -> updateButtons());

        Composite buttons = new Composite(right, SWT.NONE);
        buttons.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        GridLayout buttonLayout = new GridLayout(3, false);
        buttonLayout.marginWidth = 0;
        buttons.setLayout(buttonLayout);
        reset = button(buttons, "Reset", e -> selectedRow().ifPresent(row -> rule().ifPresent(key ->
                tryEdit(() -> model.setRuleValue(key, row.spec().key(), null)))));
        reset.setToolTipText("Takes back this property's change, so the base theme's value applies again");
        addVariant = button(buttons, "Add variant…", e -> addVariant());
        addVariant.setToolTipText("A rule for this type with SWT style bits, such as CHECK or MULTI|WRAP");
        addClass = button(buttons, "Add class…", e -> addClass());
        addClass.setToolTipText("A rule for this type with a style class (THEME_CLASS), such as primary");
        sash.setWeights(34, 66);
    }

    @Override
    protected void refresh() {
        updateContrast();
        if (types.getInput() == null) {
            types.setInput(model);
            select(selected != null ? selected : catalog.type("Button").map(TypeNode::new).orElse(null));
        } else {
            types.refresh();
        }
        refreshProperties();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (getControl() == null) {
            return;
        }
        if (visible) {
            getControl().getDisplay().addFilter(SWT.MouseDown, previewClicks);
        } else {
            getControl().getDisplay().removeFilter(SWT.MouseDown, previewClicks);
        }
    }

    @Override
    public void dispose() {
        if (getControl() != null && !getControl().isDisposed()) {
            getControl().getDisplay().removeFilter(SWT.MouseDown, previewClicks);
        }
        super.dispose();
    }

    TreeViewer types() {
        return types;
    }

    TableViewer properties() {
        return properties;
    }

    /** Selects the type {@code type}, or the node given. */
    void select(Object node) {
        if (node == null) {
            return;
        }
        if (node instanceof String type) {
            node = new TypeNode(catalog.type(type).orElseThrow(() -> new IllegalArgumentException("No type " + type)));
        }
        selected = node;
        types.setSelection(new StructuredSelection(node), true);
        refreshProperties();
    }

    Optional<RuleKey> rule() {
        return switch (selected) {
            case TypeNode type -> Optional.of(type.key());
            case RuleNode rule -> Optional.of(rule.key());
            case null, default -> Optional.empty();
        };
    }

    private Optional<TypeSpec> spec() {
        return switch (selected) {
            case TypeNode type -> Optional.of(type.spec());
            case RuleNode rule -> Optional.of(rule.spec());
            case null, default -> Optional.empty();
        };
    }

    private void refreshProperties() {
        Optional<RuleKey> key = rule();
        if (key.isEmpty()) {
            properties.setInput(List.of());
            ruleLabel.setText("Select a type.");
            updateButtons();
            updateMessage();
            return;
        }
        TypeSpec spec = spec().orElseThrow();
        List<Row> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PropertySpec property : specs(spec)) {
            if (seen.add(property.key())) {
                rows.add(new Row(property));
            }
        }
        // Keys the theme sets that the catalog does not describe still show, typed by their name.
        for (String property : ruleKeys(key.get())) {
            if (seen.add(property)) {
                PropertyKind kind = PropertyKind.ofKey(property);
                rows.add(new Row(new PropertySpec(property, kind != null ? kind : PropertyKind.STRING, null, null, null,
                        "Not described by the catalog", false)));
            }
        }
        if (!rows.equals(properties.getInput())) {
            properties.setInput(rows);
        } else if (!properties.isCellEditorActive()) {
            properties.refresh();
        }
        ruleLabel.setText(key.get() + (model.hasRule(key.get()) ? "" : " (no rule yet: setting a property adds it)")
                + (spec.description().isEmpty() ? "" : " — " + spec.description()));
        ruleLabel.getParent().layout(new Control[] { ruleLabel });
        updateButtons();
        updateMessage();
    }

    // ------------------------------------------------------------------ contrast

    private void updateContrast() {
        Map<String, List<TextContrast.Problem>> byRule = new LinkedHashMap<>();
        try {
            for (TextContrast.Problem problem : TextContrast.check(model.theme())) {
                byRule.computeIfAbsent(problem.rule(), r -> new ArrayList<>()).add(problem);
            }
        } catch (RuntimeException e) {
            // A theme that cannot be read has errors; the Save page lists them.
        }
        contrast = byRule;
    }

    /**
     * The contrast problems a node or row shows: a rule's own; a type's and its variants', classes'
     * and states'; a library's types'; a property row's, among the selected rule's.
     */
    List<TextContrast.Problem> problemsOf(Object element) {
        return switch (element) {
            case RuleNode rule -> contrast.getOrDefault(rule.key().toString(), List.of());
            case TypeNode type -> problemsOfType(type.spec().type());
            case LibraryNode library -> typesOf(library.group()).stream().flatMap(t -> problemsOfType(t.type()).stream()).toList();
            case Row row -> selectedProblems().stream()
                    .filter(p -> p.foreground().equals(row.spec().key()) || p.background().equals(row.spec().key())).toList();
            case null, default -> List.of();
        };
    }

    private List<TextContrast.Problem> problemsOfType(String type) {
        List<TextContrast.Problem> problems = new ArrayList<>();
        contrast.forEach((rule, list) -> {
            boolean under = rule.length() > type.length() && rule.startsWith(type) && "[.:".indexOf(rule.charAt(type.length())) >= 0;
            if (rule.equals(type) || under) {
                problems.addAll(list);
            }
        });
        return problems;
    }

    /** The selected rule's own problems. */
    private List<TextContrast.Problem> selectedProblems() {
        return rule().map(key -> contrast.getOrDefault(key.toString(), List.of())).orElse(List.of());
    }

    /** The page's message: what is hard to read in the selected rule, or in the rules under its type. */
    private void updateMessage() {
        List<TextContrast.Problem> own = selectedProblems();
        if (!own.isEmpty()) {
            setMessage("Hard to read: " + own.getFirst().message() + (own.size() > 1 ? " (and " + (own.size() - 1) + " more)" : ""),
                    IMessageProvider.WARNING);
        } else if (selected instanceof TypeNode type && !problemsOf(type).isEmpty()) {
            setMessage("Some of " + type.spec().type() + "'s variants, classes or states have text that is hard to read; the tree marks them.",
                    IMessageProvider.WARNING);
        } else {
            setMessage(null);
        }
    }

    private static String tip(List<TextContrast.Problem> problems) {
        if (problems.isEmpty()) {
            return null;
        }
        StringBuilder tip = new StringBuilder("Hard to read:");
        problems.forEach(p -> tip.append("\n").append(p.message()));
        return tip.toString();
    }

    private static Image warning() {
        return JFaceResources.getImage(Dialog.DLG_IMG_MESSAGE_WARNING);
    }

    private List<PropertySpec> specs(TypeSpec spec) {
        if (spec.type().equals("*")) {
            return TypeSpec.Base.CONTROL.properties();
        }
        return catalog.properties(spec.type());
    }

    private List<String> ruleKeys(RuleKey key) {
        List<String> keys = new ArrayList<>();
        Map<String, Map<String, JsonElement>> section = key.isClass() ? model.theme().classes() : model.theme().controls();
        Map<String, JsonElement> rule = section.get(key.themeKey());
        if (rule == null) {
            return keys;
        }
        if (key.state() == null) {
            rule.keySet().stream().filter(k -> !k.equals("states")).forEach(keys::add);
        } else if (rule.get("states") instanceof JsonObject states && states.get(key.state()) instanceof JsonObject state) {
            keys.addAll(state.keySet());
        }
        return keys;
    }

    private void updateButtons() {
        Optional<Row> row = selectedRow();
        reset.setEnabled(row.isPresent() && rule().map(key -> model.isEdited(key, row.get().spec().key())).orElse(false));
        boolean type = selected instanceof TypeNode node && !node.spec().type().equals("*");
        addVariant.setEnabled(type);
        addClass.setEnabled(selected instanceof TypeNode);
    }

    private Optional<Row> selectedRow() {
        return properties.getStructuredSelection().getFirstElement() instanceof Row row ? Optional.of(row) : Optional.empty();
    }

    private void addVariant() {
        if (!(selected instanceof TypeNode type)) {
            return;
        }
        InputDialog dialog = new InputDialog(getShell(), "Add variant", "Style bits for a " + type.spec().type()
                + " rule, such as CHECK or MULTI|WRAP:", "", text -> text.matches("[A-Z][A-Z_]*(\\|[A-Z][A-Z_]*)*") ? null
                        : "SWT style constants, in capitals, separated by |.");
        if (dialog.open() == Window.OK) {
            RuleKey key = new RuleKey(type.spec().type(), dialog.getValue().strip(), null, null);
            if (tryEdit(() -> model.addRule(key))) {
                select(new RuleNode(type.spec(), key));
            }
        }
    }

    private void addClass() {
        if (!(selected instanceof TypeNode type)) {
            return;
        }
        InputDialog dialog = new InputDialog(getShell(), "Add class", "A style class for " + type.spec().type()
                + " rules (set on widgets with THEME_CLASS), such as primary:", "", text -> model.nameProblem(text, text));
        if (dialog.open() == Window.OK) {
            RuleKey key = new RuleKey(type.spec().type(), null, dialog.getValue().strip(), null);
            if (tryEdit(() -> model.addRule(key))) {
                select(new RuleNode(type.spec(), key));
            }
        }
    }

    /** A click on a control of the preview selects its type. */
    private void previewClicked(Event e) {
        if (!(e.widget instanceof Control control) || getControl() == null || !getControl().isVisible()) {
            return;
        }
        for (Control c = control; c != null; c = c.getParent()) {
            if (c == preview()) {
                Object type = control.getData(ThemeKeys.THEME_CONTROL_TYPE);
                if (type instanceof String name && catalog.type(name).isPresent()) {
                    filter.setText("");
                    select(name);
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ values

    private Optional<JsonElement> value(Row row) {
        return rule().flatMap(key -> model.ruleValue(key, row.spec().key()));
    }

    /** The common name the property uses: its value's, or the default map's when it has none. */
    private Optional<String> nameOf(Row row) {
        Optional<JsonElement> value = value(row);
        if (value.isPresent()) {
            return reference(value.get());
        }
        return Optional.ofNullable(row.spec().defaultName());
    }

    private static Optional<String> reference(JsonElement value) {
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().startsWith("$")) {
            return Optional.of(value.getAsString().substring(1));
        }
        return Optional.empty();
    }

    private Optional<RGB> colorOf(Row row) {
        Optional<String> name = nameOf(row);
        if (name.isPresent()) {
            return model.color(name.get()).or(() -> model.palette().swatch(name.get()).map(s -> s.color()));
        }
        return value(row).filter(v -> v.isJsonPrimitive() && v.getAsString().matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))
                .map(v -> parse(v.getAsString()));
    }

    private String nameText(Row row) {
        Optional<JsonElement> value = value(row);
        String defaultName = row.spec().defaultName();
        if (value.isEmpty()) {
            return defaultName != null ? defaultName + " (default)" : NOT_SET;
        }
        return reference(value.get()).map(name -> name + (name.equals(defaultName) ? " (default)" : "")).orElse("");
    }

    private String overrideText(Row row) {
        Optional<JsonElement> value = value(row);
        if (value.isEmpty() || reference(value.get()).isPresent()) {
            return "";
        }
        JsonElement v = value.get();
        if (v.isJsonObject() && row.spec().kind() == PropertyKind.FONT) {
            return describeFont(v.getAsJsonObject());
        }
        return v.isJsonPrimitive() ? v.getAsString() : v.toString();
    }

    private static String describeFont(JsonObject font) {
        List<String> parts = new ArrayList<>();
        if (font.has("name")) {
            JsonElement name = font.get("name");
            parts.add(name.isJsonArray() ? name.getAsJsonArray().get(0).getAsString() : name.getAsString());
        }
        if (font.has("height")) {
            parts.add(font.get("height").getAsString());
        }
        if (font.has("style")) {
            parts.add(font.get("style").getAsString().replace('|', ' '));
        }
        return parts.isEmpty() ? font.toString() : String.join(" ", parts);
    }

    private static RGB parse(String hex) {
        int value = Integer.parseInt(hex.substring(1, 7), 16);
        return new RGB(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF);
    }

    /** The names a property can take: those with its part. */
    private List<String> compatibleNames(Row row) {
        boolean font = row.spec().kind() == PropertyKind.FONT;
        return model.names().stream().filter(n -> font ? n.hasFont() : n.hasColor()).map(NameInfo::name).toList();
    }

    private Font italic;

    private Font italic() {
        if (italic == null) {
            italic = JFaceResources.getFontRegistry().getItalic(JFaceResources.DEFAULT_FONT);
        }
        return italic;
    }

    // ------------------------------------------------------------------ the tree

    /** A label for a rule node: {@code [CHECK]}, {@code .primary}, {@code :hover}. */
    static String label(RuleKey key) {
        if (key.state() != null) {
            return key.state() + " (state)";
        }
        return key.isClass() ? "." + key.styleClass() + " (class)" : "[" + key.variant() + "] (variant)";
    }

    private boolean matches(Object element) {
        String text = filter.getText().strip().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return true;
        }
        return switch (element) {
            case LibraryNode library -> typesOf(library.group()).stream().anyMatch(type -> typeMatches(type, text));
            case TypeNode type -> typeMatches(type.spec(), text);
            case RuleNode rule -> rule.key().toString().toLowerCase(Locale.ROOT).contains(text) || typeMatches(rule.spec(), text);
            default -> true;
        };
    }

    private boolean typeMatches(TypeSpec type, String text) {
        return type.type().toLowerCase(Locale.ROOT).contains(text)
                || specs(type).stream().anyMatch(p -> p.key().toLowerCase(Locale.ROOT).contains(text));
    }

    private Map<String, List<TypeSpec>> libraries() {
        Map<String, List<TypeSpec>> libraries = new LinkedHashMap<>();
        for (TypeSpec type : catalog.types()) {
            libraries.computeIfAbsent(type.group(), g -> new ArrayList<>()).add(type);
        }
        libraries.put(ALL_TYPES, List.of(TypeSpec.of("*", ALL_TYPES, TypeSpec.Base.CONTROL)
                .described("Class rules for any type, such as *.danger")));
        return libraries;
    }

    private List<TypeSpec> typesOf(String group) {
        return libraries().getOrDefault(group, List.of());
    }

    /** A type's variants, classes and states: the catalog's, and the rules the theme has. */
    private List<RuleNode> rulesOf(TypeSpec spec) {
        Set<RuleKey> keys = new LinkedHashSet<>();
        String type = spec.type();
        spec.variants().forEach(v -> keys.add(new RuleKey(type, v, null, null)));
        for (ClassSpec c : catalog.classes()) {
            if (c.types().contains(type) || type.equals("*") && c.types().isEmpty()) {
                keys.add(new RuleKey(type, null, c.name(), null));
            }
        }
        spec.states().forEach(s -> keys.add(RuleKey.of(type).withState(s)));
        for (RuleKey key : model.rules()) {
            if (key.type().equals(type) && !(key.variant() == null && key.styleClass() == null && key.state() == null)) {
                keys.add(key);
            }
        }
        return keys.stream().map(key -> new RuleNode(spec, key)).toList();
    }

    private final class Types implements ITreeContentProvider {
        @Override
        public Object[] getElements(Object input) {
            return libraries().keySet().stream().map(LibraryNode::new).toArray();
        }

        @Override
        public Object[] getChildren(Object parent) {
            return switch (parent) {
                case LibraryNode library -> typesOf(library.group()).stream().map(TypeNode::new).toArray();
                case TypeNode type -> rulesOf(type.spec()).toArray();
                default -> new Object[0];
            };
        }

        @Override
        public Object getParent(Object element) {
            return switch (element) {
                case TypeNode type -> new LibraryNode(type.spec().group());
                case RuleNode rule -> new TypeNode(rule.spec());
                default -> null;
            };
        }

        @Override
        public boolean hasChildren(Object element) {
            return element instanceof LibraryNode || element instanceof TypeNode type && !rulesOf(type.spec()).isEmpty();
        }
    }

    // ------------------------------------------------------------------ editing in place

    /** The common name of a color or font property, chosen in place; "(default)" is the default map's. */
    private final class NameEditing extends EditingSupport {
        private List<String> choices = List.of();

        NameEditing() {
            super(properties);
        }

        @Override
        protected boolean canEdit(Object element) {
            PropertyKind kind = ((Row) element).spec().kind();
            return kind == PropertyKind.COLOR || kind == PropertyKind.FONT;
        }

        @Override
        protected CellEditor getCellEditor(Object element) {
            Row row = (Row) element;
            choices = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            if (row.spec().defaultName() != null) {
                choices.add(row.spec().defaultName());
                labels.add(row.spec().defaultName() + " (default)");
            }
            for (String name : compatibleNames(row)) {
                if (!name.equals(row.spec().defaultName())) {
                    choices.add(name);
                    labels.add(name);
                }
            }
            return new ComboBoxCellEditor(properties.getTable(), labels.toArray(String[]::new), SWT.READ_ONLY);
        }

        @Override
        protected Object getValue(Object element) {
            return nameOf((Row) element).map(choices::indexOf).orElse(-1);
        }

        @Override
        protected void setValue(Object element, Object value) {
            int index = (Integer) value;
            if (index < 0 || index >= choices.size()) {
                return;
            }
            Row row = (Row) element;
            String name = choices.get(index);
            rule().ifPresent(key -> tryEdit(() -> model.setRuleValue(key, row.spec().key(), new JsonPrimitive("$" + name))));
        }
    }

    /** A value of the property's own: a color, a font, a number, a flag, a choice or text. */
    private final class OverrideEditing extends EditingSupport {
        OverrideEditing() {
            super(properties);
        }

        @Override
        protected boolean canEdit(Object element) {
            return true;
        }

        @Override
        protected CellEditor getCellEditor(Object element) {
            PropertySpec spec = ((Row) element).spec();
            return switch (spec.kind()) {
                case COLOR -> new ColorCellEditor(properties.getTable());
                case FONT -> new FontCellEditor(properties.getTable());
                case BOOLEAN -> new ComboBoxCellEditor(properties.getTable(), new String[] { "true", "false" }, SWT.READ_ONLY);
                case CHOICE -> new ComboBoxCellEditor(properties.getTable(), spec.choices().toArray(String[]::new), SWT.READ_ONLY);
                default -> new TextCellEditor(properties.getTable());
            };
        }

        @Override
        protected Object getValue(Object element) {
            Row row = (Row) element;
            PropertySpec spec = row.spec();
            Optional<JsonElement> value = value(row);
            return switch (spec.kind()) {
                case COLOR -> colorOf(row).orElse(new RGB(0x80, 0x80, 0x80));
                case FONT -> value.filter(JsonElement::isJsonObject).map(v -> v.getAsJsonObject()).orElse(new JsonObject());
                case BOOLEAN -> value.map(v -> v.getAsBoolean() ? 0 : 1).orElse(1);
                case CHOICE -> value.map(v -> spec.choices().indexOf(v.getAsString())).orElse(-1);
                default -> value.map(v -> v.isJsonPrimitive() ? v.getAsString() : v.toString()).orElse("");
            };
        }

        @Override
        protected void setValue(Object element, Object value) {
            Row row = (Row) element;
            PropertySpec spec = row.spec();
            JsonElement json = switch (spec.kind()) {
                case COLOR -> value instanceof RGB rgb ? new JsonPrimitive(EditorModel.hex(rgb)) : null;
                case FONT -> value instanceof JsonObject font && !font.isEmpty() ? font : null;
                case BOOLEAN -> value instanceof Integer i && i >= 0 ? new JsonPrimitive(i == 0) : null;
                case CHOICE -> value instanceof Integer i && i >= 0 ? new JsonPrimitive(spec.choices().get(i)) : null;
                case INTEGER -> integer(value);
                default -> value instanceof String text && !text.isBlank() ? new JsonPrimitive(text.strip()) : null;
            };
            if (json != null) {
                rule().ifPresent(key -> tryEdit(() -> model.setRuleValue(key, spec.key(), json)));
            }
        }

        private JsonElement integer(Object value) {
            try {
                return new JsonPrimitive(Integer.parseInt(String.valueOf(value).strip()));
            } catch (NumberFormatException e) {
                setErrorMessage("A whole number, please.");
                return null;
            }
        }
    }

    /** A font of the property's own, from the font dialog, as a theme writes it. */
    private final class FontCellEditor extends DialogCellEditor {
        FontCellEditor(org.eclipse.swt.widgets.Composite parent) {
            super(parent);
        }

        @Override
        protected Object openDialogBox(Control cellEditorWindow) {
            FontDialog dialog = new FontDialog(cellEditorWindow.getShell());
            FontData chosen = dialog.open();
            if (chosen == null) {
                return null;
            }
            JsonObject font = new JsonObject();
            font.addProperty("name", chosen.getName());
            font.addProperty("height", chosen.getHeight());
            font.addProperty("style", FontChoice.styleName(chosen.getStyle() & (SWT.BOLD | SWT.ITALIC)));
            return font;
        }

        @Override
        protected void updateContents(Object value) {
            if (getDefaultLabel() != null) {
                getDefaultLabel().setText(value instanceof JsonObject font && !font.isEmpty() ? describeFont(font) : "");
            }
        }
    }

    private void column(String title, int width, ColumnLabelProvider labels, EditingSupport editing) {
        TableViewerColumn column = new TableViewerColumn(properties, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setWidth(width);
        column.setLabelProvider(labels);
        if (editing != null) {
            column.setEditingSupport(editing);
        }
    }

    private static Button button(org.eclipse.swt.widgets.Composite parent, String text, Listener listener) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, listener);
        return button;
    }
}
