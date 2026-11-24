package com.zapthemes.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link FileSystem} over a provider whose paths are strings in its own syntax, as zapscp's
 * {@code vfs.FileSystemProvider} (SFTP, SCP, FTP, WebDAV, S3) is. All path math goes through the
 * provider ({@link PathProvider#resolve}, {@link PathProvider#parent}, {@link PathProvider#fileName}),
 * so a {@link FilePath} is the chain of names from the provider's root: its {@code parent} of the
 * home folder followed up to {@code null}.
 *
 * <p>zapscp's adapter is then a {@link PathProvider} that hands each call to its provider and maps
 * its {@code FileEntry} to an {@link Item} field by field.
 *
 * <p>Every call may go to a server, so the filesystem says {@link Capability#SLOW}. Names that
 * start with a dot are hidden, on every server.
 */
public class PathProviderFileSystem implements FileSystem {

    /** An entry as the provider lists it (zapscp's {@code vfs.FileEntry}). */
    public record Item(String name, String path, boolean directory, boolean symbolicLink, long size, Instant modified,
            String permissions, String owner, String group, Duration timePrecision) {

        public Item {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(path, "path");
        }

        /** An item without a group whose time, if any, is exact. */
        public Item(String name, String path, boolean directory, boolean symbolicLink, long size, Instant modified,
                String permissions, String owner) {
            this(name, path, directory, symbolicLink, size, modified, permissions, owner, null, null);
        }
    }

    /**
     * What the provider does, with string paths. These are the methods of zapscp's
     * {@code FileSystemProvider} that browsing needs; the operations it lacks refuse by default.
     */
    public interface PathProvider extends AutoCloseable {

        /** "Local", "user@host:22". */
        String displayName();

        String homeDirectory() throws IOException;

        /** The items of {@code directory}, without "." and "..". */
        List<Item> list(String directory) throws IOException;

        /** The item at {@code path}, following links; empty when there is none. */
        Optional<Item> find(String path) throws IOException;

        String resolve(String directory, String name);

        /** The parent of {@code path}, or {@code null} at a root. */
        String parent(String path);

        /** The last name of {@code path}, or {@code path} itself at a root. */
        String fileName(String path);

        default InputStream openRead(String path) throws IOException {
            throw new UnsupportedOperationException(displayName() + " cannot read files");
        }

        default OutputStream openWrite(String path) throws IOException {
            throw new UnsupportedOperationException(displayName() + " cannot write files");
        }

        default void createDirectory(String path) throws IOException {
            throw new UnsupportedOperationException(displayName() + " cannot create folders");
        }

        /** Deletes a file or an empty folder. */
        default void delete(String path, boolean directory) throws IOException {
            throw new UnsupportedOperationException(displayName() + " cannot delete");
        }

        /** Renames or moves {@code from} to {@code to}. */
        default void rename(String from, String to) throws IOException {
            throw new UnsupportedOperationException(displayName() + " cannot rename");
        }

        /** Whether items have permissions ({@code rwxr-xr-x}) and owners. */
        default boolean supportsPermissions() {
            return false;
        }

        @Override
        default void close() throws IOException {
        }
    }

    private final PathProvider provider;
    private final String topName;
    private final Set<Capability> capabilities;
    private volatile String root;
    private volatile FilePath home;

    /**
     * A filesystem over {@code provider}, its top named {@code topName} (a host name, say), with
     * {@code capabilities} (null: every operation, permissions and owners when the provider says it
     * supports them, and {@link Capability#SLOW}).
     */
    public PathProviderFileSystem(PathProvider provider, String topName, Set<Capability> capabilities) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.topName = topName != null ? topName : provider.displayName();
        if (capabilities == null) {
            EnumSet<Capability> all = EnumSet.of(Capability.CREATE_DIRECTORY, Capability.RENAME, Capability.MOVE, Capability.DELETE,
                    Capability.READ, Capability.WRITE, Capability.SLOW);
            if (provider.supportsPermissions()) {
                all.add(Capability.PERMISSIONS);
                all.add(Capability.OWNERS);
            }
            this.capabilities = Collections.unmodifiableSet(all);
        } else {
            this.capabilities = Collections.unmodifiableSet(capabilities.isEmpty() ? EnumSet.noneOf(Capability.class)
                    : EnumSet.copyOf(capabilities));
        }
    }

    /** A filesystem over {@code provider} with the default capabilities, its top named after it. */
    public PathProviderFileSystem(PathProvider provider) {
        this(provider, null, null);
    }

    /** The provider this filesystem goes through. */
    public PathProvider provider() {
        return provider;
    }

    @Override
    public String displayName() {
        return provider.displayName();
    }

    @Override
    public String topName() {
        return topName;
    }

    /** The home folder; asks the provider (blocking) the first time. Falls back to the top if that fails. */
    @Override
    public FilePath home() {
        FilePath known = home;
        if (known == null) {
            try {
                known = pathOf(provider.homeDirectory());
            } catch (IOException | RuntimeException e) {
                return FilePath.TOP;
            }
            home = known;
        }
        return known;
    }

    @Override
    public Set<Capability> capabilities() {
        return capabilities;
    }

    @Override
    public List<FileEntry> list(FilePath folder) throws IOException {
        String dir = string(folder);
        List<Item> items = provider.list(dir);
        List<FileEntry> entries = new ArrayList<>(items.size());
        for (Item item : items) {
            if (item.name().equals(".") || item.name().equals("..")) {
                continue;
            }
            entries.add(entry(folder.resolve(item.name()), item));
        }
        return entries;
    }

    @Override
    public Optional<FileEntry> stat(FilePath path) throws IOException {
        if (path.isTop()) {
            return Optional.of(FileEntry.builder(path, EntryKind.DIRECTORY).label(topName).build());
        }
        return provider.find(string(path)).map(item -> entry(path, item));
    }

    /**
     * {@code ~} and {@code ~/...} from the home folder, a path that starts at the root as given,
     * anything else from {@code base}; "." and ".." are resolved here, without asking the server.
     */
    @Override
    public FilePath parse(String text, FilePath base) {
        String t = text.strip();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("No path given");
        }
        if (t.equals("~") || t.startsWith("~/")) {
            return PathNames.resolve(home(), PathNames.split(t.substring(1), "/"), false);
        }
        String rootText = root();
        boolean absolute = t.startsWith(rootText) || t.startsWith("/");
        if (!absolute && base == null) {
            throw new IllegalArgumentException("\"" + text + "\" is not a full path");
        }
        String rest = t.startsWith(rootText) ? t.substring(rootText.length()) : absolute ? t.substring(1) : t;
        String separators = separator() == '/' ? "/" : "/" + separator();
        return PathNames.resolve(absolute ? FilePath.TOP : base, PathNames.split(rest, separators), false);
    }

    /** The character the provider puts between names: what {@code resolve} adds between two of them. */
    @Override
    public char separator() {
        String a = provider.resolve(root(), "a");
        String ab = provider.resolve(a, "b");
        return ab.length() == a.length() + 2 && ab.startsWith(a) && ab.endsWith("b") ? ab.charAt(a.length()) : '/';
    }

    /** The provider's own path. */
    @Override
    public String format(FilePath path) {
        return string(path);
    }

    @Override
    public FilePath createDirectory(FilePath parent, String name) throws IOException {
        check(Capability.CREATE_DIRECTORY, "create folders");
        FilePath path = parent.resolve(FilePath.checkName(name));
        if (provider.find(string(path)).isPresent()) {
            throw new FileAlreadyExistsException(format(path));
        }
        provider.createDirectory(string(path));
        return path;
    }

    @Override
    public FilePath rename(FilePath path, String newName) throws IOException {
        check(Capability.RENAME, "rename");
        FilePath target = path.parent().resolve(FilePath.checkName(newName));
        if (!target.equals(path) && !newName.equalsIgnoreCase(path.name()) && provider.find(string(target)).isPresent()) {
            throw new FileAlreadyExistsException(format(target));
        }
        provider.rename(string(path), string(target));
        return target;
    }

    @Override
    public FilePath move(FilePath path, FilePath targetFolder) throws IOException {
        check(Capability.MOVE, "move");
        if (targetFolder.startsWith(path)) {
            throw new IOException(format(path) + " can't be moved into itself");
        }
        FilePath target = targetFolder.resolve(path.name());
        if (provider.find(string(target)).isPresent()) {
            throw new FileAlreadyExistsException(format(target));
        }
        provider.rename(string(path), string(target));
        return target;
    }

    /** Deletes {@code path}; a folder's contents first, deepest first, when {@code recursive}. Links aren't followed. */
    @Override
    public void delete(FilePath path, boolean recursive) throws IOException {
        check(Capability.DELETE, "delete");
        if (path.isTop()) {
            throw new IOException("The top can't be deleted");
        }
        String target = string(path);
        Item item = linkAware(path, target);
        if (!item.directory() || item.symbolicLink()) {
            provider.delete(target, item.directory() && !item.symbolicLink());
            return;
        }
        if (recursive) {
            // Depth first without recursion: folders are deleted once their contents are.
            Deque<String> folders = new ArrayDeque<>();
            Deque<String> order = new ArrayDeque<>();
            folders.push(target);
            while (!folders.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.io.InterruptedIOException("Delete of " + target + " stopped");
                }
                String dir = folders.pop();
                order.push(dir);
                for (Item child : provider.list(dir)) {
                    if (child.name().equals(".") || child.name().equals("..")) {
                        continue;
                    }
                    String childPath = provider.resolve(dir, child.name());
                    if (child.directory() && !child.symbolicLink()) {
                        folders.push(childPath);
                    } else {
                        provider.delete(childPath, false);
                    }
                }
            }
            while (!order.isEmpty()) {
                provider.delete(order.pop(), true);
            }
        } else {
            provider.delete(target, true);
        }
    }

    /** The item at {@code path} as its folder lists it (a link stays a link), or as found. */
    private Item linkAware(FilePath path, String target) throws IOException {
        try {
            for (Item item : provider.list(string(path.parent()))) {
                if (item.name().equals(path.name())) {
                    return item;
                }
            }
        } catch (IOException e) {
            // Fall back to find below.
        }
        return provider.find(target).orElseThrow(() -> new NoSuchFileException(target));
    }

    @Override
    public InputStream openRead(FilePath path) throws IOException {
        check(Capability.READ, "read files");
        return provider.openRead(string(path));
    }

    @Override
    public OutputStream openWrite(FilePath path) throws IOException {
        check(Capability.WRITE, "write files");
        return provider.openWrite(string(path));
    }

    @Override
    public void close() throws IOException {
        try {
            provider.close();
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    // ------------------------------------------------------------------ paths

    /** The provider's path of {@code path}. */
    public String string(FilePath path) {
        String s = root();
        for (String segment : path.segments()) {
            s = provider.resolve(s, segment);
        }
        return s;
    }

    /** The {@link FilePath} of the provider's path {@code text} (absolute, in its syntax). */
    public FilePath pathOf(String text) {
        Deque<String> names = new ArrayDeque<>();
        String p = text;
        for (String parent = provider.parent(p); parent != null && !parent.equals(p); parent = provider.parent(p)) {
            names.push(provider.fileName(p));
            p = parent;
        }
        if (root == null) {
            root = p;
        }
        List<String> segments = new ArrayList<>();
        for (String name : names) {
            if (!name.isEmpty()) {
                segments.add(name);
            }
        }
        return FilePath.of(segments);
    }

    /** The provider's root: the top of its home folder, or "/" until known. */
    private String root() {
        String known = root;
        if (known == null) {
            home();
            known = root;
        }
        return known != null ? known : "/";
    }

    private FileEntry entry(FilePath path, Item item) {
        return FileEntry.builder(path, item.directory() ? EntryKind.DIRECTORY : EntryKind.FILE)
                .size(item.directory() ? -1 : item.size())
                .modified(item.modified())
                .timePrecision(item.timePrecision() == null || item.timePrecision().isZero() ? null : item.timePrecision())
                .permissions(item.permissions())
                .owner(item.owner())
                .group(item.group())
                .hidden(item.name().startsWith("."))
                .link(item.symbolicLink())
                .build();
    }

    private void check(Capability capability, String what) {
        if (!capabilities.contains(capability)) {
            throw new UnsupportedOperationException(displayName() + " cannot " + what);
        }
    }
}
