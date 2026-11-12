package com.zapthemes.files;

import java.util.Objects;

/**
 * A folder of some filesystem, as the address bar's drop-down of recent locations lists it: a plain
 * path ({@code C:\opt}), a named place an application adds ("Projects", "Recycle Bin"), or a folder
 * of a remote session. A history of locations can mix filesystems, so one list serves several
 * browsers.
 *
 * @param fileSystem the filesystem the folder is in
 * @param path       the folder
 * @param label      what the drop-down shows and what can be typed to go there, or null for the
 *                   path itself ({@link #text})
 * @param kind       the icon: a {@link Place} kind ({@code "home"}, {@code "documents"}, ...), or
 *                   null for a folder's
 */
public record Location(FileSystem fileSystem, FilePath path, String label, String kind) {

    public Location {
        Objects.requireNonNull(fileSystem, "fileSystem");
        Objects.requireNonNull(path, "path");
        label = label == null || label.isBlank() ? null : label.strip();
    }

    /** The folder {@code path} of {@code fs}, shown as its path. */
    public static Location of(FileSystem fs, FilePath path) {
        return new Location(fs, path, null, null);
    }

    /** {@code place} of {@code fs}, under its label and with its icon. */
    public static Location of(FileSystem fs, Place place) {
        return new Location(fs, place.path(), place.label(), place.kind());
    }

    /**
     * What a browser of {@code shownIn} shows for this location: the label, else the path; a path
     * of another filesystem is followed by that filesystem's name ("/var/log on deploy@build-01").
     */
    public String text(FileSystem shownIn) {
        if (label != null) {
            return label;
        }
        String formatted = fileSystem.format(path);
        return fileSystem == shownIn ? formatted : formatted + " on " + fileSystem.displayName();
    }

    /** Whether {@code other} is the same folder of the same filesystem, whatever their labels. */
    public boolean sameFolder(Location other) {
        if (other == null || other.fileSystem != fileSystem) {
            return false;
        }
        if (fileSystem.has(Capability.CASE_INSENSITIVE)) {
            return fileSystem.format(path).equalsIgnoreCase(fileSystem.format(other.path));
        }
        return path.equals(other.path);
    }
}
