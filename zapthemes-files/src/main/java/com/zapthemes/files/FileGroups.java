package com.zapthemes.files;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Explorer's "Group by": the groups of a folder's entries by name (0–9, A–H, I–P, Q–Z, Other), date
 * modified (Today, Yesterday, … A long time ago), type, size (Empty, Tiny, … Gigantic) and, where a
 * filesystem has them, permissions and owner. Entries keep their order within a group.
 */
public final class FileGroups {

    /** A group: its label ("Today", "A - H") and its entries, in order. */
    public record Group(String label, List<FileEntry> entries) {
        public Group {
            Objects.requireNonNull(label, "label");
            entries = List.copyOf(entries);
        }
    }

    private static final List<String> NAMES = List.of("0 - 9", "A - H", "I - P", "Q - Z", "Other");
    private static final List<String> DATES = List.of("A long time ago", "Earlier this year", "Last month", "Earlier this month", "Last week",
            "Earlier this week", "Yesterday", "Today", "Later");
    private static final List<String> SIZES = List.of("Empty", "Tiny", "Small", "Medium", "Large", "Huge", "Gigantic");
    /** Shown below the groups with an order, whichever the direction. */
    private static final String UNKNOWN = "Unknown";
    private static final String UNSPECIFIED = "Unspecified";

    private FileGroups() {
    }

    /**
     * Groups {@code entries} (in the order they are shown) by {@code key}: in the label order of the
     * key ({@code ascending}, or reversed), entries in their order within each group. Dates are
     * grouped as at {@code now} in {@code zone}.
     */
    public static List<Group> group(List<FileEntry> entries, FileOrder.Key key, boolean ascending, Instant now, ZoneId zone) {
        Function<FileEntry, String> labelOf = switch (key) {
            case NAME -> FileGroups::nameGroup;
            case MODIFIED -> e -> dateGroup(e.modified(), now, zone);
            case TYPE -> FileTypes::typeName;
            case SIZE -> e -> e.isContainer() ? UNSPECIFIED : sizeGroup(e.size());
            case PERMISSIONS -> e -> e.permissions() == null || e.permissions().isEmpty() ? UNSPECIFIED : e.permissions();
            case OWNER -> e -> e.owner() == null || e.owner().isEmpty() ? UNSPECIFIED : e.owner();
            case GROUP -> e -> e.group() == null || e.group().isEmpty() ? UNSPECIFIED : e.group();
        };
        Map<String, List<FileEntry>> groups = new LinkedHashMap<>();
        for (FileEntry entry : entries) {
            groups.computeIfAbsent(labelOf.apply(entry), k -> new ArrayList<>()).add(entry);
        }
        List<String> labels = new ArrayList<>(groups.keySet());
        List<String> fixed = switch (key) {
            case NAME -> NAMES;
            case MODIFIED -> DATES;
            case SIZE -> SIZES;
            default -> null;
        };
        labels.sort((a, b) -> {
            boolean aLast = a.equals(UNKNOWN) || a.equals(UNSPECIFIED);
            boolean bLast = b.equals(UNKNOWN) || b.equals(UNSPECIFIED);
            if (aLast || bLast) {
                return Boolean.compare(aLast, bLast);
            }
            int order = fixed != null ? Integer.compare(fixed.indexOf(a), fixed.indexOf(b)) : FileOrder.compareNatural(a, b);
            return ascending ? order : -order;
        });
        List<Group> result = new ArrayList<>(labels.size());
        for (String label : labels) {
            result.add(new Group(label, groups.get(label)));
        }
        return List.copyOf(result);
    }

    /** The name group of {@code entry}: "0 - 9", "A - H", "I - P", "Q - Z" or "Other". */
    public static String nameGroup(FileEntry entry) {
        String name = entry.displayName();
        int first = name.isEmpty() ? 0 : name.codePointAt(0);
        if (first >= '0' && first <= '9') {
            return NAMES.get(0);
        }
        // Accents count as their letter ("É" with the E's).
        String base = java.text.Normalizer.normalize(new String(Character.toChars(Math.max(first, 1))), java.text.Normalizer.Form.NFD);
        char letter = Character.toUpperCase(base.charAt(0));
        if (letter >= 'A' && letter <= 'H') {
            return NAMES.get(1);
        }
        if (letter >= 'I' && letter <= 'P') {
            return NAMES.get(2);
        }
        if (letter >= 'Q' && letter <= 'Z') {
            return NAMES.get(3);
        }
        return NAMES.get(4);
    }

    /** The date group of {@code modified} as at {@code now}, weeks starting on Monday. */
    public static String dateGroup(Instant modified, Instant now, ZoneId zone) {
        if (modified == null) {
            return UNKNOWN;
        }
        LocalDate day = modified.atZone(zone).toLocalDate();
        ZonedDateTime today = now.atZone(zone);
        LocalDate date = today.toLocalDate();
        if (day.isAfter(date)) {
            return "Later";
        }
        if (day.equals(date)) {
            return "Today";
        }
        if (day.equals(date.minusDays(1))) {
            return "Yesterday";
        }
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!day.isBefore(weekStart)) {
            return "Earlier this week";
        }
        if (!day.isBefore(weekStart.minusWeeks(1))) {
            return "Last week";
        }
        LocalDate monthStart = date.withDayOfMonth(1);
        if (!day.isBefore(monthStart)) {
            return "Earlier this month";
        }
        if (!day.isBefore(monthStart.minusMonths(1))) {
            return "Last month";
        }
        if (day.getYear() == date.getYear()) {
            return "Earlier this year";
        }
        return "A long time ago";
    }

    /** The size group of a file of {@code bytes}, as Explorer names them. */
    public static String sizeGroup(long bytes) {
        long kb = 1024;
        long mb = kb * kb;
        if (bytes <= 0) {
            return "Empty";
        }
        if (bytes < 16 * kb) {
            return "Tiny";
        }
        if (bytes < mb) {
            return "Small";
        }
        if (bytes < 128 * mb) {
            return "Medium";
        }
        if (bytes < 1024 * mb) {
            return "Large";
        }
        if (bytes < 4096 * mb) {
            return "Huge";
        }
        return "Gigantic";
    }
}
