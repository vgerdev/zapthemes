package com.zapthemes.core.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StyleResolver;

/**
 * Whether a theme's native scroll bars are dark. The settings are in the {@code "*"} block, so
 * they resolve the same on every build platform.
 */
class Win32ThemeSupportTest {

    private static Theme parse(String json) {
        return ThemeJson.read(new StringReader(json), "test.json");
    }

    private static ResolvedStyle platform(String json) {
        return new StyleResolver(parse(json)).platform();
    }

    @Test
    void darkScrollbarsWhenTheThemeSaysSo() {
        assertThat(Win32ThemeSupport.darkScrollbars(platform("""
                { "id": "t", "platform": { "*": { "darkScrollbars": true } } }
                """))).isTrue();
        assertThat(Win32ThemeSupport.darkScrollbars(platform("""
                { "id": "t", "platform": { "*": { "darkScrollbars": false, "darkThemePreferred": true } } }
                """))).as("an explicit false wins").isFalse();
    }

    @Test
    void darkScrollbarsFollowDarkThemePreferredWhenLeftOut() {
        assertThat(Win32ThemeSupport.darkScrollbars(platform("""
                { "id": "t", "platform": { "*": { "darkThemePreferred": true } } }
                """))).isTrue();
        assertThat(Win32ThemeSupport.darkScrollbars(platform("""
                { "id": "t", "platform": { "*": { "darkThemePreferred": false } } }
                """))).isFalse();
        assertThat(Win32ThemeSupport.darkScrollbars(platform("{ \"id\": \"t\" }"))).as("no platform block").isFalse();
    }

    @Test
    void aThemeThatExtendsADarkOneHasDarkScrollbars() {
        // As zap-high-contrast: extends zap-dark and adds only a menu bar color of its own.
        ThemeRegistry registry = new ThemeRegistry();
        registry.register(parse("""
                { "id": "dark", "platform": { "*": { "darkThemePreferred": true, "darkScrollbars": true } } }
                """));
        registry.register(parse("""
                { "id": "contrast", "extends": "dark", "platform": { "*": { "menuBarBorderColor": "#FFFFFF" } } }
                """));

        assertThat(Win32ThemeSupport.darkScrollbars(new StyleResolver(registry.resolve("contrast")).platform())).isTrue();
    }
}
