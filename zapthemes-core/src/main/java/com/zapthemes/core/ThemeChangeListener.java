package com.zapthemes.core;

import com.zapthemes.core.model.Theme;

/** Notified on the UI thread after every widget has been re-themed. */
@FunctionalInterface
public interface ThemeChangeListener {

    void themeChanged(Theme previous, Theme current);
}
