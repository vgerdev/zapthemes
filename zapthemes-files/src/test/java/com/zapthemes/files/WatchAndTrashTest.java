package com.zapthemes.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link FileSystem#watch} and {@link FileSystem#trash}, headless. */
class WatchAndTrashTest {

    private static final boolean WINDOWS = File.separatorChar == '\\';

    @TempDir
    Path temp;

    private static MemoryFileSystem memory() {
        return MemoryFileSystem.builder("mem").folder("a/b").file("a/x.txt", "x").folder("c").build();
    }

    private static FilePath p(String... names) {
        return FilePath.of(List.of(names));
    }

    @Test
    void memoryFiresOnItsOwnChanges() throws IOException {
        MemoryFileSystem fs = memory();
        assertThat(fs.has(Capability.WATCH)).isTrue();
        assertThat(fs.has(Capability.TRASH)).isFalse();
        List<FilePath> heard = new CopyOnWriteArrayList<>();
        FileSystem.Watch watch = fs.watch(p("a"), heard::add);
        assertThat(fs.watchedCount()).isEqualTo(1);

        fs.createDirectory(p("a"), "new");
        assertThat(heard).containsExactly(p("a", "new"));
        heard.clear();
        fs.rename(p("a", "x.txt"), "y.txt");
        assertThat(heard).containsExactly(p("a", "x.txt"), p("a", "y.txt"));
        heard.clear();
        try (OutputStream out = fs.openWrite(p("a", "z.txt"))) {
            out.write(1);
        }
        assertThat(heard).containsExactly(p("a", "z.txt"));
        heard.clear();
        fs.move(p("a", "z.txt"), p("c"));
        assertThat(heard).containsExactly(p("a", "z.txt"));
        heard.clear();
        // Changes elsewhere, and below a subfolder, aren't the folder's.
        fs.createDirectory(p("c"), "other");
        fs.createDirectory(p("a", "b"), "deeper");
        assertThat(heard).isEmpty();
        fs.delete(p("a", "b"), true);
        assertThat(heard).containsExactly(p("a", "b"));

        watch.close();
        watch.close();
        assertThat(fs.watchedCount()).isZero();
        fs.createDirectory(p("a"), "after");
        assertThat(heard).hasSize(1);
    }

    @Test
    void memoryTellsAWatchedFolderThatWentAway() throws IOException {
        MemoryFileSystem fs = memory();
        List<FilePath> heard = new CopyOnWriteArrayList<>();
        try (FileSystem.Watch _ = fs.watch(p("a", "b"), heard::add)) {
            fs.delete(p("a"), true);
            assertThat(heard).containsExactly(p("a", "b"));
        }
        assertThatThrownBy(() -> fs.watch(p("nothing"), heard::add)).isInstanceOf(NoSuchFileException.class);
        assertThatThrownBy(() -> fs.watch(p("a"), heard::add)).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void rootedPassesWatchesThroughInItsOwnPaths() throws IOException {
        MemoryFileSystem base = memory();
        RootedFileSystem rooted = new RootedFileSystem(base, p("a"));
        assertThat(rooted.has(Capability.WATCH)).isTrue();
        List<FilePath> heard = new CopyOnWriteArrayList<>();
        try (FileSystem.Watch _ = rooted.watch(FilePath.TOP, heard::add)) {
            base.createDirectory(p("a"), "seen");
            rooted.createDirectory(FilePath.TOP, "too");
            assertThat(heard).containsExactly(p("seen"), p("too"));
        }
        assertThat(base.watchedCount()).isZero();
        assertThatThrownBy(() -> rooted.trash(p("b"))).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rooted.trash(FilePath.TOP)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusedByDefault() {
        FileSystem plain = new FileSystem() {
            @Override
            public String displayName() {
                return "plain";
            }

            @Override
            public String topName() {
                return "plain";
            }

            @Override
            public FilePath home() {
                return FilePath.TOP;
            }

            @Override
            public java.util.Set<Capability> capabilities() {
                return java.util.Set.of();
            }

            @Override
            public List<FileEntry> list(FilePath folder) {
                return List.of();
            }

            @Override
            public java.util.Optional<FileEntry> stat(FilePath path) {
                return java.util.Optional.empty();
            }

            @Override
            public FilePath parse(String text, FilePath base) {
                return FilePath.TOP;
            }

            @Override
            public String format(FilePath path) {
                return "/";
            }
        };
        assertThatThrownBy(() -> plain.watch(FilePath.TOP, p -> {
        })).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> plain.trash(p("x"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void localWatchesAFolder() throws Exception {
        LocalFileSystem fs = LocalFileSystem.get();
        assertThat(fs.has(Capability.WATCH)).isTrue();
        FilePath folder = fs.fromPath(temp);
        LinkedBlockingQueue<FilePath> heard = new LinkedBlockingQueue<>();
        try (FileSystem.Watch _ = fs.watch(folder, heard::add)) {
            assertThat(fs.watchedCount()).isEqualTo(1);
            Files.writeString(temp.resolve("made.txt"), "x");
            // macOS polls, every ten seconds by default.
            FilePath first = heard.poll(30, TimeUnit.SECONDS);
            assertThat(first).isEqualTo(folder.resolve("made.txt"));
        }
        assertThat(fs.watchedCount()).isZero();
        if (WINDOWS) {
            assertThat(fs.watch(FilePath.TOP, heard::add)).isSameAs(FileSystem.Watch.NONE);
        }
    }

    @Test
    void localRenamesTheParentOfAWatchedFolder() throws Exception {
        // On Windows a watched folder's handle keeps its parents from being renamed; the
        // filesystem's own operations pause the watch.
        LocalFileSystem fs = LocalFileSystem.get();
        Path child = Files.createDirectories(temp.resolve("parent").resolve("child"));
        LinkedBlockingQueue<FilePath> heard = new LinkedBlockingQueue<>();
        FilePath childPath = fs.fromPath(child);
        try (FileSystem.Watch _ = fs.watch(childPath, heard::add)) {
            fs.rename(fs.fromPath(temp.resolve("parent")), "renamed");
            assertThat(temp.resolve("renamed").resolve("child")).isDirectory();
            if (WINDOWS) {
                assertThat(heard.poll(5, TimeUnit.SECONDS)).isEqualTo(childPath);
            }
            // Moving and deleting work too.
            Files.createDirectories(temp.resolve("target"));
            fs.move(fs.fromPath(temp.resolve("renamed")), fs.fromPath(temp.resolve("target")));
            fs.delete(fs.fromPath(temp.resolve("target")), true);
            assertThat(temp.resolve("target")).doesNotExist();
        }
    }

    @Test
    void localWatchOfAFolderPausedAndStillThereGoesOn() throws Exception {
        LocalFileSystem fs = LocalFileSystem.get();
        Path watched = Files.createDirectories(temp.resolve("watched"));
        Files.writeString(watched.resolve("old.txt"), "x");
        LinkedBlockingQueue<FilePath> heard = new LinkedBlockingQueue<>();
        FilePath folder = fs.fromPath(watched);
        try (FileSystem.Watch _ = fs.watch(folder, heard::add)) {
            fs.rename(folder.resolve("old.txt"), "new.txt");
            assertThat(heard.poll(30, TimeUnit.SECONDS)).isNotNull();
            heard.clear();
            Files.writeString(watched.resolve("later.txt"), "x");
            FilePath later = null;
            for (FilePath p; later == null && (p = heard.poll(30, TimeUnit.SECONDS)) != null;) {
                if (p.equals(folder.resolve("later.txt"))) {
                    later = p;
                }
            }
            assertThat(later).isNotNull();
        }
    }

    @Test
    void localTrash() throws IOException {
        LocalFileSystem fs = LocalFileSystem.get();
        assertThat(fs.has(Capability.TRASH)).isEqualTo(PlatformTrash.isAvailable());
        if (WINDOWS) {
            // Loaded with the JDK's own libraries, so always there on Windows.
            assertThat(PlatformTrash.isAvailable()).isTrue();
            assertThat(fs.canTrash(fs.fromPath(temp))).isTrue();
        }
        assertThatThrownBy(() -> fs.trash(fs.fromPath(temp.resolve("missing.txt")))).isInstanceOf(NoSuchFileException.class);
        // Really trashing litters the user's trash: on CI, or when asked for.
        assumeTrue(PlatformTrash.isAvailable() && (System.getenv("CI") != null || System.getenv("ZAPTHEMES_TRASH_TEST") != null));
        // Under the home folder: elsewhere (a /tmp of its own) Linux may have no trash.
        Path dir = Files.createDirectories(Path.of(System.getProperty("user.home"), ".zapthemes-trash-test"));
        try {
            Path file = Files.writeString(dir.resolve("zapthemes-trash-test-" + System.nanoTime() + ".txt"), "trash me");
            Path folder = Files.createDirectories(dir.resolve("zapthemes-trash-folder-" + System.nanoTime()));
            Files.writeString(folder.resolve("inside.txt"), "x");
            fs.trash(fs.fromPath(file));
            fs.trash(fs.fromPath(folder));
            assertThat(file).doesNotExist();
            assertThat(folder).doesNotExist();
        } finally {
            Files.deleteIfExists(dir);
        }
    }
}
