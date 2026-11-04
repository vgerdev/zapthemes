package com.zapthemes.core.style;

/**
 * Implemented by the Table and Tree proxies, so a theme with {@code gridLineColor} can replace the
 * native grid lines (whose color cannot be changed on Windows) with painted ones, while
 * {@code setLinesVisible}/{@code getLinesVisible} keep meaning what the application asked for.
 */
public interface ThemedLines {

    /** What the application last passed to {@code setLinesVisible}. */
    boolean requestedLinesVisible();

    /** Shows or hides the platform's own grid lines. */
    void setNativeLinesVisible(boolean visible);
}
