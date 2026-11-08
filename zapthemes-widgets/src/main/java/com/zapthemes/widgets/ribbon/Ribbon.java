package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandListener;
import com.zapthemes.widgets.command.CommandRegistry;
import com.zapthemes.widgets.command.CustomizationStore;

/**
 * An Office-style ribbon: tabs of labeled groups of commands, drawn on a canvas and themed.
 *
 * <pre>{@code
 * Ribbon ribbon = new Ribbon(shell, SWT.NONE);
 * ribbon.commands().add(new Command("home.paste", "Paste").images(Images.of(paste16, paste32)).onRun(e -> paste()));
 * RibbonTab home = ribbon.addTab("home", "Home");
 * RibbonGroup clipboard = home.addGroup("home.clipboard", "Clipboard");
 * clipboard.add("home.paste").sizes(Size.LARGE, Size.LARGE);
 * }</pre>
 *
 * <ul>
 *   <li>Groups shrink step by step as the ribbon narrows, down to one drop-down button each
 *       ({@link RibbonTab#setReductionOrder}).</li>
 *   <li>Alt or F10 shows KeyTips; Ctrl+F1, double-clicking a tab or the ˄ button collapses the
 *       ribbon to its tab row, and a click on a tab then shows it as an overlay.</li>
 *   <li>{@link #addContextualTabSet contextual tab sets}, a File tab that opens the
 *       {@link Backstage}, and commands at the tab row's right ({@link #addRightCommand}).</li>
 *   <li>End users customize it in the {@link CustomizeRibbonDialog}; {@link #setCustomizationStore}
 *       saves their changes.</li>
 * </ul>
 *
 * Theme type {@code Ribbon}; see the theme authoring guide for its properties.
 */
public class Ribbon extends RibbonSurface {

    public static final String THEME_TYPE = "Ribbon";

    private final CommandRegistry commands;
    private final List<RibbonTab> tabs = new ArrayList<>();
    private final List<RibbonContextualTabSet> contextualSets = new ArrayList<>();
    private RibbonTab selection;
    private RibbonTab beforeContextual;
    private final List<Command> rightCommands = new ArrayList<>();
    private final Set<Command> emphasized = new HashSet<>();
    private String fileTabLabel;
    private Consumer<Backstage> backstageSetup;
    private Backstage backstage;
    private boolean collapsed;
    private RGB accent;
    private RibbonColors colors;
    private final KeyTips keyTips = new KeyTips(this);
    private final List<PopupShell> popups = new ArrayList<>();
    private final Map<PopupShell, Object> popupOwners = new LinkedHashMap<>();
    private Object menuOwner;
    private int scroll;
    private int maxScroll;
    private final Set<Command> listened = new HashSet<>();
    private final CommandListener commandListener = this::commandChanged;
    private final List<Consumer<RibbonTab>> selectionListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Boolean>> collapseListeners = new CopyOnWriteArrayList<>();
    private Consumer<Command> quickAccessHook;
    private boolean keyTipsEnabled = !"cocoa".equals(SWT.getPlatform());
    private final Listener filter = this::filter;
    private final ThemeChangeListener themeListener = (previous, current) -> themeChanged();
    private boolean altAlone;
    private Control lastFocus;
    private RibbonCustomization customization;
    private Map<RibbonGroup, Size> laidOut;

    /** A ribbon using its shell's {@link CommandRegistry#of(Shell) command registry}. */
    public Ribbon(Composite parent, int style) {
        this(parent, style, CommandRegistry.of(parent.getShell()));
    }

    @SuppressWarnings("this-escape")
    public Ribbon(Composite parent, int style, CommandRegistry commands) {
        super(parent, style, THEME_TYPE);
        this.commands = Objects.requireNonNull(commands, "commands");
        Display display = getDisplay();
        for (int type : new int[] { SWT.KeyDown, SWT.KeyUp, SWT.MouseDown, SWT.FocusIn }) {
            display.addFilter(type, filter);
        }
        ThemeManager.get(display).addThemeChangeListener(themeListener);
        addListener(SWT.Dispose, e -> ribbonDisposed());
    }

    /** The commands this ribbon shows. */
    public CommandRegistry commands() {
        return commands;
    }

    @Override
    Ribbon ribbon() {
        return this;
    }

    // ------------------------------------------------------------------ tabs

    public RibbonTab addTab(String id, String label) {
        checkWidget();
        RibbonTab tab = new RibbonTab(this, id, label, null);
        tabs.add(tab);
        tabsChanged();
        return tab;
    }

    /** The regular tabs (not contextual), hidden ones included. */
    public List<RibbonTab> tabs() {
        return List.copyOf(tabs);
    }

    /** Every tab, regular and contextual, hidden ones included. */
    public List<RibbonTab> allTabs() {
        List<RibbonTab> all = new ArrayList<>(tabs);
        contextualSets.forEach(set -> all.addAll(set.tabs()));
        return all;
    }

    public RibbonTab tab(String id) {
        return allTabs().stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    /** The tabs shown now, in order: regular tabs, then the visible contextual sets'. */
    public List<RibbonTab> showingTabs() {
        List<RibbonTab> showing = new ArrayList<>();
        tabs.stream().filter(RibbonTab::isShowing).forEach(showing::add);
        contextualSets.forEach(set -> set.tabs().stream().filter(RibbonTab::isShowing).forEach(showing::add));
        return showing;
    }

    void insertTab(int index, RibbonTab tab) {
        tabs.add(Math.min(index, tabs.size()), tab);
        tabsChanged();
    }

    void removeTab(RibbonTab tab) {
        tabs.remove(tab);
        tabsChanged();
    }

    void reorderTabs(List<RibbonTab> order) {
        tabs.clear();
        tabs.addAll(order);
        tabsChanged();
    }

    RibbonTab newTab(String id, String label) {
        return new RibbonTab(this, id, label, null);
    }

    public RibbonContextualTabSet addContextualTabSet(String id, String label, int accent) {
        checkWidget();
        RibbonContextualTabSet set = new RibbonContextualTabSet(this, id, label, accent);
        contextualSets.add(set);
        return set;
    }

    public List<RibbonContextualTabSet> contextualTabSets() {
        return List.copyOf(contextualSets);
    }

    public RibbonTab getSelection() {
        return selection;
    }

    public void setSelection(RibbonTab tab) {
        checkWidget();
        if (tab == selection || (tab != null && !tab.isShowing())) {
            return;
        }
        selection = tab;
        scroll = 0;
        closePopups();
        invalidateAll();
        selectionListeners.forEach(l -> l.accept(tab));
    }

    public void addSelectionListener(Consumer<RibbonTab> listener) {
        selectionListeners.add(listener);
    }

    public void removeSelectionListener(Consumer<RibbonTab> listener) {
        selectionListeners.remove(listener);
    }

    void tabsChanged() {
        if (selection == null || !selection.isShowing()) {
            List<RibbonTab> showing = showingTabs();
            RibbonTab next = showing.isEmpty() ? null : showing.getFirst();
            if (next != selection) {
                selection = next;
                selectionListeners.forEach(l -> l.accept(next));
            }
        }
        invalidateAll();
    }

    void contextualShown(RibbonContextualTabSet set, boolean select) {
        if (select) {
            RibbonTab first = set.tabs().stream().filter(RibbonTab::isShowing).findFirst().orElse(null);
            if (first != null && first != selection) {
                if (selection == null || selection.contextualSet() == null) {
                    beforeContextual = selection;
                }
                setSelection(first);
            }
        } else if (selection == null || selection.contextualSet() == null) {
            beforeContextual = selection;
        }
        tabsChanged();
    }

    void contextualHidden(RibbonContextualTabSet set) {
        if (selection != null && selection.contextualSet() == set) {
            RibbonTab back = beforeContextual != null && beforeContextual.isShowing() ? beforeContextual : null;
            selection = null;
            if (back != null) {
                selection = back;
                selectionListeners.forEach(l -> l.accept(back));
            }
        }
        tabsChanged();
    }

    // ------------------------------------------------------------------ the tab row

    /** Shows a File tab before the tabs that opens the {@link Backstage}; {@code setup} fills it once. */
    public void setFileTab(String label, Consumer<Backstage> setup) {
        checkWidget();
        fileTabLabel = label;
        backstageSetup = setup;
        invalidate();
    }

    String fileTabLabel() {
        return fileTabLabel;
    }

    /** Adds a command at the tab row's right, like Office's Comments and Share. */
    public void addRightCommand(String commandId) {
        addRightCommand(commandId, false);
    }

    /** {@code emphasis} fills its button with the accent (Share). */
    public void addRightCommand(String commandId, boolean emphasis) {
        checkWidget();
        Command command = commands.get(commandId);
        rightCommands.add(command);
        if (emphasis) {
            emphasized.add(command);
        }
        listen(command);
        invalidate();
    }

    public List<Command> rightCommands() {
        return List.copyOf(rightCommands);
    }

    // ------------------------------------------------------------------ backstage

    /** The backstage, created and filled on first use; {@code null} without a File tab. */
    public Backstage getBackstage() {
        checkWidget();
        if (backstage == null && backstageSetup != null) {
            backstage = new Backstage(this, fileTabLabel);
            backstageSetup.accept(backstage);
        }
        return backstage;
    }

    /** Opens the backstage, as clicking the File tab does. */
    public void openBackstage() {
        Backstage page = getBackstage();
        if (page != null) {
            closePopups();
            keyTips.hide();
            page.open();
        }
    }

    // ------------------------------------------------------------------ collapse

    public boolean isCollapsed() {
        return collapsed;
    }

    /** Collapses the ribbon to its tab row, or expands it again. */
    public void setCollapsed(boolean value) {
        checkWidget();
        if (value == collapsed) {
            return;
        }
        collapsed = value;
        closePopups();
        invalidate();
        requestLayout();
        Composite parent = getParent();
        parent.layout(true, true);
        collapseListeners.forEach(l -> l.accept(value));
        if (customization != null) {
            customization.collapsedChanged();
        }
    }

    public void addCollapseListener(Consumer<Boolean> listener) {
        collapseListeners.add(listener);
    }

    /** Whether the collapsed ribbon's selected tab shows as an overlay now. */
    public boolean isOverlayOpen() {
        return overlay() != null;
    }

    GroupPopup overlay() {
        for (PopupShell popup : popups) {
            if (popup instanceof GroupPopup group && group.tab() != null && popup.isOpen()) {
                return group;
            }
        }
        return null;
    }

    void openOverlay(RibbonTab tab) {
        Rectangle area = getClientArea();
        RibbonMetrics m = measure(x -> x);
        Rectangle anchor = toDisplay(new Rectangle(0, 0, area.width, m.tabRowHeight));
        openPopup(new GroupPopup(this, tab, anchor, area.width), tab);
    }

    // ------------------------------------------------------------------ theming

    /**
     * The app's brand color (Word blue, Excel green): it replaces the theme's accent in the File
     * tab, the selected tab, checked items and the backstage, unless the theme sets
     * {@code useAppAccent: false}. {@code null} uses the theme's.
     */
    public void setAccentColor(RGB rgb) {
        checkWidget();
        accent = rgb;
        colors = null;
        repaintAll();
        if (backstage != null) {
            backstage.redrawAll();
        }
    }

    public RGB getAccentColor() {
        return accent;
    }

    @Override
    RibbonColors colors() {
        if (colors == null) {
            colors = new RibbonColors(this, accent);
        }
        return colors;
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
        colors = null;
        if (popups != null) {
            // Not while the superclass constructor themes the ribbon: the fields are not set yet.
            invalidateAll();
        }
    }

    private void themeChanged() {
        if (isDisposed()) {
            return;
        }
        colors = null;
        invalidateAll();
        requestLayout();
    }

    // ------------------------------------------------------------------ KeyTips and hooks

    KeyTips keyTips() {
        return keyTips;
    }

    /** Whether Alt and F10 show KeyTips (default: yes, except on macOS). */
    public void setKeyTipsEnabled(boolean enabled) {
        keyTipsEnabled = enabled;
        if (!enabled) {
            keyTips.hide();
        }
    }

    public boolean isKeyTipsEnabled() {
        return keyTipsEnabled;
    }

    /** Shows the tab row's KeyTips, as Alt does; the backstage's while it is open. */
    public void showKeyTips() {
        checkWidget();
        if (isBackstageOpen()) {
            backstage.showKeyTips();
            return;
        }
        keyTips.show();
    }

    public void hideKeyTips() {
        keyTips.hide();
        if (backstage != null) {
            backstage.hideKeyTips();
        }
    }

    public boolean isShowingKeyTips() {
        return keyTips.isShowing() || backstage != null && backstage.isShowingKeyTips();
    }

    /** Types KeyTip letters while KeyTips show, as the keyboard does (for tests and demos). */
    public void typeKeyTips(String letters) {
        checkWidget();
        if (isBackstageOpen()) {
            backstage.typeKeyTips(letters);
            return;
        }
        for (int i = 0; i < letters.length(); i++) {
            keyTips.type(letters.substring(i, i + 1));
            if (isBackstageOpen()) {
                // The File tab's KeyTip opened the backstage: the rest are its KeyTips.
                backstage.typeKeyTips(letters.substring(i + 1));
                return;
            }
        }
    }

    /** The KeyTips shown now, by the label of what they reach (for tests and demos). */
    public Map<String, String> visibleKeyTips() {
        checkWidget();
        if (isBackstageOpen()) {
            return backstage.visibleKeyTips();
        }
        Map<String, String> tips = new LinkedHashMap<>();
        for (KeyTips.Entry entry : keyTips.visible()) {
            tips.put(entry.surface().nameOf(entry.hot()), entry.tip());
        }
        return tips;
    }

    private boolean isBackstageOpen() {
        return backstage != null && backstage.isOpen();
    }

    /** The size each group of the selected tab is drawn at now, by group id. */
    public Map<String, Size> groupSizes() {
        checkWidget();
        hots();
        Map<String, Size> sizes = new LinkedHashMap<>();
        if (!collapsed && laidOut != null) {
            laidOut.forEach((group, size) -> sizes.put(group.id(), size));
        }
        return sizes;
    }

    /**
     * Called with a command when a user picks <em>Add to Quick Access Toolbar</em> on an item's
     * context menu; the title bar's quick access toolbar sets it.
     */
    public void setQuickAccessHook(Consumer<Command> hook) {
        quickAccessHook = hook;
    }

    // ------------------------------------------------------------------ customization

    /**
     * Saves users' customizations in {@code store} (its {@code "ribbon"} section) and applies the
     * ones it holds. Call after the app has built the ribbon.
     */
    public void setCustomizationStore(CustomizationStore store) {
        checkWidget();
        customization().setStore(store);
    }

    /** The customization model: the app's defaults, the user's changes, reset. */
    public RibbonCustomization customization() {
        if (customization == null) {
            customization = new RibbonCustomization(this);
        }
        return customization;
    }

    /** Opens the Customize Ribbon dialog. */
    public void openCustomizeDialog() {
        new CustomizeRibbonDialog(this).open();
    }

    // ------------------------------------------------------------------ model changes

    void itemAttached(RibbonItem item) {
        if (item.command() != null) {
            listen(item.command());
        }
    }

    private void listen(Command command) {
        if (listened.add(command)) {
            command.addListener(commandListener);
        }
    }

    private void commandChanged(Command command, String property) {
        if (isDisposed()) {
            return;
        }
        switch (property) {
            case Command.LABEL, Command.IMAGES, Command.VISIBLE, Command.STYLE -> invalidateAll();
            default -> repaintAll();
        }
    }

    void modelChanged() {
        invalidateAll();
    }

    /** Lays out again: this ribbon and its open popups. */
    void invalidateAll() {
        if (isDisposed()) {
            return;
        }
        invalidate();
        for (PopupShell popup : popups) {
            if (popup instanceof GroupPopup group && group.surface() != null && !group.surface().isDisposed()) {
                group.surface().invalidate();
            }
        }
    }

    void repaintAll() {
        if (isDisposed()) {
            return;
        }
        redraw();
        for (PopupShell popup : popups) {
            if (popup.isOpen()) {
                for (Control child : popup.shell.getChildren()) {
                    child.redraw();
                }
            }
        }
    }

    // ------------------------------------------------------------------ popups

    void openPopup(PopupShell popup, Object owner) {
        int keep = -1;
        for (int i = 0; i < popups.size(); i++) {
            if (popups.get(i).contains(popup.origin)) {
                keep = i;
            }
        }
        closePopupsAfter(keep);
        toolTip().hide();
        popups.add(popup);
        popupOwners.put(popup, owner);
        popup.open();
        repaintAll();
    }

    void popupClosed(PopupShell popup) {
        int index = popups.indexOf(popup);
        if (index < 0) {
            popup.dispose();
            return;
        }
        closePopupsAfter(index - 1);
    }

    private void closePopupsAfter(int index) {
        boolean changed = false;
        while (popups.size() > index + 1) {
            PopupShell last = popups.removeLast();
            popupOwners.remove(last);
            last.dispose();
            changed = true;
        }
        if (changed && !isDisposed()) {
            repaintAll();
        }
    }

    /** Closes every popup (menus close themselves). */
    public void closePopups() {
        if (!popups.isEmpty()) {
            closePopupsAfter(-1);
        }
    }

    /** The open popups, innermost last; for tests. */
    List<PopupShell> popups() {
        return List.copyOf(popups);
    }

    boolean isPopupShell(Shell shell) {
        for (PopupShell popup : popups) {
            if (popup.shell == shell) {
                return true;
            }
        }
        return false;
    }

    boolean hasOpenPopup(RibbonSurface surface) {
        return popups.stream().anyMatch(p -> !p.contains(surface)) || menuOwner != null;
    }

    boolean isOpenFor(Object owner) {
        return owner != null && (popupOwners.containsValue(owner) || menuOwner == owner);
    }

    boolean isGroupPopupOpen(RibbonGroup group) {
        return popupOwners.containsValue(group);
    }

    void menuOpened(Object owner, org.eclipse.swt.widgets.Menu menu) {
        menuOwner = owner;
        repaintAll();
    }

    void menuClosed(org.eclipse.swt.widgets.Menu menu) {
        menuOwner = null;
        repaintAll();
    }

    void showContextMenu(RibbonSurface surface, Hot hot, int x, int y) {
        Menu menu = new Menu(getShell(), SWT.POP_UP);
        Command command = hot == null ? null : surface.commandOf(hot);
        if (quickAccessHook != null && command != null) {
            MenuItem add = new MenuItem(menu, SWT.PUSH);
            add.setText("&Add to Quick Access Toolbar");
            add.addListener(SWT.Selection, e -> quickAccessHook.accept(command));
            new MenuItem(menu, SWT.SEPARATOR);
        }
        MenuItem customize = new MenuItem(menu, SWT.PUSH);
        customize.setText("Customize the &Ribbon...");
        customize.addListener(SWT.Selection, e -> openCustomizeDialog());
        MenuItem collapse = new MenuItem(menu, SWT.CHECK);
        collapse.setText("&Collapse the Ribbon");
        collapse.setSelection(collapsed);
        collapse.addListener(SWT.Selection, e -> setCollapsed(!collapsed));
        menu.setLocation(x, y);
        menu.addListener(SWT.Hide, e -> getDisplay().asyncExec(() -> {
            if (!menu.isDisposed()) {
                menu.dispose();
            }
        }));
        menu.setVisible(true);
    }

    // ------------------------------------------------------------------ size and layout

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        return measure(m -> {
            int height = m.tabRowHeight + (collapsed ? 0 : m.panelHeight) + 1;
            int width = wHint;
            if (width == SWT.DEFAULT) {
                width = m.px(8) + m.collapseButton;
                if (selection != null) {
                    for (RibbonGroup group : selection.groups()) {
                        width += RibbonLayout.width(group, group.maxSize(), m) + m.groupGap;
                    }
                }
                width = Math.max(width, tabRowWidth(m));
            }
            return new Point(width, hHint == SWT.DEFAULT ? height : hHint);
        });
    }

    private int tabRowWidth(RibbonMetrics m) {
        int width = m.px(8);
        if (fileTabLabel != null) {
            width += m.tabText(fileTabLabel) + m.px(22);
        }
        for (RibbonTab tab : showingTabs()) {
            width += m.tabText(tab.label()) + m.px(22);
        }
        for (Command command : rightCommands) {
            width += rightWidth(m, command) + m.px(6);
        }
        return width;
    }

    private int tabsWidth(RibbonMetrics m, List<RibbonTab> showing, int padding) {
        int width = m.px(4);
        if (fileTabLabel != null) {
            width += m.tabText(fileTabLabel) + padding + m.px(2);
        }
        for (RibbonTab tab : showing) {
            width += m.tabText(tab.label()) + padding + m.px(2);
        }
        return width;
    }

    private int rightTotal(RibbonMetrics m, List<Command> commands, boolean compact) {
        int width = 0;
        for (Command command : commands) {
            width += rightWidth(m, command, compact) + m.px(6);
        }
        return width;
    }

    private int rightWidth(RibbonMetrics m, Command command) {
        return rightWidth(m, command, false);
    }

    /** A right command's width; {@code compact} drops the label when it has an image. */
    private int rightWidth(RibbonMetrics m, Command command, boolean compact) {
        boolean image = command.images().image(m.smallIcon) != null;
        if (compact && image) {
            return m.px(6) + m.smallIcon + m.px(6) + (command.style() == Command.Style.DROP_DOWN ? m.arrow + m.px(2) : 0);
        }
        int width = m.px(8) + (image ? m.smallIcon + m.px(4) : 0) + m.text(command.label()) + m.px(8);
        if (command.style() == Command.Style.DROP_DOWN) {
            width += m.arrow + m.px(4);
        }
        return width;
    }

    /** The panel's area (below the tab row); empty when collapsed. */
    Rectangle panelArea(RibbonMetrics m) {
        Rectangle area = getClientArea();
        if (collapsed) {
            return new Rectangle(0, m.tabRowHeight, area.width, 0);
        }
        return new Rectangle(0, m.tabRowHeight, area.width, m.panelHeight);
    }

    @Override
    List<Hot> computeHots(RibbonMetrics m) {
        List<Hot> hots = new ArrayList<>();
        Rectangle area = getClientArea();
        int tabHeight = m.tabRowHeight;
        List<RibbonTab> showing = showingTabs();
        // The tab row compacts when it runs out of room: right commands lose their labels, then
        // tabs their padding, then the tabs that still do not fit are left out (the selected one stays).
        List<Command> visibleRight = rightCommands.stream().filter(Command::isVisible).toList();
        boolean compactRight = false;
        int padding = m.px(20);
        int rightTotal = rightTotal(m, visibleRight, false);
        int tabsTotal = tabsWidth(m, showing, padding);
        int room = area.width - m.px(10);
        if (tabsTotal + rightTotal > room) {
            compactRight = true;
            rightTotal = rightTotal(m, visibleRight, true);
        }
        if (tabsTotal + rightTotal > room) {
            padding = m.px(8);
        }
        int x = m.px(4);
        int limit = area.width - rightTotal - m.px(6);
        if (fileTabLabel != null) {
            int width = m.tabText(fileTabLabel) + padding;
            hots.add(new Hot(Hot.Kind.FILE_TAB, this, new Rectangle(x, m.px(2), width, tabHeight - m.px(2))));
            x += width + m.px(2);
        }
        for (RibbonTab tab : showing) {
            int width = m.tabText(tab.label()) + padding;
            if (x + width > limit && tab != selection) {
                continue;
            }
            hots.add(new Hot(Hot.Kind.TAB, tab, new Rectangle(x, m.px(2), width, tabHeight - m.px(2))));
            x += width + m.px(2);
        }
        int right = area.width - m.px(6);
        List<Hot> rights = new ArrayList<>();
        for (int i = visibleRight.size() - 1; i >= 0; i--) {
            Command command = visibleRight.get(i);
            int width = rightWidth(m, command, compactRight);
            rights.addFirst(new Hot(Hot.Kind.RIGHT, command, null, new Rectangle(right - width, m.px(4), width, tabHeight - m.px(7)), null,
                    compactRight ? 1 : 0));
            right -= width + m.px(6);
        }
        hots.addAll(rights);
        if (!collapsed && selection != null) {
            int top = m.tabRowHeight;
            int available = area.width - m.px(12) - m.collapseButton;
            Map<RibbonGroup, Size> sizes = RibbonLayout.reduce(selection, available, m);
            laidOut = sizes;
            RibbonLayout.PanelLayout panel = RibbonLayout.layoutPanel(selection.groups(), sizes, m, m.px(4), top, available);
            int arrow = m.px(14);
            maxScroll = panel.overflow() ? panel.width() - available + 2 * arrow : 0;
            scroll = Math.max(0, Math.min(scroll, maxScroll));
            int dx = panel.overflow() ? -scroll + arrow : 0;
            addPanelHots(m, panel, dx, hots);
            if (scroll > 0) {
                hots.add(new Hot(Hot.Kind.SCROLL_LEFT, this, new Rectangle(0, top, arrow, m.panelHeight - 1)));
            }
            if (scroll < maxScroll) {
                hots.add(new Hot(Hot.Kind.SCROLL_RIGHT, this, new Rectangle(area.width - arrow - m.collapseButton - m.px(8), top, arrow,
                        m.panelHeight - 1)));
            }
            int labelTop = top + m.contentTop + m.contentHeight + m.px(2);
            hots.add(new Hot(Hot.Kind.COLLAPSE, this, new Rectangle(area.width - m.collapseButton - m.px(4), labelTop, m.collapseButton,
                    m.groupLabelHeight - m.px(1))));
        } else {
            maxScroll = 0;
        }
        return hots;
    }

    @Override
    void paintSurface(GC gc, RibbonMetrics m, List<Hot> hots) {
        RibbonColors colors = colors();
        Rectangle area = getClientArea();
        gc.setBackground(colors.tabStrip);
        gc.fillRectangle(0, 0, area.width, m.tabRowHeight);
        Rectangle panel = panelArea(m);
        gc.setBackground(colors.background);
        gc.fillRectangle(panel.x, panel.y, panel.width, area.height - panel.y);
        gc.setForeground(colors.border);
        gc.drawLine(0, area.height - 1, area.width, area.height - 1);
        for (Hot hot : hots) {
            switch (hot.kind()) {
                case FILE_TAB -> paintFileTab(gc, m, hot);
                case TAB -> paintTab(gc, m, hot);
                case RIGHT -> paintRight(gc, m, hot);
                default -> {
                }
            }
        }
        if (!collapsed) {
            Rectangle clip = new Rectangle(0, panel.y, area.width - m.collapseButton - m.px(8), panel.height);
            paintPanel(gc, m, hots, clip);
            for (Hot hot : hots) {
                switch (hot.kind()) {
                    case COLLAPSE -> paintChevron(gc, m, hot, true);
                    case SCROLL_LEFT, SCROLL_RIGHT -> paintScroll(gc, m, hot);
                    default -> {
                    }
                }
            }
        }
    }

    /** The File tab: a tab like the others, which opens the backstage instead of a panel. */
    private void paintFileTab(GC gc, RibbonMetrics m, Hot hot) {
        RibbonColors colors = colors();
        Rectangle b = hot.bounds();
        gc.setFont(m.tabFont);
        String label = Command.stripMnemonic(fileTabLabel);
        Point extent = gc.textExtent(label);
        gc.setForeground(colors.tabForeground);
        int textX = b.x + (b.width - extent.x) / 2;
        gc.drawText(label, textX, b.y + (b.height - extent.y) / 2, SWT.DRAW_TRANSPARENT);
        if (isHovered(hot)) {
            paintTabBar(gc, m, b, textX, extent.x, colors.tabHoverIndicator);
        }
        gc.setFont(m.font);
    }

    /** The bar under a tab's label: the accent for the selected tab, a light line for one under the mouse. */
    private static void paintTabBar(GC gc, RibbonMetrics m, Rectangle b, int x, int width, Color color) {
        gc.setBackground(color);
        int bar = Math.max(2, m.px(3));
        gc.fillRoundRectangle(x, b.y + b.height - bar - m.px(1), width, bar, bar, bar);
    }

    private void paintTab(GC gc, RibbonMetrics m, Hot hot) {
        RibbonColors colors = colors();
        RibbonTab tab = (RibbonTab) hot.target();
        Rectangle b = hot.bounds();
        boolean selected = tab == selection && (!collapsed || isOverlayOpen());
        Color contextual = tab.contextualSet() == null ? null : colors.contextual[tab.contextualSet().accent() - 1];
        if (selected && !colors.selectedTab.equals(colors.tabStrip)) {
            gc.setBackground(colors.selectedTab);
            gc.fillRoundRectangle(b.x, b.y, b.width, b.height + colors.arc, colors.arc, colors.arc);
        }
        if (contextual != null) {
            gc.setBackground(contextual);
            gc.fillRectangle(b.x, b.y - m.px(2), b.width, m.px(3));
        }
        gc.setFont(m.tabFont);
        String label = Command.stripMnemonic(tab.label());
        Point extent = gc.textExtent(label);
        Color foreground = contextual != null ? contextual : selected ? colors.selectedTabForeground : colors.tabForeground;
        gc.setForeground(foreground);
        int textX = b.x + (b.width - extent.x) / 2;
        int textY = b.y + (b.height - extent.y) / 2;
        gc.drawText(label, textX, textY, SWT.DRAW_TRANSPARENT);
        if (selected) {
            paintTabBar(gc, m, b, textX, extent.x, contextual != null ? contextual : colors.tabIndicator);
        } else if (isHovered(hot)) {
            paintTabBar(gc, m, b, textX, extent.x, colors.tabHoverIndicator);
        }
        gc.setFont(m.font);
    }

    private void paintRight(GC gc, RibbonMetrics m, Hot hot) {
        RibbonColors colors = colors();
        Command command = (Command) hot.target();
        Rectangle b = hot.bounds();
        boolean emphasis = emphasized.contains(command);
        boolean enabled = command.isEnabled();
        Color fill = emphasis ? colors.emphasis : colors.tabStrip;
        if (enabled && (isHovered(hot) || isOpenFor(command))) {
            fill = emphasis ? RibbonColors.mix(colors.emphasis, colors.emphasisForeground, 0.15) : colors.tabHover;
        }
        gc.setBackground(fill);
        gc.fillRoundRectangle(b.x, b.y, b.width, b.height, colors.arc, colors.arc);
        gc.setForeground(emphasis ? colors.emphasis : colors.border);
        gc.drawRoundRectangle(b.x, b.y, b.width - 1, b.height - 1, colors.arc, colors.arc);
        Color foreground = !enabled ? colors.disabled : emphasis ? colors.emphasisForeground : colors.tabForeground;
        boolean compact = hot.index() == 1;
        int x = b.x + (compact ? m.px(6) : m.px(8));
        if (command.images().image(m.smallIcon) != null) {
            drawImage(gc, command.images(), m.smallIcon, x, b.y + (b.height - m.smallIcon) / 2, enabled);
            x += m.smallIcon + (compact ? m.px(2) : m.px(4));
        }
        gc.setForeground(foreground);
        gc.setFont(m.font);
        if (!compact) {
            gc.drawText(command.plainLabel(), x, b.y + (b.height - m.fontHeight) / 2, SWT.DRAW_TRANSPARENT);
            x += m.text(command.label()) + m.px(4);
        }
        if (command.style() == Command.Style.DROP_DOWN) {
            drawArrow(gc, x, b.y + (b.height - m.arrow / 2) / 2, m.arrow, foreground);
        }
    }

    private void paintChevron(GC gc, RibbonMetrics m, Hot hot, boolean up) {
        RibbonColors colors = colors();
        Rectangle b = hot.bounds();
        if (isHovered(hot)) {
            gc.setBackground(isPressed(hot) ? colors.pressed : colors.hover);
            gc.fillRoundRectangle(b.x, b.y, b.width, b.height, colors.arc, colors.arc);
        }
        gc.setForeground(colors.foreground);
        int w = m.arrow + m.px(2);
        int half = w / 2;
        int x = b.x + (b.width - w) / 2;
        int y = b.y + (b.height - half) / 2;
        gc.drawPolyline(up ? new int[] { x, y + half, x + half, y, x + 2 * half, y + half }
                : new int[] { x, y, x + half, y + half, x + 2 * half, y });
    }

    private void paintScroll(GC gc, RibbonMetrics m, Hot hot) {
        RibbonColors colors = colors();
        Rectangle b = hot.bounds();
        gc.setBackground(isHovered(hot) ? colors.hover : colors.background);
        gc.fillRectangle(b);
        gc.setForeground(colors.border);
        gc.drawRectangle(b.x, b.y, b.width - 1, b.height - 1);
        gc.setForeground(colors.foreground);
        int half = m.arrow / 2;
        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;
        if (hot.kind() == Hot.Kind.SCROLL_LEFT) {
            gc.drawPolyline(new int[] { cx + half / 2, cy - half, cx - half / 2, cy, cx + half / 2, cy + half });
        } else {
            gc.drawPolyline(new int[] { cx - half / 2, cy - half, cx + half / 2, cy, cx - half / 2, cy + half });
        }
    }

    // ------------------------------------------------------------------ activation and keyboard

    @Override
    void activateOther(Hot hot, Event event) {
        switch (hot.kind()) {
            case FILE_TAB -> openBackstage();
            case TAB -> {
                RibbonTab tab = (RibbonTab) hot.target();
                if (collapsed) {
                    boolean wasOpen = isOverlayOpen() && tab == selection;
                    closePopups();
                    setSelection(tab);
                    if (!wasOpen) {
                        openOverlay(tab);
                    }
                    redraw();
                } else {
                    setSelection(tab);
                }
            }
            case RIGHT -> {
                Command command = (Command) hot.target();
                if (command.style() == Command.Style.DROP_DOWN) {
                    openMenu(command, command, hot.bounds());
                } else {
                    run(command, event, false, null);
                }
            }
            case COLLAPSE -> setCollapsed(true);
            case SCROLL_LEFT -> {
                scroll -= measure(m -> m.px(160));
                invalidate();
            }
            case SCROLL_RIGHT -> {
                scroll += measure(m -> m.px(160));
                invalidate();
            }
            default -> {
            }
        }
    }

    @Override
    void tabDoubleClicked(Hot hot) {
        setCollapsed(!collapsed);
    }

    @Override
    void focusChanged(Hot hot) {
        if (hot != null && hot.kind() == Hot.Kind.TAB && !collapsed) {
            setSelection((RibbonTab) hot.target());
        }
    }

    @Override
    Hot initialFocus(List<Hot> hots) {
        for (Hot hot : hots) {
            if (hot.kind() == Hot.Kind.TAB && hot.target() == selection) {
                return hot;
            }
        }
        return hots.stream().filter(Hot::focusable).findFirst().orElse(null);
    }

    private Hot selectedTabHot() {
        for (Hot hot : hots()) {
            if (hot.kind() == Hot.Kind.TAB && hot.target() == selection) {
                return hot;
            }
        }
        return null;
    }

    @Override
    void arrowDown(Event event) {
        Hot focus = focusHot();
        if (focus != null && focus.inTabRow()) {
            if (collapsed && focus.kind() == Hot.Kind.TAB) {
                openOverlay((RibbonTab) focus.target());
                return;
            }
            hots().stream().filter(h -> h.focusable() && !h.inTabRow()).findFirst().ifPresent(this::setFocusHot);
            return;
        }
        super.arrowDown(event);
    }

    @Override
    void arrowUp() {
        Hot focus = focusHot();
        if (focus != null && !focus.inTabRow()) {
            Hot first = hots().stream().filter(h -> h.focusable() && !h.inTabRow()).findFirst().orElse(null);
            if (focus.sameAs(first)) {
                Hot tab = selectedTabHot();
                if (tab != null) {
                    setFocusHot(tab);
                    return;
                }
            }
        }
        super.arrowUp();
    }

    @Override
    void escape() {
        Hot focus = focusHot();
        if (focus != null && !focus.inTabRow()) {
            Hot tab = selectedTabHot();
            if (tab != null) {
                setFocusHot(tab);
                return;
            }
        }
        leave();
    }

    /** Gives the focus back to the control that had it before the ribbon. */
    void leave() {
        keyTips.hide();
        if (lastFocus != null && !lastFocus.isDisposed() && lastFocus.isVisible()) {
            lastFocus.setFocus();
        } else {
            getShell().traverse(SWT.TRAVERSE_TAB_NEXT);
        }
    }

    Control lastFocus() {
        return lastFocus;
    }

    void filter(Event event) {
        if (isDisposed() || !(event.widget instanceof Control control) || control.isDisposed()) {
            return;
        }
        Shell shell = control.getShell();
        if (shell != getShell() && !isPopupShell(shell)) {
            return;
        }
        switch (event.type) {
            case SWT.KeyDown -> {
                altAlone = event.keyCode == SWT.ALT && (event.stateMask & SWT.MODIFIER_MASK) == 0;
                if (event.keyCode == SWT.F1 && (event.stateMask & SWT.MODIFIER_MASK) == SWT.CTRL) {
                    setCollapsed(!collapsed);
                    event.doit = false;
                } else if (event.keyCode == SWT.F10 && (event.stateMask & SWT.MODIFIER_MASK) == 0 && keyTipsEnabled && (isVisible() || isBackstageOpen())) {
                    toggleKeyTips();
                    event.doit = false;
                }
            }
            case SWT.KeyUp -> {
                if (event.keyCode == SWT.ALT && altAlone && keyTipsEnabled && (isVisible() || isBackstageOpen())) {
                    altAlone = false;
                    toggleKeyTips();
                    event.doit = false;
                }
            }
            case SWT.MouseDown -> altAlone = false;
            case SWT.FocusIn -> {
                if (shell == getShell() && !(control instanceof RibbonSurface) && !isInBackstage(control)) {
                    lastFocus = control;
                }
            }
            default -> {
            }
        }
    }

    private boolean isInBackstage(Control control) {
        for (Control c = control; c != null; c = c.getParent()) {
            if (c instanceof Backstage) {
                return true;
            }
        }
        return false;
    }

    private void toggleKeyTips() {
        if (isBackstageOpen()) {
            backstage.toggleKeyTips();
            return;
        }
        if (keyTips.isShowing()) {
            keyTips.hide();
            leave();
        } else {
            keyTips.show();
        }
    }

    private void ribbonDisposed() {
        Display display = getDisplay();
        for (int type : new int[] { SWT.KeyDown, SWT.KeyUp, SWT.MouseDown, SWT.FocusIn }) {
            display.removeFilter(type, filter);
        }
        ThemeManager.get(display).removeThemeChangeListener(themeListener);
        listened.forEach(c -> c.removeListener(commandListener));
        listened.clear();
        closePopups();
    }

    /** An image for {@code size} pixels of {@code command}, for tests and the dialog. */
    static Image image(Command command, int size) {
        return command.images().image(size);
    }
}
