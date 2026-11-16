package com.zapthemes.files;

import java.util.ArrayList;
import java.util.List;

/** Splitting typed paths into names and resolving {@code "."} and {@code ".."}, for filesystems. */
public final class PathNames {

    private PathNames() {
    }

    /** The non-empty names of {@code text} between any of {@code separators}. */
    public static List<String> split(String text, String separators) {
        List<String> names = new ArrayList<>();
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (separators.indexOf(c) >= 0) {
                if (!name.isEmpty()) {
                    names.add(name.toString());
                    name.setLength(0);
                }
            } else {
                name.append(c);
            }
        }
        if (!name.isEmpty()) {
            names.add(name.toString());
        }
        return names;
    }

    /**
     * {@code start} followed by {@code names}, with {@code "."} skipped and {@code ".."} going up.
     * {@code ".."} at the top stays there, unless {@code refuseAboveTop}.
     *
     * @throws IllegalArgumentException for {@code ".."} at the top when refused, or an invalid name
     */
    public static FilePath resolve(FilePath start, List<String> names, boolean refuseAboveTop) {
        List<String> segments = new ArrayList<>(start.segments());
        for (String name : names) {
            if (name.equals(".")) {
                continue;
            }
            if (name.equals("..")) {
                if (segments.isEmpty()) {
                    if (refuseAboveTop) {
                        throw new IllegalArgumentException("The path goes above the top");
                    }
                } else {
                    segments.removeLast();
                }
                continue;
            }
            segments.add(FilePath.checkName(name));
        }
        return FilePath.of(segments);
    }
}
