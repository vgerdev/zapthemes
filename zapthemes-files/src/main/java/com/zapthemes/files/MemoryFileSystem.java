package com.zapthemes.files;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tree of folders and files in memory: a virtual filesystem for tests and demos, and an example of
 * one. Paths are {@code /}-separated. Each call can wait a while first ({@link Builder#delay}), as a
 * remote server would, and a path can be made to fail ({@link #failOn}).
 *
 * <pre>{@code
 * MemoryFileSystem fs = MemoryFileSystem.builder("server")
 *         .folder("home/me")
 *         .file("home/me/notes.txt", "Hello")
 *         .file("home/me/big.iso", 700L << 20)
 *         .delay(Duration.ofMillis(300))
 *         .build();
 * }</pre>
 *
 * <p>It is safe to use from several threads.
 */
public final class MemoryFileSystem implements FileSystem {

    private static final class Node {
        final boolean folder;
        final Map<String, Node> children = new TreeMap<>();
        byte[] content;
        long size;
        Instant modified;
        String permissions;
        String owner;
        String group;

        Node(boolean folder, Instant modified) {
            this.folder = folder;
            this.modified = modified;
        }
    }

    private final String name;
    private final Node top;
    private final Duration delay;
    private final FilePath home;
    private final boolean posix;
    private final Map<FilePath, IOException> failures = new ConcurrentHashMap<>();
    private final Instant created;

    private MemoryFileSystem(Builder builder) {
        this.name = builder.name;
        this.top = builder.top;
        this.delay = builder.delay;
        this.home = builder.home;
        this.posix = builder.posix;
        this.created = builder.now;
    }

    /** A builder for a filesystem whose top is named {@code name}. */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /** Makes every call on {@code path} fail with {@code failure}, until {@link #clearFailures}. */
    public void failOn(FilePath path, IOException failure) {
        failures.put(path, failure);
    }

    public void clearFailures() {
        failures.clear();
    }

    @Override
    public String displayName() {
        return name;
    }

    @Override
    public String topName() {
        return name;
    }

    @Override
    public FilePath home() {
        return home;
    }

    @Override
    public Set<Capability> capabilities() {
        Set<Capability> set = EnumSet.of(Capability.CREATE_DIRECTORY, Capability.RENAME, Capability.MOVE, Capability.DELETE,
                Capability.READ, Capability.WRITE);
        if (posix) {
            set.add(Capability.PERMISSIONS);
            set.add(Capability.OWNERS);
        }
        if (!delay.isZero()) {
            set.add(Capability.SLOW);
        }
        return set;
    }

    @Override
    public List<FileEntry> list(FilePath folder) throws IOException {
        enter(folder);
        synchronized (this) {
            Node node = find(folder);
            if (!node.folder) {
                throw new NotDirectoryException(format(folder));
            }
            List<FileEntry> entries = new ArrayList<>(node.children.size());
            node.children.forEach((childName, child) -> entries.add(entry(folder.resolve(childName), child)));
            return entries;
        }
    }

    @Override
    public Optional<FileEntry> stat(FilePath path) throws IOException {
        enter(path);
        synchronized (this) {
            Node node = lookup(path);
            return node == null ? Optional.empty() : Optional.of(entry(path, node));
        }
    }

    @Override
    public FilePath parse(String text, FilePath base) {
        String t = text.strip();
        if (t.equals("~") || t.startsWith("~/")) {
            return PathNames.resolve(home, PathNames.split(t.substring(1), "/"), false);
        }
        boolean absolute = t.startsWith("/");
        if (!absolute && base == null) {
            throw new IllegalArgumentException("\"" + text + "\" is not a full path");
        }
        return PathNames.resolve(absolute ? FilePath.TOP : base, PathNames.split(t, "/"), false);
    }

    @Override
    public String format(FilePath path) {
        return "/" + String.join("/", path.segments());
    }

    @Override
    public FilePath createDirectory(FilePath parent, String childName) throws IOException {
        checkName(childName);
        enter(parent);
        synchronized (this) {
            Node folder = folder(parent);
            if (folder.children.containsKey(childName)) {
                throw new FileAlreadyExistsException(format(parent.resolve(childName)));
            }
            folder.children.put(childName, new Node(true, Instant.now()));
            folder.modified = Instant.now();
        }
        return parent.resolve(childName);
    }

    @Override
    public FilePath rename(FilePath path, String newName) throws IOException {
        checkName(newName);
        enter(path);
        synchronized (this) {
            Node parent = folder(path.parent());
            Node node = parent.children.get(path.name());
            if (node == null) {
                throw new NoSuchFileException(format(path));
            }
            if (!newName.equals(path.name()) && parent.children.containsKey(newName)) {
                throw new FileAlreadyExistsException(format(path.parent().resolve(newName)));
            }
            parent.children.remove(path.name());
            parent.children.put(newName, node);
        }
        return path.parent().resolve(newName);
    }

    @Override
    public FilePath move(FilePath path, FilePath targetFolder) throws IOException {
        if (path.isTop()) {
            throw new IOException("The top can't be moved");
        }
        if (targetFolder.startsWith(path)) {
            throw new IOException(format(path) + " can't be moved into itself");
        }
        enter(path);
        synchronized (this) {
            Node from = folder(path.parent());
            Node node = from.children.get(path.name());
            if (node == null) {
                throw new NoSuchFileException(format(path));
            }
            Node to = folder(targetFolder);
            if (to.children.containsKey(path.name())) {
                throw new FileAlreadyExistsException(format(targetFolder.resolve(path.name())));
            }
            from.children.remove(path.name());
            to.children.put(path.name(), node);
        }
        return targetFolder.resolve(path.name());
    }

    @Override
    public void delete(FilePath path, boolean recursive) throws IOException {
        if (path.isTop()) {
            throw new IOException("The top can't be deleted");
        }
        enter(path);
        synchronized (this) {
            Node parent = folder(path.parent());
            Node node = parent.children.get(path.name());
            if (node == null) {
                throw new NoSuchFileException(format(path));
            }
            if (node.folder && !node.children.isEmpty() && !recursive) {
                throw new DirectoryNotEmptyException(format(path));
            }
            parent.children.remove(path.name());
        }
    }

    @Override
    public InputStream openRead(FilePath path) throws IOException {
        enter(path);
        synchronized (this) {
            Node node = find(path);
            if (node.folder) {
                throw new IOException(format(path) + " is a folder");
            }
            byte[] bytes = node.content != null ? node.content : new byte[(int) Math.min(node.size, 1 << 20)];
            return new ByteArrayInputStream(bytes);
        }
    }

    @Override
    public OutputStream openWrite(FilePath path) throws IOException {
        enter(path);
        FilePath parentPath = path.parent();
        synchronized (this) {
            folder(parentPath);
        }
        return new ByteArrayOutputStream() {
            @Override
            public void close() {
                synchronized (MemoryFileSystem.this) {
                    Node parent = lookup(parentPath);
                    if (parent == null || !parent.folder) {
                        return;
                    }
                    Node node = new Node(false, Instant.now());
                    node.content = toByteArray();
                    node.size = node.content.length;
                    parent.children.put(path.name(), node);
                }
            }
        };
    }

    /** Waits the delay and throws an injected failure. */
    private void enter(FilePath path) throws IOException {
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted");
            }
        }
        IOException failure = failures.get(path);
        if (failure != null) {
            throw failure;
        }
    }

    private Node lookup(FilePath path) {
        Node node = top;
        for (String segment : path.segments()) {
            if (!node.folder) {
                return null;
            }
            node = node.children.get(segment);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private Node find(FilePath path) throws NoSuchFileException {
        Node node = lookup(path);
        if (node == null) {
            throw new NoSuchFileException(format(path));
        }
        return node;
    }

    private Node folder(FilePath path) throws IOException {
        Node node = find(path);
        if (!node.folder) {
            throw new NotDirectoryException(format(path));
        }
        return node;
    }

    private FileEntry entry(FilePath path, Node node) {
        FileEntry.Builder builder = FileEntry.builder(path, node.folder ? EntryKind.DIRECTORY : EntryKind.FILE)
                .modified(node.modified != null ? node.modified : created).hidden(path.name().startsWith("."))
                .permissions(node.permissions).owner(node.owner).group(node.group);
        if (path.isTop()) {
            builder.label(name);
        }
        if (!node.folder) {
            builder.size(node.size);
        }
        return builder.build();
    }

    private static void checkName(String childName) {
        FilePath.checkName(childName);
    }

    /** Builds a {@link MemoryFileSystem}. Paths are {@code /}-separated and relative to the top. */
    public static final class Builder {
        private final String name;
        private final Node top;
        private final Instant now = Instant.now();
        private Duration delay = Duration.ZERO;
        private FilePath home = FilePath.TOP;
        private boolean posix;
        private Node last;

        private Builder(String name) {
            this.name = name;
            this.top = new Node(true, now);
        }

        /** Adds the folder {@code path} and its parents. */
        public Builder folder(String path) {
            last = make(path, true);
            return this;
        }

        /** Adds a file of {@code size} bytes (its content reads as zeros). */
        public Builder file(String path, long size) {
            last = make(path, false);
            last.size = size;
            return this;
        }

        /** Adds a file holding {@code text} in UTF-8. */
        public Builder file(String path, String text) {
            last = make(path, false);
            last.content = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            last.size = last.content.length;
            return this;
        }

        /** Sets the last added entry's modification time. */
        public Builder modified(Instant modified) {
            last.modified = modified;
            return this;
        }

        /** Sets the last added entry's POSIX permissions, owner and group, and turns those columns on. */
        public Builder posix(String permissions, String owner, String group) {
            last.permissions = permissions;
            last.owner = owner;
            last.group = group;
            posix = true;
            return this;
        }

        /** Makes every call wait {@code delay} first, like a remote server. */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /** Where browsing starts; the top by default. */
        public Builder home(String path) {
            this.home = FilePath.of(PathNames.split(path, "/"));
            return this;
        }

        public MemoryFileSystem build() {
            return new MemoryFileSystem(this);
        }

        private Node make(String path, boolean folder) {
            List<String> names = PathNames.split(path, "/");
            if (names.isEmpty()) {
                throw new IllegalArgumentException("Give a path below the top");
            }
            Node node = top;
            for (int i = 0; i < names.size(); i++) {
                String segment = FilePath.checkName(names.get(i));
                boolean isLast = i == names.size() - 1;
                Node child = node.children.get(segment);
                if (child == null) {
                    child = new Node(!isLast || folder, now);
                    node.children.put(segment, child);
                } else if (!child.folder && !isLast) {
                    throw new IllegalArgumentException(segment + " is a file");
                }
                node = child;
            }
            return node;
        }
    }
}
