package com.zapthemes.editor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;

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
        assertThat(model.colorChoice("background")).contains(ColorChoice.ref("backgroundSwatch"));

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

    private static void assertReadable(EditorModel model) {
        assertThat(model.contrast()).hasSizeGreaterThan(20);
        assertThat(model.contrast()).filteredOn(p -> !p.ok()).as("contrast failures")
                .extracting(ContrastCheck.Pair::name).isEmpty();
    }
}
