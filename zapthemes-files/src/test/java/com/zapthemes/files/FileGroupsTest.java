package com.zapthemes.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

class FileGroupsTest {

    private static final ZoneId UTC = ZoneOffset.UTC;
    /** A Thursday. */
    private static final Instant NOW = Instant.parse("2026-11-19T15:00:00Z");

    private static FileEntry file(String name, long size, String modified) {
        return FileEntry.builder(FilePath.of(name), EntryKind.FILE).size(size).modified(modified == null ? null : Instant.parse(modified)).build();
    }

    private static FileEntry folder(String name) {
        return FileEntry.builder(FilePath.of(name), EntryKind.DIRECTORY).build();
    }

    private static List<String> labels(List<FileGroups.Group> groups) {
        return groups.stream().map(g -> g.label() + " " + g.entries().stream().map(FileEntry::name).toList()).toList();
    }

    @Test
    void byNameInLetterRanges() {
        List<FileEntry> entries = List.of(folder("src"), file("2024.txt", 1, null), file("Apple", 1, null), file("éclair", 1, null),
                file("Kiwi", 1, null), file("_tmp", 1, null), file("zebra", 1, null));
        assertThat(labels(FileGroups.group(entries, FileOrder.Key.NAME, true, NOW, UTC))).containsExactly("0 - 9 [2024.txt]",
                "A - H [Apple, éclair]", "I - P [Kiwi]", "Q - Z [src, zebra]", "Other [_tmp]");
        assertThat(labels(FileGroups.group(entries, FileOrder.Key.NAME, false, NOW, UTC)).getFirst()).startsWith("Other");
    }

    @Test
    void byDateAsExplorerNamesThem() {
        assertThat(FileGroups.dateGroup(Instant.parse("2026-11-19T01:00:00Z"), NOW, UTC)).isEqualTo("Today");
        assertThat(FileGroups.dateGroup(Instant.parse("2026-11-18T23:00:00Z"), NOW, UTC)).isEqualTo("Yesterday");
        assertThat(FileGroups.dateGroup(Instant.parse("2026-11-16T08:00:00Z"), NOW, UTC)).isEqualTo("Earlier this week");
        assertThat(FileGroups.dateGroup(Instant.parse("2026-11-09T08:00:00Z"), NOW, UTC)).isEqualTo("Last week");
        assertThat(FileGroups.dateGroup(Instant.parse("2026-11-02T08:00:00Z"), NOW, UTC)).isEqualTo("Earlier this month");
        assertThat(FileGroups.dateGroup(Instant.parse("2026-10-01T08:00:00Z"), NOW, UTC)).isEqualTo("Last month");
        assertThat(FileGroups.dateGroup(Instant.parse("2026-02-01T08:00:00Z"), NOW, UTC)).isEqualTo("Earlier this year");
        assertThat(FileGroups.dateGroup(Instant.parse("2024-04-08T13:45:00Z"), NOW, UTC)).isEqualTo("A long time ago");
        assertThat(FileGroups.dateGroup(Instant.parse("2026-12-01T08:00:00Z"), NOW, UTC)).isEqualTo("Later");
        assertThat(FileGroups.dateGroup(null, NOW, UTC)).isEqualTo("Unknown");
        // The zone decides the day.
        assertThat(FileGroups.dateGroup(Instant.parse("2026-11-19T01:00:00Z"), NOW, ZoneId.of("America/New_York"))).isEqualTo("Yesterday");
    }

    @Test
    void byDateNewestFirstWhenDescendingWithUnknownLast() {
        List<FileEntry> entries = List.of(file("old", 1, "2024-04-08T13:45:00Z"), file("new", 1, "2026-11-19T10:00:00Z"), file("none", 1, null));
        assertThat(labels(FileGroups.group(entries, FileOrder.Key.MODIFIED, false, NOW, UTC))).containsExactly("Today [new]",
                "A long time ago [old]", "Unknown [none]");
        assertThat(labels(FileGroups.group(entries, FileOrder.Key.MODIFIED, true, NOW, UTC))).containsExactly("A long time ago [old]",
                "Today [new]", "Unknown [none]");
    }

    @Test
    void bySizeWithFoldersUnspecified() {
        List<FileEntry> entries = List.of(folder("docs"), file("empty", 0, null), file("tiny", 100, null), file("small", 20_000, null),
                file("medium", 5_000_000, null), file("large", 200_000_000L, null), file("huge", 2_000_000_000L, null),
                file("gigantic", 5_000_000_000L, null));
        assertThat(labels(FileGroups.group(entries, FileOrder.Key.SIZE, true, NOW, UTC))).containsExactly("Empty [empty]", "Tiny [tiny]",
                "Small [small]", "Medium [medium]", "Large [large]", "Huge [huge]", "Gigantic [gigantic]", "Unspecified [docs]");
    }

    @Test
    void byTypeAndOwnerByValue() {
        List<FileEntry> entries = List.of(folder("docs"), file("a.txt", 1, null), file("b.png", 1, null), file("c.txt", 1, null));
        assertThat(labels(FileGroups.group(entries, FileOrder.Key.TYPE, true, NOW, UTC))).containsExactly("File folder [docs]",
                "PNG image [b.png]", "Text document [a.txt, c.txt]");
        FileEntry mine = FileEntry.builder(FilePath.of("x"), EntryKind.FILE).owner("me").build();
        FileEntry nobody = FileEntry.builder(FilePath.of("y"), EntryKind.FILE).build();
        assertThat(labels(FileGroups.group(List.of(nobody, mine), FileOrder.Key.OWNER, false, NOW, UTC))).containsExactly("me [x]",
                "Unspecified [y]");
    }
}
