package com.zapthemes.core;

/**
 * Service-provider interface ({@link java.util.ServiceLoader}) for modules that extend theming
 * beyond widget styling, e.g. syncing a toolkit's own resource registries. Installed once per
 * {@link ThemeManager}, on the UI thread, when the manager is created; typically adds a
 * {@link ThemeChangeListener}.
 */
public interface ThemeExtension {

    void install(ThemeManager manager);
}
