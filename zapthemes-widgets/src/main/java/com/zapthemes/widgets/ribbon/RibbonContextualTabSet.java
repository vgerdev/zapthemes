package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tabs that show only while something is selected, like Office's <em>Table Design</em> and
 * <em>Layout</em> for a table. Drawn in one of the theme's contextual accents
 * ({@code contextualAccent1} … {@code contextualAccent4}). The app shows and hides the set as
 * its selection changes.
 */
public final class RibbonContextualTabSet {

    private final Ribbon ribbon;
    private final String id;
    private final String label;
    private final int accent;
    private final List<RibbonTab> tabs = new ArrayList<>();
    private boolean visible;

    RibbonContextualTabSet(Ribbon ribbon, String id, String label, int accent) {
        if (accent < 1 || accent > 4) {
            throw new IllegalArgumentException("accent must be 1 to 4: " + accent);
        }
        this.ribbon = ribbon;
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.accent = accent;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    /** The theme accent, 1 to 4. */
    public int accent() {
        return accent;
    }

    public RibbonTab addTab(String tabId, String tabLabel) {
        RibbonTab tab = new RibbonTab(ribbon, tabId, tabLabel, this);
        tabs.add(tab);
        ribbon.tabsChanged();
        return tab;
    }

    public List<RibbonTab> tabs() {
        return List.copyOf(tabs);
    }

    public boolean isVisible() {
        return visible;
    }

    /** Shows or hides the set without changing the selected tab (unless one of its tabs is selected). */
    public void setVisible(boolean show) {
        setVisible(show, false);
    }

    /**
     * Shows or hides the set; {@code select} also selects its first tab when showing (as Office
     * does when the object was just inserted). Hiding a set whose tab is selected selects the tab
     * that was selected before it showed.
     */
    public void setVisible(boolean show, boolean select) {
        if (show == visible) {
            if (show && select) {
                ribbon.contextualShown(this, true);
            }
            return;
        }
        visible = show;
        if (show) {
            ribbon.contextualShown(this, select);
        } else {
            ribbon.contextualHidden(this);
        }
    }
}
