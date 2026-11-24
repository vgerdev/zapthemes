package com.zapthemes.files;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileMaskTest {

    private static FileEntry file(String name) {
        return FileEntry.builder(FilePath.of("dir", name), EntryKind.FILE).size(1).build();
    }

    private static FileEntry folder(String name) {
        return FileEntry.builder(FilePath.of("dir", name), EntryKind.DIRECTORY).build();
    }

    @Test
    void blankShowsEverything() {
        for (String text : new String[] { null, "", "  ", " ; , " }) {
            FileMask mask = new FileMask(text);
            assertThat(mask.isEmpty()).isTrue();
            assertThat(mask.shows(file("a.txt"))).isTrue();
            assertThat(mask.shows(folder("src"))).isTrue();
        }
        assertThat(FileMask.ALL.text()).isEmpty();
    }

    @Test
    void wildcardsMatchWholeNamesWithoutCase() {
        FileMask mask = new FileMask(" *.log; report?.TXT , exact.md ");
        assertThat(mask.text()).isEqualTo("*.log; report?.TXT , exact.md");
        assertThat(mask.shows(file("app.log"))).isTrue();
        assertThat(mask.shows(file("APP.LOG"))).isTrue();
        assertThat(mask.shows(file("app.log.1"))).isFalse();
        assertThat(mask.shows(file("report1.txt"))).isTrue();
        assertThat(mask.shows(file("report10.txt"))).isFalse();
        assertThat(mask.shows(file("exact.md"))).isTrue();
        assertThat(mask.shows(file("inexact.md"))).isFalse();
        // Regex characters are plain characters.
        assertThat(new FileMask("a+b(1).[x]").shows(file("a+b(1).[x]"))).isTrue();
        assertThat(new FileMask("a.b").shows(file("aXb"))).isFalse();
    }

    @Test
    void foldersShowUnlessFolderMasksAreGiven() {
        FileMask files = new FileMask("*.log");
        assertThat(files.shows(folder("src"))).isTrue();
        FileMask folders = new FileMask("src/; *.java");
        assertThat(folders.shows(folder("src"))).isTrue();
        assertThat(folders.shows(folder("build"))).isFalse();
        assertThat(folders.shows(file("Main.java"))).isTrue();
        assertThat(folders.shows(file("notes.txt"))).isFalse();
        // Only folder masks: every file stays.
        FileMask onlyFolders = new FileMask(".git/, node_modules /");
        assertThat(onlyFolders.shows(file("anything.bin"))).isTrue();
        assertThat(onlyFolders.shows(folder("node_modules"))).isTrue();
        assertThat(onlyFolders.shows(folder("src"))).isFalse();
    }

    @Test
    void matchesAsZapscpDoes() {
        FileMask mask = new FileMask("*.log; .git/");
        assertThat(mask.matches("app.log", false)).isTrue();
        assertThat(mask.matches("logs.log", true)).isTrue();
        assertThat(mask.matches(".git", true)).isTrue();
        assertThat(mask.matches(".git", false)).isFalse();
    }

    @Test
    void equalByText() {
        assertThat(new FileMask("*.log ")).isEqualTo(new FileMask(" *.log")).hasSameHashCodeAs(new FileMask("*.log"));
        assertThat(new FileMask("*.log")).isNotEqualTo(new FileMask("*.txt"));
    }
}
