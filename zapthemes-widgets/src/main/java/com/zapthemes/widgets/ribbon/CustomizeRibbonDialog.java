package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.swt.widgets.Button;
import com.zapthemes.swt.widgets.Combo;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;

import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.swt.widgets.Tree;
import com.zapthemes.swt.widgets.TreeItem;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.ribbon.RibbonCustomization.GroupNode;
import com.zapthemes.widgets.ribbon.RibbonCustomization.TabNode;

/**
 * Office's <em>Customize the Ribbon</em> dialog: choose commands on the left (all, those not in
 * the ribbon, or a category, with a filter) and add them to custom groups of the tree on the
 * right; remove, reorder, rename and hide (uncheck) tabs and groups; add tabs and groups of your
 * own; reset. OK applies and saves. The tree's actions are also in its context menu, and tabs,
 * groups and commands move by drag and drop.
 *
 * <p>The rules are Office's. Built-in groups are locked: their commands show greyed, and can't
 * be added to, removed, reordered or renamed; only custom groups take commands. Built-in groups
 * themselves can be renamed, reordered, removed and moved to other tabs; <em>Main Tabs</em> on
 * the left lists the app's tabs and groups, so a removed group can be added back, or a group put
 * on another tab. A built-in group is in the ribbon once at most. Built-in tabs can be hidden
 * but not deleted. Combos and galleries stay in their groups.
 */
public final class CustomizeRibbonDialog {

    private static final String ALL = "All Commands";
    private static final String NOT_IN_RIBBON = "Commands Not in the Ribbon";
    private static final String MAIN_TABS = "Main Tabs";
    static final String CUSTOM_GROUPS_ONLY = "Commands can be added only to custom groups. To create a custom group, "
            + "pick a tab, and then click New Group.";

    private final Ribbon ribbon;
    private final RibbonCustomization customization;
    private java.util.List<TabNode> state;
    private Shell shell;
    private Combo source;
    private Text filter;
    private com.zapthemes.swt.widgets.List commandList;
    /** The app's tabs, groups and commands, shown in place of the list when Main Tabs is chosen. */
    private Tree mainTabs;
    private StackLayout sourceStack;
    private Tree tree;
    private Color lockedForeground;
    private TreeEditor editor;
    /** The in-place rename field, while one is open. */
    private Text editText;
    /** Counts clicks in the tree, so a pending second-click rename knows when another click came. */
    private int clicks;
    private java.util.function.Consumer<String> messages = this::showMessage;
    private final java.util.List<Command> listed = new ArrayList<>();
    private boolean applied;
    private Button addButton;
    private Button removeButton;
    private final java.util.List<Action> treeActions = new ArrayList<>();
    private final java.util.List<Button> treeButtons = new ArrayList<>();
    /** The tree node being dragged, while a drag from the tree is under way. */
    private Object dragged;

    /** An action on the tree's selection: a button on the right and an item of the tree's context menu. */
    private record Action(String text, Runnable run, BooleanSupplier enabled) {
    }

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
        treeLabel.setText("Ri&bbon:");
        new Label(shell, SWT.NONE);

        Composite left = new Composite(shell, SWT.NONE);
        left.setLayout(new GridLayout(1, false));
        left.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        source = new Combo(left, SWT.READ_ONLY);
        source.add(ALL);
        source.add(NOT_IN_RIBBON);
        source.add(MAIN_TABS);
        ribbon.commands().categories().forEach(source::add);
        source.select(0);
        source.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        source.addListener(SWT.Selection, e -> fillCommands());
        filter = new Text(left, SWT.SEARCH | SWT.ICON_CANCEL);
        filter.setMessage("Filter");
        filter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        filter.addListener(SWT.Modify, e -> fillCommands());
        Composite sources = new Composite(left, SWT.NONE);
        sourceStack = new StackLayout();
        sources.setLayout(sourceStack);
        GridData listData = new GridData(SWT.FILL, SWT.FILL, true, true);
        listData.widthHint = 240;
        listData.heightHint = 360;
        sources.setLayoutData(listData);
        commandList = new com.zapthemes.swt.widgets.List(sources, SWT.SINGLE | SWT.V_SCROLL | SWT.BORDER);
        commandList.addListener(SWT.DefaultSelection, e -> add());
        commandList.addListener(SWT.Selection, e -> updateActions());
        mainTabs = new Tree(sources, SWT.SINGLE | SWT.BORDER | SWT.V_SCROLL);
        mainTabs.addListener(SWT.DefaultSelection, e -> add());
        mainTabs.addListener(SWT.Selection, e -> updateActions());
        sourceStack.topControl = commandList;

        Composite middle = new Composite(shell, SWT.NONE);
        middle.setLayout(new GridLayout(1, false));
        middle.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, true));
        addButton = button(middle, "&Add >>", this::add);
        removeButton = button(middle, "<< &Remove", this::remove);

        tree = new Tree(shell, SWT.SINGLE | SWT.CHECK | SWT.BORDER | SWT.V_SCROLL);
        GridData treeData = new GridData(SWT.FILL, SWT.FILL, true, true);
        treeData.widthHint = 260;
        treeData.heightHint = 360;
        tree.setLayoutData(treeData);
        tree.addListener(SWT.Selection, this::treeSelected);
        editor = new TreeEditor(tree);
        tree.addListener(SWT.MouseDown, this::secondClick);
        tree.addListener(SWT.DefaultSelection, e -> clicks++);
        tree.addListener(SWT.DragDetect, e -> clicks++);
        tree.addListener(SWT.KeyDown, e -> {
            if (e.keyCode == SWT.F2 && canRename()) {
                rename();
            }
        });
        lockedForeground = RibbonColors.mix(tree.getBackground(), tree.getForeground(), 0.5);

        treeActions.add(new Action("&Up", () -> move(-1), () -> canMove(-1)));
        treeActions.add(new Action("&Down", () -> move(1), () -> canMove(1)));
        treeActions.add(new Action("New &Tab", this::newTab, () -> true));
        treeActions.add(new Action("New &Group", this::newGroup, () -> tabOf(selection()) != null));
        treeActions.add(new Action("Rena&me...", this::rename, this::canRename));
        treeActions.add(new Action("D&elete", this::remove, this::canRemove));
        treeActions.add(new Action("Re&set Tab", this::resetTab, this::canResetTab));
        treeActions.add(new Action("Reset &All", this::resetAll, () -> true));

        Composite side = new Composite(shell, SWT.NONE);
        side.setLayout(new GridLayout(1, false));
        side.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true));
        Menu menu = new Menu(tree);
        for (Action action : treeActions) {
            treeButtons.add(button(side, action.text(), action.run()));
            MenuItem item = new MenuItem(menu, SWT.PUSH);
            item.setText(action.text());
            item.setData(action);
            item.addListener(SWT.Selection, e -> action.run().run());
        }
        menu.addListener(SWT.Show, e -> {
            for (org.eclipse.swt.widgets.MenuItem item : menu.getItems()) {
                item.setEnabled(((Action) item.getData()).enabled().getAsBoolean());
            }
        });
        tree.setMenu(menu);
        dragAndDrop();

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
        Object top = MAIN_TABS.equals(choice) ? mainTabs : commandList;
        if (sourceStack.topControl != top) {
            sourceStack.topControl = (org.eclipse.swt.widgets.Control) top;
            mainTabs.getParent().layout();
        }
        if (MAIN_TABS.equals(choice)) {
            fillMainTabs(text);
            updateActions();
            return;
        }
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
        updateActions();
    }

    /**
     * Fills the Main Tabs source with the app's own tabs, groups and items, as the app defined
     * them, so built-in groups can be added back or put on other tabs. With a filter, it keeps the
     * groups whose name or items match.
     */
    private void fillMainTabs(String text) {
        Object chosen = chosen();
        mainTabs.removeAll();
        for (TabNode tab : customization.defaults()) {
            TreeItem tabItem = null;
            for (GroupNode group : tab.groups) {
                java.util.List<String> ids = customization.defaultItemIds(group.id);
                String groupLabel = label(group);
                boolean groupMatches = text.isEmpty() || groupLabel.toLowerCase(Locale.ROOT).contains(text)
                        || ids.stream().anyMatch(id -> customization.itemLabel(id).toLowerCase(Locale.ROOT).contains(text));
                if (!groupMatches) {
                    continue;
                }
                if (tabItem == null) {
                    tabItem = new TreeItem(mainTabs, SWT.NONE);
                    tabItem.setText(label(tab));
                    tabItem.setData(tab);
                }
                TreeItem groupItem = new TreeItem(tabItem, SWT.NONE);
                groupItem.setText(groupLabel);
                groupItem.setData(group);
                for (String id : ids) {
                    TreeItem item = new TreeItem(groupItem, SWT.NONE);
                    item.setText(customization.itemLabel(id));
                    item.setData(id);
                    if (!customization.isRenamable(id)) {
                        // Combos and galleries stay in their group: they can't be added on their own.
                        item.setForeground(lockedForeground);
                    }
                }
                groupItem.setExpanded(!text.isEmpty());
                if (sameSource(chosen, group)) {
                    mainTabs.setSelection(groupItem);
                }
            }
            if (tabItem != null) {
                tabItem.setExpanded(!text.isEmpty());
            }
        }
    }

    private static boolean sameSource(Object a, Object b) {
        return a instanceof GroupNode x && b instanceof GroupNode y && x.id.equals(y.id);
    }

    /**
     * What the left side has selected: a {@link Command} from the list; from Main Tabs, an app
     * {@link TabNode} or {@link GroupNode} (as the app defined it) or an item id.
     */
    private Object chosen() {
        if (sourceStack.topControl == mainTabs) {
            org.eclipse.swt.widgets.TreeItem[] selection = mainTabs.getSelection();
            return selection.length == 0 ? null : selection[0].getData();
        }
        int index = commandList.getSelectionIndex();
        return index < 0 ? null : listed.get(index);
    }

    /** The command id the left side's selection adds, or {@code null} when it is not a command. */
    private String chosenCommand(Object chosen) {
        return chosen instanceof Command command ? command.id()
                : chosen instanceof String id && customization.isRenamable(id) ? id : null;
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
        cancelEdit();
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
                    if (locked(ref)) {
                        commandItem.setForeground(lockedForeground);
                    }
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
        updateActions();
    }

    /** Enables the buttons that act on the tree's selection (the context menu does this when it shows). */
    private void updateActions() {
        addButton.setEnabled(canAdd());
        removeButton.setEnabled(canRemove());
        for (int i = 0; i < treeActions.size(); i++) {
            treeButtons.get(i).setEnabled(treeActions.get(i).enabled().getAsBoolean());
        }
    }

    /**
     * Whether a command is inside a built-in group. As in Office, those can't be added to,
     * removed, reordered or renamed; the tree shows them greyed. (Saved customizations that
     * changed one still load, and Reset puts it back.)
     */
    private static boolean locked(Object node) {
        return node instanceof ItemRef ref && !ref.group.custom;
    }

    /**
     * Add is on when the left side has a command or a group selected and the tree has a place
     * selected, except inside a built-in group. On a tab or a built-in group, adding a command
     * explains that commands go into custom groups.
     */
    private boolean canAdd() {
        Object chosen = chosen();
        Object node = selection();
        return node != null && !locked(node) && (chosen instanceof GroupNode || chosenCommand(chosen) != null);
    }

    private boolean canMove(int delta) {
        Object node = selection();
        if (locked(node)) {
            return false;
        }
        int index;
        int size;
        if (node instanceof TabNode tab) {
            index = state.indexOf(tab);
            size = state.size();
        } else if (node instanceof GroupNode group) {
            TabNode tab = tabOf(group);
            index = tab.groups.indexOf(group);
            size = tab.groups.size();
        } else if (node instanceof ItemRef ref) {
            index = ref.index;
            size = items(ref.group).size();
        } else {
            return false;
        }
        return index + delta >= 0 && index + delta < size;
    }

    private boolean canRename() {
        Object node = selection();
        return node instanceof TabNode || node instanceof GroupNode || (node instanceof ItemRef ref && !locked(ref) && renamable(ref));
    }

    private boolean canRemove() {
        Object node = selection();
        return (node instanceof ItemRef && !locked(node)) || node instanceof GroupNode || (node instanceof TabNode tab && tab.custom);
    }

    private boolean canResetTab() {
        TabNode tab = tabOf(selection());
        return tab != null && !tab.custom;
    }

    private record ItemRef(GroupNode group, int index) {
    }

    private void treeSelected(Event event) {
        updateActions();
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
        if (!canAdd()) {
            return;
        }
        Object chosen = chosen();
        Object node = selection();
        if (chosen instanceof GroupNode group) {
            addGroup(group, node);
            return;
        }
        String id = chosenCommand(chosen);
        if (node instanceof TabNode || (node instanceof GroupNode group && !group.custom)) {
            messages.accept(CUSTOM_GROUPS_ONLY);
            return;
        }
        if (node instanceof GroupNode group) {
            materialize(group, items(group)).add(id);
            fillTree(new ItemRef(group, group.items.size() - 1));
        } else if (node instanceof ItemRef ref) {
            materialize(ref.group, items(ref.group)).add(ref.index + 1, id);
            fillTree(new ItemRef(ref.group, ref.index + 1));
        }
        fillCommands();
    }

    /**
     * Puts the app group {@code defaults} (from Main Tabs) at the end of the selected tab, or after
     * the selected group. A group is in the ribbon once at most: if it already is, it moves here,
     * with its changes; if it was removed, it comes back as the app defined it.
     */
    private void addGroup(GroupNode defaults, Object node) {
        GroupNode after = node instanceof ItemRef ref ? ref.group : node instanceof GroupNode g ? g : null;
        TabNode to = tabOf(node);
        GroupNode group = null;
        for (TabNode tab : state) {
            for (GroupNode g : tab.groups) {
                if (g.id.equals(defaults.id)) {
                    group = g;
                }
            }
        }
        if (group == null) {
            group = defaults.copy();
        } else if (group == after) {
            fillTree(group);
            return;
        } else {
            tabOf(group).groups.remove(group);
        }
        to.groups.add(after == null ? to.groups.size() : to.groups.indexOf(after) + 1, group);
        fillTree(group);
        fillCommands();
    }

    void remove() {
        Object node = selection();
        if (locked(node)) {
            return;
        }
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
        int step = canMove(delta) ? delta : 0;
        if (node instanceof TabNode tab) {
            moveTab(tab, state.indexOf(tab) + step);
        } else if (node instanceof GroupNode group) {
            TabNode tab = tabOf(group);
            moveGroup(group, tab, tab.groups.indexOf(group) + step);
        } else if (node instanceof ItemRef ref && step != 0) {
            moveItem(ref, ref.group, ref.index + step);
        }
    }

    // The moves that Up, Down and drag and drop make. Each index is the node's place once it has
    // left its old one.

    private void moveTab(TabNode tab, int index) {
        state.remove(tab);
        state.add(index, tab);
        fillTree(tab);
    }

    private void moveGroup(GroupNode group, TabNode to, int index) {
        tabOf(group).groups.remove(group);
        to.groups.add(index, group);
        fillTree(group);
    }

    private void moveItem(ItemRef from, GroupNode to, int index) {
        java.util.List<String> target = materialize(to, items(to));
        String id = materialize(from.group, items(from.group)).remove(from.index);
        target.add(index, id);
        if (to != from.group) {
            // A command renamed in its group keeps that name in the group it moves to.
            String label = from.group.labels.remove(id);
            if (label != null) {
                to.labels.put(id, label);
            }
        }
        fillTree(new ItemRef(to, index));
    }

    // ------------------------------------------------------------------ drag and drop

    /*
     * On Windows the drag listeners run inside the native drag loop (OLE's DoDragDrop). Two rules
     * keep that loop safe: the tree is not rebuilt while it runs (the drop's move waits until the
     * loop has returned), and no exception escapes a listener (one crossing the native frames ends
     * the process); an exception is thrown again from the event loop instead, where it is reported.
     */
    private void dragAndDrop() {
        Transfer[] transfers = { TextTransfer.getInstance() };
        DragSource source = new DragSource(tree, DND.DROP_MOVE);
        source.setTransfer(transfers);
        source.addDragListener(new DragSourceListener() {
            @Override
            public void dragStart(DragSourceEvent event) {
                event.doit = false;
                guarded(() -> {
                    dragged = selection();
                    event.doit = dragged != null && !locked(dragged);
                });
            }

            @Override
            public void dragSetData(DragSourceEvent event) {
                // The dragged node stays in the dialog; the id is only what other apps would get.
                // TextTransfer refuses empty text, so there is always some.
                event.data = "zapthemes.ribbon";
                guarded(() -> event.data = switch (dragged) {
                    case TabNode tab -> tab.id;
                    case GroupNode group -> group.id;
                    case ItemRef ref -> itemId(ref);
                    case null, default -> "zapthemes.ribbon";
                });
            }

            @Override
            public void dragFinished(DragSourceEvent event) {
                dragged = null;
            }
        });
        DropTarget target = new DropTarget(tree, DND.DROP_MOVE);
        target.setTransfer(transfers);
        target.addDropListener(new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetEvent event) {
                dragOver(event);
            }

            @Override
            public void dragOperationChanged(DropTargetEvent event) {
                dragOver(event);
            }

            @Override
            public void dragOver(DropTargetEvent event) {
                event.feedback = DND.FEEDBACK_SCROLL | DND.FEEDBACK_EXPAND;
                event.detail = DND.DROP_NONE;
                guarded(() -> {
                    Drop drop = plan(event);
                    if (drop != null) {
                        event.feedback |= drop.feedback();
                        event.detail = DND.DROP_MOVE;
                    }
                });
            }

            @Override
            public void drop(DropTargetEvent event) {
                Drop[] drop = { null };
                guarded(() -> drop[0] = plan(event));
                if (drop[0] == null) {
                    event.detail = DND.DROP_NONE;
                    return;
                }
                Runnable move = drop[0].move();
                tree.getDisplay().asyncExec(() -> {
                    if (!tree.isDisposed()) {
                        move.run();
                    }
                });
            }
        });
    }

    /** Runs a drag listener's work; an exception is thrown again later, outside the native drag loop. */
    private void guarded(Runnable work) {
        try {
            work.run();
        } catch (RuntimeException | org.eclipse.swt.SWTError e) {
            tree.getDisplay().asyncExec(() -> {
                throw e;
            });
        }
    }

    private Drop plan(DropTargetEvent event) {
        if (!(event.item instanceof org.eclipse.swt.widgets.TreeItem item)) {
            return null;
        }
        Point point = tree.toControl(event.x, event.y);
        Rectangle bounds = item.getBounds();
        return plan(dragged, item.getData(), point.y < bounds.y + bounds.height / 2);
    }

    /** A drop the dialog accepts: how the tree shows it, and the move it makes. */
    private record Drop(int feedback, Runnable move) {
    }

    /**
     * What dropping {@code source} on {@code target} (in its upper half when {@code before}) does,
     * or {@code null} when the dialog refuses it. As in Office, tabs go before or after tabs;
     * groups before or after groups, in any tab, or at the end of a tab; commands before or after
     * commands, or at the end of a group, in their own group or a custom one. Other items (combos,
     * galleries) keep to their group.
     */
    private Drop plan(Object source, Object target, boolean before) {
        if (locked(source)) {
            return null;
        }
        int feedback = before ? DND.FEEDBACK_INSERT_BEFORE : DND.FEEDBACK_INSERT_AFTER;
        int after = before ? 0 : 1;
        if (source instanceof TabNode tab && target instanceof TabNode to && to != tab) {
            int index = state.indexOf(to) - (state.indexOf(tab) < state.indexOf(to) ? 1 : 0) + after;
            return new Drop(feedback, () -> moveTab(tab, index));
        }
        if (source instanceof GroupNode group && target instanceof GroupNode at && at != group) {
            TabNode from = tabOf(group);
            TabNode to = tabOf(at);
            int index = to.groups.indexOf(at) - (from == to && from.groups.indexOf(group) < to.groups.indexOf(at) ? 1 : 0) + after;
            return new Drop(feedback, () -> moveGroup(group, to, index));
        }
        if (source instanceof GroupNode group && target instanceof TabNode to) {
            int index = to.groups.size() - (tabOf(group) == to ? 1 : 0);
            return new Drop(DND.FEEDBACK_SELECT, () -> moveGroup(group, to, index));
        }
        if (source instanceof ItemRef ref && target instanceof ItemRef at && !(at.group == ref.group && at.index == ref.index)
                && accepts(at.group, ref)) {
            int index = at.index - (at.group == ref.group && ref.index < at.index ? 1 : 0) + after;
            return new Drop(feedback, () -> moveItem(ref, at.group, index));
        }
        if (source instanceof ItemRef ref && target instanceof GroupNode to && accepts(to, ref)) {
            int index = items(to).size() - (to == ref.group ? 1 : 0);
            return new Drop(DND.FEEDBACK_SELECT, () -> moveItem(ref, to, index));
        }
        return null;
    }

    /** Whether item {@code ref} may move into {@code group}: its own, or a custom group that lacks it. */
    private boolean accepts(GroupNode group, ItemRef ref) {
        return group == ref.group || (group.custom && renamable(ref) && !items(group).contains(itemId(ref)));
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
            case ItemRef ref when !locked(ref) && renamable(ref) -> ref.group.labels.getOrDefault(itemId(ref), customization.itemLabel(itemId(ref)));
            case null, default -> null;
        };
        org.eclipse.swt.widgets.TreeItem[] items = tree.getSelection();
        if (current == null || items.length != 1) {
            return;
        }
        editInPlace(items[0], node, Command.stripMnemonic(current));
    }

    // ------------------------------------------------------------------ renaming in place

    /**
     * Renames in the tree itself: a text field over the item, started by Rename..., F2 or a
     * second click on the selected item's name. Enter or leaving the field renames; Escape
     * doesn't.
     */
    private void editInPlace(org.eclipse.swt.widgets.TreeItem item, Object node, String initial) {
        cancelEdit();
        Text text = new Text(tree, SWT.SINGLE | SWT.BORDER);
        text.setText(initial);
        text.selectAll();
        editor.horizontalAlignment = SWT.LEFT;
        editor.grabHorizontal = true;
        editor.minimumWidth = 140;
        editor.setEditor(text, item);
        editText = text;
        text.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_RETURN || e.detail == SWT.TRAVERSE_ESCAPE) {
                // Neither closes the dialog (OK is the default button; Escape would cancel it).
                boolean cancel = e.detail == SWT.TRAVERSE_ESCAPE;
                e.doit = false;
                e.detail = SWT.TRAVERSE_NONE;
                endEdit(cancel ? null : node, initial);
            }
        });
        text.addListener(SWT.FocusOut, e -> endEdit(node, initial));
        text.setFocus();
    }

    /** Ends the edit; renames {@code node} to the field's text unless it is null, blank or unchanged. */
    private void endEdit(Object node, String initial) {
        Text text = editText;
        if (text == null) {
            return;
        }
        editText = null;
        String name = text.getText().trim();
        editor.setEditor(null);
        text.dispose();
        if (node != null && !name.isEmpty() && !name.equals(initial)) {
            rename(node, name);
        } else {
            tree.setFocus();
        }
    }

    private void cancelEdit() {
        endEdit(null, null);
    }

    /** Starts renaming on a second click on the selected item's name, unless it turns into a double-click or a drag. */
    private void secondClick(Event event) {
        org.eclipse.swt.widgets.TreeItem[] selection = tree.getSelection();
        Point point = new Point(event.x, event.y);
        if (event.button != 1 || selection.length != 1 || tree.getItem(point) != selection[0]
                || !selection[0].getBounds().contains(point) || !canRename()) {
            clicks++;
            return;
        }
        int click = ++clicks;
        org.eclipse.swt.widgets.TreeItem item = selection[0];
        tree.getDisplay().timerExec(tree.getDisplay().getDoubleClickTime(), () -> {
            if (click == clicks && !tree.isDisposed() && !item.isDisposed() && tree.getSelectionCount() == 1
                    && tree.getSelection()[0] == item) {
                rename();
            }
        });
    }

    /** Renames the selected tab, group or command without prompting (tests). */
    void rename(String name) {
        rename(selection(), name);
    }

    private void rename(Object node, String name) {
        if (node instanceof TabNode tab) {
            tab.label = name;
            fillTree(tab);
        } else if (node instanceof GroupNode group) {
            group.label = name;
            fillTree(group);
        } else if (node instanceof ItemRef ref && !locked(ref) && renamable(ref)) {
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

    private void showMessage(String message) {
        Shell dialog = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
        dialog.setText(shell.getText());
        dialog.setLayout(new GridLayout(1, false));
        Label label = new Label(dialog, SWT.WRAP);
        label.setText(message);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.widthHint = 320;
        label.setLayoutData(data);
        Button ok = new Button(dialog, SWT.PUSH);
        ok.setText("OK");
        GridData okData = new GridData(SWT.END, SWT.CENTER, false, false);
        okData.widthHint = 80;
        ok.setLayoutData(okData);
        ok.addListener(SWT.Selection, e -> dialog.close());
        dialog.setDefaultButton(ok);
        dialog.pack();
        dialog.open();
        while (!dialog.isDisposed()) {
            if (!dialog.getDisplay().readAndDispatch()) {
                dialog.getDisplay().sleep();
            }
        }
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

    /** The tree node of tab or group {@code id}, or of its item {@code itemId} when that is not null (tests). */
    Object node(String id, String itemId) {
        for (TabNode tab : state) {
            if (tab.id.equals(id) && itemId == null) {
                return tab;
            }
            for (GroupNode group : tab.groups) {
                if (group.id.equals(id)) {
                    return itemId == null ? group : new ItemRef(group, items(group).indexOf(itemId));
                }
            }
        }
        return null;
    }

    /** The tree's feedback for dropping {@code source} on {@code target}; {@link DND#FEEDBACK_NONE} when refused (tests). */
    int dropFeedback(Object source, Object target, boolean before) {
        Drop drop = plan(source, target, before);
        return drop == null ? DND.FEEDBACK_NONE : drop.feedback();
    }

    /** Drops {@code source} on {@code target} as the tree's drop listener does; returns whether it was accepted (tests). */
    boolean drop(Object source, Object target, boolean before) {
        Drop drop = plan(source, target, before);
        if (drop != null) {
            drop.move().run();
        }
        return drop != null;
    }

    /** The in-place rename field, or {@code null} when none is open (tests). */
    Text renameField() {
        return editText;
    }

    /** The tree's context menu (tests). */
    Menu treeMenu() {
        return (Menu) tree.getMenu();
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
        updateActions();
    }

    /**
     * Shows Main Tabs on the left and selects the app's tab or group {@code id}, or its item
     * {@code itemId} when that is not null (tests).
     */
    void selectMainTabs(String id, String itemId) {
        source.select(source.indexOf(MAIN_TABS));
        filter.setText("");
        fillCommands();
        for (org.eclipse.swt.widgets.TreeItem tab : mainTabs.getItems()) {
            if (((TabNode) tab.getData()).id.equals(id) && itemId == null) {
                mainTabs.setSelection(tab);
            }
            for (org.eclipse.swt.widgets.TreeItem group : tab.getItems()) {
                if (((GroupNode) group.getData()).id.equals(id)) {
                    if (itemId == null) {
                        mainTabs.setSelection(group);
                    }
                    for (org.eclipse.swt.widgets.TreeItem item : group.getItems()) {
                        if (item.getData().equals(itemId)) {
                            mainTabs.setSelection(item);
                        }
                    }
                }
            }
        }
        updateActions();
    }

    /** Types {@code text} into the filter (tests). */
    void filter(String text) {
        filter.setText(text);
    }

    /** The Main Tabs source (tests). */
    Tree mainTabs() {
        return mainTabs;
    }

    /** Sends the dialog's messages to {@code sink} instead of showing them (tests). */
    void onMessage(java.util.function.Consumer<String> sink) {
        messages = sink;
    }

    Button addButton() {
        return addButton;
    }

    /** Whether the tree shows the selected node greyed, as a locked command (tests). */
    boolean selectionGreyed() {
        org.eclipse.swt.widgets.TreeItem[] selection = tree.getSelection();
        return selection.length == 1 && lockedForeground.equals(selection[0].getForeground());
    }
}
