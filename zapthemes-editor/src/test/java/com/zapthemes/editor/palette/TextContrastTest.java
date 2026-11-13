package com.zapthemes.editor.palette;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeJson;
import com.zapthemes.core.ThemeRegistry;
import com.zapthemes.core.model.Theme;
import com.zapthemes.themes.BundledThemes;

class TextContrastTest {

    private static Theme theme(String json) {
        return ThemeJson.read(new StringReader(json), "test");
    }

    private static List<String> messages(Theme theme) {
        return TextContrast.check(theme).stream().map(TextContrast.Problem::message).toList();
    }

    @Test
    void theBundledThemesHaveReadableText() {
        ThemeRegistry registry = new ThemeRegistry();
        BundledThemes.RESOURCES.forEach(r -> registry.loadResource(BundledThemes.class.getClassLoader(), r));
        for (String id : BundledThemes.IDS) {
            assertThat(messages(registry.resolve(id))).as(id).isEmpty();
        }
    }

    @Test
    void aForegroundTooCloseToItsBackgroundIsReported() {
        Theme theme = theme("""
                { "id": "t", "swatches": { "paper": "#F7F8FA", "ink": "#1F2328", "mist": "#D0D3D8" },
                  "defaults": { "background": "$paper", "foreground": "$ink" },
                  "controls": { "Table": { "headerBackground": "$paper", "headerForeground": "$mist" } } }
                """);
        assertThat(messages(theme)).containsExactly("Table: headerForeground #D0D3D8 on headerBackground #F7F8FA is 1.4:1, below 4.5:1");
    }

    @Test
    void aRuleIsReadOverTheOnesItBuildsOn() {
        // Button changes only its background; the foreground it keeps from defaults no longer reads.
        Theme theme = theme("""
                { "id": "t", "defaults": { "background": "#FFFFFF", "foreground": "#202020" },
                  "controls": { "Button": { "background": "#303030" },
                                "Button[CHECK]": { "foreground": "#FFFFFF" } },
                  "classes": { "Button.primary": { "foreground": "#3A3A3A" } } }
                """);
        assertThat(messages(theme)).containsExactly(
                "Button: foreground #202020 on background #303030 is 1.2:1, below 4.5:1",
                "Button.primary: foreground #3A3A3A on background #303030 is 1.2:1, below 4.5:1");
    }

    @Test
    void statesAreChecked() {
        Theme theme = theme("""
                { "id": "t", "defaults": { "background": "#FFFFFF", "foreground": "#000000" },
                  "controls": { "Tree": { "states": { "selected": { "background": "#111111" } } } } }
                """);
        assertThat(messages(theme)).containsExactly("Tree:selected: foreground #000000 on background #111111 is 1.1:1, below 4.5:1");
    }

    @Test
    void disabledTextNeedsOnlyThreeToOne() {
        Theme theme = theme("""
                { "id": "t", "controls": { "Label": { "background": "#FFFFFF", "foreground": "#000000",
                                                      "disabledBackground": "#FFFFFF", "disabledForeground": "#949494" } } }
                """);
        assertThat(messages(theme)).isEmpty();
        Theme fainter = theme("""
                { "id": "t", "controls": { "Label": { "disabledBackground": "#FFFFFF", "disabledForeground": "#B0B0B0" } } }
                """);
        assertThat(messages(fainter)).containsExactly("Label: disabledForeground #B0B0B0 on disabledBackground #FFFFFF is 2.2:1, below 3.0:1");
    }

    @Test
    void aLightWindowsMenuBarIsReportedWhateverItsForeground() {
        // SWT draws a colored menu bar's text white; menuBarForeground does not change it.
        Theme light = theme("""
                { "id": "t", "platform": { "win32": { "menuBarBackground": "#F7F8FA", "menuBarForeground": "#1F2328" } } }
                """);
        assertThat(messages(light)).singleElement().asString()
                .startsWith("platform.win32 (SWT draws menu bar text white; the menu bar stays native): menu bar text #FFFFFF on menuBarBackground #F7F8FA");
        Theme dark = theme("""
                { "id": "t", "platform": { "win32": { "menuBarBackground": "#1E1F22", "menuBarForeground": "#DFE1E5" } } }
                """);
        assertThat(messages(dark)).isEmpty();
    }

    @Test
    void platformTextColorsAreChecked() {
        Theme theme = theme("""
                { "id": "t", "platform": { "gtk": { "menuBarBackground": "#202020", "menuBarForeground": "#303030" } } }
                """);
        assertThat(messages(theme)).containsExactly("platform.gtk: menuBarForeground #303030 on menuBarBackground #202020 is 1.2:1, below 4.5:1");
    }

    @Test
    void namesAndReferencesAreFollowedAndBadValuesLeftToTheValidator() {
        Theme theme = theme("""
                { "id": "t", "names": { "background": { "color": "#FFFFFF" }, "foreground": { "color": "#EEEEEE" } },
                  "defaults": { "background": "$background", "foreground": "$foreground" },
                  "controls": { "Text": { "foreground": "$nothing" }, "Link": { "linkForeground": "blue", "linkBackground": "#FFFFFF" } } }
                """);
        assertThat(messages(theme)).containsExactly("defaults: foreground #EEEEEE on background #FFFFFF is 1.2:1, below 4.5:1");
    }

    @Test
    void backgroundKeys() {
        assertThat(TextContrast.backgroundFor("foreground")).isEqualTo("background");
        assertThat(TextContrast.backgroundFor("selectionForeground")).isEqualTo("selectionBackground");
        assertThat(TextContrast.backgroundFor("Foreground")).isNull();
        assertThat(TextContrast.backgroundFor("borderColor")).isNull();
    }
}
