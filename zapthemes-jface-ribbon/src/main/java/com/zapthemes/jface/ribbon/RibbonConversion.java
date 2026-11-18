package com.zapthemes.jface.ribbon;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.ICoolBarManager;
import org.eclipse.jface.action.LegacyActionTools;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarContributionItem;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import com.zapthemes.widgets.FlatButton;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.Images;
import com.zapthemes.widgets.ribbon.Backstage;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.ribbon.RibbonButton;
import com.zapthemes.widgets.ribbon.RibbonGroup;
import com.zapthemes.widgets.ribbon.RibbonTab;

/**
 * Turns a JFace window's menu bar and tool bar (or cool bar) into ribbon tabs, groups and the
 * backstage, the way {@link ZapAppWindow} shows an application written for
 * {@code ApplicationWindow}:
 *
 * <ol>
 *   <li>every action in the menus and the tool bar is registered as a command first, submenus
 *       included, so every accelerator works whether or not the ribbon shows the action;</li>
 *   <li>the tool bar's sections become groups of large buttons in a <em>Home</em> tab, first;</li>
 *   <li>every top-level menu but File becomes a tab, its sections (cut at {@link Separator}s)
 *       groups, submenus drop-down buttons, and its mnemonics KeyTips;</li>
 *   <li>the File menu becomes the backstage ({@link #fillBackstage}): its actions command entries,
 *       its submenus pages, with <em>About</em> from the Help menu and <em>Exit</em> last.</li>
 * </ol>
 *
 * Everything it makes has an id starting with {@value #PREFIX}, so the window's own
 * {@code createRibbon} can find and replace it, and {@link #clear} removes it again. An action
 * without an id gets {@value #PREFIX} + its menu path + its label ({@code auto.edit.rename}),
 * unique within the window; the action itself is not changed. {@link ControlContribution}s are
 * skipped (the ribbon draws on a canvas and hosts no native controls), as are
 * {@link GroupMarker}s and items that are not visible.
 *
 * <p>Subclass it and override {@link ZapAppWindow#createRibbonConversion()} to change the rules.
 */
public class RibbonConversion {

    /** The start of every id the conversion makes. */
    public static final String PREFIX = "auto.";

    private static final System.Logger LOG = System.getLogger(RibbonConversion.class.getName());
    /** The least width of a backstage page's buttons, so a list of short names lines up. */
    private static final int PAGE_BUTTON_WIDTH = 320;

    /** Command ids, tab ids and backstage page ids taken. */
    private final Set<String> used = new HashSet<>();
    /** Group ids taken: a namespace of their own, so a group may share its name with a command. */
    private final Set<String> usedGroups = new HashSet<>();
    private final Map<RibbonTab, Integer> nextGroup = new HashMap<>();
    private final Map<RibbonTab, Set<String>> tipsByTab = new HashMap<>();
    private final Map<IContributionItem, Command> dynamics = new WeakHashMap<>();

    // ------------------------------------------------------------------ the tabs

    /**
     * Fills {@code ribbon} from {@code menuBar} and {@code toolBar} (a tool bar or cool bar
     * manager); either may be {@code null}. Tabs the conversion made before are reused, and the
     * groups it makes go before a tab's other groups.
     */
    public void fillTabs(Ribbon ribbon, MenuManager menuBar, IContributionManager toolBar) {
        nextGroup.clear();
        tipsByTab.clear();
        used.clear();
        usedGroups.clear();
        ribbon.commands().all().forEach(c -> used.add(c.id()));
        ribbon.tabs().forEach(t -> t.groups().forEach(g -> usedGroups.add(g.id())));
        if (menuBar != null) {
            for (IContributionItem item : menuBar.getItems()) {
                if (item instanceof MenuManager menu && item.isVisible()) {
                    registerAll(ribbon, menu, key(menu));
                }
            }
        }
        List<List<IContributionItem>> toolBars = toolBars(toolBar);
        toolBars.forEach(items -> registerAll(ribbon, items, "home"));

        RibbonTab previous = null;
        boolean toolBarTab = toolBars.stream().anyMatch(items -> items.stream().anyMatch(this::shown));
        if (toolBarTab) {
            RibbonTab home = tab(ribbon, PREFIX + "home", "Home", null);
            keyTip(home, 'H');
            int count = 0;
            List<Section> sections = new ArrayList<>();
            for (List<IContributionItem> items : toolBars) {
                sections.addAll(sections(items));
            }
            for (Section section : sections) {
                count++;
                String label = section.name() != null ? humanize(section.name())
                        : sections.size() == 1 ? "Commands" : "Commands " + count;
                group(home, section, label, "home", true);
            }
            previous = home;
        }
        if (menuBar != null) {
            for (IContributionItem item : menuBar.getItems()) {
                if (!(item instanceof MenuManager menu) || !item.isVisible() || isFileMenu(menu)) {
                    continue;
                }
                String key = key(menu);
                RibbonTab tab = tab(ribbon, PREFIX + key, plain(menu.getMenuText()), previous);
                char mnemonic = mnemonic(menu.getMenuText());
                if (mnemonic != 0) {
                    keyTip(tab, mnemonic);
                }
                for (Section section : sections(List.of(menu.getItems()))) {
                    String label = section.name() != null ? humanize(section.name()) : firstLabel(section);
                    group(tab, section, label, key, false);
                }
                previous = tab;
            }
        }
        for (RibbonTab tab : ribbon.tabs()) {
            if (tab.id().startsWith(PREFIX) && tab.groups().isEmpty()) {
                ribbon.removeTab(tab);
            }
        }
    }

    /** Removes the groups the conversion made from every tab, and its tabs that are left empty. */
    public void clear(Ribbon ribbon) {
        for (RibbonTab tab : ribbon.tabs()) {
            for (RibbonGroup group : tab.groups()) {
                if (group.id().startsWith(PREFIX)) {
                    tab.removeGroup(group);
                }
            }
            if (tab.id().startsWith(PREFIX) && tab.groups().isEmpty()) {
                ribbon.removeTab(tab);
            }
        }
    }

    private RibbonTab tab(Ribbon ribbon, String id, String label, RibbonTab previous) {
        RibbonTab tab = ribbon.tab(id);
        if (tab == null) {
            int index = previous == null ? 0 : ribbon.tabs().indexOf(previous) + 1;
            tab = ribbon.addTab(index, id, label);
        } else if (!tab.label().equals(label)) {
            tab.setLabel(label);
        }
        used.add(id);
        return tab;
    }

    private void keyTip(RibbonTab tab, char letter) {
        Set<String> taken = new HashSet<>(Set.of("F"));
        for (RibbonTab other : tab.ribbon().tabs()) {
            if (other != tab && other.keyTip() != null) {
                taken.add(other.keyTip());
            }
        }
        String tip = String.valueOf(Character.toUpperCase(letter));
        if (!taken.contains(tip)) {
            tab.setKeyTip(tip);
        }
    }

    private void group(RibbonTab tab, Section section, String label, String path, boolean large) {
        String key = section.name() != null ? slug(section.name()) : String.valueOf(nextGroup.getOrDefault(tab, 0) + 1);
        int index = nextGroup.getOrDefault(tab, 0);
        RibbonGroup group = null;
        Set<String> tips = tipsByTab.computeIfAbsent(tab, t -> new HashSet<>());
        for (IContributionItem item : section.items()) {
            if (!shown(item)) {
                continue;
            }
            Command command = command(tab.ribbon(), item, path, label);
            if (command == null) {
                continue;
            }
            if (group == null) {
                group = tab.addGroup(index, unique(usedGroups, tab.id() + "." + key), label);
                nextGroup.put(tab, index + 1);
            }
            RibbonGroup target = group;
            if (target.items().stream().anyMatch(i -> i.command() == command)) {
                continue;
            }
            RibbonButton button = target.add(command);
            if (item instanceof ActionContributionItem actionItem) {
                IAction action = actionItem.getAction();
                ActionCommands.joinRadioGroup(target, action);
                boolean hasImage = action.getImageDescriptor() != null || ActionCommands.hasLargeImage(action);
                if (ActionCommands.hasLargeImage(action) || large && hasImage) {
                    button.large();
                }
                char mnemonic = mnemonic(action.getText());
                if (mnemonic != 0 && tips.add(String.valueOf(Character.toUpperCase(mnemonic)))) {
                    button.keyTip(String.valueOf(Character.toUpperCase(mnemonic)));
                }
            } else if (item instanceof MenuManager menu) {
                char mnemonic = mnemonic(menu.getMenuText());
                if (mnemonic != 0 && tips.add(String.valueOf(Character.toUpperCase(mnemonic)))) {
                    button.keyTip(String.valueOf(Character.toUpperCase(mnemonic)));
                }
            }
        }
    }

    /** The command a contribution item becomes, or {@code null} for items the ribbon can't show. */
    private Command command(Ribbon ribbon, IContributionItem item, String path, String groupLabel) {
        if (item instanceof ActionContributionItem actionItem) {
            return register(ribbon, actionItem.getAction(), path);
        }
        if (item instanceof MenuManager menu) {
            return dropDown(ribbon, menu, path);
        }
        if (item instanceof ControlContribution) {
            LOG.log(Level.INFO, () -> "Skipping control contribution " + item.getId()
                    + ": the ribbon hosts no native controls; add a ribbon combo for it in createRibbon");
            return null;
        }
        if (item instanceof Separator || item instanceof GroupMarker) {
            return null;
        }
        if (item instanceof ToolBarContributionItem) {
            return null;
        }
        return dynamic(ribbon, item, path, groupLabel);
    }

    private Command register(Ribbon ribbon, IAction action, String path) {
        String known = ActionCommands.knownId(action);
        String id = known != null ? known : unique(PREFIX + path + "." + slug(plain(action.getText())));
        return ActionCommands.register(ribbon.commands(), action, ribbon, id);
    }

    private Command dropDown(Ribbon ribbon, MenuManager menu, String path) {
        String id = menu.getId() != null ? menu.getId() : PREFIX + path + "." + slug(plain(menu.getMenuText()));
        Command existing = ribbon.commands().find(id).orElse(null);
        if (existing != null) {
            return existing;
        }
        used.add(id);
        Command command = new Command(id, menu.getMenuText() == null ? id : LegacyActionTools.removeAcceleratorText(menu.getMenuText()))
                .style(Command.Style.DROP_DOWN).menu(new CommandMenu.Popup(control -> {
                    Menu popup = menu.createContextMenu(control);
                    menu.update(true);
                    return popup;
                }));
        if (menu.getImageDescriptor() != null) {
            LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources(), ribbon);
            command.images(Images.of(resources.create(menu.getImageDescriptor())));
        }
        return ribbon.commands().add(command);
    }

    /** A drop-down whose menu the item fills each time it opens (a list of recent files, of themes). */
    private Command dynamic(Ribbon ribbon, IContributionItem item, String path, String groupLabel) {
        Command known = dynamics.get(item);
        if (known != null && ribbon.commands().find(known.id()).orElse(null) == known) {
            return known;
        }
        String name = item.getId() != null ? humanize(item.getId()) : groupLabel;
        String id = unique(PREFIX + path + "." + slug(item.getId() != null ? item.getId() : name + " more"));
        Menu[] last = new Menu[1];
        Command command = new Command(id, name).style(Command.Style.DROP_DOWN).menu(new CommandMenu.Popup(control -> {
            if (last[0] != null && !last[0].isDisposed()) {
                last[0].dispose();
            }
            Menu popup = new com.zapthemes.swt.widgets.Menu(control);
            item.fill(popup, 0);
            last[0] = popup;
            return popup;
        }));
        dynamics.put(item, command);
        return ribbon.commands().add(command);
    }

    private void registerAll(Ribbon ribbon, MenuManager menu, String path) {
        registerAll(ribbon, List.of(menu.getItems()), path);
    }

    private void registerAll(Ribbon ribbon, List<IContributionItem> items, String path) {
        for (IContributionItem item : items) {
            if (item instanceof ActionContributionItem actionItem) {
                register(ribbon, actionItem.getAction(), path);
            } else if (item instanceof MenuManager sub) {
                registerAll(ribbon, sub, path + "." + key(sub));
            }
        }
    }

    private List<List<IContributionItem>> toolBars(IContributionManager toolBar) {
        List<List<IContributionItem>> result = new ArrayList<>();
        if (toolBar instanceof ICoolBarManager coolBar) {
            for (IContributionItem item : coolBar.getItems()) {
                if (item instanceof ToolBarContributionItem bar && bar.isVisible() && bar.getToolBarManager() != null) {
                    result.add(List.of(bar.getToolBarManager().getItems()));
                }
            }
        } else if (toolBar != null) {
            result.add(List.of(toolBar.getItems()));
        }
        return result;
    }

    // ------------------------------------------------------------------ the backstage

    /**
     * Fills {@code backstage} from the File menu of {@code menuBar}: each action is a command entry
     * and each submenu a page listing its items (built again each time the backstage opens, so
     * dynamic menus such as recent files stay current); separators stay; <em>About</em> from the Help
     * menu and the File menu's <em>Exit</em> and <em>Close</em> come last. Returns how many entries
     * it added.
     */
    public int fillBackstage(Backstage backstage, MenuManager menuBar) {
        if (menuBar == null) {
            return 0;
        }
        Ribbon ribbon = backstage.ribbon();
        int count = 0;
        boolean separator = false;
        List<IAction> last = new ArrayList<>();
        MenuManager file = fileMenu(menuBar);
        if (file != null) {
            for (IContributionItem item : file.getItems()) {
                if (item instanceof Separator && item.isVisible()) {
                    separator = count > 0;
                } else if (item instanceof ActionContributionItem actionItem && item.isVisible()) {
                    IAction action = actionItem.getAction();
                    if (isExit(action)) {
                        last.add(action);
                        continue;
                    }
                    if (separator) {
                        backstage.addSeparator();
                        separator = false;
                    }
                    backstage.addCommand(register(ribbon, action, "file").id());
                    count++;
                } else if (item instanceof MenuManager sub && item.isVisible()) {
                    if (separator) {
                        backstage.addSeparator();
                        separator = false;
                    }
                    page(backstage, sub);
                    count++;
                } else if (shown(item) && !(item instanceof GroupMarker)) {
                    LOG.log(Level.INFO, () -> "Skipping File menu item " + item.getId()
                            + ": put it in a submenu, or add a backstage page for it in createBackstage");
                }
            }
        }
        IAction about = about(menuBar);
        if (about != null) {
            backstage.addSeparator();
            backstage.addCommand(register(ribbon, about, "help").id());
            count++;
        }
        if (!last.isEmpty()) {
            if (count > 0 && about == null) {
                backstage.addSeparator();
            }
            for (IAction action : last) {
                backstage.addCommand(register(ribbon, action, "file").id());
                count++;
            }
        }
        return count;
    }

    private void page(Backstage backstage, MenuManager menu) {
        String id = unique(PREFIX + "file." + key(menu));
        Composite[] page = new Composite[1];
        backstage.addPage(id, plain(menu.getMenuText()), null, parent -> {
            page[0] = parent;
            fillPage(parent, menu);
        });
        backstage.addOpenListener(open -> {
            if (open && page[0] != null && !page[0].isDisposed()) {
                fillPage(page[0], menu);
            }
        });
    }

    /** The submenu's items as buttons, as the menu would show them now. */
    private void fillPage(Composite page, MenuManager menu) {
        for (Control child : page.getChildren()) {
            child.dispose();
        }
        com.zapthemes.swt.widgets.Label title = new com.zapthemes.swt.widgets.Label(page, SWT.NONE);
        title.setText(plain(menu.getMenuText()));
        com.zapthemes.core.ThemeManager.get(page.getDisplay()).setThemeClass(title, "backstageTitle");
        Menu items = menu.createContextMenu(page);
        items.notifyListeners(SWT.Show, new Event());
        addButtons(page, items);
        page.layout(true, true);
    }

    private static void addButtons(Composite page, Menu menu) {
        for (MenuItem item : menu.getItems()) {
            if ((item.getStyle() & SWT.SEPARATOR) != 0) {
                com.zapthemes.swt.widgets.Label line = new com.zapthemes.swt.widgets.Label(page, SWT.SEPARATOR | SWT.HORIZONTAL);
                line.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
                continue;
            }
            if (item.getMenu() != null) {
                item.getMenu().notifyListeners(SWT.Show, new Event());
                addButtons(page, item.getMenu());
                continue;
            }
            FlatButton button = new FlatButton(page, SWT.PUSH);
            button.setText(LegacyActionTools.removeAcceleratorText(item.getText()));
            button.setImage(item.getImage());
            button.setEnabled(item.isEnabled());
            GridData data = new GridData(SWT.LEFT, SWT.CENTER, false, false);
            data.widthHint = Math.max(button.computeSize(SWT.DEFAULT, SWT.DEFAULT).x, PAGE_BUTTON_WIDTH);
            button.setLayoutData(data);
            button.addListener(SWT.Selection, e -> {
                if (!item.isDisposed()) {
                    if ((item.getStyle() & (SWT.CHECK | SWT.RADIO)) != 0) {
                        item.setSelection(!item.getSelection());
                    }
                    item.notifyListeners(SWT.Selection, new Event());
                }
            });
        }
    }

    // ------------------------------------------------------------------ rules

    /** The File menu: the top-level menu with id {@code file}, else the one whose text is "File". */
    public MenuManager fileMenu(MenuManager menuBar) {
        if (menuBar == null) {
            return null;
        }
        for (IContributionItem item : menuBar.getItems()) {
            if (item instanceof MenuManager menu && item.isVisible() && isFileMenu(menu)) {
                return menu;
            }
        }
        return null;
    }

    protected boolean isFileMenu(MenuManager menu) {
        return "file".equals(menu.getId()) || menu.getId() == null && "file".equalsIgnoreCase(plain(menu.getMenuText()));
    }

    protected boolean isHelpMenu(MenuManager menu) {
        return "help".equals(menu.getId()) || menu.getId() == null && "help".equalsIgnoreCase(plain(menu.getMenuText()));
    }

    /** Exit, Quit and Close (by id, else by label): last in the backstage, as in Office. */
    protected boolean isExit(IAction action) {
        String id = action.getId() == null ? "" : action.getId().toLowerCase(Locale.ROOT);
        String label = plain(action.getText()).toLowerCase(Locale.ROOT);
        return id.endsWith("exit") || id.endsWith("quit") || id.endsWith(".close") || label.equals("exit") || label.equals("quit")
                || label.equals("close") || label.equals("close window");
    }

    protected boolean isAbout(IAction action) {
        String id = action.getId() == null ? "" : action.getId().toLowerCase(Locale.ROOT);
        return id.endsWith("about") || plain(action.getText()).toLowerCase(Locale.ROOT).startsWith("about");
    }

    private IAction about(MenuManager menuBar) {
        for (IContributionItem item : menuBar.getItems()) {
            if (item instanceof MenuManager menu && item.isVisible() && isHelpMenu(menu)) {
                for (IContributionItem entry : menu.getItems()) {
                    if (entry instanceof ActionContributionItem actionItem && entry.isVisible() && isAbout(actionItem.getAction())) {
                        return actionItem.getAction();
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private record Section(String name, List<IContributionItem> items) {
    }

    /** {@code items} cut at visible separators; a named separator names the section after it. */
    private static List<Section> sections(List<IContributionItem> items) {
        List<Section> sections = new ArrayList<>();
        String name = null;
        List<IContributionItem> current = new ArrayList<>();
        for (IContributionItem item : items) {
            if (item instanceof Separator && item.isVisible()) {
                if (!current.isEmpty()) {
                    sections.add(new Section(name, current));
                }
                current = new ArrayList<>();
                name = meaningful(item.getId());
            } else {
                current.add(item);
            }
        }
        if (!current.isEmpty()) {
            sections.add(new Section(name, current));
        }
        return sections;
    }

    private static String meaningful(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }
        String last = groupName.substring(groupName.lastIndexOf('.') + 1);
        return last.equals("additions") || last.endsWith("Start") || last.endsWith("End") ? null : last;
    }

    private boolean shown(IContributionItem item) {
        return item.isVisible() && !(item instanceof Separator) && !(item instanceof GroupMarker);
    }

    private String firstLabel(Section section) {
        for (IContributionItem item : section.items()) {
            if (item instanceof ActionContributionItem actionItem && item.isVisible()) {
                return plain(actionItem.getAction().getText());
            }
            if (item instanceof MenuManager menu && item.isVisible()) {
                return plain(menu.getMenuText());
            }
        }
        return "";
    }

    private String unique(String id) {
        return unique(used, id);
    }

    private static String unique(Set<String> taken, String id) {
        String candidate = id;
        for (int n = 2; taken.contains(candidate); n++) {
            candidate = id + n;
        }
        taken.add(candidate);
        return candidate;
    }

    private static String key(MenuManager menu) {
        return menu.getId() != null ? menu.getId() : slug(plain(menu.getMenuText()));
    }

    /** Menu text without the mnemonic, the accelerator and a trailing "...". */
    static String plain(String text) {
        if (text == null) {
            return "";
        }
        String plain = Command.stripMnemonic(LegacyActionTools.removeAcceleratorText(text)).strip();
        while (plain.endsWith(".") || plain.endsWith("…")) {
            plain = plain.substring(0, plain.length() - 1);
        }
        return plain.strip();
    }

    /** {@code "Save All"} to {@code "saveAll"}, as command ids are written. */
    static String slug(String text) {
        StringBuilder id = new StringBuilder();
        boolean upper = false;
        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                id.append(id.isEmpty() ? Character.toLowerCase(c) : upper ? Character.toUpperCase(c) : c);
                upper = false;
            } else {
                upper = true;
            }
        }
        return id.isEmpty() ? "item" : id.toString();
    }

    /** {@code "clipboard"} or {@code "fontStyles"} to {@code "Clipboard"} and {@code "Font Styles"}. */
    static String humanize(String name) {
        StringBuilder label = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '-' || c == '_' || c == '.') {
                label.append(' ');
            } else if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(name.charAt(i - 1))) {
                label.append(' ').append(c);
            } else {
                label.append(label.isEmpty() || label.charAt(label.length() - 1) == ' ' ? Character.toUpperCase(c) : c);
            }
        }
        return label.toString().strip();
    }

    /** The letter after the first single {@code &}, or 0. */
    static char mnemonic(String text) {
        if (text == null) {
            return 0;
        }
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '&') {
                if (text.charAt(i + 1) == '&') {
                    i++;
                    continue;
                }
                char c = text.charAt(i + 1);
                return Character.isLetterOrDigit(c) ? c : 0;
            }
        }
        return 0;
    }
}
