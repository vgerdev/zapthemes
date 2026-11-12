package com.zapthemes.files;

import java.time.Instant;
import java.util.Comparator;

/** Explorer's orders: folders first, then by a column, ties broken by natural name order. */
public final class FileOrder {

    /** What entries are sorted by. */
    public enum Key {
        NAME, MODIFIED, TYPE, SIZE, PERMISSIONS, OWNER
    }

    private FileOrder() {
    }

    /**
     * Compares names as people read them: case-insensitively, with runs of digits by their value,
     * so {@code file2} comes before {@code file10}. Names equal but for case are then ordered by case.
     */
    public static int compareNatural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = skipZeros(a, i);
                int sj = skipZeros(b, j);
                int ei = digitsEnd(a, si);
                int ej = digitsEnd(b, sj);
                int lengths = Integer.compare(ei - si, ej - sj);
                if (lengths != 0) {
                    return lengths;
                }
                int digits = a.substring(si, ei).compareTo(b.substring(sj, ej));
                if (digits != 0) {
                    return digits;
                }
                i = ei;
                j = ej;
                continue;
            }
            int c = Character.compare(Character.toLowerCase(ca), Character.toLowerCase(cb));
            if (c != 0) {
                return c;
            }
            i++;
            j++;
        }
        int rest = Integer.compare(a.length() - i, b.length() - j);
        return rest != 0 ? rest : a.compareTo(b);
    }

    private static int skipZeros(String s, int from) {
        int i = from;
        while (i < s.length() - 1 && s.charAt(i) == '0' && Character.isDigit(s.charAt(i + 1))) {
            i++;
        }
        return i;
    }

    private static int digitsEnd(String s, int from) {
        int i = from;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        return i;
    }

    /** The order of {@code key}, ascending or not; folders (and drives) come first either way. */
    public static Comparator<FileEntry> by(Key key, boolean ascending) {
        Comparator<FileEntry> byName = (x, y) -> compareNatural(x.displayName(), y.displayName());
        Comparator<FileEntry> column = switch (key) {
            case NAME -> byName;
            case MODIFIED -> Comparator.comparing(FileEntry::modified, Comparator.nullsFirst(Comparator.<Instant>naturalOrder()));
            case TYPE -> Comparator.comparing(FileTypes::typeName, FileOrder::compareNatural);
            case SIZE -> Comparator.comparingLong(FileEntry::size);
            case PERMISSIONS -> Comparator.comparing(FileEntry::permissions, Comparator.nullsFirst(Comparator.<String>naturalOrder()));
            case OWNER -> Comparator.comparing(FileEntry::owner, Comparator.nullsFirst(Comparator.<String>naturalOrder()));
        };
        if (key != Key.NAME) {
            column = column.thenComparing(byName);
        }
        if (!ascending) {
            column = column.reversed();
        }
        Comparator<FileEntry> foldersFirst = Comparator.comparingInt(e -> e.isContainer() ? 0 : 1);
        return foldersFirst.thenComparing(column);
    }
}
