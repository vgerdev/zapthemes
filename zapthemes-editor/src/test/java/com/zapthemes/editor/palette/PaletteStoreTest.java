package com.zapthemes.editor.palette;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.swt.graphics.RGB;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParseException;

/** Saved palettes: one JSON file each, in the swatches and generator shapes of a theme. */
class PaletteStoreTest {

    @TempDir
    Path dir;

    @Test
    void aSavedPaletteLoadsBackTheSame() {
        PaletteStore store = new PaletteStore(dir.resolve("palettes"));
        Palette palette = new PaletteGenerator(1).generate(Harmony.TRIADIC, new RGB(0x2A, 0xAC, 0xB8), 6, true)
                .withLocked("accent", true);

        assertThat(store.list()).as("no directory yet").isEmpty();
        store.save("Ocean", palette);

        assertThat(store.list()).containsExactly("Ocean");
        assertThat(store.load("Ocean")).isEqualTo(palette);
    }

    @Test
    void theFileHasTheThemesShapes() throws Exception {
        PaletteStore store = new PaletteStore(dir);
        Palette palette = new PaletteGenerator(2).generate(Harmony.ANALOGOUS, new RGB(0x2A, 0xAC, 0xB8), 5, false)
                .withLocked("ground", true);
        store.save("Sea", palette);

        String json = Files.readString(dir.resolve("Sea.json"));

        assertThat(json).contains("\"formatVersion\": 1", "\"name\": \"Sea\"", "\"dark\": false", "\"rule\": \"analogous\"",
                "\"base\": \"#2AACB8\"", "\"ground\": \"" + palette.swatch("ground").orElseThrow().hex() + "\"");
        assertThat(json.replaceAll("\\s", "")).contains("\"locked\":[\"ground\"]");
    }

    @Test
    void aPaletteNotGeneratedHasNoRuleOrBase() {
        PaletteStore store = new PaletteStore(dir);
        Palette plain = Palette.of(true, java.util.List.of(new RGB(1, 2, 3), new RGB(250, 240, 230)));

        store.save("plain", plain);

        assertThat(store.load("plain")).isEqualTo(plain);
    }

    @Test
    void namesAreCheckedAndDeleteRemoves() {
        PaletteStore store = new PaletteStore(dir);
        Palette palette = Palette.of(false, java.util.List.of(new RGB(1, 2, 3)));

        assertThatThrownBy(() -> store.save("../escape", palette)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.save(".hidden", palette)).isInstanceOf(IllegalArgumentException.class);
        store.save("Mine 2", palette);
        store.delete("Mine 2");
        assertThat(store.list()).isEmpty();
        assertThat(PaletteStore.user().directory().endsWith(Path.of(".zapthemes", "palettes"))).isTrue();
    }

    @Test
    void aNewerFormatIsRefused() throws Exception {
        Files.writeString(dir.resolve("future.json"), "{\"formatVersion\": 2, \"swatches\": {}}");

        assertThatThrownBy(() -> new PaletteStore(dir).load("future")).isInstanceOf(JsonParseException.class)
                .hasMessageContaining("newer");
    }
}
