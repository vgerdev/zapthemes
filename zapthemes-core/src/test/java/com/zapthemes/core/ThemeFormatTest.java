package com.zapthemes.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGBA;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zapthemes.core.model.FontSpec;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StyleResolver;
import com.zapthemes.core.style.ThemeNames;
import com.zapthemes.core.style.ThemeValidator;
import com.zapthemes.themes.BundledThemes;

/** Theme format 2: swatches, common names, references by part, font lists, and upgrades from 1. */
class ThemeFormatTest {

    private static final String V2 = """
            {
              "formatVersion": 2,
              "id": "ocean",
              "dark": true,
              "swatches": { "ink": "#0E1A24", "sea": "#2AACB8", "foam": "#DDE7EE" },
              "names": {
                "background": { "color": "$ink" },
                "foreground": { "color": "$foam", "font": { "name": ["No Such Font", "Arial"], "height": 9 } },
                "heading1":   { "color": "$foreground", "font": { "inherit": "$foreground", "height": 14, "style": "bold" } },
                "border":     { "color": "#2B4256" },
                "code":       { "font": { "name": "Consolas", "height": 10 } }
              },
              "defaults": { "background": "$background", "foreground": "$foreground", "font": "$foreground" },
              "controls": {
                "Label": { "font": "$heading1", "borderColor": "$border", "tint": "$sea", "renderer": "$code" }
              },
              "editor": { "generator": { "rule": "analogous", "locked": ["ink"] } }
            }
            """;

    private static Theme parse(String json) {
        return ThemeJson.read(new StringReader(json), "test.json");
    }

    private static RGBA rgb(int value) {
        return new RGBA(value >> 16 & 0xFF, value >> 8 & 0xFF, value & 0xFF, 255);
    }

    @Test
    void aReferenceTakesThePartItsKeyAsksFor() {
        ResolvedStyle label = new StyleResolver(parse(V2)).resolve("Label", SWT.NONE);

        assertThat(label.color("background")).contains(rgb(0x0E1A24));
        assertThat(label.color("foreground")).as("a name's color").contains(rgb(0xDDE7EE));
        assertThat(label.color("tint")).as("a swatch, read as a color").contains(rgb(0x2AACB8));
        assertThat(label.color("borderColor")).contains(rgb(0x2B4256));
        assertThat(label.font("font")).as("heading1's font, from foreground's")
                .contains(new FontSpec(List.of("No Such Font", "Arial"), 14, SWT.BOLD));
        assertThat(label.value("renderer").orElseThrow().isJsonObject()).as("a font-only name, read untyped").isTrue();
    }

    @Test
    void namesAndSwatchesCanBeReadDirectly() {
        ThemeNames names = ThemeNames.of(parse(V2));

        assertThat(names.color("heading1")).as("through foreground to a swatch").contains(rgb(0xDDE7EE));
        assertThat(names.color("ink")).contains(rgb(0x0E1A24));
        assertThat(names.font("code")).contains(new FontSpec("Consolas", 10, SWT.NORMAL));
        assertThat(names.color("code")).isEmpty();
        assertThat(names.font("border")).isEmpty();
    }

    @Test
    void aNameWithoutThePartIsAnError() {
        Theme theme = parse("""
                { "formatVersion": 2, "id": "t", "swatches": { "ink": "#000000" },
                  "names": { "border": { "color": "#112233" }, "a": { "color": "$b" }, "b": { "color": "$a" } },
                  "controls": { "Label": { "font": "$border", "foreground": "$a", "background": "$nope", "headerFont": "$ink" } } }
                """);
        ResolvedStyle label = new StyleResolver(theme).resolve("Label", SWT.NONE);

        assertThatThrownBy(() -> label.font("font")).hasMessageContaining("\"$border\"").hasMessageContaining("has no font");
        assertThatThrownBy(() -> label.color("foreground")).hasMessageContaining("cycle").hasMessageContaining("$a -> $b -> $a");
        assertThatThrownBy(() -> label.color("background")).hasMessageContaining("Unknown name \"$nope\"");
        assertThatThrownBy(() -> label.font("headerFont")).hasMessageContaining("is a swatch");
        assertThat(ThemeValidator.validate(theme)).anyMatch(p -> p.startsWith("names.a.color:") && p.contains("cycle"))
                .anyMatch(p -> p.startsWith("controls.Label.font:"));
    }

    @Test
    void theValidatorChecksSwatchesAndNames() {
        Theme theme = parse("""
                { "formatVersion": 2, "id": "t",
                  "swatches": { "ink": "#000000", "sea": "$ink", "bad": "#12", "border": "#111111" },
                  "names": { "border": { "color": "#222222" }, "plain": "#333333", "empty": {},
                             "odd": { "colour": "#444444" }, "font.x": { "font": { "inherit": "$font.x" } } } }
                """);

        assertThat(ThemeValidator.validate(theme)).anyMatch(p -> p.startsWith("swatches.sea:") && p.contains("not a reference"))
                .anyMatch(p -> p.startsWith("swatches.bad:") && p.contains("#RRGGBB"))
                .anyMatch(p -> p.startsWith("swatches.border:") && p.contains("ambiguous"))
                .anyMatch(p -> p.startsWith("names.plain:") && p.contains("must be an object"))
                .anyMatch(p -> p.startsWith("names.empty:"))
                .anyMatch(p -> p.startsWith("names.odd.colour:"))
                .anyMatch(p -> p.startsWith("names.font.x.font:") && p.contains("cycle"));
        assertThat(ThemeValidator.validate(parse(V2))).isEmpty();
    }

    @Test
    void aFontListPicksTheFirstInstalledFamily() {
        // Picking the installed family needs a Display: ThemeManagerTest. Here: the list is kept as written.
        FontSpec font = ThemeNames.of(parse(V2)).font("foreground").orElseThrow();

        assertThat(font.names()).containsExactly("No Such Font", "Arial");
        assertThat(font.name()).isEqualTo("No Such Font");
    }

    @Test
    void writesTheCurrentFormatAndKeepsTheEditorSection() throws Exception {
        Theme theme = parse(V2);
        StringWriter out = new StringWriter();
        ThemeJson.write(theme, out);
        JsonObject json = JsonParser.parseString(out.toString()).getAsJsonObject();

        assertThat(json.keySet().iterator().next()).isEqualTo("formatVersion");
        assertThat(json.get("formatVersion").getAsInt()).isEqualTo(Theme.FORMAT_VERSION);
        assertThat(json.has("palette")).isFalse();
        assertThat(parse(out.toString())).isEqualTo(theme);
        assertThat(parse(out.toString()).editor().get("generator").getAsJsonObject().get("rule").getAsString()).isEqualTo("analogous");
    }

    @Test
    void anExtendingThemeMergesNamesByPart() {
        ThemeRegistry registry = new ThemeRegistry();
        registry.register(parse(V2));
        registry.register(parse("""
                { "formatVersion": 2, "id": "child", "extends": "ocean",
                  "names": { "foreground": { "color": "#FFFFFF" } } }
                """));
        ThemeNames names = ThemeNames.of(registry.resolve("child"));

        assertThat(names.color("foreground")).contains(rgb(0xFFFFFF));
        assertThat(names.font("foreground")).as("kept from ocean").isPresent();
        assertThat(registry.resolve("child").dark()).isTrue();
    }

    /** The schema is valid JSON and documents every section of the format. */
    @Test
    void theSchemaCoversTheFormat() throws Exception {
        JsonObject properties;
        try (var in = new InputStreamReader(BundledThemes.class.getClassLoader().getResourceAsStream("zapthemes/theme.schema.json"),
                StandardCharsets.UTF_8)) {
            properties = JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("properties");
        }
        for (var component : Theme.class.getRecordComponents()) {
            String name = component.getName().equals("parent") ? "extends" : component.getName();
            assertThat(properties.has(name)).as("schema documents \"" + name + "\"").isTrue();
        }
        assertThat(properties.getAsJsonObject("formatVersion").get("maximum").getAsInt()).isEqualTo(Theme.FORMAT_VERSION);
    }

    @Test
    void aNewerFormatIsRefused() {
        assertThatThrownBy(() -> parse("{ \"formatVersion\": 99, \"id\": \"x\" }"))
                .isInstanceOf(ThemeJson.ThemeFormatException.class)
                .hasMessageContaining("format version 99").hasMessageContaining("update ZapThemes");
        assertThatThrownBy(() -> parse("{ \"formatVersion\": \"two\", \"id\": \"x\" }"))
                .hasMessageContaining("positive integer");
    }

    @Test
    void version1PaletteEntriesBecomeNamesWithOnePart() {
        Theme theme = parse("""
                { "id": "old",
                  "palette": { "bg": "#101010", "alias": "$bg", "font.ui": { "name": "Arial", "height": 10 },
                               "font.alias": "$font.ui", "fromParent": "$surface", "font.fromParent": "$font.default" },
                  "defaults": { "background": "$alias", "font": "$font.alias" },
                  "platform": { "*": { "darkThemePreferred": true } } }
                """);

        assertThat(theme.formatVersion()).isEqualTo(2);
        assertThat(theme.dark()).isTrue();
        assertThat(theme.names().get("bg").getAsJsonObject().keySet()).containsExactly("color");
        assertThat(theme.names().get("alias").getAsJsonObject().keySet()).containsExactly("color");
        assertThat(theme.names().get("font.ui").getAsJsonObject().keySet()).containsExactly("font");
        assertThat(theme.names().get("font.alias").getAsJsonObject().keySet()).containsExactly("font");
        assertThat(theme.names().get("fromParent").getAsJsonObject().keySet()).containsExactly("color");
        assertThat(theme.names().get("font.fromParent").getAsJsonObject().keySet()).containsExactly("font");
        ResolvedStyle label = new StyleResolver(theme).resolve("Label", SWT.NONE);
        assertThat(label.color("background")).contains(rgb(0x101010));
        assertThat(label.font("font")).contains(new FontSpec("Arial", 10, SWT.NORMAL));
    }

    /**
     * Every reference in the bundled (version 1) themes resolves after the upgrade to what it did
     * under version 1's rules (one palette, followed by name), and the upgraded themes write and
     * read back unchanged.
     */
    @Test
    void bundledThemesResolveAsTheyDidInVersion1() throws Exception {
        Map<String, JsonObject> raw = new HashMap<>();
        ThemeRegistry registry = new ThemeRegistry();
        for (String resource : BundledThemes.RESOURCES) {
            try (var in = new InputStreamReader(BundledThemes.class.getClassLoader().getResourceAsStream(resource), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(in).getAsJsonObject();
                raw.put(json.get("id").getAsString(), json);
            }
            registry.loadResource(BundledThemes.class.getClassLoader(), resource);
        }
        int checked = 0;
        for (String id : BundledThemes.IDS) {
            Map<String, JsonElement> palette = v1Palette(raw, id);
            Theme theme = registry.resolve(id);
            ThemeNames names = ThemeNames.of(theme);
            List<Map<String, JsonElement>> blocks = new java.util.ArrayList<>();
            blocks.add(theme.defaults());
            blocks.addAll(theme.controls().values());
            blocks.addAll(theme.classes().values());
            blocks.addAll(theme.platform().values());
            for (Map.Entry<String, Map<String, JsonElement>> registryBlock : theme.registries().entrySet()) {
                blocks.add(registryBlock.getValue());
            }
            for (Map<String, JsonElement> block : blocks) {
                checked += compare(block, palette, names, id);
            }

            StringWriter out = new StringWriter();
            ThemeJson.write(theme, out);
            assertThat(parse(out.toString())).as(id + " written and read").isEqualTo(theme);
        }
        assertThat(checked).as("references checked").isGreaterThan(300);
    }

    private static int compare(Map<String, JsonElement> block, Map<String, JsonElement> palette, ThemeNames names, String id) {
        int checked = 0;
        for (Map.Entry<String, JsonElement> e : block.entrySet()) {
            String key = e.getKey();
            JsonElement value = e.getValue();
            if (key.equals(ResolvedStyle.STATES) && value.isJsonObject()) {
                for (Map.Entry<String, JsonElement> state : value.getAsJsonObject().entrySet()) {
                    checked += compare(state.getValue().getAsJsonObject().asMap(), palette, names, id);
                }
                continue;
            }
            if (!(value.isJsonPrimitive() && value.getAsString().startsWith("$"))) {
                continue;
            }
            JsonElement v1 = value;
            while (v1.isJsonPrimitive() && v1.getAsString().startsWith("$")) {
                v1 = palette.get(v1.getAsString().substring(1));
            }
            ThemeNames.Part part = key.equals("font") || key.endsWith("Font") || v1.isJsonObject() ? ThemeNames.Part.FONT
                    : ThemeNames.Part.COLOR;
            assertThat(names.resolve(value, part, key)).as(id + " " + key + " = " + value).isEqualTo(v1);
            checked++;
        }
        return checked;
    }

    /** Version 1's palette for {@code id}, its {@code extends} chain merged, from the raw files. */
    private static Map<String, JsonElement> v1Palette(Map<String, JsonObject> raw, String id) {
        JsonObject theme = raw.get(id);
        Map<String, JsonElement> palette = theme.has("extends") ? v1Palette(raw, theme.get("extends").getAsString()) : new HashMap<>();
        if (theme.has("palette")) {
            palette.putAll(theme.getAsJsonObject("palette").asMap());
        }
        return palette;
    }
}
