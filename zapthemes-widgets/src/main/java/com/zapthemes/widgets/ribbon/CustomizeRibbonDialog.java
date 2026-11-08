package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;

import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeItem;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.ribbon.RibbonCustomization.GroupNode;
import com.zapthemes.widgets.ribbon.RibbonCustomization.TabNode;

/**
 * Office's <em>Customize the Ribbon</em> dialog: choose commands on the left (all, those not in
 * the ribbon, or a category, with a filter) and add them to groups of the tree on the right;
 * remove, reorder, rename and hide (uncheck) tabs and groups; rename commands in a group; add
 * tabs and groups of your own;
 * reset. OK applies and saves.
 *
 * <p>Users add commands only to their own groups and to app groups; combos and galleries keep
 * their places and come back through Reset.
 */
public final class CustomizeRibbonDialog {

    private static final String ALL = "All Commands";
    private static final String NOT_IN_RIBBON = "Commands Not in the Ribbon";

    private final Ribbon ribbon;
    private final RibbonCustomization customization;
    private java.util.List<TabNode> state;
    private Shell shell;
    private Combo source;
    private Text filter;
    private com.zapthemes.swt.widgets.List commandList;
    private Tree tree;
    private final java.util.List<Command> listed = new ArrayList<>();
    private boolean applied;

    public CustomizeRibbonDialog(Ribbon ribbon) {
        this.ribbon = ribbon;
        this.customization = ribbon.customization();
        this.state = RibbonCustomization.copy(customization.snapshot());
    }

    /** Opens the dialog and waits until it closes; returns whether OK was pressed. */
    public boolean open() {
        create();
        shell.open();
        while (!shell.isDisposed()) {
            if (!shell.getDisplay().readAndDispatch()) {
                shell.getDisplay().sleep();
            }
        }
        return applied;
    }

    /** Creates the dialog's shell without opening it (tests drive it, then call {@link #ok()}). */
    Shell create() {
        shell = new Shell(ribbon.getShell(), SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RESIZE);
        shell.setText("Customize the Ribbon");
        shell.setLayout(new GridLayout(4, false));

        Label chooseLabel = new Label(shell, SWT.NONE);
        chooseLabel.setText("&Choose commands from:");
        new Label(shell, SWT.NONE);
        Label treeLabel = new Label(shell, SWT.NONE);
        treeLabel.setText("Customize the Ri&bbon:");
        new Label(shell, SWT.NONE);

        Composite left = new Composite(shell, SWT.NONE);
        left.setLayout(new GridLayout(1, false));
        left.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        source = new Combo(left, SWT.READ_ONLY);
        source.add(ALL);
        source.add(NOT_IN_RIBBON);
        ribbon.commands().categories().forEach(source::add);
        source.select(0);
        source.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        source.addListener(SWT.Selection, e -> fillCommands());
        filter = new Text(left, SWT.SEARCH | SWT.ICON_CANCEL);
        filter.setMessage("Filter");
        filter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        filter.addListener(SWT.Modify, e -> fillCommands());
        commandList = new com.zapthemes.swt.widgets.List(left, SWT.SINGLE | SWT.V_SCROLL | SWT.BORDER);
        GridData listData = new GridData(SWT.FILL, SWT.FILL, true, true);
        listData.widthHint = 240;
        listData.heightHint = 360;
        commandList.setLayoutData(listData);
        commandList.addListener(SWT.DefaultSelection, e -> add());

        Composite middle = new Composite(shell, SWT.NONE);
        middle.setLayout(new GridLayout(1, false));
        middle.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
        button(middle, "&Add >>", this::add);
        button(middle, "<< &Remove", this::remove);

        tree = new Tree(shell, SWT.SINGLE | SWT.CHECK | SWT.BORDER | SWT.V_SCROLL);
        GridData treeData = new GridData(SWT.FILL, SWT.FILL, true, true);
        treeData.widthHint = 260;
        treeData.heightHint = 360;
        tree.setLayoutData(treeData);
        tree.addListener(SWT.Selection, this::treeSelected);

        Composite side = new Composite(shell, SWT.NONE);
        side.setLayout(new GridLayout(1, false));
        side.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
        button(side, "&Up", () -> move(-1));
        button(side, "&Down", () -> move(1));
        button(side, "New &Tab", this::newTab);
        button(side, "New &Group", this::newGroup);
        button(side, "Rena&me...", this::rename);
        button(side, "Re&set Tab", this::resetTab);
        button(side, "Reset &All", this::resetAll);

        Composite buttons = new Composite(shell, SWT.NONE);
        buttons.setLayout(new GridLayout(2, true));
        buttons.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false, 4, 1));
        Button ok = button(buttons, "OK", this::ok);
        button(buttons, "Cancel", shell::close);
        shell.setDefaultButton(ok);

        fillCommands();
        fillTree(null);
        shell.pack();
        return shell;
    }

    private Button button(Composite parent, String text, Runnable action) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText(text);
        button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        button.addListener(SWT.Selection, e -> action.run());
        return button;
    }

    // ------------------------------------------------------------------ the command list

    private void fillCommands() {
        listed.clear();
        commandList.removeAll();
        String choice = source.getText();
        String text = filter.getText().trim().toLowerCase(Locale.ROOT);
        java.util.Set<String> inRibbon = new java.util.HashSet<>();
        for (TabNode tab : state) {
            for (GroupNode group : tab.groups) {
                inRibbon.addAll(items(group));
            }
        }
        for (Command command : ribbon.commands().all()) {
            boolean matches = switch (choice) {
                case ALL -> true;
                case NOT_IN_RIBBON -> !inRibbon.contains(command.id());
                default -> command.category().equals(choice);
            };
            if (matches && (text.isEmpty() || command.plainLabel().toLowerCase(Locale.ROOT).contains(text))) {
                listed.add(command);
            }
        }
        listed.sort((a, b) -> a.plainLabel().compareToIgnoreCase(b.plainLabel()));
        listed.forEach(c -> commandList.add(c.plainLabel()));
    }

    // ------------------------------------------------------------------ the tree

    private java.util.List<String> items(GroupNode group) {
        return group.items != null ? group.items : group.custom ? java.util.List.of() : customization.defaultItemIds(group.id);
    }

    private String label(TabNode tab) {
        String label = tab.label != null ? tab.label : customization.defaultLabel(tab.id);
        return Command.stripMnemonic(label == null ? tab.id : label) + (tab.custom ? " (Custom)" : "");
    }

    private String label(GroupNode group) {
        String label = group.label != null ? group.label : customization.defaultLabel(group.id);
        return Command.stripMnemonic(label == null ? group.id : label) + (group.custom ? " (Custom)" : "");
    }

    /** Rebuilds the tree and selects the node whose data is {@code select}. */
    private void fillTree(Object select) {
        tree.removeAll();
        TreeItem selected = null;
        for (TabNode tab : state) {
            TreeItem tabItem = new TreeItem(tree, SWT.NONE);
            tabItem.setText(label(tab));
            tabItem.setData(tab);
            tabItem.setChecked(!tab.hidden);
            if (tab == select) {
                selected = tabItem;
            }
            for (GroupNode group : tab.groups) {
                TreeItem groupItem = new TreeItem(tabItem, SWT.NONE);
                groupItem.setText(label(group));
                groupItem.setData(group);
                groupItem.setChecked(true);
                if (group == select) {
                    selected = groupItem;
                }
                java.util.List<String> ids = items(group);
                for (int i = 0; i < ids.size(); i++) {
                    TreeItem commandItem = new TreeItem(groupItem, SWT.NONE);
                    String custom = group.labels.get(ids.get(i));
                    commandItem.setText(custom != null ? Command.stripMnemonic(custom) : customization.itemLabel(ids.get(i)));
                    ItemRef ref = new ItemRef(group, i);
                    commandItem.setData(ref);
                    commandItem.setChecked(true);
                    if (select instanceof ItemRef r && r.group == group && r.index == i) {
                        selected = commandItem;
                    }
                }
                groupItem.setExpanded(select instanceof ItemRef r && r.group == group);
            }
            tabItem.setExpanded(tab == select || tab.groups.contains(select)
                    || (select instanceof ItemRef r && tab.groups.contains(r.group)));
        }
        if (selected != null) {
            tree.setSelection(selected);
            tree.showItem(selected);
        }
    }

    private record ItemRef(GroupNode group, int index) {
    }

    private void treeSelected(Event event) {
        if (event.detail == SWT.CHECK && event.item instanceof org.eclipse.swt.widgets.TreeItem item) {
            if (item.getData() instanceof TabNode tab) {
                tab.hidden = !item.getChecked();
            } else {
                item.setChecked(true);
            }
        }
    }

    private Object selection() {
        org.eclipse.swt.widgets.TreeItem[] selection = tree.getSelection();
        return selection.length == 0 ? null : selection[0].getData();
    }

    private TabNode tabOf(Object node) {
        for (TabNode tab : state) {
            if (tab == node || tab.groups.contains(node) || (node instanceof ItemRef r && tab.groups.contains(r.group))) {
                return tab;
            }
        }
        return null;
    }

    private static java.util.List<String> materialize(GroupNode group, java.util.List<String> current) {
        if (group.items == null) {
            group.items = new ArrayList<>(current);
        }
        return group.items;
    }

    // ------------------------------------------------------------------ operations

    void add() {
        int index = commandList.getSelectionIndex();
        Object node = selection();
        if (index < 0 || node == null || node instanceof TabNode) {
            return;
        }
        String id = listed.get(index).id();
        if (node instanceof GroupNode group) {
            materialize(group, items(group)).add(id);
            fillTree(new ItemRef(group, group.items.size() - 1));
        } else if (node instanceof ItemRef ref) {
            materialize(ref.group, items(ref.group)).add(ref.index + 1, id);
            fillTree(new ItemRef(ref.group, ref.index + 1));
        }
        fillCommands();
    }

    void remove() {
        Object node = selection();
        if (node instanceof ItemRef ref) {
            java.util.List<String> items = materialize(ref.group, items(ref.group));
            items.remove(ref.index);
            fillTree(items.isEmpty() ? ref.group : new ItemRef(ref.group, Math.min(ref.index, items.size() - 1)));
        } else if (node instanceof GroupNode group) {
            TabNode tab = tabOf(group);
            tab.groups.remove(group);
            fillTree(tab);
        } else if (node instanceof TabNode tab && tab.custom) {
            state.remove(tab);
            fillTree(null);
        }
        fillCommands();
    }

    void move(int delta) {
        Object node = selection();
        if (node instanceof TabNode tab) {
            int index = state.indexOf(tab);
            int next = index + delta;
            if (next >= 0 && next < state.size()) {
                state.remove(index);
                state.add(next, tab);
            }
            fillTree(tab);
        } else if (node instanceof GroupNode group) {
            TabNode tab = tabOf(group);
            int index = tab.groups.indexOf(group);
            int next = index + delta;
            if (next >= 0 && next < tab.groups.size()) {
                tab.groups.remove(index);
                tab.groups.add(next, group);
            }
            fillTree(group);
        } else if (node instanceof ItemRef ref) {
            java.util.List<String> items = materialize(ref.group, items(ref.group));
            int next = ref.index + delta;
            if (next >= 0 && next < items.size()) {
                items.add(next, items.remove(ref.index));
                fillTree(new ItemRef(ref.group, next));
            }
        }
    }

    void newTab() {
        TabNode after = tabOf(selection());
        TabNode tab = new TabNode(customization.newId(RibbonCustomization.CUSTOM_TAB, state), "New Tab", false, true);
        GroupNode group = new GroupNode(customization.newId(RibbonCustomization.CUSTOM_GROUP, state), "New Group", true, java.util.List.of());
        tab.groups.add(group);
        state.add(after == null ? state.size() : state.indexOf(after) + 1, tab);
        fillTree(group);
    }

    void newGroup() {
        Object node = selection();
        TabNode tab = tabOf(node);
        if (tab == null) {
            return;
        }
        GroupNode group = new GroupNode(customization.newId(RibbonCustomization.CUSTOM_GROUP, state), "New Group", true, java.util.List.of());
        int index = node instanceof GroupNode g ? tab.groups.indexOf(g) + 1
                : node instanceof ItemRef r ? tab.groups.indexOf(r.group) + 1 : tab.groups.size();
        tab.groups.add(index, group);
        fillTree(group);
    }

    void rename() {
        Object node = selection();
        String current = switch (node) {
            case TabNode tab -> tab.label != null ? tab.label : customization.defaultLabel(tab.id);
            case GroupNode group -> group.label != null ? group.label : customization.defaultLabel(group.id);
            case ItemRef ref when renamable(ref) -> ref.group.labels.getOrDefault(itemId(ref), customization.itemLabel(itemId(ref)));
            case null, default -> null;
        };
        if (current == null) {
            return;
        }
        String name = prompt("Rename", Command.stripMnemonic(current));
        if (name != null && !name.isBlank()) {
            rename(name);
        }
    }

    /** Renames the selected tab, group or command without prompting (tests). */
    void rename(String name) {
        Object node = selection();
        if (node instanceof TabNode tab) {
            tab.label = name;
            fillTree(tab);
        } else if (node instanceof GroupNode group) {
            group.label = name;
            fillTree(group);
        } else if (node instanceof ItemRef ref && renamable(ref)) {
            String id = itemId(ref);
            if (name == null || name.isBlank() || name.equals(customization.itemLabel(id))) {
                ref.group.labels.remove(id);
            } else {
                ref.group.labels.put(id, name);
            }
            fillTree(ref);
        }
    }

    private String itemId(ItemRef ref) {
        return items(ref.group).get(ref.index);
    }

    private boolean renamable(ItemRef ref) {
        return customization.isRenamable(itemId(ref));
    }

    void resetTab() {
        TabNode tab = tabOf(selection());
        if (tab == null || tab.custom) {
            return;
        }
        for (TabNode defaults : customization.defaults()) {
            if (defaults.id.equals(tab.id)) {
                int index = state.indexOf(tab);
                // Groups of the default tab that the user moved elsewhere come back here.
                for (TabNode other : state) {
                    other.groups.removeIf(g -> defaults.groups.stream().anyMatch(d -> d.id.equals(g.id)));
                }
                state.set(index, defaults);
                fillTree(defaults);
                return;
            }
        }
    }

    void resetAll() {
        state = RibbonCustomization.copy(customization.defaults());
        fillTree(null);
        fillCommands();
    }

    void ok() {
        customization.apply(state);
        applied = true;
        shell.close();
    }

    private String prompt(String title, String initial) {
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText(title);
        dialog.setLayout(new GridLayout(2, true));
        Text text = new Text(dialog, SWT.BORDER | SWT.SINGLE);
        text.setText(initial == null ? "" : initial);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        data.widthHint = 260;
        text.setLayoutData(data);
        text.selectAll();
        String[] result = { null };
        Button ok = new Button(dialog, SWT.PUSH);
        ok.setText("OK");
        ok.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        ok.addListener(SWT.Selection, e -> {
            result[0] = text.getText();
            dialog.close();
        });
        Button cancel = new Button(dialog, SWT.PUSH);
        cancel.setText("Cancel");
        cancel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        cancel.addListener(SWT.Selection, e -> dialog.close());
        dialog.setDefaultButton(ok);
        dialog.pack();
        dialog.open();
        while (!dialog.isDisposed()) {
            if (!dialog.getDisplay().readAndDispatch()) {
                dialog.getDisplay().sleep();
            }
        }
        return result[0];
    }

    // ------------------------------------------------------------------ for tests

    Shell shell() {
        return shell;
    }

    Tree tree() {
        return tree;
    }

    com.zapthemes.swt.widgets.List commandList() {
        return commandList;
    }

    java.util.List<TabNode> state() {
        return state;
    }

    /** Selects the tree node whose data is a tab or group with {@code id}. */
    void selectNode(String id) {
        for (TabNode tab : state) {
            if (tab.id.equals(id)) {
                fillTree(tab);
                return;
            }
            for (GroupNode group : tab.groups) {
                if (group.id.equals(id)) {
                    fillTree(group);
                    return;
                }
            }
        }
    }

    /** Selects item {@code itemId} of group {@code groupId} in the tree (tests). */
    void selectItem(String groupId, String itemId) {
        for (TabNode tab : state) {
            for (GroupNode group : tab.groups) {
                if (group.id.equals(groupId)) {
                    int index = items(group).indexOf(itemId);
                    if (index >= 0) {
                        fillTree(new ItemRef(group, index));
                    }
                    return;
                }
            }
        }
    }

    /** Selects the command with {@code id} in the left list. */
    void selectCommand(String id) {
        source.select(0);
        filter.setText("");
        fillCommands();
        for (int i = 0; i < listed.size(); i++) {
            if (listed.get(i).id().equals(id)) {
                commandList.select(i);
            }
        }
    }
}
