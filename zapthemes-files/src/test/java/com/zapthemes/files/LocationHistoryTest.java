package com.zapthemes.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class LocationHistoryTest {

    private final MemoryFileSystem server = MemoryFileSystem.builder("server").folder("home/me/projects").folder("etc").home("home/me").build();
    private final MemoryFileSystem laptop = MemoryFileSystem.builder("laptop").folder("work").build();

    private static List<String> texts(List<Location> locations, FileSystem shownIn) {
        return locations.stream().map(l -> l.text(shownIn)).toList();
    }

    @Test
    void newestFirstOnceEachUpToTheLimit() {
        LocationHistory history = new LocationHistory();
        List<String> changes = new ArrayList<>();
        history.addChangeListener(() -> changes.add("changed"));
        history.add(Location.of(server, FilePath.of("etc")));
        history.add(Location.of(server, FilePath.of("home", "me")));
        history.add(Location.of(server, FilePath.of("etc")));
        assertThat(texts(history.entries(), server)).containsExactly("/etc", "/home/me");
        assertThat(changes).hasSize(3);

        history.setLimit(1);
        assertThat(texts(history.entries(), server)).containsExactly("/etc");
        assertThat(history.remove(Location.of(server, FilePath.of("etc")))).isTrue();
        assertThat(history.entries()).isEmpty();
    }

    @Test
    void labelsAreKeptAndTyped() {
        LocationHistory history = new LocationHistory();
        history.add(new Location(server, FilePath.of("home", "me", "projects"), "Projects", "documents"));
        history.add(Location.of(laptop, FilePath.of("work")));
        // The same folder again, without a label: it moves up and keeps its name.
        history.add(Location.of(server, FilePath.of("home", "me", "projects")));

        assertThat(texts(history.entries(), laptop)).containsExactly("Projects", "/work");
        assertThat(texts(history.entries(), server)).containsExactly("Projects", "/work on laptop");
        assertThat(history.labeled(" projects ")).map(Location::path).contains(FilePath.of("home", "me", "projects"));
        assertThat(history.labeled("work")).isEmpty();
        assertThat(texts(history.matching("/w", laptop), laptop)).containsExactly("/work");
        assertThat(texts(history.matching("pro", laptop), laptop)).containsExactly("Projects");
        assertThat(history.matching("", laptop)).hasSize(2);
    }

    @Test
    void sameFolderFollowsTheFilesystemsCase() {
        assertThat(Location.of(server, FilePath.of("etc")).sameFolder(Location.of(laptop, FilePath.of("etc")))).isFalse();
        assertThat(Location.of(server, FilePath.of("etc")).sameFolder(Location.of(server, FilePath.of("ETC")))).isFalse();
        assertThat(Location.of(server, FilePath.of("etc")).sameFolder(new Location(server, FilePath.of("etc"), "Config", null))).isTrue();
    }
}
