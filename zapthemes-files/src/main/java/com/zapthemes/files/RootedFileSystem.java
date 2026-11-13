package com.zapthemes.files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One folder of another filesystem, shown as a filesystem of its own: its top is that folder,
 * paths are relative to it ({@code \reports\2025}), and nothing above it can be listed, typed or
 * changed. {@code ".."} at the top is refused, and so are names with a separator.
 *
 * <p>Symbolic links inside the folder are followed as the base filesystem follows them, so a link
 * to somewhere outside shows that place's files under the link's path. A base that must not allow
 * that should not list such links.
 */
public final class RootedFileSystem implements FileSystem {

    private final FileSystem base;
    private final FilePath root;
    private final String name;

    /** {@code base} rooted at {@code root}, whose top is named after the folder. */
    public RootedFileSystem(FileSystem base, FilePath root) {
        this(base, root, null);
    }

    /** {@code base} rooted at {@code root}, whose top is named {@code name} (the folder's label if null). */
    public RootedFileSystem(FileSystem base, FilePath root, String name) {
        this.base = Objects.requireNonNull(base, "base");
        this.root = Objects.requireNonNull(root, "root");
        this.name = name;
    }

    /** The filesystem this one is a folder of. */
    public FileSystem base() {
        return base;
    }

    /** The folder of {@link #base()} that is this filesystem's top. */
    public FilePath root() {
        return root;
    }

    /** {@code path} of this filesystem as a path of the base. */
    public FilePath toBase(FilePath path) {
        return root.resolve(path);
    }

    /** {@code path} of the base as a path of this filesystem, or empty if it is outside the root. */
    public Optional<FilePath> fromBase(FilePath path) {
        return path.startsWith(root) ? Optional.of(path.relativeTo(root)) : Optional.empty();
    }

    @Override
    public String displayName() {
        return base.displayName() + ": " + topName();
    }

    @Override
    public String topName() {
        if (name != null) {
            return name;
        }
        try {
            return base.label(root);
        } catch (IOException e) {
            return root.name();
        }
    }

    @Override
    public FilePath home() {
        return FilePath.TOP;
    }

    @Override
    public Set<Capability> capabilities() {
        return base.capabilities();
    }

    @Override
    public char separator() {
        return base.separator();
    }

    @Override
    public List<FileEntry> list(FilePath folder) throws IOException {
        return base.list(toBase(folder)).stream().map(e -> e.withPath(folder.resolve(e.name()))).toList();
    }

    @Override
    public Optional<FileEntry> stat(FilePath path) throws IOException {
        if (path.isTop()) {
            return base.stat(root).map(e -> e.toBuilder().label(topName()).build().withPath(FilePath.TOP));
        }
        return base.stat(toBase(path)).map(e -> e.withPath(path));
    }

    @Override
    public String label(FilePath path) throws IOException {
        return path.isTop() ? topName() : base.label(toBase(path));
    }

    /**
     * Parses a path relative to the root ({@code \a\b} or {@code /a/b} from the top, {@code a\b}
     * from {@code base}), or a full path of the base filesystem inside the root.
     */
    @Override
    public FilePath parse(String text, FilePath from) {
        String t = text.strip();
        String separators = separator() == '\\' ? "\\/" : "/";
        boolean absolute = !t.isEmpty() && separators.indexOf(t.charAt(0)) >= 0;
        if (!t.isEmpty()) {
            // A full path of the base filesystem (C:\data\reports, /srv/data/reports) is taken if it
            // lies inside the root. Outside it, one starting with a separator is read from the root.
            FilePath full;
            try {
                full = base.parse(t, null);
            } catch (IllegalArgumentException e) {
                full = null; // Not a full path: relative to `from`.
            }
            if (full != null && full.depth() > root.depth() && full.startsWith(root)) {
                return full.relativeTo(root);
            }
            if (full != null && full.equals(root)) {
                return FilePath.TOP;
            }
            if (full != null && !absolute) {
                throw new IllegalArgumentException("\"" + text + "\" is outside " + topName());
            }
        }
        List<String> names = PathNames.split(t, separators);
        FilePath start = absolute || from == null ? FilePath.TOP : from;
        try {
            return PathNames.resolve(start, names, true);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("\"" + text + "\" is outside " + topName(), e);
        }
    }

    @Override
    public String format(FilePath path) {
        String sep = String.valueOf(separator());
        return sep + String.join(sep, path.segments());
    }

    @Override
    public FilePath createDirectory(FilePath parent, String name) throws IOException {
        checkName(name);
        base.createDirectory(toBase(parent), name);
        return parent.resolve(name);
    }

    @Override
    public FilePath rename(FilePath path, String newName) throws IOException {
        if (path.isTop()) {
            throw new IllegalArgumentException("The top of " + topName() + " can't be renamed");
        }
        checkName(newName);
        base.rename(toBase(path), newName);
        return path.parent().resolve(newName);
    }

    @Override
    public void delete(FilePath path, boolean recursive) throws IOException {
        if (path.isTop()) {
            throw new IllegalArgumentException("The top of " + topName() + " can't be deleted");
        }
        base.delete(toBase(path), recursive);
    }

    @Override
    public InputStream openRead(FilePath path) throws IOException {
        return base.openRead(toBase(path));
    }

    @Override
    public OutputStream openWrite(FilePath path) throws IOException {
        return base.openWrite(toBase(path));
    }

    @Override
    public void close() throws IOException {
        base.close();
    }

    private void checkName(String name) {
        FilePath.checkName(name);
        if (name.indexOf(separator()) >= 0 || name.indexOf('/') >= 0) {
            throw new IllegalArgumentException("A name can't contain " + separator());
        }
    }
}
