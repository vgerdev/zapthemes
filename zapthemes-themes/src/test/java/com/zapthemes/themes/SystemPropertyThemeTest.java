package com.zapthemes.themes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The values {@code -Dzapthemes.theme} accepts. */
class SystemPropertyThemeTest {

    @Test
    void aBundledIdResolvesWithItsParents() {
        var theme = SystemPropertyTheme.resolve("zap-high-contrast", null);

        assertThat(theme.id()).isEqualTo("zap-high-contrast");
        // From zap-dark and zap-base, which it extends.
        assertThat(theme.names()).containsKeys("bg", "selection");
    }

    @Test
    void aThemeFileMayExtendABundledTheme(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("acme.json");
        Files.writeString(file, "{ \"id\": \"acme\", \"extends\": \"zap-dark\", \"palette\": { \"accent\": \"#FF8800\" } }");

        var theme = SystemPropertyTheme.resolve(file.toString(), null);

        assertThat(theme.id()).isEqualTo("acme");
        assertThat(com.zapthemes.core.style.ThemeNames.of(theme).color("accent")).contains(new org.eclipse.swt.graphics.RGBA(0xFF, 0x88, 0, 255));
        assertThat(theme.names()).containsKey("surface");
    }

    @Test
    void anUnknownValueSaysWhatIsAccepted() {
        assertThatThrownBy(() -> SystemPropertyTheme.resolve("zap-purple", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zap-dark");
    }
}
