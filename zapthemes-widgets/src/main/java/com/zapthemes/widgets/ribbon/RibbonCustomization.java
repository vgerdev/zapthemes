package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zapthemes.widgets.command.CustomizationStore;

/**
 * Users' changes to the ribbon: the app's structure is the default, and a user may reorder,
 * rename and hide tabs, add tabs and groups of their own, and add, remove and reorder commands.
 * The changes are kept in a {@link CustomizationStore}'s {@code "ribbon"} section.
 *
 * <p>The structure is edited as a list of {@link TabNode}s ({@link #snapshot()}, then
 * {@link #apply}); {@link #defaults()} is the app's. Contextual tabs are not customizable.
 *
 * <p>Stored ids survive app updates: ids the app no longer defines are dropped, and tabs and
 * groups the app added since are inserted at their default places.
 */
public final class RibbonCustomization {

    public static final String SECTION = "ribbon";
    static final String CUSTOM_TAB = "custom.tab.";
    static final String CUSTOM_GROUP = "custom.group.";

    /** A tab as customization sees it. {@code label} is {@code null} when not renamed. */
    public static final class TabNode {
        public final String id;
        public String label;
        public boolean hidden;
        public final boolean custom;
        public final List<GroupNode> groups = new ArrayList<>();

        public TabNode(String id, String label, boolean hidden, boolean custom) {
            this.id = Objects.requireNonNull(id);
            this.label = label;
            this.hidden = hidden;
            this.custom = custom;
        }

        TabNode copy() {
            TabNode copy = new TabNode(id, label, hidden, custom);
            groups.forEach(g -> copy.groups.add(g.copy()));
            return copy;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof TabNode t && t.id.equals(id) && Objects.equals(t.label, label) && t.hidden == hidden
                    && t.custom == custom && t.groups.equals(groups);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "Tab[" + id + (label != null ? " \"" + label + "\"" : "") + (hidden ? " hidden" : "") + " " + groups + "]";
        }
    }

    /** A group: {@code items} are item ids, or {@code null} for the app's items (custom groups: none). */
    public static final class GroupNode {
        public final String id;
        public String label;
        public final boolean custom;
        public List<String> items;

        public GroupNode(String id, String label, boolean custom, List<String> items) {
            this.id = Objects.requireNonNull(id);
            this.label = label;
            this.custom = custom;
            this.items = items == null ? null : new ArrayList<>(items);
        }

        GroupNode copy() {
            return new GroupNode(id, label, custom, items);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GroupNode g && g.id.equals(id) && Objects.equals(g.label, label) && g.custom == custom
                    && Objects.equals(g.items, items);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return "Group[" + id + (label != null ? " \"" + label + "\"" : "") + (items != null ? " " + items : "") + "]";
        }
    }

    private record GroupDefault(RibbonTab tab, String label, List<Object> elements, Map<RibbonRow, List<RibbonItem>> rowItems) {
    }

    private final Ribbon ribbon;
    private CustomizationStore store;
    private final List<RibbonTab> defaultTabs = new ArrayList<>();
    private final Map<RibbonTab, String> tabLabels = new LinkedHashMap<>();
    private final Map<RibbonTab, List<RibbonGroup>> tabGroups = new LinkedHashMap<>();
    private final Map<RibbonGroup, GroupDefault> groupDefaults = new LinkedHashMap<>();
    private final Map<String, RibbonItem> appItems = new LinkedHashMap<>();
    private final Map<String, RibbonTab> customTabs = new LinkedHashMap<>();
    private final Map<String, RibbonGroup> customGroups = new LinkedHashMap<>();
    private boolean captured;

    RibbonCustomization(Ribbon ribbon) {
        this.ribbon = ribbon;
    }

    public CustomizationStore store() {
        return store;
    }

    void setStore(CustomizationStore newStore) {
        capture();
        store = newStore;
        if (store == null) {
            return;
        }
        JsonElement section = store.section(SECTION);
        if (section != null && section.isJsonObject()) {
            JsonObject object = section.getAsJsonObject();
            List<TabNode> state = merge(read(object), object);
            apply(state, false);
            if (object.has("collapsed")) {
                ribbon.setCollapsed(object.get("collapsed").getAsBoolean());
            }
        }
    }

    /** Records the app's structure the first time; later calls do nothing. */
    void capture() {
        if (captured) {
            return;
        }
        captured = true;
        for (RibbonTab tab : ribbon.tabs()) {
            defaultTabs.add(tab);
            tabLabels.put(tab, tab.label());
            tabGroups.put(tab, tab.groups());
            for (RibbonGroup group : tab.groups()) {
                Map<RibbonRow, List<RibbonItem>> rows = new LinkedHashMap<>();
                for (Object element : group.elements()) {
                    if (element instanceof RibbonRow row) {
                        rows.put(row, row.items());
                    }
                }
                groupDefaults.put(group, new GroupDefault(tab, group.label(), group.elements(), rows));
                for (RibbonItem item : group.items()) {
                    if (item.isInteractive()) {
                        appItems.putIfAbsent(item.id(), item);
                    }
                }
            }
        }
    }

    /** The app's structure. */
    public List<TabNode> defaults() {
        capture();
        List<TabNode> nodes = new ArrayList<>();
        for (RibbonTab tab : defaultTabs) {
            TabNode node = new TabNode(tab.id(), null, false, false);
            for (RibbonGroup group : tabGroups.get(tab)) {
                node.groups.add(new GroupNode(group.id(), null, false, null));
            }
            nodes.add(node);
        }
        return nodes;
    }

    /** The current structure. */
    public List<TabNode> snapshot() {
        capture();
        List<TabNode> nodes = new ArrayList<>();
        for (RibbonTab tab : ribbon.tabs()) {
            boolean custom = customTabs.containsKey(tab.id());
            String label = custom || !tab.label().equals(tabLabels.get(tab)) ? tab.label() : null;
            TabNode node = new TabNode(tab.id(), label, tab.isHidden(), custom);
            for (RibbonGroup group : tab.groups()) {
                GroupDefault defaults = groupDefaults.get(group);
                boolean customGroup = defaults == null;
                String groupLabel = customGroup || !group.label().equals(defaults.label()) ? group.label() : null;
                List<String> ids = itemIds(group);
                List<String> items = !customGroup && ids.equals(defaultItemIds(group.id())) ? null : ids;
                node.groups.add(new GroupNode(group.id(), groupLabel, customGroup, items));
            }
            nodes.add(node);
        }
        return nodes;
    }

    private static List<String> itemIds(RibbonGroup group) {
        return group.items().stream().filter(RibbonItem::isInteractive).map(RibbonItem::id).toList();
    }

    /** The ids of an app group's items as the app defined them; empty for custom groups. */
    public List<String> defaultItemIds(String groupId) {
        capture();
        for (Map.Entry<RibbonGroup, GroupDefault> entry : groupDefaults.entrySet()) {
            if (entry.getKey().id().equals(groupId)) {
                List<String> ids = new ArrayList<>();
                for (Object element : entry.getValue().elements()) {
                    List<RibbonItem> items = element instanceof RibbonRow row ? entry.getValue().rowItems().get(row) : List.of((RibbonItem) element);
                    items.stream().filter(RibbonItem::isInteractive).forEach(i -> ids.add(i.id()));
                }
                return ids;
            }
        }
        return List.of();
    }

    /** The app's label of a tab or group id, or {@code null}. */
    public String defaultLabel(String id) {
        capture();
        for (Map.Entry<RibbonTab, String> entry : tabLabels.entrySet()) {
            if (entry.getKey().id().equals(id)) {
                return entry.getValue();
            }
        }
        for (Map.Entry<RibbonGroup, GroupDefault> entry : groupDefaults.entrySet()) {
            if (entry.getKey().id().equals(id)) {
                return entry.getValue().label();
            }
        }
        return null;
    }

    /** A label for an item id: its item's, else its command's, else the id. */
    public String itemLabel(String id) {
        RibbonItem item = appItems.get(id);
        if (item != null) {
            return item.label();
        }
        return ribbon.commands().find(id).map(c -> c.plainLabel()).orElse(id);
    }

    /** Whether {@code id} names something a group can hold: an app item or a command. */
    boolean resolvable(String id) {
        return appItems.containsKey(id) || ribbon.commands().find(id).isPresent();
    }

    /** A new id for a user tab or group. */
    String newId(String prefix, List<TabNode> state) {
        Set<String> used = new HashSet<>(customTabs.keySet());
        used.addAll(customGroups.keySet());
        for (TabNode tab : state) {
            used.add(tab.id);
            tab.groups.forEach(g -> used.add(g.id));
        }
        for (int i = 1;; i++) {
            if (!used.contains(prefix + i)) {
                return prefix + i;
            }
        }
    }

    /** Makes the ribbon match {@code state}, and saves it when there is a store. */
    public void apply(List<TabNode> state) {
        apply(state, true);
    }

    /** Puts the app's structure back, and saves. */
    public void reset() {
        apply(defaults(), true);
    }

    private void apply(List<TabNode> state, boolean save) {
        capture();
        Map<String, RibbonTab> tabsById = new LinkedHashMap<>();
        defaultTabs.forEach(t -> tabsById.put(t.id(), t));
        tabsById.putAll(customTabs);
        Map<String, RibbonGroup> groupsById = new LinkedHashMap<>();
        groupDefaults.keySet().forEach(g -> groupsById.put(g.id(), g));
        groupsById.putAll(customGroups);

        Set<RibbonGroup> placed = new HashSet<>();
        List<RibbonTab> order = new ArrayList<>();
        for (TabNode node : state) {
            RibbonTab tab = tabsById.get(node.id);
            if (tab == null && node.custom) {
                tab = ribbon.newTab(node.id, node.label == null ? "New Tab" : node.label);
                customTabs.put(node.id, tab);
            }
            if (tab == null || order.contains(tab)) {
                continue;
            }
            String label = node.label != null ? node.label : tabLabels.getOrDefault(tab, tab.label());
            if (!label.equals(tab.label())) {
                tab.setLabel(label);
            }
            List<RibbonGroup> groups = new ArrayList<>();
            for (GroupNode groupNode : node.groups) {
                RibbonGroup group = groupsById.get(groupNode.id);
                if (group == null && groupNode.custom) {
                    group = new RibbonGroup(tab, groupNode.id, groupNode.label == null ? "New Group" : groupNode.label);
                    customGroups.put(groupNode.id, group);
                }
                if (group == null || !placed.add(group)) {
                    continue;
                }
                group.moveTo(tab);
                GroupDefault defaults = groupDefaults.get(group);
                String groupLabel = groupNode.label != null ? groupNode.label : defaults != null ? defaults.label() : group.label();
                if (!groupLabel.equals(group.label())) {
                    group.setLabel(groupLabel);
                }
                if (groupNode.items == null) {
                    if (defaults != null) {
                        group.restore(defaults.elements(), defaults.rowItems());
                    } else {
                        group.replaceItems(List.of());
                    }
                } else {
                    group.replaceItems(buildItems(group, groupNode.items));
                }
                groups.add(group);
            }
            tab.reorderGroups(groups);
            order.add(tab);
            if (tab.isHidden() != node.hidden) {
                tab.setHidden(node.hidden);
            }
        }
        // Groups no longer placed lose their items' attachments, so they can be added elsewhere.
        for (RibbonGroup group : groupsById.values()) {
            if (!placed.contains(group)) {
                group.replaceItems(List.of());
            }
        }
        ribbon.reorderTabs(order);
        if (save) {
            save();
        }
    }

    private List<RibbonItem> buildItems(RibbonGroup group, List<String> ids) {
        List<RibbonItem> items = new ArrayList<>();
        Map<String, RibbonItem> current = new LinkedHashMap<>();
        group.items().forEach(i -> current.put(i.id(), i));
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (!seen.add(id)) {
                continue;
            }
            RibbonItem item = current.get(id);
            if (item == null) {
                RibbonItem app = appItems.get(id);
                if (app != null && (app.group == null || app.group == group || !isPlacedElsewhere(app))) {
                    item = app;
                    if (app.group != null && app.group != group) {
                        app.group.remove(app);
                    }
                }
            }
            if (item == null) {
                item = ribbon.commands().find(id).map(RibbonButton::new).orElse(null);
            }
            if (item != null) {
                if (item.group != null && item.group != group) {
                    item = item.command() != null ? new RibbonButton(item.command()) : null;
                }
                if (item != null) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private boolean isPlacedElsewhere(RibbonItem item) {
        return item.group != null;
    }

    void collapsedChanged() {
        if (store != null) {
            save();
        }
    }

    /** Writes the current state to the store, if there is one. */
    public void save() {
        if (store == null) {
            return;
        }
        store.put(SECTION, write(snapshot()));
        store.save();
    }

    // ------------------------------------------------------------------ JSON

    JsonObject write(List<TabNode> state) {
        JsonObject root = new JsonObject();
        root.addProperty("collapsed", ribbon.isCollapsed());
        JsonArray knownTabs = new JsonArray();
        defaultTabs.forEach(t -> knownTabs.add(t.id()));
        root.add("knownTabs", knownTabs);
        JsonArray knownGroups = new JsonArray();
        groupDefaults.keySet().forEach(g -> knownGroups.add(g.id()));
        root.add("knownGroups", knownGroups);
        JsonArray tabs = new JsonArray();
        for (TabNode tab : state) {
            JsonObject t = new JsonObject();
            t.addProperty("id", tab.id);
            if (tab.label != null) {
                t.addProperty("label", tab.label);
            }
            if (tab.hidden) {
                t.addProperty("hidden", true);
            }
            if (tab.custom) {
                t.addProperty("custom", true);
            }
            JsonArray groups = new JsonArray();
            for (GroupNode group : tab.groups) {
                JsonObject g = new JsonObject();
                g.addProperty("id", group.id);
                if (group.label != null) {
                    g.addProperty("label", group.label);
                }
                if (group.custom) {
                    g.addProperty("custom", true);
                }
                if (group.items != null) {
                    JsonArray items = new JsonArray();
                    group.items.forEach(items::add);
                    g.add("items", items);
                }
                groups.add(g);
            }
            t.add("groups", groups);
            tabs.add(t);
        }
        root.add("tabs", tabs);
        return root;
    }

    static List<TabNode> read(JsonObject root) {
        List<TabNode> state = new ArrayList<>();
        if (!root.has("tabs") || !root.get("tabs").isJsonArray()) {
            return state;
        }
        for (JsonElement element : root.getAsJsonArray("tabs")) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("id")) {
                continue;
            }
            JsonObject t = element.getAsJsonObject();
            TabNode tab = new TabNode(t.get("id").getAsString(), string(t, "label"), bool(t, "hidden"), bool(t, "custom"));
            if (t.has("groups") && t.get("groups").isJsonArray()) {
                for (JsonElement ge : t.getAsJsonArray("groups")) {
                    if (!ge.isJsonObject() || !ge.getAsJsonObject().has("id")) {
                        continue;
                    }
                    JsonObject g = ge.getAsJsonObject();
                    List<String> items = null;
                    if (g.has("items") && g.get("items").isJsonArray()) {
                        items = new ArrayList<>();
                        for (JsonElement ie : g.getAsJsonArray("items")) {
                            items.add(ie.getAsString());
                        }
                    }
                    tab.groups.add(new GroupNode(g.get("id").getAsString(), string(g, "label"), bool(g, "custom"), items));
                }
            }
            state.add(tab);
        }
        return state;
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }

    /**
     * Fits a stored state to the app as it is now: drops what it no longer defines and items that
     * resolve to nothing, and inserts tabs and groups it did not know when the state was saved.
     */
    List<TabNode> merge(List<TabNode> stored, JsonObject root) {
        Set<String> knownTabs = ids(root, "knownTabs");
        Set<String> knownGroups = ids(root, "knownGroups");
        Set<String> appTabs = new HashSet<>();
        defaultTabs.forEach(t -> appTabs.add(t.id()));
        Set<String> appGroups = new HashSet<>();
        groupDefaults.keySet().forEach(g -> appGroups.add(g.id()));

        List<TabNode> state = new ArrayList<>();
        for (TabNode tab : stored) {
            if (!tab.custom && !appTabs.contains(tab.id)) {
                continue;
            }
            TabNode copy = new TabNode(tab.id, tab.label, tab.hidden, tab.custom);
            for (GroupNode group : tab.groups) {
                if (!group.custom && !appGroups.contains(group.id)) {
                    continue;
                }
                List<String> items = group.items == null ? null : group.items.stream().filter(this::resolvable).toList();
                copy.groups.add(new GroupNode(group.id, group.label, group.custom, items));
            }
            state.add(copy);
        }
        // New app tabs: after the default tab before them that is present.
        List<TabNode> defaults = defaults();
        for (int i = 0; i < defaults.size(); i++) {
            TabNode tab = defaults.get(i);
            if (knownTabs.contains(tab.id) || state.stream().anyMatch(t -> t.id.equals(tab.id))) {
                continue;
            }
            int index = 0;
            for (int j = i - 1; j >= 0; j--) {
                String previous = defaults.get(j).id;
                int at = indexOfTab(state, previous);
                if (at >= 0) {
                    index = at + 1;
                    break;
                }
            }
            state.add(index, tab.copy());
        }
        // New app groups: into their default tab, after the default group before them.
        for (TabNode defaultTab : defaults) {
            TabNode target = state.stream().filter(t -> t.id.equals(defaultTab.id)).findFirst().orElse(null);
            if (target == null) {
                continue;
            }
            for (int i = 0; i < defaultTab.groups.size(); i++) {
                GroupNode group = defaultTab.groups.get(i);
                boolean present = state.stream().anyMatch(t -> t.groups.stream().anyMatch(g -> g.id.equals(group.id)));
                if (present || (knownGroups.contains(group.id) && knownTabs.contains(defaultTab.id))) {
                    continue;
                }
                int index = 0;
                for (int j = i - 1; j >= 0; j--) {
                    String previous = defaultTab.groups.get(j).id;
                    int at = -1;
                    for (int k = 0; k < target.groups.size(); k++) {
                        if (target.groups.get(k).id.equals(previous)) {
                            at = k;
                        }
                    }
                    if (at >= 0) {
                        index = at + 1;
                        break;
                    }
                }
                target.groups.add(index, group.copy());
            }
        }
        return state;
    }

    private static int indexOfTab(List<TabNode> state, String id) {
        for (int i = 0; i < state.size(); i++) {
            if (state.get(i).id.equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static Set<String> ids(JsonObject root, String key) {
        Set<String> ids = new HashSet<>();
        if (root.has(key) && root.get(key).isJsonArray()) {
            root.getAsJsonArray(key).forEach(e -> ids.add(e.getAsString()));
        }
        return ids;
    }

    /** Deep copy of a state, for editing. */
    public static List<TabNode> copy(List<TabNode> state) {
        return new ArrayList<>(state.stream().map(TabNode::copy).toList());
    }
}
