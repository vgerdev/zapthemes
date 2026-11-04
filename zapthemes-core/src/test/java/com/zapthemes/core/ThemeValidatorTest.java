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

        for (String id : new String[] { "zap-light", "zap-dark" }) {
            assertThat(ThemeValidator.validate(registry.resolve(id))).as(id).isEmpty();
        }
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
