package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A tab of the ribbon: a label and groups of commands. {@link #setReductionOrder} says in which
 * order groups shrink when the window is too narrow for all of them.
 */
public final class RibbonTab {

    /** One reduction: {@code group} shrinks to {@code size}. */
    public record Step(RibbonGroup group, Size size) {
    }

    private final Ribbon ribbon;
    private final String id;
    private final RibbonContextualTabSet contextualSet;
    private String label;
    private String keyTip;
    private boolean hidden;
    private final List<RibbonGroup> groups = new ArrayList<>();
    private List<Step> reductionOrder = List.of();

    RibbonTab(Ribbon ribbon, String id, String label, RibbonContextualTabSet contextualSet) {
        this.ribbon = ribbon;
        this.id = Objects.requireNonNull(id, "id");
        this.label = Objects.requireNonNull(label, "label");
        this.contextualSet = contextualSet;
    }

    public static Step step(RibbonGroup group, Size size) {
        return new Step(group, size);
    }

    public String id() {
        return id;
    }

    public Ribbon ribbon() {
        return ribbon;
    }

    /** The contextual tab set this tab belongs to, or {@code null} for a regular tab. */
    public RibbonContextualTabSet contextualSet() {
        return contextualSet;
    }

    public String label() {
        return label;
    }

    public void setLabel(String text) {
        label = Objects.requireNonNull(text);
        changed();
    }

    public RibbonTab setKeyTip(String letters) {
        keyTip = letters == null ? null : letters.toUpperCase();
        return this;
    }

    public String keyTip() {
        return keyTip;
    }

    /** Hidden tabs are not shown (users hide tabs in the customization dialog). */
    public void setHidden(boolean value) {
        hidden = value;
        ribbon.tabsChanged();
    }

    public boolean isHidden() {
        return hidden;
    }

    /** Whether the tab is shown now: not hidden, and its contextual set (if any) is visible. */
    public boolean isShowing() {
        return !hidden && (contextualSet == null || contextualSet.isVisible());
    }

    public RibbonGroup addGroup(String groupId, String groupLabel) {
        RibbonGroup group = new RibbonGroup(this, groupId, groupLabel);
        groups.add(group);
        changed();
        return group;
    }

    public List<RibbonGroup> groups() {
        return List.copyOf(groups);
    }

    public RibbonGroup group(String groupId) {
        return groups.stream().filter(g -> g.id().equals(groupId)).findFirst().orElse(null);
    }

    void insertGroup(int index, RibbonGroup group) {
        groups.add(Math.min(index, groups.size()), group);
        changed();
    }

    void removeGroup(RibbonGroup group) {
        groups.remove(group);
        changed();
    }

    void reorderGroups(List<RibbonGroup> order) {
        groups.clear();
        groups.addAll(order);
        changed();
    }

    /**
     * The order in which groups shrink. Steps that would grow a group, or leave it as it is, are
     * skipped; groups the order leaves out shrink by the default order afterwards: every group to
     * medium, then to small, then collapsed, by priority (lower first), then right to left.
     */
    public void setReductionOrder(Step... steps) {
        reductionOrder = List.of(steps);
        changed();
    }

    public List<Step> reductionOrder() {
        return reductionOrder;
    }

    /** The order actually used: the app's steps, then the default ones. */
    List<Step> effectiveReductionOrder() {
        List<Step> steps = new ArrayList<>(reductionOrder);
        List<RibbonGroup> byPriority = new ArrayList<>(groups);
        java.util.Collections.reverse(byPriority);
        byPriority.sort(java.util.Comparator.comparingInt(RibbonGroup::priority));
        for (Size size : List.of(Size.MEDIUM, Size.SMALL, Size.COLLAPSED)) {
            for (RibbonGroup group : byPriority) {
                steps.add(new Step(group, size));
            }
        }
        return steps;
    }

    void changed() {
        ribbon.modelChanged();
    }

    @Override
    public String toString() {
        return "RibbonTab[" + id + "]";
    }
}
