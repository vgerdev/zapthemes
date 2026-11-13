package com.zapthemes.editor.palette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;

import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;

/** The text on selected and highlighted items, as lists, tables and trees paint it. */
class SelectionContrastTest {

    private static final RGB WHITE = new RGB(255, 255, 255);
    private static final RGB NAVY = new RGB(0x10, 0x20, 0x60);
    private static final RGB SKY = new RGB(0xCF, 0xDE, 0xFC);

    private static Theme theme(String controls) {
        return ThemeJson.read(new StringReader("""
                {
                  "id": "t",
                  "names": {
                    "foreground": { "color": "#FFFFFF" },
                    "selection": { "color": "#102060" },
                    "selectionForeground": { "color": "#FFFFFF" },
                    "light": { "color": "#CFDEFC" }
                  },
                  "defaults": { "background": "#000000", "foreground": "$foreground" },
                  "controls": { %s }
                }
                """.formatted(controls)), "t");
    }

    private static SelectionContrast.Pair only(List<SelectionContrast.Pair> pairs, String type, String item) {
        return pairs.stream().filter(p -> p.type().equals(type) && p.item().equals(item)).findFirst().orElseThrow();
    }

    @Test
    void theBundledThemesAreReadable() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        for (String id : BundledThemes.IDS) {
            List<SelectionContrast.Pair> pairs = SelectionContrast.check(registry.resolve(id));
            assertThat(pairs).as(id).extracting(SelectionContrast.Pair::where)
                    .contains("List: selected items", "Table: selected rows", "Tree: selected rows", "Table: the focus cell",
                            "Table: the row under the mouse");
            assertThat(pairs).as(id).filteredOn(p -> !p.ok()).extracting(SelectionContrast.Pair::message).isEmpty();
        }
    }

    @Test
    void aListComparesItsSelectionForegroundWithItsSelectionBackground() {
        Theme theme = theme("""
                "List": { "selectionBackground": "$selection", "selectionForeground": "$selectionForeground" }""");
        SelectionContrast.Pair pair = only(SelectionContrast.check(theme), "List", "selected items");
        assertThat(pair.textKey()).isEqualTo("selectionForeground");
        assertThat(pair.backKey()).isEqualTo("selectionBackground");
        assertThat(pair.text()).isEqualTo("selectionForeground");
        assertThat(pair.on()).isEqualTo("selection");
        assertThat(pair.front()).isEqualTo(WHITE);
        assertThat(pair.back()).isEqualTo(NAVY);
        assertThat(pair.ratio()).isEqualTo(Contrast.ratio(WHITE, NAVY));
        assertThat(pair.ok()).isTrue();
        assertThat(pair.minimum()).isEqualTo(Contrast.TEXT);
    }

    @Test
    void anOverrideOfOneControlIsWhatIsChecked() {
        // White text on a light selection, in trees only; through inherit, as the resolver reads it.
        Theme theme = theme("""
                "Table": { "selectionBackground": "$selection", "selectionForeground": "$selectionForeground" },
                "Tree": { "inherit": "Table", "selectionBackground": "$light" }""");
        List<SelectionContrast.Pair> pairs = SelectionContrast.check(theme);
        assertThat(only(pairs, "Table", "selected rows").ok()).isTrue();
        SelectionContrast.Pair tree = only(pairs, "Tree", "selected rows");
        assertThat(tree.text()).isEqualTo("selectionForeground");
        assertThat(tree.on()).isEqualTo("light");
        assertThat(tree.ratio()).isEqualTo(Contrast.ratio(WHITE, SKY));
        assertThat(tree.ok()).isFalse();
        assertThat(SelectionContrast.failures(theme)).containsExactly(tree);
        assertThat(tree.message()).isEqualTo(String.format(java.util.Locale.ROOT,
                "Tree: selected rows: selectionForeground #FFFFFF on selectionBackground #CFDEFC is %.1f:1, below 4.5:1", tree.ratio()));
    }

    @Test
    void withoutASelectionForegroundTheTextKeepsItsForeground() {
        Theme theme = theme("""
                "List": { "selectionBackground": "#FFFFFF" }""");
        SelectionContrast.Pair pair = only(SelectionContrast.check(theme), "List", "selected items");
        assertThat(pair.textKey()).isEqualTo("foreground");
        assertThat(pair.text()).isEqualTo("foreground");
        assertThat(pair.on()).isEqualTo("List.selectionBackground");
        assertThat(pair.ratio()).isEqualTo(1.0);
    }

    @Test
    void theFocusCellAndTheRowUnderTheMouseAreChecked() {
        Theme theme = theme("""
                "Table": { "focusCellBackground": "$light", "focusCellForeground": "#000000",
                           "states": { "hover": { "background": "#EEEEEE" } } }""");
        List<SelectionContrast.Pair> pairs = SelectionContrast.check(theme);
        SelectionContrast.Pair focus = only(pairs, "Table", "the focus cell");
        assertThat(focus.text()).isEqualTo("Table.focusCellForeground");
        assertThat(focus.ok()).isTrue();
        SelectionContrast.Pair hover = only(pairs, "Table", "the row under the mouse");
        assertThat(hover.textKey()).isEqualTo("foreground");
        assertThat(hover.backKey()).isEqualTo("states.hover.background");
        assertThat(hover.on()).isEqualTo("Table.states.hover.background");
        assertThat(hover.ok()).isFalse();
    }

    @Test
    void aHighlightTheThemeDoesNotSetIsLeftToThePlatform() {
        assertThat(SelectionContrast.check(theme(""))).isEmpty();
        assertThat(SelectionContrast.check(theme("""
                "Table": { "selectionForeground": "#FFFFFF" }"""))).isEmpty();
    }
}
