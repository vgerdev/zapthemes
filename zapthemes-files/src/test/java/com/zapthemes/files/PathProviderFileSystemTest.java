package com.zapthemes.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import com.zapthemes.files.PathProviderFileSystem.Item;
import com.zapthemes.files.PathProviderFileSystem.PathProvider;

/** The adapter for zapscp's providers, over stand-ins that do paths as zapscp's do. */
class PathProviderFileSystemTest {

    private static final Instant T = Instant.parse("2025-01-15T09:12:00Z");

    /** A server with POSIX paths, as zapscp's SFTP provider sees one (paths math as zapscp's {@code PosixPaths}). */
    static class PosixServer implements PathProvider {
        record Node(boolean directory, String link, byte[] content, String permissions, String owner) {
        }

        final Map<String, Node> nodes = new TreeMap<>();
        final List<String> calls = new ArrayList<>();
        boolean permissions = true;
        boolean closed;

        PosixServer() {
            dir("/");
            dir("/home");
            dir("/home/deploy");
            dir("/home/deploy/logs");
            file("/home/deploy/logs/app.log", "started");
            file("/home/deploy/app.jar", "jar");
            file("/home/deploy/.bashrc", "alias ll='ls -l'");
            nodes.put("/home/deploy/current", new Node(true, "/home/deploy/logs", null, "rwxrwxrwx", "deploy"));
            dir("/var");
        }

        void dir(String path) {
            nodes.put(path, new Node(true, null, null, "rwxr-x---", "deploy"));
        }

        void file(String path, String text) {
            nodes.put(path, new Node(false, null, text.getBytes(StandardCharsets.UTF_8), "rw-r--r--", "deploy"));
        }

        @Override
        public String displayName() {
            return "deploy@server:22";
        }

        @Override
        public String homeDirectory() {
            return "/home/deploy";
        }

        private Item item(String path, Node node) {
            return new Item(fileName(path), path, node.directory(), node.link() != null, node.content() == null ? 0 : node.content().length,
                    T, permissions ? node.permissions() : null, permissions ? node.owner() : null, permissions ? "staff" : null,
                    path.endsWith(".log") ? Duration.ofMinutes(1) : Duration.ZERO);
        }

        @Override
        public List<Item> list(String directory) throws IOException {
            calls.add("list " + directory);
            Node node = nodes.get(directory);
            if (node == null) {
                throw new NoSuchFileException(directory);
            }
            if (node.link() != null) {
                return list(node.link()).stream().map(i -> new Item(i.name(), resolve(directory, i.name()), i.directory(),
                        i.symbolicLink(), i.size(), i.modified(), i.permissions(), i.owner(), i.group(), i.timePrecision())).toList();
            }
            if (!node.directory()) {
                throw new NotDirectoryException(directory);
            }
            List<Item> items = new ArrayList<>();
            nodes.forEach((path, child) -> {
                if (!path.equals(directory) && directory.equals(parent(path))) {
                    items.add(item(path, child));
                }
            });
            return items;
        }

        @Override
        public Optional<Item> find(String path) {
            calls.add("find " + path);
            Node node = nodes.get(path);
            return node == null ? Optional.empty() : Optional.of(item(path, node));
        }

        @Override
        public String resolve(String directory, String name) {
            return directory.endsWith("/") ? directory + name : directory + "/" + name;
        }

        @Override
        public String parent(String path) {
            if (path.equals("/")) {
                return null;
            }
            int slash = path.lastIndexOf('/');
            return slash <= 0 ? "/" : path.substring(0, slash);
        }

        @Override
        public String fileName(String path) {
            return path.equals("/") ? path : path.substring(path.lastIndexOf('/') + 1);
        }

        @Override
        public InputStream openRead(String path) throws IOException {
            Node node = nodes.get(path);
            if (node == null || node.directory()) {
                throw new NoSuchFileException(path);
            }
            return new ByteArrayInputStream(node.content());
        }

        @Override
        public OutputStream openWrite(String path) {
            return new ByteArrayOutputStream() {
                @Override
                public void close() {
                    nodes.put(path, new Node(false, null, toByteArray(), "rw-r--r--", "deploy"));
                }
            };
        }

        @Override
        public void createDirectory(String path) {
            calls.add("mkdir " + path);
            dir(path);
        }

        @Override
        public void delete(String path, boolean directory) throws IOException {
            calls.add((directory ? "rmdir " : "rm ") + path);
            Node node = nodes.get(path);
            if (node == null) {
                throw new NoSuchFileException(path);
            }
            if (directory && nodes.keySet().stream().anyMatch(p -> !p.equals(path) && path.equals(parent(p)))) {
                throw new IOException(path + " is not empty");
            }
            nodes.remove(path);
        }

        @Override
        public void rename(String from, String to) {
            calls.add("mv " + from + " " + to);
            Map<String, Node> moved = new TreeMap<>();
            nodes.entrySet().removeIf(e -> {
                if (e.getKey().equals(from) || e.getKey().startsWith(from + "/")) {
                    moved.put(to + e.getKey().substring(from.length()), e.getValue());
                    return true;
                }
                return false;
            });
            nodes.putAll(moved);
        }

        @Override
        public boolean supportsPermissions() {
            return permissions;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    @Test
    void pathsAreTheChainOfNamesFromTheRoot() {
        PosixServer server = new PosixServer();
        PathProviderFileSystem fs = new PathProviderFileSystem(server, "server", null);
        assertThat(fs.home()).isEqualTo(FilePath.of("home", "deploy"));
        assertThat(fs.format(fs.home())).isEqualTo("/home/deploy");
        assertThat(fs.format(FilePath.TOP)).isEqualTo("/");
        assertThat(fs.pathOf("/var")).isEqualTo(FilePath.of("var"));
        assertThat(fs.string(FilePath.of("home", "deploy", "logs"))).isEqualTo("/home/deploy/logs");
        assertThat(fs.separator()).isEqualTo('/');
        assertThat(fs.topName()).isEqualTo("server");
        assertThat(fs.displayName()).isEqualTo("deploy@server:22");
        assertThat(new PathProviderFileSystem(server).topName()).isEqualTo("deploy@server:22");
    }

    @Test
    void typedPathsResolveWithoutTheServer() {
        PosixServer server = new PosixServer();
        PathProviderFileSystem fs = new PathProviderFileSystem(server);
        fs.home();
        server.calls.clear();
        FilePath home = FilePath.of("home", "deploy");
        assertThat(fs.parse("/var/log/../tmp", null)).isEqualTo(FilePath.of("var", "tmp"));
        assertThat(fs.parse("logs", home)).isEqualTo(home.resolve("logs"));
        assertThat(fs.parse("~", null)).isEqualTo(home);
        assertThat(fs.parse("~/logs", null)).isEqualTo(home.resolve("logs"));
        assertThat(fs.parse("..", home)).isEqualTo(FilePath.of("home"));
        assertThatThrownBy(() -> fs.parse("logs", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(server.calls).isEmpty();
    }

    @Test
    void listsEntriesFieldByField() throws IOException {
        PathProviderFileSystem fs = new PathProviderFileSystem(new PosixServer());
        List<FileEntry> entries = fs.list(FilePath.of("home", "deploy"));
        assertThat(entries).extracting(FileEntry::name).containsExactlyInAnyOrder(".bashrc", "app.jar", "current", "logs");
        FileEntry jar = entries.stream().filter(e -> e.name().equals("app.jar")).findFirst().orElseThrow();
        assertThat(jar.kind()).isEqualTo(EntryKind.FILE);
        assertThat(jar.size()).isEqualTo(3);
        assertThat(jar.modified()).isEqualTo(T);
        assertThat(jar.permissions()).isEqualTo("rw-r--r--");
        assertThat(jar.owner()).isEqualTo("deploy");
        assertThat(jar.group()).isEqualTo("staff");
        assertThat(jar.timePrecision()).isNull();
        assertThat(jar.hidden()).isFalse();
        FileEntry bashrc = entries.stream().filter(e -> e.name().equals(".bashrc")).findFirst().orElseThrow();
        assertThat(bashrc.hidden()).isTrue();
        FileEntry link = entries.stream().filter(e -> e.name().equals("current")).findFirst().orElseThrow();
        assertThat(link.isContainer()).isTrue();
        assertThat(link.link()).isTrue();
        assertThat(link.size()).isEqualTo(-1);
        FileEntry log = fs.list(FilePath.of("home", "deploy", "current")).getFirst();
        assertThat(log.path()).isEqualTo(FilePath.of("home", "deploy", "current", "app.log"));
        assertThat(log.timePrecision()).isEqualTo(Duration.ofMinutes(1));
        assertThat(fs.stat(FilePath.of("home", "deploy", "app.jar"))).map(FileEntry::size).contains(3L);
        assertThat(fs.stat(FilePath.of("nope"))).isEmpty();
        assertThat(fs.stat(FilePath.TOP)).map(FileEntry::displayName).contains("deploy@server:22");
        assertThatThrownBy(() -> fs.list(FilePath.of("nope"))).isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void capabilitiesFollowTheProvider() {
        PosixServer server = new PosixServer();
        assertThat(new PathProviderFileSystem(server).capabilities()).contains(Capability.SLOW, Capability.RENAME, Capability.MOVE,
                Capability.DELETE, Capability.READ, Capability.WRITE, Capability.CREATE_DIRECTORY, Capability.PERMISSIONS, Capability.OWNERS);
        server.permissions = false;
        assertThat(new PathProviderFileSystem(server).capabilities()).doesNotContain(Capability.PERMISSIONS, Capability.OWNERS);
        PathProviderFileSystem readOnly = new PathProviderFileSystem(server, null, java.util.EnumSet.of(Capability.SLOW, Capability.READ));
        assertThat(readOnly.capabilities()).containsExactlyInAnyOrder(Capability.SLOW, Capability.READ);
        assertThatThrownBy(() -> readOnly.delete(FilePath.of("var"), false)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> readOnly.createDirectory(FilePath.TOP, "x")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void operationsGoThroughTheProvider() throws IOException {
        PosixServer server = new PosixServer();
        PathProviderFileSystem fs = new PathProviderFileSystem(server);
        FilePath home = fs.home();
        assertThat(fs.createDirectory(home, "backup")).isEqualTo(home.resolve("backup"));
        assertThat(server.nodes).containsKey("/home/deploy/backup");
        assertThatThrownBy(() -> fs.createDirectory(home, "logs")).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(fs.rename(home.resolve("app.jar"), "app-1.jar")).isEqualTo(home.resolve("app-1.jar"));
        assertThatThrownBy(() -> fs.rename(home.resolve("app-1.jar"), "logs")).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(fs.move(home.resolve("app-1.jar"), home.resolve("backup"))).isEqualTo(home.resolve("backup").resolve("app-1.jar"));
        assertThat(server.nodes).containsKey("/home/deploy/backup/app-1.jar");
        assertThatThrownBy(() -> fs.move(home, home.resolve("backup"))).hasMessageContaining("into itself");
        try (OutputStream out = fs.openWrite(home.resolve("new.txt"))) {
            out.write("hi".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = fs.openRead(home.resolve("new.txt"))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("hi");
        }
        fs.close();
        assertThat(server.closed).isTrue();
    }

    @Test
    void deletesFoldersDeepestFirstAndLinksWithoutFollowing() throws IOException {
        PosixServer server = new PosixServer();
        PathProviderFileSystem fs = new PathProviderFileSystem(server);
        FilePath home = fs.home();
        // A link to a folder goes, not what it points to.
        fs.delete(home.resolve("current"), true);
        assertThat(server.nodes).doesNotContainKey("/home/deploy/current").containsKey("/home/deploy/logs/app.log");
        assertThat(server.calls).contains("rm /home/deploy/current");
        assertThatThrownBy(() -> fs.delete(home.resolve("logs"), false)).hasMessageContaining("not empty");
        server.calls.clear();
        fs.delete(home, true);
        assertThat(server.nodes.keySet()).containsExactly("/", "/home", "/var");
        assertThat(server.calls.stream().filter(c -> c.startsWith("rm")).toList()).containsExactly("rm /home/deploy/.bashrc",
                "rm /home/deploy/app.jar", "rm /home/deploy/logs/app.log", "rmdir /home/deploy/logs", "rmdir /home/deploy");
        assertThatThrownBy(() -> fs.delete(FilePath.TOP, true)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> fs.delete(FilePath.of("nope"), false)).isInstanceOf(NoSuchFileException.class);
    }

    /** A provider whose paths look like Windows ones ({@code C:\work}): the adapter only uses its own path math. */
    @Test
    void otherSyntaxesWork() throws IOException {
        PathProvider windows = new PathProvider() {
            @Override
            public String displayName() {
                return "Share";
            }

            @Override
            public String homeDirectory() {
                return "C:\\work\\reports";
            }

            @Override
            public List<Item> list(String directory) {
                return directory.equals("C:\\work") ? List.of(new Item("reports", "C:\\work\\reports", true, false, 0, null, null, null))
                        : List.of();
            }

            @Override
            public Optional<Item> find(String path) {
                return Optional.empty();
            }

            @Override
            public String resolve(String directory, String name) {
                return directory.endsWith("\\") ? directory + name : directory + "\\" + name;
            }

            @Override
            public String parent(String path) {
                int slash = path.lastIndexOf('\\');
                return path.endsWith(":\\") ? null : slash == 2 ? path.substring(0, 3) : path.substring(0, slash);
            }

            @Override
            public String fileName(String path) {
                return path.endsWith(":\\") ? path : path.substring(path.lastIndexOf('\\') + 1);
            }
        };
        PathProviderFileSystem fs = new PathProviderFileSystem(windows);
        assertThat(fs.home()).isEqualTo(FilePath.of("work", "reports"));
        assertThat(fs.separator()).isEqualTo('\\');
        assertThat(fs.format(FilePath.of("work"))).isEqualTo("C:\\work");
        assertThat(fs.format(FilePath.TOP)).isEqualTo("C:\\");
        assertThat(fs.parse("C:\\work\\..\\tmp", null)).isEqualTo(FilePath.of("tmp"));
        assertThat(fs.parse("2025\\q1", fs.home())).isEqualTo(FilePath.of("work", "reports", "2025", "q1"));
        assertThat(fs.list(FilePath.of("work"))).extracting(FileEntry::path).containsExactly(FilePath.of("work", "reports"));
        assertThat(fs.capabilities()).doesNotContain(Capability.PERMISSIONS);
    }
}
