package com.zapthemes.widgets.ribbon;

/**
 * How large a ribbon item or group is drawn, largest first. Items use {@link #LARGE} (a 32 px icon
 * over its label, a column tall), {@link #MEDIUM} (a 16 px icon and its label, three to a column)
 * and {@link #SMALL} (the icon only); a group may also be {@link #COLLAPSED} into one drop-down
 * button that opens it as a popup.
 */
public enum Size {
    LARGE, MEDIUM, SMALL, COLLAPSED;

    /** This size clamped between {@code max} (the largest allowed) and {@code min} (the smallest). */
    Size clamp(Size max, Size min) {
        int ordinal = Math.max(max.ordinal(), Math.min(ordinal(), min.ordinal()));
        return values()[ordinal];
    }

    boolean smallerThan(Size other) {
        return ordinal() > other.ordinal();
    }
}
