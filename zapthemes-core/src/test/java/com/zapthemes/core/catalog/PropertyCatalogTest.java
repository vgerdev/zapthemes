package com.zapthemes.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.model.Theme;

/** The catalog's model and the core part of it (the full catalog is checked in zapthemes-demo). */
class PropertyCatalogTest {

    private final PropertyCatalog catalog = PropertyCatalog.load();

    @Test
    void baseNamesAreUniqueAndHaveParts() {
        Set<String> names = new HashSet<>();
        for (NameSpec name : BaseNames.ALL) {
            assertThat(names.add(name.name())).as(name.name() + " listed once").isTrue();
            assertThat(name.parts()).as(name.name()).isNotEmpty().isSubsetOf("color", "font");
            name.contrastWith().forEach(other -> assertThat(BaseNames.get(other)).as(name.name() + " on " + other).isPresent());
        }
        assertThat(BaseNames.get("foreground").orElseThrow().parts()).containsExactlyInAnyOrder("color", "font");
        assertThat(BaseNames.get("border").orElseThrow().parts()).containsExactly("color");
        assertThat(BaseNames.get("code").orElseThrow().parts()).containsExactly("font");
    }

    @Test
    void everySwtTypeHasTheBaseProperties() {
        assertThat(catalog.type("Button")).isPresent();
        assertThat(catalog.property("Label", "foreground").orElseThrow().defaultName()).isEqualTo("foreground");
        assertThat(catalog.property("Label", "font").orElseThrow().kind()).isEqualTo(PropertyKind.FONT);
        assertThat(catalog.property("Composite", "backgroundMode").orElseThrow().choices()).contains("force");
        assertThat(catalog.property("Label", "backgroundMode")).as("not a composite").isEmpty();
        assertThat(catalog.property("MenuItem", "background")).as("styled through its parent").isEmpty();
        assertThat(catalog.property("CTabItem", "background")).isEmpty();
        assertThat(catalog.property("CTabItem", "foreground")).isPresent();
    }

    @Test
    void builtinStylersPropertiesHaveTheirDefaults() {
        assertThat(catalog.property("Table", "background").orElseThrow().defaultName()).as("own default over the base one")
                .isEqualTo("surface");
        assertThat(catalog.property("Table", "gridLineColor").orElseThrow().defaultName()).isEqualTo("gridLine");
        assertThat(catalog.property("Tree", "alternateRowBackground")).as("Table only").isEmpty();
        assertThat(catalog.property("TableItem", "headerBackground")).as("inherited from Table").isPresent();
        assertThat(catalog.states("Table")).containsExactly("hover");
        assertThat(catalog.property("CTabFolder", "selectionBarThickness").orElseThrow().defaultValue().getAsInt()).isEqualTo(2);
        assertThat(catalog.type("Button").orElseThrow().variants()).contains("PUSH", "CHECK");
        assertThat(catalog.registry("systemColors")).anyMatch(p -> p.key().equals("COLOR_LIST_SELECTION"));
        assertThat(catalog.platform("gtk")).anyMatch(p -> p.key().equals("menuSelectionBackground"));
    }

    @Test
    void everyDefaultNameIsABaseNameWithThatPart() {
        for (TypeSpec type : catalog.types()) {
            for (PropertySpec p : type.allProperties()) {
                checkDefault(type.type() + "." + p.key(), p);
            }
        }
        catalog.registryNames().forEach(r -> catalog.registry(r).forEach(p -> checkDefault("registries." + r + "." + p.key(), p)));
        catalog.platformNames().forEach(ws -> catalog.platform(ws).forEach(p -> checkDefault("platform." + ws + "." + p.key(), p)));
    }

    static void checkDefault(String where, PropertySpec p) {
        if (p.defaultName() == null) {
            return;
        }
        NameSpec name = BaseNames.get(p.defaultName()).orElse(null);
        assertThat(name).as(where + " defaults to unknown name " + p.defaultName()).isNotNull();
        if (p.kind() == PropertyKind.COLOR) {
            assertThat(name.hasColor()).as(where + ": " + p.defaultName() + " has no color").isTrue();
        } else {
            assertThat(name.hasFont()).as(where + ": " + p.defaultName() + " has no font").isTrue();
        }
    }

    /** A widget themed through setters, as a PropertyStyler sees it. */
    public static class Widgetish {
        public void setNeedleColor(Color color) {
        }

        public void setTitleFont(Font font) {
        }

        public void setBackground(Color color) {
        }

        public void setThickness(int thickness) {
        }

        public static void setSharedColor(Color color) {
        }
    }

    @Test
    void reflectFindsColorAndFontSetters() {
        TypeSpec spec = TypeSpec.reflect("Widgetish", "Test", TypeSpec.Base.NONE, Widgetish.class)
                .defaults("needleColor=accent", "renderer.lineColor=gridLine", "textColorSelected=accent");

        assertThat(spec.properties()).extracting(PropertySpec::key)
                .containsExactly("needleColor", "titleFont", "renderer.lineColor", "textColorSelected");
        assertThat(spec.properties().get(0).defaultName()).isEqualTo("accent");
        assertThat(spec.properties().get(1).kind()).isEqualTo(PropertyKind.FONT);
        assertThat(spec.properties().get(3).kind()).as("a default name is a color").isEqualTo(PropertyKind.COLOR);
        assertThatThrownBy(() -> spec.defaults("needleColor")).hasMessageContaining("key=name");
    }

    @Test
    void describingATypeTwiceMergesIt() {
        PropertyCatalog merged = new PropertyCatalog.Builder()
                .type(TypeSpec.of("Thing", "A", TypeSpec.Base.CONTROL).with(PropertySpec.color("lineColor", null, "")))
                .type(TypeSpec.of("Thing", "", TypeSpec.Base.NONE).with(PropertySpec.color("lineColor", "gridLine", "Lines"))
                        .withStates("hover").described("A thing"))
                .build();
        TypeSpec thing = merged.type("Thing").orElseThrow();

        assertThat(thing.group()).isEqualTo("A");
        assertThat(thing.base()).isEqualTo(TypeSpec.Base.CONTROL);
        assertThat(merged.property("Thing", "lineColor").orElseThrow().defaultName()).isEqualTo("gridLine");
        assertThat(merged.property("Thing", "lineColor").orElseThrow().description()).isEqualTo("Lines");
        assertThat(thing.states()).containsExactly("hover");
        assertThat(thing.description()).isEqualTo("A thing");
    }

    @Test
    void undescribedListsWhatTheCatalogDoesNotKnow() {
        Theme theme = ThemeJson.read(new StringReader("""
                { "formatVersion": 2, "id": "t",
                  "defaults": { "background": "#000000", "sparkle": 1 },
                  "controls": { "Table": { "headerBackground": "#111111", "glow": "#222222",
                                           "states": { "hover": { "background": "#333333" }, "pressed": {} } },
                                "Button[CHECK]": { "foreground": "#444444" }, "Gizmo": { "background": "#555555" } },
                  "classes": { "Button.primary": { "background": "#666666" }, "*.shiny": { "foreground": "#777777" },
                               "*.muted": { "nope": 1 } },
                  "registries": { "systemColors": { "COLOR_LIST_SELECTION": "#888888", "COLOR_NOPE": "#000000" } },
                  "platform": { "gtk": { "menuBackground": "#999999", "wobble": true } } }
                """), "t.json");

        assertThat(catalog.undescribed(theme)).containsExactlyInAnyOrder(
                "defaults.sparkle: no type has this property",
                "controls.Table.glow: not a property of Table",
                "controls.Table.states.pressed: Table does not paint this state",
                "controls.Gizmo: unknown type \"Gizmo\"",
                "classes.*.shiny: unknown style class \"shiny\"",
                "classes.*.muted.nope: no type has this property",
                "registries.systemColors.COLOR_NOPE: not a known entry",
                "platform.gtk.wobble: not a known setting");
    }

    @Test
    void describesReadAcceptsTypesRegistriesAndPlatform() {
        assertThat(catalog.describesRead("Table", "headerBackground")).isTrue();
        assertThat(catalog.describesRead("Table", "glow")).isFalse();
        assertThat(catalog.describesRead("Gizmo", "background")).isFalse();
        assertThat(catalog.describesRead("registries.systemColors", "COLOR_INFO_BACKGROUND")).isTrue();
        assertThat(catalog.describesRead("platform", "darkScrollbars")).isTrue();
        assertThat(catalog.describesRead("platform", "wobble")).isFalse();
    }
}
