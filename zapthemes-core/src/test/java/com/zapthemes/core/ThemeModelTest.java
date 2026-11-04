package com.zapthemes.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.StringReader;
import java.io.StringWriter;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGBA;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.model.FontSpec;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StyleResolver;

class ThemeModelTest {

    private static final String BASE = """
            {
              "id": "base",
              "palette": { "bg": "#101010", "fg": "#EEEEEE", "font.ui": { "name": "Arial", "height": 10, "style": "bold" } },
              "defaults": { "background": "$bg", "foreground": "$fg", "font": "$font.ui" },
              "controls": {
                "Table": { "background": "#202020", "headerBackground": "#303030" },
                "Button[CHECK]": { "foreground": "#00FF00" }
              }
            }
            """;

    private static final String CHILD = """
            {
              "id": "child",
              "extends": "base",
              "palette": { "bg": "#FFFFFF" },
              "controls": {
                "Table": { "headerBackground": "#404040" },
                "Grid":  { "inherit": "Table", "foreground": "#123456CC" }
              }
            }
            """;

    private static Theme parse(String json) {
        return ThemeJson.read(new StringReader(json), "test.json");
    }

    private static Theme resolvedChild() {
        ThemeRegistry registry = new ThemeRegistry();
        registry.register(parse(BASE));
        registry.register(parse(CHILD));
        return registry.resolve("child");
    }

    @Test
    void resolvesPaletteReferencesAndDefaults() {
        ResolvedStyle label = new StyleResolver(parse(BASE)).resolve("Label", SWT.NONE);

        assertThat(label.color("background")).contains(new RGBA(0x10, 0x10, 0x10, 255));
        assertThat(label.font("font")).contains(new FontSpec("Arial", 10, SWT.BOLD));
    }

    @Test
    void extendsMergesPaletteAndRules() {
        Theme child = resolvedChild();
        StyleResolver resolver = new StyleResolver(child);

        assertThat(resolver.resolve("Label", SWT.NONE).color("background")).contains(new RGBA(255, 255, 255, 255));
        ResolvedStyle table = resolver.resolve("Table", SWT.NONE);
        assertThat(table.color("background")).contains(new RGBA(0x20, 0x20, 0x20, 255));
        assertThat(table.color("headerBackground")).contains(new RGBA(0x40, 0x40, 0x40, 255));
    }

    @Test
    void inheritCopiesAnotherControlRule() {
        ResolvedStyle grid = new StyleResolver(resolvedChild()).resolve("Grid", SWT.NONE);

        assertThat(grid.color("headerBackground")).contains(new RGBA(0x40, 0x40, 0x40, 255));
        assertThat(grid.color("foreground")).contains(new RGBA(0x12, 0x34, 0x56, 0xCC));
        assertThat(grid.has("inherit")).isFalse();
    }

    @Test
    void styleBitRulesApplyOnlyWhenBitsAreSet() {
        StyleResolver resolver = new StyleResolver(parse(BASE));

        assertThat(resolver.resolve("Button", SWT.CHECK).color("foreground")).contains(new RGBA(0, 255, 0, 255));
        assertThat(resolver.resolve("Button", SWT.PUSH).color("foreground")).contains(new RGBA(0xEE, 0xEE, 0xEE, 255));
    }

    @Test
    void reportsMalformedJsonWithPosition() {
        assertThatThrownBy(() -> parse("{ \"id\": \"x\", \"palette\": { \"bg\": } }"))
                .isInstanceOf(ThemeJson.ThemeFormatException.class)
                .hasMessageContaining("test.json")
                .hasMessageContaining("line 1");
        assertThatThrownBy(() -> parse("{ \"name\": \"no id\" }"))
                .hasMessageContaining("no \"id\"");
    }

    @Test
    void rejectsUnknownPaletteEntriesAndBadColors() {
        Theme theme = parse("""
                { "id": "bad", "defaults": { "background": "$missing", "foreground": "#12" } }
                """);
        ResolvedStyle style = new StyleResolver(theme).resolve("Label", SWT.NONE);

        assertThatThrownBy(() -> style.color("background")).hasMessageContaining("$missing");
        assertThatThrownBy(() -> style.color("foreground")).hasMessageContaining("#RRGGBB");
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        Theme theme = parse(BASE);
        StringWriter out = new StringWriter();
        ThemeJson.write(theme, out);

        assertThat(parse(out.toString())).isEqualTo(theme);
    }
}
