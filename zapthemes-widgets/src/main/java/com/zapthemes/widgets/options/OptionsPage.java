package com.zapthemes.widgets.options;

import org.eclipse.swt.widgets.Composite;

/**
 * A page of an {@link OptionsDialog}: it builds its controls when it is first shown, and applies
 * what the user changed when they press OK. Cancel just closes the window, so a page keeps its
 * edits to itself until {@link #apply()}.
 */
public interface OptionsPage {

    /** Builds the page's controls into {@code parent}, which has no layout yet: the page sets one. */
    void create(Composite parent);

    /** OK was pressed: makes the page's changes take effect. Called only on pages that were shown. */
    default void apply() {
    }
}
