package com.zapthemes.files.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.eclipse.swt.SWT;

import com.zapthemes.files.Capability;
import com.zapthemes.files.FileEntry;
import com.zapthemes.files.FileOrder;
import com.zapthemes.files.FileTypes;

/**
 * The Details layout's columns: Name, Date modified, Type and Size, plus Permissions, Owner and
 * Group when the filesystem has them, and the text each shows for an entry.
 */
final class DetailsColumns {

    private static final DateTimeFormatter DATES = dateFormat(Locale.getDefault());

    /** A column: what it shows and sorts by, its title, its width at 100 % and its alignment ({@code SWT.LEFT} or {@code SWT.RIGHT}). */
    record Column(FileOrder.Key key, String title, int width, int align) {
    }

    private DetailsColumns() {
    }

    /** The columns for a filesystem with {@code capabilities}, without those of {@code hidden} (Name always). */
    static List<Column> columnsFor(Set<Capability> capabilities, Set<FileOrder.Key> hidden) {
        List<Column> list = new ArrayList<>();
        list.add(new Column(FileOrder.Key.NAME, "Name", 260, SWT.LEFT));
        list.add(new Column(FileOrder.Key.MODIFIED, "Date modified", 150, SWT.LEFT));
        list.add(new Column(FileOrder.Key.TYPE, "Type", 150, SWT.LEFT));
        list.add(new Column(FileOrder.Key.SIZE, "Size", 90, SWT.RIGHT));
        if (capabilities.contains(Capability.PERMISSIONS)) {
            list.add(new Column(FileOrder.Key.PERMISSIONS, "Permissions", 95, SWT.LEFT));
        }
        if (capabilities.contains(Capability.OWNERS)) {
            list.add(new Column(FileOrder.Key.OWNER, "Owner", 90, SWT.LEFT));
            list.add(new Column(FileOrder.Key.GROUP, "Group", 90, SWT.LEFT));
        }
        list.removeIf(column -> column.key() != FileOrder.Key.NAME && hidden.contains(column.key()));
        return list;
    }

    /** The text {@code entry} shows in the column of {@code key}. */
    static String text(FileBrowser browser, FileEntry entry, FileOrder.Key key) {
        return switch (key) {
            case NAME -> browser.displayName(entry);
            case MODIFIED -> formatDate(entry.modified());
            case TYPE -> browser.typeName(entry);
            case SIZE -> entry.isContainer() ? "" : FileTypes.detailsSize(entry.size());
            case PERMISSIONS -> nonNull(entry.permissions());
            case OWNER -> nonNull(entry.owner());
            case GROUP -> nonNull(entry.group());
        };
    }

    private static String nonNull(String text) {
        return text == null ? "" : text;
    }

    /** Explorer's short date and time, with a four-digit year: {@code 4/8/2024 1:45 PM}. */
    static DateTimeFormatter dateFormat(Locale locale) {
        String date = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale);
        if (!date.contains("yyyy") && !date.contains("y".repeat(3))) {
            date = date.replace("yy", "yyyy");
            if (!date.contains("yyyy")) {
                date = date.replace("y", "yyyy");
            }
        }
        String time = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale);
        // Some JDK patterns use a narrow no-break space before AM/PM; a plain one reads the same everywhere.
        return DateTimeFormatter.ofPattern(date + " " + time.replace(' ', ' '), locale).withZone(ZoneId.systemDefault());
    }

    /** A date as the Details layout shows it. */
    static String formatDate(Instant instant) {
        return instant == null ? "" : DATES.format(instant);
    }
}
