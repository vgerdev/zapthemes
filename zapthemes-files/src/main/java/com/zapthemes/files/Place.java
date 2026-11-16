package com.zapthemes.files;

import java.util.Objects;

/**
 * A place a browser's navigation pane lists: a pinned or standard folder.
 *
 * @param label what the pane shows
 * @param path  where it goes
 * @param kind  which icon it gets: {@code "home"}, {@code "desktop"}, {@code "downloads"},
 *              {@code "documents"}, {@code "pictures"}, {@code "music"}, {@code "videos"} or
 *              {@code "folder"}
 */
public record Place(String label, FilePath path, String kind) {

    public Place {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(path, "path");
        kind = kind == null ? "folder" : kind;
    }

    /** A plain folder, labeled by its name. */
    public static Place folder(FilePath path) {
        return new Place(path.name(), path, "folder");
    }
}
