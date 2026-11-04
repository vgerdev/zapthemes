package com.zapthemes.swt.widgets;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ThemedLines;

/**
 * Theme-aware {@link org.eclipse.swt.widgets.Table}: tags itself with
 * {@code THEME_CONTROL_TYPE = "Table"} and applies the current theme on creation.
 *
 * <p>Written by hand instead of generated: {@link #setLinesVisible(boolean)} records what the
 * application asked for, so a theme with {@code gridLineColor} can paint the grid lines in its own
 * color instead of showing the native ones (see {@link ThemedLines}).
 */
public class Table extends org.eclipse.swt.widgets.Table implements ThemedLines {

    private boolean linesVisible;

    /** @see org.eclipse.swt.widgets.Table#Table(org.eclipse.swt.widgets.Composite, int) */
    @SuppressWarnings("this-escape")
    public Table(org.eclipse.swt.widgets.Composite parent, int style) {
        super(parent, style);
        ThemeManager.applyOnCreate(this, "Table");
    }

    @Override
    public void setLinesVisible(boolean show) {
        checkWidget();
        linesVisible = show;
        // The styler decides between native and painted lines.
        ThemeManager.get(getDisplay()).reapply(this);
    }

    @Override
    public boolean getLinesVisible() {
        checkWidget();
        return linesVisible;
    }

    @Override
    public boolean requestedLinesVisible() {
        return linesVisible;
    }

    @Override
    public void setNativeLinesVisible(boolean visible) {
        super.setLinesVisible(visible);
    }

    @Override
    protected void checkSubclass() {
        // SWT rejects subclasses by default; a proxy only adds theming.
    }
}
