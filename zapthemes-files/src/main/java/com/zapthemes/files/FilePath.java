package com.zapthemes.files;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An absolute path within one {@link FileSystem}, as a list of segments from the filesystem's top.
 * The top itself has no segments: "This PC" for the local filesystem on Windows (whose first
 * segments are drives such as {@code "C:\"}), {@code /} elsewhere, the root folder of a
 * {@link RootedFileSystem}. Paths are immutable, compare segment by segment, and never hold
 * {@code "."} or {@code ".."}: a filesystem's {@link FileSystem#parse} resolves those.
 */
public final class FilePath implements Comparable<FilePath> {

    /** The filesystem's top. */
    public static final FilePath TOP = new FilePath(List.of());

    private final List<String> segments;

    private FilePath(List<String> segments) {
        this.segments = segments;
    }

    /** A path of {@code segments}, each a valid name ({@link #checkName}). */
    public static FilePath of(String... segments) {
        return of(List.of(segments));
    }

    /** A path of {@code segments}, each a valid name ({@link #checkName}). */
    public static FilePath of(List<String> segments) {
        if (segments.isEmpty()) {
            return TOP;
        }
        segments.forEach(FilePath::checkName);
        return new FilePath(List.copyOf(segments));
    }

    /**
     * Checks one segment: not empty, not {@code "."} or {@code ".."}, no {@code '/'} and no
     * {@code '\0'}. (A backslash is allowed: Windows drive segments are {@code "C:\"}; filesystems
     * with {@code '\'} as a separator refuse it in names themselves.)
     *
     * @throws IllegalArgumentException if it is not a valid segment
     */
    public static String checkName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Not a valid file name: \"" + name + "\"");
        }
        return name;
    }

    public List<String> segments() {
        return segments;
    }

    public boolean isTop() {
        return segments.isEmpty();
    }

    /** The number of segments; 0 at the top. */
    public int depth() {
        return segments.size();
    }

    /** The last segment, or {@code ""} at the top. */
    public String name() {
        return segments.isEmpty() ? "" : segments.getLast();
    }

    /** The parent path, or {@code null} at the top. */
    public FilePath parent() {
        return segments.isEmpty() ? null : segments.size() == 1 ? TOP : new FilePath(segments.subList(0, segments.size() - 1));
    }

    /** This path with one more segment. */
    public FilePath resolve(String name) {
        checkName(name);
        List<String> list = new ArrayList<>(segments.size() + 1);
        list.addAll(segments);
        list.add(name);
        return new FilePath(List.copyOf(list));
    }

    /** This path followed by the segments of {@code relative}. */
    public FilePath resolve(FilePath relative) {
        if (relative.isTop()) {
            return this;
        }
        if (isTop()) {
            return relative;
        }
        List<String> list = new ArrayList<>(segments);
        list.addAll(relative.segments);
        return new FilePath(List.copyOf(list));
    }

    /** The first {@code depth} segments of this path. */
    public FilePath prefix(int depth) {
        return depth <= 0 ? TOP : depth >= segments.size() ? this : new FilePath(List.copyOf(segments.subList(0, depth)));
    }

    /** Whether this path is {@code other} or inside it. */
    public boolean startsWith(FilePath other) {
        return other.segments.size() <= segments.size() && segments.subList(0, other.segments.size()).equals(other.segments);
    }

    /** The path of this one relative to {@code ancestor}, which it must start with. */
    public FilePath relativeTo(FilePath ancestor) {
        if (!startsWith(ancestor)) {
            throw new IllegalArgumentException(this + " is not inside " + ancestor);
        }
        return of(segments.subList(ancestor.segments.size(), segments.size()));
    }

    @Override
    public int compareTo(FilePath other) {
        int n = Math.min(segments.size(), other.segments.size());
        for (int i = 0; i < n; i++) {
            int c = segments.get(i).compareTo(other.segments.get(i));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(segments.size(), other.segments.size());
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof FilePath other && segments.equals(other.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    /** A debugging form, {@code /a/b}; use {@link FileSystem#format} for what users see. */
    @Override
    public String toString() {
        return "/" + String.join("/", segments);
    }
}
