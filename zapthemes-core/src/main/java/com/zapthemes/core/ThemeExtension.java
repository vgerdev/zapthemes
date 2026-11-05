package com.zapthemes.core;

import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.model.Theme;

/**
 * Service-provider interface ({@link java.util.ServiceLoader}) for modules that extend theming
 * beyond widget styling, e.g. syncing a toolkit's own resource registries. Installed once per
 * {@link ThemeManager}, on the UI thread, when the manager is created; typically adds a
 * {@link ThemeChangeListener}.
 */
public interface ThemeExtension {

    void install(ThemeManager manager);

    /**
     * A theme for a new manager, so code that never calls {@link ThemeManager#setTheme} is themed
     * too (e.g. from a system property). Asked after every extension is installed, so their
     * listeners see it; the first non-null answer wins. The default has none.
     */
    default Theme initialTheme(Display display) {
        return null;
    }
}
