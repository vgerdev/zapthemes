package com.zapthemes.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.TypeSpec;
import com.zapthemes.editor.EditorModel.RuleKey;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.catalog.BaseNames;
import com.zapthemes.core.catalog.NameSpec;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.StyleResolver;
import com.zapthemes.core.style.ThemeNames;
import com.zapthemes.core.style.ThemeValidator;
import com.zapthemes.editor.EditorModel.Page;
import com.zapthemes.editor.EditorModel.Source;
import com.zapthemes.editor.palette.ContrastCheck;
import com.zapthemes.editor.palette.Harmony;
import com.zapthemes.themes.BundledThemes;

/** The wizard's model, with no display. */
class EditorModelTest {

    private static final RGB PLUM = new RGB(0x24, 0x1B, 0x2E);
    private static List<Theme> bundled;

    @BeforeAll
    static void load() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        bundled = BundledThemes.IDS.stream().map(registry::resolve).toList();
    }

    private static EditorModel model() {
        return new EditorModel(bundled, 7);
    }

    @Test
    void aNewPaletteGivesAReadableThemeOnTheDarkOrLightBase() {
        EditorModel model = model();
        assertThat(model.base().id()).isEqualTo("zap-dark");
        assertReadable(model);
        assertThat(ThemeNames.of(model.theme()).color("background").orElseThrow().rgb).isEqualTo(model.color("background").orElseThrow());

        model.setDark(false);
        assertThat(model.base().id()).isEqualTo("zap-light");
        assertThat(model.theme().dark()).isFalse();
        assertReadable(model);
    }

    @Test
    void theThemeIsValidAndItsNamesReferToTheSwatches() {
        EditorModel model = model();
        Theme theme = model.theme();
        assertThat(ThemeValidator.validate(theme)).isEmpty();
        assertThat(theme.swatches()).containsOnlyKeys("ground", "panel", "ink", "accent1", "accent2", "accent3");
        // The background is the ground swatch, written as a reference so it follows the swatch.
        assertThat(theme.names().get("background").getAsJsonObject().get("color").getAsString()).isEqualTo("$ground");
        assertThat(theme.editor().get("generator").getAsJsonObject().get("rule").getAsString()).isEqualTo("analogous");
        // Every catalog rule still resolves: the base theme's rules are all there.
        new StyleResolver(theme).resolve("Table", 0);
    }

    @Test
    void colorsTheUserSetSurviveRegenerateAndTheRestFollowThePalette() {
        EditorModel model = model();
        model.setColor("background", ColorChoice.of(PLUM));
        RGB surface = model.color("surface").orElseThrow();
        RGB accent = model.color("accent").orElseThrow();

        model.regenerate();

        assertThat(model.color("background")).contains(PLUM);
        assertThat(model.colorSource("background")).isEqualTo(Source.SET);
        assertThat(model.colorSource("accent")).isEqualTo(Source.AUTO);
        assertThat(model.color("accent")).isNotEqualTo(java.util.Optional.of(accent));
        // Surfaces step from the background the user set.
        assertThat(model.color("surface").orElseThrow()).isNotEqualTo(PLUM);
        assertThat(surface).isNotNull();
        assertReadable(model);
    }

    @Test
    void aColorReferringToASwatchFollowsIt() {
        EditorModel model = model();
        model.setColor("accent", ColorChoice.ref("accent2"));
        RGB pink = new RGB(0xD9, 0x4F, 0x9A);
        model.setSwatchColor("accent2", pink);
        assertThat(model.color("accent")).contains(pink);
        assertThat(model.theme().names().get("accent").getAsJsonObject().get("color").getAsString()).isEqualTo("$accent2");
        assertThatThrownBy(() -> model.setColor("accent", ColorChoice.ref("nothing"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachPageUndoesOnlyItsOwnEdits() {
        EditorModel model = model();
        var first = model.palette();
        model.setColor("background", ColorChoice.of(PLUM));
        model.regenerate();
        model.addName("badgeBackground", EditorModel.COLOR);

        model.undo(Page.PALETTE);

        assertThat(model.palette()).isEqualTo(first);
        assertThat(model.color("background")).contains(PLUM);
        assertThat(model.name("badgeBackground")).isPresent();

        model.undo(Page.VALUES);
        assertThat(model.colorChoice("background")).isEmpty();
        model.redo(Page.VALUES);
        assertThat(model.color("background")).contains(PLUM);
        model.undo(Page.NAMES);
        assertThat(model.name("badgeBackground")).isEmpty();
        assertThat(model.history(Page.NAMES).canUndo()).isFalse();
        assertThat(model.history(Page.NAMES).redoLabel()).contains("Add badgeBackground");
    }

    @Test
    void aRunOfChangesToOneSwatchUndoesAsOne() {
        EditorModel model = model();
        var before = model.palette();
        for (int lightness = 10; lightness < 60; lightness += 10) {
            model.setSwatchColor("accent1", new RGB(lightness, 2 * lightness, 3 * lightness));
        }
        model.setLocked("ground", true);
        model.setSwatchColor("accent1", new RGB(1, 2, 3));

        model.undo(Page.PALETTE);
        assertThat(model.palette().swatch("accent1").orElseThrow().color()).isEqualTo(new RGB(50, 100, 150));
        model.undo(Page.PALETTE);
        model.undo(Page.PALETTE);
        assertThat(model.palette()).isEqualTo(before);
    }

    @Test
    void addedNamesRenameWithWhatRefersToThemAndRemoveOnlyWhenUnused() {
        EditorModel model = model();
        model.addName("badgeBackground", EditorModel.COLOR);
        model.addName("badgeTint", EditorModel.COLOR);
        assertThat(model.color("badgeBackground")).as("an added color starts as the accent").isEqualTo(model.color("accent"));
        model.setColor("badgeTint", ColorChoice.ref("badgeBackground"));
        model.setColor("badgeBackground", ColorChoice.of(PLUM));
        assertThat(model.color("badgeTint")).contains(PLUM);

        model.renameName("badgeBackground", "badge2");

        assertThat(model.colorChoice("badgeTint")).contains(ColorChoice.ref("badge2"));
        assertThat(model.color("badge2")).contains(PLUM);
        assertThat(model.theme().names()).containsKey("badge2").doesNotContainKey("badgeBackground");
        assertThatThrownBy(() -> model.removeName("badge2")).hasMessageContaining("used by badgeTint");
        assertThatThrownBy(() -> model.renameName("foreground", "text")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> model.addName("ground", EditorModel.COLOR)).hasMessageContaining("swatch");
        assertThatThrownBy(() -> model.addName("2nd", EditorModel.COLOR)).hasMessageContaining("starts with a letter");

        model.undo(Page.NAMES);
        assertThat(model.colorChoice("badgeTint")).contains(ColorChoice.ref("badgeBackground"));
        assertThat(model.color("badgeTint")).contains(PLUM);

        model.setColor("badgeTint", null);
        model.removeName("badgeBackground");
        assertThat(model.name("badgeBackground")).isEmpty();
        model.undo(Page.NAMES);
        assertThat(model.color("badgeBackground")).contains(PLUM);
    }

    @Test
    void fontsInheritAndAreWrittenAsTheThemeWritesThem() {
        EditorModel model = model();
        model.addName("banner", EditorModel.BOTH);
        assertThat(model.font("banner")).isEqualTo(model.font("foreground"));

        model.setFont("banner", FontChoice.inheriting("heading1").withHeight(20));
        assertThat(model.font("banner").orElseThrow().height()).isEqualTo(20);
        assertThat(model.font("banner").orElseThrow().style()).isEqualTo(model.font("heading1").orElseThrow().style());
        model.setFont("heading1", new FontChoice(List.of("Georgia", "DejaVu Serif"), 16, SWT.BOLD | SWT.ITALIC, null));
        assertThat(model.font("banner").orElseThrow().names()).containsExactly("Georgia", "DejaVu Serif");
        assertThat(model.theme().names().get("heading1").getAsJsonObject().get("font").toString())
                .isEqualTo("{\"name\":[\"Georgia\",\"DejaVu Serif\"],\"height\":16,\"style\":\"bold|italic\"}");
        assertThatThrownBy(() -> model.setFont("heading1", FontChoice.inheriting("banner"))).hasMessageContaining("inherits");
        assertThatThrownBy(() -> model.setFont("border", FontChoice.inheriting("foreground"))).hasMessageContaining("no font");

        model.setFont("heading1", null);
        assertThat(model.fontSource("heading1")).isEqualTo(Source.AUTO);
        assertThat(model.font("heading1")).isEqualTo(ThemeNames.of(model.base()).font("heading1"));
    }

    @Test
    void duplicatingABundledThemeKeepsEveryColorAndRule() {
        for (Theme source : bundled) {
            EditorModel model = model();
            model.duplicate(source);

            assertThat(model.base()).isSameAs(source);
            assertThat(model.duplicated()).contains(source);
            Theme copy = model.theme();
            assertThat(copy.id()).isEqualTo(source.id() + "-copy");
            ThemeNames before = ThemeNames.of(source);
            ThemeNames after = ThemeNames.of(copy);
            for (NameSpec spec : BaseNames.ALL) {
                if (spec.hasColor()) {
                    assertThat(after.color(spec.name())).as(source.id() + " " + spec.name()).isEqualTo(before.color(spec.name()));
                    assertThat(model.colorSource(spec.name())).isEqualTo(Source.THEME);
                }
                if (spec.hasFont()) {
                    assertThat(after.font(spec.name())).as(source.id() + " " + spec.name()).isEqualTo(before.font(spec.name()));
                }
            }
            // No swatch shadows a name.
            assertThat(copy.swatches().keySet()).allMatch(s -> BaseNames.get(s).isEmpty());
            for (String type : List.of("Table", "Button", "TitleBar", "Ribbon")) {
                assertThat(new StyleResolver(copy).resolve(type, 0).toString())
                        .isEqualTo(new StyleResolver(source).resolve(type, 0).toString());
            }

            // Editing one name marks it set; undo goes back to the palette before.
            model.setColor("accent", ColorChoice.of(PLUM));
            assertThat(model.colorSource("accent")).isEqualTo(Source.SET);
            model.undo(Page.PALETTE);
            assertThat(model.duplicated()).isEmpty();
            assertThat(model.base().id()).isEqualTo("zap-dark");
        }
    }

    @Test
    void fillingFromThePaletteForgetsTheDuplicatedColors() {
        EditorModel model = model();
        model.duplicate(bundled.get(1));
        model.addName("badgeBackground", EditorModel.COLOR);
        model.setColor("badgeBackground", ColorChoice.of(PLUM));

        model.fillAllFromPalette();

        assertThat(model.colorSource("foreground")).isEqualTo(Source.AUTO);
        assertThat(model.color("badgeBackground")).contains(PLUM);
        assertReadable(model);
        model.undo(Page.NAMES);
        assertThat(model.colorSource("foreground")).isEqualTo(Source.THEME);
    }

    @Test
    void aPaletteWhoseSwatchesAreNamedLikeNamesIsRenamed() {
        EditorModel model = model();
        var palette = com.zapthemes.editor.palette.Palette.fromTheme(bundled.get(0));
        assertThat(palette.swatches().getFirst().name()).isEqualTo("background");

        model.usePalette("Zap Light's palette", palette);

        assertThat(model.palette().swatches()).extracting(s -> s.name()).allMatch(n -> BaseNames.get(n).isEmpty())
                .contains("backgroundSwatch");
        assertThat(model.base().id()).isEqualTo("zap-light");
        assertReadable(model);
    }

    @Test
    void aReferenceToASwatchThePaletteNoLongerHasFillsFromThePaletteAgain() {
        EditorModel model = model();
        model.duplicate(bundled.get(1));
        // The theme writes its background as a color, which duplicating keeps; refer to the swatch instead.
        assertThat(model.colorChoice("background")).contains(ColorChoice.of(new RGB(0x1E, 0x1F, 0x22)));
        model.setColor("background", ColorChoice.ref("backgroundSwatch"));

        model.regenerate();
        model.usePalette("Generated", com.zapthemes.editor.palette.Palette.of(true,
                List.of(new RGB(0x10, 0x14, 0x1C), new RGB(0x2A, 0xAC, 0xB8), new RGB(0xE0, 0xE4, 0xEA), new RGB(0xF0, 0x71, 0x78),
                        new RGB(0x5F, 0xC4, 0x8A))));

        assertThat(model.colorChoice("background")).isEmpty();
        assertThat(model.colorSource("background")).isEqualTo(Source.AUTO);
        assertThat(model.color("background")).contains(new RGB(0x10, 0x14, 0x1C));
        assertThat(ThemeValidator.validate(model.theme())).isEmpty();
        assertReadable(model);
        // Undo brings the swatch back, and the reference with it.
        model.undo(Page.PALETTE);
        model.undo(Page.PALETTE);
        assertThat(model.colorChoice("background")).contains(ColorChoice.ref("backgroundSwatch"));
    }

    @Test
    void listenersHearEveryEditAndUndo() {
        EditorModel model = model();
        int[] heard = { 0 };
        model.addListener(() -> heard[0]++);
        model.regenerate();
        model.setLocked("ink", true);
        model.undo(Page.PALETTE);
        assertThat(model.undo(Page.NAMES)).isFalse();
        assertThat(heard[0]).isEqualTo(3);
        assertThat(model.history(Page.PALETTE).undoLabel()).contains("Regenerate");
        assertThat(model.history(Page.PALETTE).redoLabel()).contains("Lock ink");
    }

    @Test
    void sizeRuleAndBaseKeepLockedSwatches() {
        EditorModel model = model();
        model.setLocked("ground", true);
        RGB ground = model.palette().swatch("ground").orElseThrow().color();
        model.setSize(8);
        model.setRule(Harmony.TRIADIC);
        model.setBaseColor(new RGB(0xF0, 0x71, 0x78));
        assertThat(model.palette().swatches()).hasSize(8);
        assertThat(model.palette().rule()).isEqualTo(Harmony.TRIADIC);
        assertThat(model.palette().swatch("ground").orElseThrow().color()).isEqualTo(ground);
        assertThat(Set.copyOf(model.palette().toSwatches().keySet())).hasSize(8);
    }

    @Test
    void openingABundledThemeAndSavingItLosesNothingAndSavingAgainGivesTheSameFile() throws Exception {
        PropertyCatalog catalog = PropertyCatalog.load();
        for (Theme source : bundled) {
            EditorModel model = model();
            model.open(source, null);
            String first = model.toJson();
            Theme saved = ThemeJson.read(new StringReader(first), source.id());

            assertThat(saved.id()).isEqualTo(source.id());
            assertThat(saved.name()).isEqualTo(source.name());
            assertThat(saved.parent()).as("a full theme").isNull();
            assertThat(first).doesNotContain("\"extends\"");
            assertThat(ThemeValidator.validate(saved)).as(source.id()).isEmpty();
            // Everything as written, names included: references stay references.
            assertThat(saved.names()).as(source.id() + " names").isEqualTo(source.names());
            assertThat(saved.controls()).isEqualTo(source.controls());
            assertThat(saved.classes()).isEqualTo(source.classes());
            assertThat(saved.platform()).isEqualTo(source.platform());
            assertThat(saved.registries()).isEqualTo(source.registries());
            assertThat(saved.defaults()).isEqualTo(source.defaults());
            assertThat(saved.editor().get("libraryVersion").getAsString()).isEqualTo(EditorModel.LIBRARY_VERSION);
            assertSameLook(catalog, source, saved);

            EditorModel again = model();
            again.open(saved, null);
            assertThat(again.toJson()).as(source.id() + " saved twice").isEqualTo(first);
        }
    }

    @Test
    void aGeneratedThemeSavedAndOpenedAgainKeepsItsPaletteGeneratorAndEdits(@org.junit.jupiter.api.io.TempDir Path dir)
            throws Exception {
        EditorModel model = model();
        model.setLocked("ground", true);
        model.setColor("accent", ColorChoice.ref("accent2"));
        model.setRuleValue(RuleKey.of("Table"), "headerBackground", new JsonPrimitive("$accent"));
        model.setIdentity("ocean", "Ocean", true);
        Path file = dir.resolve("ocean.json");
        model.save(file);

        ThemeRegistry registry = new ThemeRegistry();
        Theme loaded = registry.loadFile(file);
        EditorModel reopened = model();
        reopened.open(loaded, file);

        assertThat(reopened.id()).isEqualTo("ocean");
        assertThat(reopened.file()).contains(file);
        assertThat(reopened.palette().swatches()).isEqualTo(model.palette().swatches());
        assertThat(reopened.palette().rule()).isEqualTo(model.palette().rule());
        assertThat(reopened.palette().base()).isEqualTo(model.palette().base());
        assertThat(reopened.colorChoice("accent")).contains(ColorChoice.ref("accent2"));
        assertThat(reopened.ruleValue(RuleKey.of("Table"), "headerBackground")).contains(new JsonPrimitive("$accent"));
        assertThat(reopened.toJson()).isEqualTo(java.nio.file.Files.readString(file));
        // And the palette still follows: a new swatch color reaches the accent.
        reopened.setSwatchColor("accent2", PLUM);
        assertThat(reopened.color("accent")).contains(PLUM);
    }

    @Test
    void controlRulesAreEditedOverTheBaseAndUndoOnTheirOwnPage() {
        EditorModel model = model();
        RuleKey table = RuleKey.of("Table");
        JsonElement before = model.ruleValue(table, "headerBackground").orElseThrow();

        model.setRuleValue(table, "headerBackground", new JsonPrimitive("#112233"));
        assertThat(model.theme().controls().get("Table").get("headerBackground").getAsString()).isEqualTo("#112233");
        assertThat(model.isEdited(table, "headerBackground")).isTrue();
        // The rule's other properties stay the base's.
        assertThat(model.theme().controls().get("Table").keySet()).containsAll(model.base().controls().get("Table").keySet());

        model.setColor("background", ColorChoice.of(PLUM));
        model.undo(Page.CONTROLS);
        assertThat(model.ruleValue(table, "headerBackground")).contains(before);
        assertThat(model.color("background")).contains(PLUM);

        model.setRuleValue(table, "headerBackground", new JsonPrimitive("$accent"));
        model.setRuleValue(table, "headerBackground", null);
        assertThat(model.isEdited(table, "headerBackground")).isFalse();
        assertThat(model.theme().controls().get("Table")).isEqualTo(model.base().controls().get("Table"));
    }

    @Test
    void aStatesEditKeepsTheOtherStatesAndNewRulesCanBeAdded() {
        EditorModel model = model();
        String type = model.base().controls().entrySet().stream()
                .filter(e -> e.getValue().get("states") instanceof JsonObject states && states.size() >= 2)
                .map(java.util.Map.Entry::getKey).filter(k -> !k.contains("[")).findFirst().orElseThrow();
        JsonObject baseStates = model.base().controls().get(type).get("states").getAsJsonObject();
        String first = baseStates.keySet().iterator().next();
        RuleKey state = RuleKey.of(type).withState(first);
        assertThat(model.rules()).contains(state);

        model.setRuleValue(state, "background", new JsonPrimitive("#010203"));

        JsonObject states = model.theme().controls().get(type).get("states").getAsJsonObject();
        assertThat(states.keySet()).isEqualTo(baseStates.keySet());
        assertThat(states.get(first).getAsJsonObject().get("background").getAsString()).isEqualTo("#010203");
        assertThat(ThemeValidator.validate(model.theme())).isEmpty();

        RuleKey flat = new RuleKey("Button", "FLAT", null, null);
        RuleKey note = new RuleKey("Label", null, "note", null);
        model.addRule(flat);
        model.addRule(note);
        model.setRuleValue(note, "foreground", new JsonPrimitive("$muted"));
        assertThat(model.theme().controls()).containsKey("Button[FLAT]");
        assertThat(model.theme().classes().get("Label.note").get("foreground").getAsString()).isEqualTo("$muted");
        assertThat(model.rules()).contains(flat, note);
        assertThatThrownBy(() -> model.addRule(new RuleKey("Button", "flat", null, null))).hasMessageContaining("SWT constants");
        model.undo(Page.CONTROLS);
        model.undo(Page.CONTROLS);
        model.undo(Page.CONTROLS);
        assertThat(model.theme().controls()).doesNotContainKey("Button[FLAT]");
    }

    @Test
    void identityAndProblems() {
        EditorModel model = model();
        assertThat(model.id()).isEqualTo("untitled");
        model.setIdentity("ocean-dark", "  Ocean Dark ", false);
        assertThat(model.theme().id()).isEqualTo("ocean-dark");
        assertThat(model.theme().name()).isEqualTo("Ocean Dark");
        assertThat(model.theme().dark()).isFalse();
        assertThat(EditorModel.idProblem("no spaces")).isNotNull();
        assertThat(model.problems()).isEmpty();

        model.setColor("muted", ColorChoice.of(model.color("background").orElseThrow()));
        assertThat(model.problems()).anyMatch(p -> p.startsWith("Warning: muted on background"));
        assertThatThrownBy(() -> model.setRuleValue(RuleKey.of("Table"), "headerBackground", new JsonPrimitive("$nothing")))
                .hasMessageContaining("not a swatch");
        assertThatThrownBy(() -> model.setRuleValue(RuleKey.of("Table"), "font", new JsonPrimitive("$border")))
                .hasMessageContaining("name with a font");
        model.open(broken(bundled.get(1)), null);
        assertThat(model.problems()).anyMatch(p -> p.startsWith("Error: ") && p.contains("nothing"));
        model.duplicate(bundled.get(1));
        assertThat(model.id()).isEqualTo("zap-dark-copy");
    }

    /** {@code theme} with a reference to a name it does not have, as a hand-edited file may. */
    static Theme broken(Theme theme) {
        Theme error = new Theme(Theme.FORMAT_VERSION, theme.id(), theme.name(), null, null, null, null, null,
                java.util.Map.of("Table", java.util.Map.of("headerBackground", new JsonPrimitive("$nothing"))), null, null, null, null);
        return error.over(theme);
    }

    /** Every catalog type, its variants, the classes on it and its states resolve alike in both themes. */
    private static void assertSameLook(PropertyCatalog catalog, Theme a, Theme b) throws ReflectiveOperationException {
        StyleResolver one = new StyleResolver(a);
        StyleResolver two = new StyleResolver(b);
        int checked = 0;
        for (TypeSpec type : catalog.types()) {
            List<Integer> styles = new java.util.ArrayList<>(List.of(0));
            for (String variant : type.variants()) {
                int bits = 0;
                for (String bit : variant.split("\\|")) {
                    bits |= SWT.class.getField(bit.strip()).getInt(null);
                }
                styles.add(bits);
            }
            List<String> classes = new java.util.ArrayList<>(java.util.Arrays.asList((String) null));
            catalog.classes().forEach(c -> classes.add(c.name()));
            for (int style : styles) {
                for (String cls : classes) {
                    var first = one.resolve(type.type(), style, cls);
                    var second = two.resolve(type.type(), style, cls);
                    assertThat(second.toString()).as(type.type() + " " + style + " " + cls).isEqualTo(first.toString());
                    for (String state : type.states()) {
                        assertThat(second.state(state).toString()).isEqualTo(first.state(state).toString());
                    }
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
    }

    private static void assertReadable(EditorModel model) {
        assertThat(model.contrast()).hasSizeGreaterThan(20);
        assertThat(model.contrast()).filteredOn(p -> !p.ok()).as("contrast failures")
                .extracting(ContrastCheck.Pair::name).isEmpty();
    }
}
