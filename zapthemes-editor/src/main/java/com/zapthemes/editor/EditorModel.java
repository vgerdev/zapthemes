package com.zapthemes.editor;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.eclipse.swt.graphics.RGB;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zapthemes.core.catalog.BaseNames;
import com.zapthemes.core.catalog.NameSpec;
import com.zapthemes.core.model.FontSpec;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ThemeNames;
import com.zapthemes.editor.palette.ContrastCheck;
import com.zapthemes.editor.palette.Harmony;
import com.zapthemes.editor.palette.NameFill;
import com.zapthemes.editor.palette.Palette;
import com.zapthemes.editor.palette.PaletteGenerator;
import com.zapthemes.editor.palette.Swatch;

/**
 * What the theme wizard edits, with no UI: the palette (page 1), the common names (page 2), and the
 * colors and fonts the user gave them (page 3). Every color the user has not set is filled from
 * the palette ({@link NameFill}) and follows it; a color the user set stays. {@link #theme()} is
 * the theme all this gives, layered on a base theme: the duplicated one, or a bundled dark or light
 * theme.
 *
 * <p>Each page has its own {@link UndoStack}: an edit records how to undo itself, so undoing on
 * one page leaves the others' edits alone. Listeners hear about every change, undo included.
 */
public final class EditorModel {

    /** The pages that edit the model, each with its own undo history. */
    public enum Page { PALETTE, NAMES, VALUES, CONTROLS }

    /** Where a name's color or font comes from. */
    public enum Source {
        /** Filled from the palette, or taken from the base theme. */
        AUTO,
        /** Taken from the duplicated theme, and not changed since. */
        THEME,
        /** Set by the user. */
        SET
    }

    /** A common name: a base name, or one the user added. */
    public record NameInfo(String name, String group, Set<String> parts, String description, boolean added) {
        public boolean hasColor() {
            return parts.contains("color");
        }

        public boolean hasFont() {
            return parts.contains("font");
        }
    }

    /** The group of the names the user adds. */
    public static final String ADDED = "Added";
    public static final Set<String> COLOR = Set.of("color");
    public static final Set<String> FONT = Set.of("font");
    public static final Set<String> BOTH = Set.of("color", "font");

    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private static final RGB DEFAULT_BASE = new RGB(0x2A, 0xAC, 0xB8);
    private static final int DEFAULT_SIZE = 6;
    /** The library version a theme is written with ({@code editor.libraryVersion}). */
    public static final String LIBRARY_VERSION = libraryVersion();

    private final List<Theme> themes;
    private final PaletteGenerator generator;
    private final Map<Page, UndoStack> history = new EnumMap<>(Page.class);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private Theme base;
    private Theme duplicated;
    private Palette palette;
    private final Map<String, Set<String>> added = new LinkedHashMap<>();
    private final Map<String, ColorChoice> userColors = new LinkedHashMap<>();
    private final Map<String, FontChoice> userFonts = new LinkedHashMap<>();
    private final Set<String> fromTheme = new LinkedHashSet<>();
    private final Map<RuleKey, Map<String, JsonElement>> ruleEdits = new LinkedHashMap<>();
    private String id = "untitled";
    private String title = "Untitled";
    private Boolean darkFlag;
    private Path file;
    private String lastKey;

    private Map<String, RGB> colors = Map.of();
    private Theme theme;

    /**
     * @param themes resolved themes to duplicate and to build on; a new palette builds on the dark
     *               or light one ({@code zap-dark}, {@code zap-light}, else the first of that kind)
     * @param seed   the generator's seed, so that tests repeat
     */
    public EditorModel(List<Theme> themes, long seed) {
        if (themes.isEmpty()) {
            throw new IllegalArgumentException("The editor needs a theme to build on");
        }
        this.themes = List.copyOf(themes);
        this.generator = new PaletteGenerator(seed);
        for (Page page : Page.values()) {
            history.put(page, new UndoStack());
        }
        palette = generator.generate(Harmony.ANALOGOUS, DEFAULT_BASE, DEFAULT_SIZE, true);
        base = baseFor(true);
        recompute();
    }

    // ------------------------------------------------------------------ queries

    public List<Theme> themes() {
        return themes;
    }

    public Palette palette() {
        return palette;
    }

    /** The theme the edited one is layered on. */
    public Theme base() {
        return base;
    }

    /** The theme being duplicated, if the user started from one. */
    public Optional<Theme> duplicated() {
        return Optional.ofNullable(duplicated);
    }

    /** The theme the edits give, ready to preview. */
    public Theme theme() {
        return theme;
    }

    /** Every name, base names first in their order, then the ones the user added. */
    public List<NameInfo> names() {
        List<NameInfo> names = new ArrayList<>();
        for (NameSpec spec : BaseNames.ALL) {
            names.add(new NameInfo(spec.name(), spec.group(), spec.parts(), spec.description(), false));
        }
        added.forEach((name, parts) -> names.add(new NameInfo(name, ADDED, parts, "Added by you", true)));
        return names;
    }

    public Optional<NameInfo> name(String name) {
        return names().stream().filter(n -> n.name().equals(name)).findFirst();
    }

    /** The color name {@code name} gets, if it has a color part. */
    public Optional<RGB> color(String name) {
        return Optional.ofNullable(colors.get(name));
    }

    /** Every name's color, base names first. */
    public Map<String, RGB> colors() {
        return colors;
    }

    /** The color the user gave {@code name}; empty when it is filled from the palette. */
    public Optional<ColorChoice> colorChoice(String name) {
        return Optional.ofNullable(live(userColors.get(name)));
    }

    /** The font {@code name} gets in {@link #theme()}, inherited parts merged. */
    public Optional<FontSpec> font(String name) {
        try {
            return ThemeNames.of(theme).font(name);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** The font the user gave {@code name}; empty when it is the base theme's. */
    public Optional<FontChoice> fontChoice(String name) {
        return Optional.ofNullable(userFonts.get(name));
    }

    public Source colorSource(String name) {
        if (live(userColors.get(name)) == null) {
            return Source.AUTO;
        }
        return fromTheme.contains(name) ? Source.THEME : Source.SET;
    }

    public Source fontSource(String name) {
        return userFonts.containsKey(name) ? Source.SET : Source.AUTO;
    }

    /** Every contrast pair the base names declare, with the current colors. */
    public List<ContrastCheck.Pair> contrast() {
        return ContrastCheck.check(colors);
    }

    /** The pairs {@code name} is part of, as the text or the background. */
    public List<ContrastCheck.Pair> contrastOf(String name) {
        return contrast().stream().filter(p -> p.name().equals(name) || p.against().equals(name)).toList();
    }

    /** The names whose color or font refers to {@code name}. */
    public List<String> usedBy(String name) {
        Set<String> users = new LinkedHashSet<>();
        userColors.forEach((owner, choice) -> {
            if (choice instanceof ColorChoice.Ref ref && ref.name().equals(name)) {
                users.add(owner);
            }
        });
        userFonts.forEach((owner, font) -> {
            if (name.equals(font.inherit())) {
                users.add(owner);
            }
        });
        return List.copyOf(users);
    }

    /** Why {@code candidate} cannot be a new name (or {@code current}'s new name), or null if it can. */
    public String nameProblem(String candidate, String current) {
        if (candidate == null || candidate.isBlank()) {
            return "A name cannot be empty.";
        }
        if (!NAME.matcher(candidate).matches()) {
            return "A name starts with a letter and has only letters, digits and underscores.";
        }
        if (candidate.equals(current)) {
            return null;
        }
        if (BaseNames.get(candidate).isPresent() || added.containsKey(candidate)) {
            return "There is already a name \"" + candidate + "\".";
        }
        if (palette.swatch(candidate).isPresent()) {
            return "\"" + candidate + "\" is a swatch of the palette.";
        }
        return null;
    }

    /** A name that is free, from {@code stem}: {@code stem}, {@code stem2}, ... */
    public String freeName(String stem) {
        String name = stem;
        for (int i = 2; nameProblem(name, null) != null; i++) {
            name = stem + i;
        }
        return name;
    }

    public UndoStack history(Page page) {
        return history.get(page);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    // ------------------------------------------------------------------ page 1: palette

    /** A new generated palette; locked swatches of the current one stay where their names match. */
    public void generate(Harmony rule, RGB baseColor, int size, boolean dark) {
        Palette fresh = keepLocked(generator.generate(rule, baseColor, size, dark));
        editPalette("New palette", null, fresh, duplicated == null ? baseFor(dark) : base, duplicated);
    }

    /** Rolls every unlocked swatch again. */
    public void regenerate() {
        editPalette("Regenerate", null, generator.regenerate(palette), base, duplicated);
    }

    public void setRule(Harmony rule) {
        editPalette("Rule: " + rule.id(), null, generator.regenerate(palette.withRule(rule).withBase(generatedBase())), base, duplicated);
    }

    public void setBaseColor(RGB color) {
        editPalette("Base color", null, generator.regenerate(palette.withRule(generatedRule()).withBase(color)), base, duplicated);
    }

    public void setSize(int size) {
        generate(generatedRule(), generatedBase(), size, palette.dark());
    }

    /** Light or dark: the swatches are rolled again at that end, and a new theme builds on that kind. */
    public void setDark(boolean dark) {
        if (dark == palette.dark()) {
            return;
        }
        editPalette(dark ? "Dark" : "Light", null, generator.regenerate(palette.withDark(dark)),
                duplicated == null ? baseFor(dark) : base, duplicated);
    }

    /** One swatch's color; a run of changes to the same swatch undoes as one. */
    public void setSwatchColor(String swatch, RGB color) {
        Swatch old = palette.swatch(swatch).orElseThrow(() -> new IllegalArgumentException("No swatch " + swatch));
        editPalette("Change " + swatch, "swatch:" + swatch, palette.with(swatch, old.withColor(color)), base, duplicated);
    }

    public void setLocked(String swatch, boolean locked) {
        editPalette((locked ? "Lock " : "Unlock ") + swatch, null, palette.withLocked(swatch, locked), base, duplicated);
    }

    /** Uses {@code chosen}, a saved palette, a theme's swatches or an image's colors, as the palette. */
    public void usePalette(String label, Palette chosen) {
        editPalette(label, null, safe(chosen), duplicated == null ? baseFor(chosen.dark()) : base, duplicated);
    }

    /**
     * Starts from {@code source}: its palette, its names and their colors, its added names, and
     * the theme itself as the base, so every rule it has stays. The copy's id is
     * {@code <id>-copy}. Undo goes back to before it.
     */
    public void duplicate(Theme source) {
        start(source, true, null, "Duplicate " + source.name());
    }

    /**
     * Opens {@code source}, saved in {@code path} (or null), to edit it: as {@link #duplicate}, but
     * keeping its id, name and file, and its generator settings. Each name keeps its color as the
     * theme writes it, a reference ({@code "$accent"}) or a color, so saving it again writes the
     * same theme.
     */
    public void open(Theme source, Path path) {
        start(source, false, path, "Open " + source.name());
    }

    private void start(Theme source, boolean copy, Path path, String label) {
        Objects.requireNonNull(source, "source");
        State before = state();
        Palette from = withGenerator(safe(Palette.fromTheme(source)), source);
        ThemeNames names = ThemeNames.of(source);
        Map<String, Set<String>> addedNames = new LinkedHashMap<>();
        Map<String, ColorChoice> setColors = new LinkedHashMap<>();
        Map<String, FontChoice> setFonts = new LinkedHashMap<>();
        source.names().forEach((name, entry) -> {
            boolean base = BaseNames.get(name).isPresent();
            if (!base && entry.isJsonObject()) {
                Set<String> parts = new LinkedHashSet<>(entry.getAsJsonObject().keySet());
                parts.retainAll(BOTH);
                if (parts.isEmpty()) {
                    return;
                }
                addedNames.put(name, Set.copyOf(parts));
                if (parts.contains("font")) {
                    names.font(name).ifPresent(f -> setFonts.put(name, new FontChoice(f.names(), f.height(), f.style(), null)));
                }
            }
            if (base && !BaseNames.get(name).orElseThrow().hasColor()) {
                return;
            }
            try {
                names.color(name).ifPresent(c -> setColors.put(name, written(entry, from, source).orElseGet(() -> swatchOf(from, c.rgb)
                        .<ColorChoice>map(ColorChoice.Ref::new).orElse(new ColorChoice.Literal(c.rgb)))));
            } catch (RuntimeException e) {
                // A name the theme itself cannot resolve keeps the palette's fill.
            }
        });
        State after = new State(from, source, source, addedNames, setColors, setFonts, setColors.keySet(),
                copy ? source.id() + "-copy" : source.id(), copy ? source.name() + " copy" : source.name(), copy ? null : path,
                Map.of());
        record(Page.PALETTE, label, null, () -> restore(before), () -> restore(after));
    }

    /** A name's color as {@code source} writes it: a reference to a swatch or another name, or a color. */
    private static Optional<ColorChoice> written(JsonElement entry, Palette palette, Theme source) {
        if (!entry.isJsonObject() || !(entry.getAsJsonObject().get("color") instanceof JsonPrimitive value) || !value.isString()) {
            return Optional.empty();
        }
        String text = value.getAsString();
        if (text.startsWith("$")) {
            String ref = text.substring(1);
            boolean known = palette.swatch(ref).isPresent() || source.names().containsKey(ref) && !source.swatches().containsKey(ref);
            return known ? Optional.of(new ColorChoice.Ref(ref)) : Optional.empty();
        }
        if (text.matches("#[0-9A-Fa-f]{6}")) {
            return Optional.of(new ColorChoice.Literal(parseHex(text)));
        }
        return Optional.empty();
    }

    /** {@code palette} with the rule, base and locks {@code source}'s {@code editor.generator} records. */
    private static Palette withGenerator(Palette palette, Theme source) {
        if (!(source.editor().get("generator") instanceof JsonObject settings)) {
            return palette;
        }
        Palette result = palette;
        try {
            if (settings.has("rule")) {
                result = result.withRule(Harmony.byId(settings.get("rule").getAsString()));
            }
            if (settings.has("base")) {
                result = result.withBase(parseHex(settings.get("base").getAsString()));
            }
            if (settings.get("locked") instanceof JsonArray locked) {
                for (JsonElement name : locked) {
                    if (result.swatch(name.getAsString()).isPresent()) {
                        result = result.withLocked(name.getAsString(), true);
                    }
                }
            }
        } catch (RuntimeException e) {
            // Settings the editor cannot read are left out; the swatches stay.
        }
        return result;
    }

    private static RGB parseHex(String hex) {
        int value = Integer.parseInt(hex.substring(1, 7), 16);
        return new RGB(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF);
    }

    // ------------------------------------------------------------------ page 4: controls

    /**
     * A rule of {@code controls} or {@code classes}: a control type, optionally with style bits
     * ({@code Button[CHECK]}) or a style class ({@code Button.primary}, {@code *.danger}), and
     * optionally one owner-draw state of it ({@code hover}).
     */
    public record RuleKey(String type, String variant, String styleClass, String state) {
        public RuleKey {
            Objects.requireNonNull(type, "type");
            if (variant != null && styleClass != null) {
                throw new IllegalArgumentException("A rule is a variant or a class, not both");
            }
        }

        public static RuleKey of(String type) {
            return new RuleKey(type, null, null, null);
        }

        public RuleKey withState(String name) {
            return new RuleKey(type, variant, styleClass, name);
        }

        /** The rule without its state. */
        public RuleKey rule() {
            return new RuleKey(type, variant, styleClass, null);
        }

        /** Its key in the theme: {@code Button}, {@code Button[CHECK]} or {@code Button.primary}. */
        public String themeKey() {
            return styleClass != null ? type + "." + styleClass : variant != null ? type + "[" + variant + "]" : type;
        }

        public boolean isClass() {
            return styleClass != null;
        }

        @Override
        public String toString() {
            return themeKey() + (state != null ? ":" + state : "");
        }
    }

    /** What rule {@code key} gives {@code property}: the edit, else the base theme's value. */
    public Optional<JsonElement> ruleValue(RuleKey key, String property) {
        Map<String, JsonElement> edits = ruleEdits.get(key);
        if (edits != null && edits.containsKey(property)) {
            return Optional.of(edits.get(property));
        }
        return Optional.ofNullable(baseRule(key)).map(rule -> rule.get(property));
    }

    /** Whether {@code property} of {@code key} was changed in the wizard. */
    public boolean isEdited(RuleKey key, String property) {
        Map<String, JsonElement> edits = ruleEdits.get(key);
        return edits != null && edits.containsKey(property);
    }

    /** Whether the theme has rule {@code key}: the base has it, or it was added. */
    public boolean hasRule(RuleKey key) {
        return ruleEdits.containsKey(key) || baseRule(key) != null;
    }

    /** The rules of the base theme and the ones added, in theme order. */
    public List<RuleKey> rules() {
        Set<RuleKey> rules = new LinkedHashSet<>();
        base.controls().forEach((key, props) -> addRules(rules, key, props, false));
        base.classes().forEach((key, props) -> addRules(rules, key, props, true));
        rules.addAll(ruleEdits.keySet());
        return List.copyOf(rules);
    }

    /**
     * Sets {@code property} of rule {@code key} to {@code value}: {@code "$name"} for a common name,
     * or a literal (an override). Null takes the edit back, so the base theme's value applies again.
     * Changes to one property in a row undo as one.
     */
    public void setRuleValue(RuleKey key, String property, JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && value.getAsString().startsWith("$")) {
            String ref = value.getAsString().substring(1);
            boolean font = com.zapthemes.core.catalog.PropertyKind.ofKey(property) == com.zapthemes.core.catalog.PropertyKind.FONT;
            boolean known = font ? name(ref).filter(NameInfo::hasFont).isPresent()
                    : name(ref).filter(NameInfo::hasColor).isPresent() || palette.swatch(ref).isPresent();
            if (!known) {
                throw new IllegalArgumentException("\"$" + ref + "\" is not a " + (font ? "name with a font" : "swatch or a name with a color"));
            }
        }
        Map<String, JsonElement> old = ruleEdits.containsKey(key) ? new LinkedHashMap<>(ruleEdits.get(key)) : null;
        Map<String, JsonElement> next = old == null ? new LinkedHashMap<>() : new LinkedHashMap<>(old);
        if (value == null) {
            next.remove(property);
        } else {
            next.put(property, value.deepCopy());
        }
        if (Objects.equals(old, next) || old == null && next.isEmpty()) {
            return;
        }
        // An edit map that empties goes away, unless it is what makes an added rule exist.
        boolean drop = next.isEmpty() && baseRule(key) != null;
        record(Page.CONTROLS, (value == null ? "Reset " : "Set ") + key + " " + property, "rule:" + key + ":" + property,
                () -> putOrRemove(ruleEdits, key, old), () -> putOrRemove(ruleEdits, key, drop ? null : next));
    }

    /** Adds an empty rule: a variant, class or state the base theme has no rule for. */
    public void addRule(RuleKey key) {
        if (hasRule(key)) {
            return;
        }
        if (key.variant() != null && !key.variant().matches("[A-Z][A-Z_]*(\\|[A-Z][A-Z_]*)*")) {
            throw new IllegalArgumentException("Style bits are SWT constants such as CHECK or MULTI|WRAP.");
        }
        if (key.styleClass() != null && !NAME.matcher(key.styleClass()).matches()) {
            throw new IllegalArgumentException("A class starts with a letter and has only letters, digits and underscores.");
        }
        record(Page.CONTROLS, "Add " + key, null, () -> ruleEdits.remove(key), () -> ruleEdits.put(key, new LinkedHashMap<>()));
    }

    private static void addRules(Set<RuleKey> rules, String key, Map<String, JsonElement> props, boolean isClass) {
        RuleKey rule = parseRule(key, isClass);
        if (rule == null) {
            return;
        }
        rules.add(rule);
        if (props.get("states") instanceof JsonObject states) {
            states.keySet().forEach(state -> rules.add(rule.withState(state)));
        }
    }

    /** The rule of a theme key: {@code Button}, {@code Button[CHECK]}, or a class's {@code Button.primary}. */
    static RuleKey parseRule(String key, boolean isClass) {
        if (isClass) {
            int dot = key.indexOf('.');
            return dot <= 0 ? null : new RuleKey(key.substring(0, dot), null, key.substring(dot + 1), null);
        }
        int bracket = key.indexOf('[');
        if (bracket > 0 && key.endsWith("]")) {
            return new RuleKey(key.substring(0, bracket), key.substring(bracket + 1, key.length() - 1), null, null);
        }
        return RuleKey.of(key);
    }

    /** The base theme's properties for {@code key} (a state's, for a state), or null. */
    private Map<String, JsonElement> baseRule(RuleKey key) {
        Map<String, Map<String, JsonElement>> section = key.isClass() ? base.classes() : base.controls();
        Map<String, JsonElement> rule = section.get(key.themeKey());
        if (rule == null || key.state() == null) {
            return rule;
        }
        if (rule.get("states") instanceof JsonObject states && states.get(key.state()) instanceof JsonObject state) {
            Map<String, JsonElement> props = new LinkedHashMap<>();
            state.entrySet().forEach(e -> props.put(e.getKey(), e.getValue()));
            return props;
        }
        return null;
    }

    private JsonObject baseStates(RuleKey key) {
        Map<String, Map<String, JsonElement>> section = key.isClass() ? base.classes() : base.controls();
        Map<String, JsonElement> rule = section.get(key.themeKey());
        return rule != null && rule.get("states") instanceof JsonObject states ? states.deepCopy() : new JsonObject();
    }

    // ------------------------------------------------------------------ the finish page: identity, file, saving

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    /** Where Finish saves the theme, once chosen; set when a theme file is opened. */
    public Optional<Path> file() {
        return Optional.ofNullable(file);
    }

    /** Whether the theme is dark: as set on the finish page, else the palette's. */
    public boolean dark() {
        return darkFlag != null ? darkFlag : palette.dark();
    }

    /** Why {@code candidate} cannot be a theme's id, or null if it can. */
    public static String idProblem(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "The theme needs an id.";
        }
        return candidate.matches("[A-Za-z0-9][A-Za-z0-9._-]*") ? null
                : "An id has only letters, digits, dots, dashes and underscores, and starts with a letter or a digit.";
    }

    /** The theme's id, name and dark flag. These are not undone with the pages' edits. */
    public void setIdentity(String newId, String newTitle, boolean isDark) {
        String problem = idProblem(newId);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        String name = newTitle == null || newTitle.isBlank() ? newId : newTitle.strip();
        Boolean flag = isDark == palette.dark() ? null : isDark;
        if (newId.equals(id) && name.equals(title) && Objects.equals(flag, darkFlag)) {
            return;
        }
        id = newId;
        title = name;
        darkFlag = flag;
        lastKey = null;
        changed();
    }

    public void setFile(Path path) {
        file = path;
    }

    /**
     * What is wrong with the theme: errors, which keep it from loading (unknown names, cycles, bad
     * values), then warnings (contrast pairs below their minimum). Errors start with "Error: ".
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        for (String error : com.zapthemes.core.style.ThemeValidator.validate(theme)) {
            problems.add("Error: " + error);
        }
        for (ContrastCheck.Pair pair : contrast()) {
            if (!pair.ok()) {
                problems.add(String.format(java.util.Locale.ROOT, "Warning: %s on %s is %.1f:1, below %.1f:1", pair.name(),
                        pair.against(), pair.ratio(), pair.minimum()));
            }
        }
        return problems;
    }

    /** Writes {@link #theme()} to {@code path}: a full theme that needs no other, with no {@code extends}. */
    public void save(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, toJson(), StandardCharsets.UTF_8);
        file = path;
    }

    /** The theme as {@link #save} writes it. */
    public String toJson() {
        StringWriter writer = new StringWriter();
        try {
            com.zapthemes.core.ThemeJson.write(theme, writer);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return writer.toString();
    }

    // ------------------------------------------------------------------ page 2: names

    /** Adds a name with {@code parts} ({@link #COLOR}, {@link #FONT} or {@link #BOTH}). */
    public void addName(String name, Set<String> parts) {
        String problem = nameProblem(name, null);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        checkParts(parts);
        Set<String> copy = Set.copyOf(parts);
        record(Page.NAMES, "Add " + name, null, () -> added.remove(name), () -> added.put(name, copy));
    }

    /** Renames an added name; whatever refers to it follows. */
    public void renameName(String from, String to) {
        if (!added.containsKey(from)) {
            throw new IllegalArgumentException("Only names you added can be renamed: " + from);
        }
        if (from.equals(to)) {
            return;
        }
        String problem = nameProblem(to, from);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        record(Page.NAMES, "Rename " + from, null, () -> rename(to, from), () -> rename(from, to));
    }

    /** Removes an added name that nothing refers to. */
    public void removeName(String name) {
        if (!added.containsKey(name)) {
            throw new IllegalArgumentException("Only names you added can be removed: " + name);
        }
        List<String> users = usedBy(name);
        if (!users.isEmpty()) {
            throw new IllegalArgumentException("\"" + name + "\" is used by " + String.join(", ", users) + "; change those first.");
        }
        Set<String> parts = added.get(name);
        ColorChoice color = userColors.get(name);
        FontChoice font = userFonts.get(name);
        record(Page.NAMES, "Remove " + name, null, () -> {
            added.put(name, parts);
            putOrRemove(userColors, name, color);
            putOrRemove(userFonts, name, font);
        }, () -> {
            added.remove(name);
            userColors.remove(name);
            userFonts.remove(name);
        });
    }

    /** What an added name holds; a part nothing refers to can be dropped. */
    public void setParts(String name, Set<String> parts) {
        Set<String> old = added.get(name);
        if (old == null) {
            throw new IllegalArgumentException("Only names you added can change what they hold: " + name);
        }
        checkParts(parts);
        if (old.equals(parts)) {
            return;
        }
        if (!parts.contains("color") && userColors.values().stream().anyMatch(c -> c instanceof ColorChoice.Ref r && r.name().equals(name))
                || !parts.contains("font") && userFonts.values().stream().anyMatch(f -> name.equals(f.inherit()))) {
            throw new IllegalArgumentException("\"" + name + "\" is used by " + String.join(", ", usedBy(name)) + "; change those first.");
        }
        Set<String> copy = Set.copyOf(parts);
        ColorChoice color = userColors.get(name);
        FontChoice font = userFonts.get(name);
        record(Page.NAMES, "Change " + name, null, () -> {
            added.put(name, old);
            putOrRemove(userColors, name, color);
            putOrRemove(userFonts, name, font);
        }, () -> {
            added.put(name, copy);
            if (!copy.contains("color")) {
                userColors.remove(name);
            }
            if (!copy.contains("font")) {
                userFonts.remove(name);
            }
        });
    }

    /** Forgets every color the user set or the duplicated theme gave, so all fill from the palette. */
    public void fillAllFromPalette() {
        Map<String, ColorChoice> old = new LinkedHashMap<>(userColors);
        Set<String> oldTheme = new LinkedHashSet<>(fromTheme);
        Map<String, ColorChoice> kept = new LinkedHashMap<>();
        // Added names have no fill of their own, so they keep what they have.
        old.forEach((name, choice) -> {
            if (added.containsKey(name)) {
                kept.put(name, choice);
            }
        });
        record(Page.NAMES, "Fill from palette", null, () -> {
            userColors.clear();
            userColors.putAll(old);
            fromTheme.clear();
            fromTheme.addAll(oldTheme);
        }, () -> {
            userColors.clear();
            userColors.putAll(kept);
            fromTheme.retainAll(kept.keySet());
        });
    }

    // ------------------------------------------------------------------ page 3: colors and fonts

    /** {@code name}'s color, or null to fill it from the palette again; changes to one name in a row undo as one. */
    public void setColor(String name, ColorChoice choice) {
        NameInfo info = name(name).orElseThrow(() -> new IllegalArgumentException("No name " + name));
        if (!info.hasColor()) {
            throw new IllegalArgumentException("\"" + name + "\" holds no color");
        }
        if (choice instanceof ColorChoice.Ref ref && !refersToColor(ref.name(), name)) {
            throw new IllegalArgumentException("\"$" + ref.name() + "\" is not a swatch or another name with a color");
        }
        ColorChoice old = userColors.get(name);
        boolean wasTheme = fromTheme.contains(name);
        if (Objects.equals(old, choice) && !wasTheme) {
            return;
        }
        record(Page.VALUES, (choice == null ? "Fill " : "Color of ") + name, "color:" + name, () -> {
            putOrRemove(userColors, name, old);
            if (wasTheme) {
                fromTheme.add(name);
            }
        }, () -> {
            putOrRemove(userColors, name, choice);
            fromTheme.remove(name);
        });
    }

    /** {@code name}'s font, or null for the base theme's; changes to one name in a row undo as one. */
    public void setFont(String name, FontChoice choice) {
        NameInfo info = name(name).orElseThrow(() -> new IllegalArgumentException("No name " + name));
        if (!info.hasFont()) {
            throw new IllegalArgumentException("\"" + name + "\" holds no font");
        }
        if (choice != null && choice.inherit() != null) {
            if (name(choice.inherit()).filter(NameInfo::hasFont).isEmpty()) {
                throw new IllegalArgumentException("\"" + choice.inherit() + "\" is not a name with a font");
            }
            if (inheritsFrom(choice.inherit(), name)) {
                throw new IllegalArgumentException("\"" + choice.inherit() + "\" already inherits from \"" + name + "\"");
            }
        }
        FontChoice old = userFonts.get(name);
        if (Objects.equals(old, choice)) {
            return;
        }
        record(Page.VALUES, (choice == null ? "Reset font of " : "Font of ") + name, "font:" + name,
                () -> putOrRemove(userFonts, name, old), () -> putOrRemove(userFonts, name, choice));
    }

    // ------------------------------------------------------------------ undo

    /** Forgets every page's undo history, as after opening the theme the wizard edits. */
    public void clearHistory() {
        for (Page page : Page.values()) {
            history.put(page, new UndoStack());
        }
        lastKey = null;
        changed();
    }

    public boolean undo(Page page) {
        lastKey = null;
        boolean undone = history.get(page).undo();
        if (undone) {
            changed();
        }
        return undone;
    }

    public boolean redo(Page page) {
        lastKey = null;
        boolean redone = history.get(page).redo();
        if (redone) {
            changed();
        }
        return redone;
    }

    // ------------------------------------------------------------------ internals

    /** Everything an edit can change, for edits that change it all. */
    private record State(Palette palette, Theme base, Theme duplicated, Map<String, Set<String>> added,
            Map<String, ColorChoice> userColors, Map<String, FontChoice> userFonts, Collection<String> fromTheme,
            String id, String title, Path file, Map<RuleKey, Map<String, JsonElement>> ruleEdits) {
        State {
            added = new LinkedHashMap<>(added);
            userColors = new LinkedHashMap<>(userColors);
            userFonts = new LinkedHashMap<>(userFonts);
            fromTheme = List.copyOf(fromTheme);
            Map<RuleKey, Map<String, JsonElement>> rules = new LinkedHashMap<>();
            ruleEdits.forEach((key, props) -> rules.put(key, new LinkedHashMap<>(props)));
            ruleEdits = rules;
        }
    }

    private State state() {
        return new State(palette, base, duplicated, added, userColors, userFonts, fromTheme, id, title, file, ruleEdits);
    }

    private void restore(State state) {
        palette = state.palette();
        base = state.base();
        duplicated = state.duplicated();
        added.clear();
        added.putAll(state.added());
        userColors.clear();
        userColors.putAll(state.userColors());
        userFonts.clear();
        userFonts.putAll(state.userFonts());
        fromTheme.clear();
        fromTheme.addAll(state.fromTheme());
        id = state.id();
        title = state.title();
        file = state.file();
        darkFlag = null;
        ruleEdits.clear();
        state.ruleEdits().forEach((key, props) -> ruleEdits.put(key, new LinkedHashMap<>(props)));
    }

    private void editPalette(String label, String key, Palette next, Theme nextBase, Theme nextDuplicated) {
        Palette oldPalette = palette;
        Theme oldBase = base;
        Theme oldDuplicated = duplicated;
        record(Page.PALETTE, label, key, () -> {
            palette = oldPalette;
            base = oldBase;
            duplicated = oldDuplicated;
        }, () -> {
            palette = next;
            base = nextBase;
            duplicated = nextDuplicated;
        });
    }

    /** Makes an edit and records it on {@code page}; an edit with the last edit's key joins it. */
    private void record(Page page, String label, String key, Runnable undo, Runnable redo) {
        UndoStack stack = history.get(page);
        boolean coalesce = key != null && key.equals(lastKey) && stack.top() != null && key.equals(stack.top().key());
        redo.run();
        stack.push(new UndoStack.Edit(label, key, undo, redo), coalesce);
        lastKey = key;
        changed();
    }

    private void changed() {
        recompute();
        listeners.forEach(Runnable::run);
    }

    private void recompute() {
        Map<String, RGB> user = new LinkedHashMap<>();
        userColors.forEach((name, choice) -> direct(choice).ifPresent(rgb -> user.put(name, rgb)));
        Map<String, RGB> filled = new LinkedHashMap<>(NameFill.fill(palette, user));
        // A name that refers to another name gets that name's color, not the fill's.
        for (NameInfo info : names()) {
            if (live(userColors.get(info.name())) instanceof ColorChoice.Ref ref && palette.swatch(ref.name()).isEmpty()) {
                filled.remove(info.name());
            }
        }
        // References to other names, and added names' defaults, once the names they refer to have colors.
        for (int pass = 0; pass < 8; pass++) {
            boolean progress = false;
            for (NameInfo info : names()) {
                if (!info.hasColor() || filled.containsKey(info.name())) {
                    continue;
                }
                ColorChoice choice = colorOf(info);
                if (choice instanceof ColorChoice.Ref ref && filled.containsKey(ref.name())) {
                    filled.put(info.name(), filled.get(ref.name()));
                    progress = true;
                }
            }
            if (!progress) {
                break;
            }
        }
        filled.keySet().removeIf(name -> name(name).filter(NameInfo::hasColor).isEmpty());
        colors = java.util.Collections.unmodifiableMap(filled);
        theme = buildTheme();
    }

    /**
     * The user's color for {@code info}, or an added name's default, the accent's. A reference to a
     * swatch the palette no longer has counts as unset: the name is filled from the palette again.
     */
    private ColorChoice colorOf(NameInfo info) {
        ColorChoice choice = live(userColors.get(info.name()));
        return choice == null && info.added() ? new ColorChoice.Ref("accent") : choice;
    }

    private ColorChoice live(ColorChoice choice) {
        if (choice instanceof ColorChoice.Ref ref && palette.swatch(ref.name()).isEmpty()
                && name(ref.name()).filter(NameInfo::hasColor).isEmpty()) {
            return null;
        }
        return choice;
    }

    /** A literal color, or a swatch's; references to names resolve later. */
    private Optional<RGB> direct(ColorChoice choice) {
        if (choice instanceof ColorChoice.Literal literal) {
            return Optional.of(literal.rgb());
        }
        return palette.swatch(((ColorChoice.Ref) choice).name()).map(Swatch::color);
    }

    private Theme buildTheme() {
        Map<String, JsonElement> names = new LinkedHashMap<>();
        for (NameInfo info : names()) {
            JsonObject entry = new JsonObject();
            if (info.hasColor() && colors.containsKey(info.name())) {
                ColorChoice choice = colorOf(info);
                String value = choice instanceof ColorChoice.Ref ref ? "$" + ref.name()
                        : choice instanceof ColorChoice.Literal literal ? hex(literal.rgb())
                        : swatchOf(palette, colors.get(info.name())).map(s -> "$" + s).orElse(hex(colors.get(info.name())));
                entry.addProperty("color", value);
            }
            if (info.hasFont()) {
                FontChoice font = userFonts.get(info.name());
                if (font == null && info.added()) {
                    font = FontChoice.inheriting("foreground");
                }
                if (font != null) {
                    entry.add("font", font.toJson());
                }
            }
            if (!entry.isEmpty()) {
                names.put(info.name(), entry);
            }
        }
        Map<String, JsonElement> swatches = new LinkedHashMap<>();
        palette.toSwatches().forEach((name, hex) -> swatches.put(name, new JsonPrimitive(hex)));
        Map<String, JsonElement> editor = new LinkedHashMap<>();
        editor.put("libraryVersion", new JsonPrimitive(LIBRARY_VERSION));
        if (palette.rule() != null && palette.base() != null) {
            JsonObject generated = new JsonObject();
            generated.addProperty("rule", palette.rule().id());
            generated.addProperty("base", hex(palette.base()));
            JsonArray locked = new JsonArray();
            palette.swatches().stream().filter(Swatch::locked).forEach(s -> locked.add(s.name()));
            generated.add("locked", locked);
            editor.put("generator", generated);
        }
        Map<String, Map<String, JsonElement>> controls = new LinkedHashMap<>();
        Map<String, Map<String, JsonElement>> classes = new LinkedHashMap<>();
        ruleEdits.forEach((key, props) -> {
            Map<String, Map<String, JsonElement>> section = key.isClass() ? classes : controls;
            Map<String, JsonElement> rule = section.computeIfAbsent(key.themeKey(), k -> new LinkedHashMap<>());
            if (key.state() == null) {
                rule.putAll(props);
                return;
            }
            // A state's edit goes into the whole "states" object, since a rule's keys replace the base's.
            JsonObject states = rule.get("states") instanceof JsonObject edited ? edited : baseStates(key);
            JsonObject state = states.get(key.state()) instanceof JsonObject existing ? existing : new JsonObject();
            props.forEach(state::add);
            states.add(key.state(), state);
            rule.put("states", states);
        });
        Theme layered = new Theme(Theme.FORMAT_VERSION, id, title, null, dark(), swatches, names, null, controls, classes, null, null,
                editor).over(base);
        // A full theme: what the base extended is in it already.
        return new Theme(Theme.FORMAT_VERSION, layered.id(), layered.name(), null, layered.dark(), layered.swatches(),
                layered.names(), layered.defaults(), layered.controls(), layered.classes(), layered.platform(),
                layered.registries(), layered.editor());
    }

    private Theme baseFor(boolean dark) {
        String id = dark ? "zap-dark" : "zap-light";
        return themes.stream().filter(t -> t.id().equals(id)).findFirst()
                .or(() -> themes.stream().filter(t -> Boolean.valueOf(dark).equals(t.dark())).findFirst())
                .orElse(themes.getFirst());
    }

    private Harmony generatedRule() {
        return palette.rule() != null ? palette.rule() : Harmony.ANALOGOUS;
    }

    private RGB generatedBase() {
        if (palette.base() != null) {
            return palette.base();
        }
        return palette.swatch(PaletteGenerator.ACCENT).map(Swatch::color).orElse(DEFAULT_BASE);
    }

    private Palette keepLocked(Palette fresh) {
        Palette kept = fresh;
        for (Swatch swatch : palette.swatches()) {
            if (swatch.locked() && kept.swatch(swatch.name()).isPresent()) {
                kept = kept.with(swatch.name(), swatch);
            }
        }
        return kept;
    }

    /** {@code chosen} with every swatch named apart from the names, which a swatch would shadow. */
    private Palette safe(Palette chosen) {
        List<Swatch> swatches = new ArrayList<>();
        Set<String> taken = new LinkedHashSet<>();
        for (Swatch swatch : chosen.swatches()) {
            String name = swatch.name();
            if (BaseNames.get(name).isPresent() || added.containsKey(name) || !NAME.matcher(name).matches()) {
                name = (NAME.matcher(name).matches() ? name : "swatch") + "Swatch";
            }
            String unique = name;
            for (int i = 2; !taken.add(unique); i++) {
                unique = name + i;
            }
            swatches.add(swatch.withName(unique));
        }
        return new Palette(swatches, chosen.dark(), chosen.rule(), chosen.base());
    }

    private boolean refersToColor(String target, String owner) {
        if (palette.swatch(target).isPresent()) {
            return true;
        }
        if (target.equals(owner) || name(target).filter(NameInfo::hasColor).isEmpty()) {
            return false;
        }
        // No cycles: the target may not refer back to the owner.
        String next = target;
        for (int i = 0; i < 16 && userColors.get(next) instanceof ColorChoice.Ref ref; i++) {
            if (ref.name().equals(owner)) {
                return false;
            }
            next = ref.name();
        }
        return true;
    }

    private boolean inheritsFrom(String name, String ancestor) {
        String next = name;
        for (int i = 0; i < 16 && next != null; i++) {
            if (next.equals(ancestor)) {
                return true;
            }
            FontChoice font = userFonts.get(next);
            next = font == null ? (added.containsKey(next) ? "foreground" : null) : font.inherit();
        }
        return false;
    }

    private void rename(String from, String to) {
        added.put(to, added.remove(from));
        move(userColors, from, to);
        move(userFonts, from, to);
        if (fromTheme.remove(from)) {
            fromTheme.add(to);
        }
        userColors.replaceAll((owner, choice) -> choice instanceof ColorChoice.Ref ref && ref.name().equals(from) ? new ColorChoice.Ref(to) : choice);
        userFonts.replaceAll((owner, font) -> from.equals(font.inherit()) ? font.withInherit(to) : font);
    }

    private static <V> void move(Map<String, V> map, String from, String to) {
        V value = map.remove(from);
        if (value != null) {
            map.put(to, value);
        }
    }

    private static <K, V> void putOrRemove(Map<K, V> map, K key, V value) {
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    private static void checkParts(Set<String> parts) {
        if (parts.isEmpty() || !BOTH.containsAll(parts)) {
            throw new IllegalArgumentException("A name holds a color, a font, or both: " + parts);
        }
    }

    private static Optional<String> swatchOf(Palette palette, RGB rgb) {
        return palette.swatches().stream().filter(s -> s.color().equals(rgb)).map(Swatch::name).findFirst();
    }

    private static String libraryVersion() {
        try (InputStream in = EditorModel.class.getResourceAsStream("version.txt")) {
            return in == null ? "unknown" : new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            return "unknown";
        }
    }

    static String hex(RGB rgb) {
        return String.format("#%02X%02X%02X", rgb.red, rgb.green, rgb.blue);
    }
}
