package com.zapthemes.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemsTest {

    private static final boolean WINDOWS = File.separatorChar == '\\';

    @TempDir
    Path temp;

    private static List<String> names(List<FileEntry> entries) {
        return entries.stream().map(FileEntry::name).sorted().toList();
    }

    // ------------------------------------------------------------------ FilePath

    @Test
    void pathsAreSegmentsFromTheTop() {
        FilePath path = FilePath.of("a", "b", "c");

        assertThat(path.name()).isEqualTo("c");
        assertThat(path.parent()).isEqualTo(FilePath.of("a", "b"));
        assertThat(FilePath.of("a").parent()).isSameAs(FilePath.TOP);
        assertThat(FilePath.TOP.parent()).isNull();
        assertThat(path.startsWith(FilePath.of("a"))).isTrue();
        assertThat(path.startsWith(FilePath.of("b"))).isFalse();
        assertThat(path.relativeTo(FilePath.of("a"))).isEqualTo(FilePath.of("b", "c"));
        assertThat(FilePath.of("a").resolve(FilePath.of("b", "c"))).isEqualTo(path);
        assertThat(path.prefix(2)).isEqualTo(FilePath.of("a", "b"));
        assertThat(FilePath.of("C:\\", "x").segments()).containsExactly("C:\\", "x");
    }

    @Test
    void pathsRefuseDotsAndSlashes() {
        for (String bad : List.of("", ".", "..", "a/b", "nul\0")) {
            assertThatThrownBy(() -> FilePath.TOP.resolve(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ------------------------------------------------------------------ order, types and sizes

    @Test
    void namesSortNaturallyWithFoldersFirst() {
        assertThat(FileOrder.compareNatural("file2", "file10")).isNegative();
        assertThat(FileOrder.compareNatural("File2", "file10")).isNegative();
        assertThat(FileOrder.compareNatural("a", "B")).isNegative();
        assertThat(FileOrder.compareNatural("x007", "x7")).isNegative();
        List<FileEntry> entries = new ArrayList<>(List.of(
                FileEntry.builder(FilePath.of("b.txt"), EntryKind.FILE).size(10).build(),
                FileEntry.builder(FilePath.of("z"), EntryKind.DIRECTORY).build(),
                FileEntry.builder(FilePath.of("a.txt"), EntryKind.FILE).size(30).build(),
                FileEntry.builder(FilePath.of("m"), EntryKind.DIRECTORY).build()));

        entries.sort(FileOrder.by(FileOrder.Key.NAME, true));
        assertThat(entries).extracting(FileEntry::name).containsExactly("m", "z", "a.txt", "b.txt");
        entries.sort(FileOrder.by(FileOrder.Key.NAME, false));
        assertThat(entries).extracting(FileEntry::name).containsExactly("z", "m", "b.txt", "a.txt");
        entries.sort(FileOrder.by(FileOrder.Key.SIZE, false));
        assertThat(entries).extracting(FileEntry::name).containsExactly("z", "m", "a.txt", "b.txt");
    }

    @Test
    void typesAndSizesReadLikeExplorer() {
        assertThat(FileTypes.typeName(FileEntry.builder(FilePath.of("d"), EntryKind.DIRECTORY).build())).isEqualTo("File folder");
        assertThat(FileTypes.typeName(FileEntry.builder(FilePath.of("Install.DOCX"), EntryKind.FILE).build())).isEqualTo("Word document");
        assertThat(FileTypes.typeName(FileEntry.builder(FilePath.of("helm.7z"), EntryKind.FILE).build())).isEqualTo("Compressed archive");
        assertThat(FileTypes.typeName(FileEntry.builder(FilePath.of("data.xyz"), EntryKind.FILE).build())).isEqualTo("XYZ file");
        assertThat(FileTypes.typeName(FileEntry.builder(FilePath.of("Makefile"), EntryKind.FILE).build())).isEqualTo("File");
        assertThat(FileTypes.extension(".bashrc")).isEmpty();
        assertThat(FileTypes.category(FileEntry.builder(FilePath.of("a.png"), EntryKind.FILE).build()))
                .isEqualTo(FileTypes.Category.IMAGE);
        assertThat(FileTypes.detailsSize(0)).isEqualTo("0 KB");
        assertThat(FileTypes.detailsSize(1)).isEqualTo("1 KB");
        assertThat(FileTypes.detailsSize(1024)).isEqualTo("1 KB");
        assertThat(FileTypes.detailsSize(1_066_000).replaceAll("\\D", "")).isEqualTo("1042");
        assertThat(FileTypes.detailsSize(-1)).isEmpty();
        assertThat(FileTypes.shortSize(512)).isEqualTo("512 bytes");
        assertThat(FileTypes.shortSize(1536).replace(',', '.')).isEqualTo("1.5 KB");
    }

    // ------------------------------------------------------------------ memory

    private static MemoryFileSystem sample() {
        return MemoryFileSystem.builder("server")
                .folder("home/me/projects")
                .file("home/me/notes.txt", "Hello")
                .file("home/me/.profile", 120)
                .file("home/me/big.iso", 700L << 20)
                .posix("rw-r--r--", "me", "staff")
                .folder("etc")
                .home("home/me")
                .build();
    }

    @Test
    void memoryListsStatsAndParses() throws IOException {
        MemoryFileSystem fs = sample();
        FilePath me = FilePath.of("home", "me");

        assertThat(fs.home()).isEqualTo(me);
        assertThat(names(fs.list(me))).containsExactly(".profile", "big.iso", "notes.txt", "projects");
        assertThat(fs.list(me)).filteredOn(FileEntry::hidden).extracting(FileEntry::name).containsExactly(".profile");
        FileEntry iso = fs.stat(me.resolve("big.iso")).orElseThrow();
        assertThat(iso.size()).isEqualTo(700L << 20);
        assertThat(iso.permissions()).isEqualTo("rw-r--r--");
        assertThat(iso.owner()).isEqualTo("me");
        assertThat(fs.has(Capability.PERMISSIONS)).isTrue();
        assertThat(fs.has(Capability.SLOW)).isFalse();
        assertThat(fs.stat(me.resolve("nothing"))).isEmpty();
        assertThatThrownBy(() -> fs.list(me.resolve("nothing"))).isInstanceOf(NoSuchFileException.class);

        assertThat(fs.parse("/home/me/../me/./projects", null)).isEqualTo(me.resolve("projects"));
        assertThat(fs.parse("projects", me)).isEqualTo(me.resolve("projects"));
        assertThat(fs.parse("~/projects", null)).isEqualTo(me.resolve("projects"));
        assertThat(fs.parse("/..", null)).isEqualTo(FilePath.TOP);
        assertThatThrownBy(() -> fs.parse("projects", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(fs.format(me)).isEqualTo("/home/me");
        assertThat(fs.format(FilePath.TOP)).isEqualTo("/");
        assertThat(fs.label(FilePath.TOP)).isEqualTo("server");
    }

    @Test
    void memoryChangesFiles() throws IOException {
        MemoryFileSystem fs = sample();
        FilePath me = FilePath.of("home", "me");

        FilePath dir = fs.createDirectory(me, "new");
        assertThatThrownBy(() -> fs.createDirectory(me, "new")).isInstanceOf(FileAlreadyExistsException.class);
        try (OutputStream out = fs.openWrite(dir.resolve("a.txt"))) {
            out.write("abc".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = fs.openRead(dir.resolve("a.txt"))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("abc");
        }
        assertThat(fs.rename(dir.resolve("a.txt"), "b.txt")).isEqualTo(dir.resolve("b.txt"));
        assertThatThrownBy(() -> fs.rename(me.resolve("notes.txt"), "big.iso")).isInstanceOf(FileAlreadyExistsException.class);
        assertThatThrownBy(() -> fs.delete(dir, false)).isInstanceOf(DirectoryNotEmptyException.class);
        fs.delete(dir, true);
        assertThat(fs.stat(dir)).isEmpty();
    }

    @Test
    void memoryWaitsAndFailsOnRequest() throws IOException {
        MemoryFileSystem fs = MemoryFileSystem.builder("slow").folder("a").delay(Duration.ofMillis(120)).build();
        fs.failOn(FilePath.of("a"), new AccessDeniedException("/a"));

        long start = System.nanoTime();
        assertThat(fs.list(FilePath.TOP)).hasSize(1);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofMillis(100));
        assertThat(fs.has(Capability.SLOW)).isTrue();
        assertThatThrownBy(() -> fs.list(FilePath.of("a"))).isInstanceOf(AccessDeniedException.class);
        fs.clearFailures();
        assertThat(fs.list(FilePath.of("a"))).isEmpty();
    }

    // ------------------------------------------------------------------ local

    @Test
    void localMapsNioPaths() throws IOException {
        LocalFileSystem fs = LocalFileSystem.get();
        Files.createDirectories(temp.resolve("sub/inner"));
        Files.writeString(temp.resolve("sub/file.txt"), "12345");
        FilePath sub = fs.fromPath(temp.resolve("sub"));

        assertThat(fs.toPath(sub)).isEqualTo(temp.resolve("sub").toAbsolutePath().normalize());
        assertThat(fs.format(sub)).isEqualTo(temp.resolve("sub").toAbsolutePath().normalize().toString());
        assertThat(fs.parse(fs.format(sub), null)).isEqualTo(sub);
        assertThat(fs.parse("inner" + File.separator + "..", sub)).isEqualTo(sub);
        List<FileEntry> entries = fs.list(sub);
        assertThat(names(entries)).containsExactly("file.txt", "inner");
        FileEntry file = entries.stream().filter(e -> e.name().equals("file.txt")).findFirst().orElseThrow();
        assertThat(file.kind()).isEqualTo(EntryKind.FILE);
        assertThat(file.size()).isEqualTo(5);
        assertThat(file.modified()).isNotNull();
        assertThat(fs.stat(sub.resolve("inner")).orElseThrow().kind()).isEqualTo(EntryKind.DIRECTORY);
        assertThat(fs.stat(sub.resolve("missing"))).isEmpty();
        if (WINDOWS) {
            assertThat(sub.segments().getFirst()).endsWith(":\\");
            assertThat(fs.format(FilePath.TOP)).isEqualTo("This PC");
            assertThat(fs.parse("This PC", null)).isEqualTo(FilePath.TOP);
            assertThat(fs.list(FilePath.TOP)).allMatch(e -> e.kind() == EntryKind.DRIVE).extracting(FileEntry::label)
                    .allMatch(label -> label.matches(".*\\(.*\\)"));
        } else {
            assertThat(fs.format(FilePath.TOP)).isEqualTo("/");
            assertThat(fs.list(FilePath.TOP)).isNotEmpty();
            assertThat(file.permissions()).hasSize(9);
            assertThat(file.owner()).isNotBlank();
        }
    }

    @Test
    void localChangesFiles() throws IOException {
        LocalFileSystem fs = LocalFileSystem.get();
        FilePath dir = fs.fromPath(temp);

        FilePath created = fs.createDirectory(dir, "made");
        Files.writeString(fs.toPath(created).resolve("x.txt"), "x");
        assertThatThrownBy(() -> fs.createDirectory(dir, "a" + File.separator + "b")).isInstanceOf(IllegalArgumentException.class);
        FilePath renamed = fs.rename(created, "renamed");
        assertThat(Files.isDirectory(fs.toPath(renamed))).isTrue();
        fs.delete(renamed, true);
        assertThat(Files.exists(fs.toPath(renamed))).isFalse();
    }

    // ------------------------------------------------------------------ rooted

    @Test
    void rootedShowsOnlyItsFolder() throws IOException {
        LocalFileSystem local = LocalFileSystem.get();
        Files.createDirectories(temp.resolve("root/reports/2025"));
        Files.writeString(temp.resolve("root/readme.txt"), "hi");
        Files.writeString(temp.resolve("outside.txt"), "no");
        FilePath root = local.fromPath(temp.resolve("root"));
        RootedFileSystem fs = new RootedFileSystem(local, root);
        char sep = File.separatorChar;

        assertThat(fs.topName()).isEqualTo("root");
        assertThat(fs.home()).isEqualTo(FilePath.TOP);
        assertThat(names(fs.list(FilePath.TOP))).containsExactly("readme.txt", "reports");
        assertThat(fs.list(FilePath.TOP)).extracting(FileEntry::path)
                .contains(FilePath.of("reports"), FilePath.of("readme.txt"));
        assertThat(fs.stat(FilePath.TOP).orElseThrow().displayName()).isEqualTo("root");
        assertThat(fs.format(FilePath.of("reports", "2025"))).isEqualTo(sep + "reports" + sep + "2025");
        assertThat(fs.format(FilePath.TOP)).isEqualTo(String.valueOf(sep));
        assertThat(fs.parse(sep + "reports" + sep + "2025", null)).isEqualTo(FilePath.of("reports", "2025"));
        assertThat(fs.parse("2025", FilePath.of("reports"))).isEqualTo(FilePath.of("reports", "2025"));
        assertThat(fs.parse("..", FilePath.of("reports"))).isEqualTo(FilePath.TOP);
        assertThat(fs.parse(local.format(root.resolve("reports")), null)).isEqualTo(FilePath.of("reports"));

        assertThatThrownBy(() -> fs.parse("..", FilePath.TOP)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fs.parse(sep + ".." + sep + "outside.txt", null)).isInstanceOf(IllegalArgumentException.class);
        String outside = local.format(local.fromPath(temp));
        if (WINDOWS) {
            // A full path outside the root is refused.
            assertThatThrownBy(() -> fs.parse(outside, null)).isInstanceOf(IllegalArgumentException.class);
        } else {
            // It starts with the separator, so it is read from the root: it stays inside.
            assertThat(fs.parse(outside, null)).isEqualTo(FilePath.of(PathNames.split(outside, "/")));
        }
        assertThatThrownBy(() -> fs.createDirectory(FilePath.TOP, ".." + sep + "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fs.rename(FilePath.of("readme.txt"), ".." + sep + "moved.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> fs.delete(FilePath.TOP, true)).isInstanceOf(IllegalArgumentException.class);

        FilePath made = fs.createDirectory(FilePath.of("reports"), "2026");
        assertThat(made).isEqualTo(FilePath.of("reports", "2026"));
        assertThat(Files.isDirectory(temp.resolve("root/reports/2026"))).isTrue();
    }

    @Test
    void rootedWorksOverVirtualFilesystems() throws IOException {
        RootedFileSystem fs = new RootedFileSystem(sample(), FilePath.of("home", "me"), "My home");

        assertThat(fs.topName()).isEqualTo("My home");
        assertThat(fs.list(FilePath.TOP)).extracting(FileEntry::path).contains(FilePath.of("projects"));
        assertThat(fs.format(FilePath.of("projects"))).isEqualTo("/projects");
        assertThat(fs.parse("/projects", null)).isEqualTo(FilePath.of("projects"));
        assertThatThrownBy(() -> fs.parse("/../../etc", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(fs.capabilities()).contains(Capability.PERMISSIONS);
        List<FileEntry> sorted = new ArrayList<>(fs.list(FilePath.TOP));
        sorted.sort(Comparator.comparing(FileEntry::name));
        assertThat(sorted.getFirst().name()).isEqualTo(".profile");
    }
}
