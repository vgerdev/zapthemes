package com.zapthemes.core;

/**
 * Notified on the UI thread after a {@link ThemeManager}'s zoom changed and every widget has been
 * re-themed at the new zoom.
 */
@FunctionalInterface
public interface ZoomListener {

    /** {@code previous} and {@code current} are factors, {@code 1.0} for 100 %. */
    void zoomChanged(double previous, double current);
}
