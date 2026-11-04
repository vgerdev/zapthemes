package com.zapthemes.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGBA;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StyleResolver;
import com.zapthemes.core.style.ThemeValidator;
import com.zapthemes.themes.BundledThemes;

class ThemeValidatorTest {

    private static Theme parse(String json) {
        return ThemeJson.read(new StringReader(json), "test.json");
    }

    @Test
    void bundledThemesAreValid() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));

        for (String id : BundledThemes.IDS) {
            assertThat(ThemeValidator.validate(registry.resolve(id))).as(id).isEmpty();
        }
    }

    /** WCAG AAA for normal text: every text color against its background reaches 7:1. */
    @Test
    void highContrastThemeMeetsWcagAaa() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        StyleResolver resolver = new StyleResolver(registry.resolve("zap-high-contrast"));

        record Pair(String what, ResolvedStyle style, String foreground, String background) {
        }
        ResolvedStyle label = resolver.resolve("Label", SWT.NONE);
        ResolvedStyle table = resolver.resolve("Table", SWT.NONE);
        ResolvedStyle tab = resolver.resolve("CTabFolder", SWT.NONE);
        ResolvedStyle system = resolver.registry(SystemColors.REGISTRY);
        java.util.List<Pair> pairs = java.util.List.of(
                new Pair("Label", label, "foreground", "background"),
                new Pair("Text", resolver.resolve("Text", SWT.NONE), "foreground", "background"),
                new Pair("Button", resolver.resolve("Button", SWT.PUSH), "foreground", "background"),
                new Pair("Label.muted", resolver.resolve("Label", SWT.NONE, "muted"), "foreground", "background"),
                new Pair("Table header", table, "headerForeground", "headerBackground"),
                new Pair("Table selection", table, "selectionForeground", "selectionBackground"),
                new Pair("CTabFolder selection", tab, "selectionForeground", "selectionBackground"),
                new Pair("system list selection", system, "COLOR_LIST_SELECTION_TEXT", "COLOR_LIST_SELECTION"),
                new Pair("system list", system, "COLOR_LIST_FOREGROUND", "COLOR_LIST_BACKGROUND"),
                new Pair("system disabled", system, "COLOR_WIDGET_DISABLED_FOREGROUND", "COLOR_WIDGET_BACKGROUND"),
                new Pair("system link", system, "COLOR_LINK_FOREGROUND", "COLOR_WIDGET_BACKGROUND"));
        for (Pair pair : pairs) {
            RGBA fg = pair.style().color(pair.foreground()).orElseThrow();
            RGBA bg = pair.style().color(pair.background()).orElseThrow();
            assertThat(contrast(fg, bg)).as(pair.what()).isGreaterThanOrEqualTo(7.0);
        }
        RGBA link = resolver.resolve("Link", SWT.NONE).color("linkForeground").orElseThrow();
        assertThat(contrast(link, label.color("background").orElseThrow())).as("Link").isGreaterThanOrEqualTo(7.0);
    }

    private static double contrast(RGBA a, RGBA b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(RGBA c) {
        return 0.2126 * linear(c.rgb.red) + 0.7152 * linear(c.rgb.green) + 0.0722 * linear(c.rgb.blue);
    }

    private static double linear(int channel) {
        double v = channel / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    @Test
    void reportsEveryKindOfMistakeWithItsPath() {
        Theme theme = parse("""
                {
                  "id": "broken",
                  "palette": { "bg": "#000000" },
                  "defaults": { "background": "$nope" },
                  "controls": {
                    "Button[CHEKC]": { },
                    "Grid": { "inherit": "Missing", "headerBackground": "red" },
                    "Label": { "font": "Arial", "states": { "hover": { "foreground": "#12" } } }
                  },
                  "classes": { "primary": { } }
                }
                """);

        assertThat(ThemeValidator.validate(theme)).anySatisfy(p -> assertThat(p).startsWith("defaults.background").contains("$nope"))
                .anySatisfy(p -> assertThat(p).startsWith("controls.Button[CHEKC]").contains("CHEKC"))
                .anySatisfy(p -> assertThat(p).startsWith("controls.Grid.inherit").contains("Missing"))
                .anySatisfy(p -> assertThat(p).startsWith("controls.Grid.headerBackground"))
                .anySatisfy(p -> assertThat(p).startsWith("controls.Label.font"))
                .anySatisfy(p -> assertThat(p).startsWith("controls.Label.states.hover.foreground"))
                .anySatisfy(p -> assertThat(p).startsWith("classes.primary"));
    }

    @Test
    void classRulesApplyInOrderAfterTypeRules() {
        Theme theme = parse("""
                {
                  "id": "classes",
                  "defaults": { "background": "#000000" },
                  "controls": { "Button": { "background": "#111111" } },
                  "classes": {
                    "*.danger": { "background": "#FF0000", "foreground": "#FFFFFF" },
                    "Button.primary": { "background": "#0000FF" }
                  }
                }
                """);
        StyleResolver resolver = new StyleResolver(theme);

        assertThat(resolver.resolve("Button", SWT.PUSH, "primary").color("background")).contains(new RGBA(0, 0, 255, 255));
        ResolvedStyle both = resolver.resolve("Button", SWT.PUSH, "danger primary");
        assertThat(both.color("background")).contains(new RGBA(0, 0, 255, 255));
        assertThat(both.color("foreground")).contains(new RGBA(255, 255, 255, 255));
        assertThat(resolver.resolve("Label", SWT.NONE, "danger").color("background")).contains(new RGBA(255, 0, 0, 255));
    }

    @Test
    void statesOverlayTheBaseStyle() {
        Theme theme = parse("""
                { "id": "states", "controls": { "Button": { "background": "#111111", "foreground": "#EEEEEE",
                  "states": { "hover": { "background": "#222222" } } } } }
                """);
        ResolvedStyle button = new StyleResolver(theme).resolve("Button", SWT.PUSH);

        assertThat(button.states()).containsExactly("hover");
        assertThat(button.state("hover").color("background")).contains(new RGBA(0x22, 0x22, 0x22, 255));
        assertThat(button.state("hover").color("foreground")).contains(new RGBA(0xEE, 0xEE, 0xEE, 255));
        assertThat(button.state("pressed")).isSameAs(button);
    }

    @Test
    void platformSettingsAreMergedThroughExtends() {
        ThemeRegistry registry = new ThemeRegistry();
        registry.register(parse("""
                { "id": "base", "platform": { "win32": { "darkScrollbars": false, "titleColoring": true } } }
                """));
        registry.register(parse("""
                { "id": "child", "extends": "base", "platform": { "win32": { "darkScrollbars": true } } }
                """));

        var win32 = registry.resolve("child").platformSettings("win32");
        assertThat(win32.get("darkScrollbars").getAsBoolean()).isTrue();
        assertThat(win32.get("titleColoring").getAsBoolean()).isTrue();
        assertThat(registry.resolve("child").platformSettings("gtk")).isEmpty();
    }
}
