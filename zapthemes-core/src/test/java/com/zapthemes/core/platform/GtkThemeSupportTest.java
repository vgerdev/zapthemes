package com.zapthemes.core.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;

import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.style.StyleResolver;

/** The menu CSS GTK gets from a theme's {@code platform} settings. */
class GtkThemeSupportTest {

    @Test
    void menuColorsBecomeCss() {
        // The "*" block, so the settings resolve the same on every build platform.
        String css = GtkThemeSupport.css(new StyleResolver(ThemeJson.read(new StringReader("""
                {
                  "id": "menus",
                  "palette": { "bg": "#102030" },
                  "platform": { "*": { "menuBarBackground": "$bg", "menuBarForeground": "#FFFFFF",
                                       "menuSelectionBackground": "#00000080" } }
                }
                """), "menus")).platform());

        assertThat(css).contains("menubar { background-color: rgba(16,32,48,1.0); background-image: none; color: rgba(255,255,255,1.0); }")
                .contains("menubar > menuitem { color: rgba(255,255,255,1.0); }")
                .contains(":hover { background-color: rgba(0,0,0,0.5019607843137255); background-image: none; }")
                .doesNotContain("menu, .menu");
    }

    @Test
    void noMenuColorsNoCss() {
        String css = GtkThemeSupport.css(new StyleResolver(ThemeJson.read(new StringReader("{ \"id\": \"plain\" }"), "plain")).platform());

        assertThat(css).isEmpty();
    }
}
