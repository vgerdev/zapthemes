package com.zapthemes.files;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The recent locations of the address bar's drop-down, most recent first: the folders typed into
 * the address bar, those picked from the drop-down, and any an application adds (named places,
 * remote folders). Locations of any filesystem can be mixed, so several browsers can share one
 * history ({@code FileBrowser.setLocationHistory}).
 *
 * <p>A folder is listed once: adding it again moves it to the top, keeping its label if the new one
 * has none. Past the limit (25, as Explorer keeps), the oldest go. Not thread-safe: use it on the
 * thread of the browsers that show it.
 *
 * <p>The history is not saved. An application that wants it back next time saves
 * {@link #entries()} in its own terms (which filesystem, which path) and adds them again, oldest
 * first.
 */
public final class LocationHistory {

    /** The default limit, as Explorer's typed paths. */
    public static final int DEFAULT_LIMIT = 25;

    private final List<Location> entries = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private int limit = DEFAULT_LIMIT;

    /** The locations, most recent first. */
    public List<Location> entries() {
        return List.copyOf(entries);
    }

    /** Puts {@code location} at the top. */
    public void add(Location location) {
        Objects.requireNonNull(location, "location");
        Location added = location;
        for (int i = 0; i < entries.size(); i++) {
            Location old = entries.get(i);
            if (old.sameFolder(location)) {
                entries.remove(i);
                if (location.label() == null && old.label() != null) {
                    added = old;
                }
                break;
            }
        }
        entries.addFirst(added);
        trim();
        changed();
    }

    /** Takes {@code location}'s folder out of the list. */
    public boolean remove(Location location) {
        boolean removed = entries.removeIf(e -> e.sameFolder(location));
        if (removed) {
            changed();
        }
        return removed;
    }

    public void clear() {
        if (!entries.isEmpty()) {
            entries.clear();
            changed();
        }
    }

    public int getLimit() {
        return limit;
    }

    /** How many locations are kept, at least 1. */
    public void setLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit < 1");
        }
        this.limit = limit;
        if (trim()) {
            changed();
        }
    }

    /**
     * The locations whose text in a browser of {@code shownIn} starts with {@code typed}, ignoring
     * case, most recent first; all of them for blank text.
     */
    public List<Location> matching(String typed, FileSystem shownIn) {
        String prefix = typed == null ? "" : typed.strip().toLowerCase(Locale.ROOT);
        return entries.stream().filter(e -> e.text(shownIn).toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    /** The location labeled {@code typed}, ignoring case: typing a place's name goes there. */
    public Optional<Location> labeled(String typed) {
        String text = typed == null ? "" : typed.strip();
        return entries.stream().filter(e -> e.label() != null && e.label().equalsIgnoreCase(text)).findFirst();
    }

    /** {@code listener} runs after every change. */
    public void addChangeListener(Runnable listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private boolean trim() {
        boolean trimmed = false;
        while (entries.size() > limit) {
            entries.removeLast();
            trimmed = true;
        }
        return trimmed;
    }

    private void changed() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }
}
