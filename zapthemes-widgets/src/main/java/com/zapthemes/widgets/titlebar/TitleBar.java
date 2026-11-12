package com.zapthemes.widgets.titlebar;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;
import com.zapthemes.swt.widgets.Composite;
import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;
import com.zapthemes.swt.widgets.Text;

/**
 * An Office-style title bar for a {@link Shell}: from left to right the app icon with the system
 * menu, the quick access toolbar ({@link TitleBarItem}s and the customize button), the document
 * title with its menu, a centered search box, slots for app controls, the account avatar with a
 * profile pop-up, and the minimize, maximize and close buttons.
 *
 * <p>Create the shell with {@link #shellStyle(int)}, make the bar its first, direct child and lay it
 * out across the top. On Windows and macOS the bar replaces the native caption and keeps the native
 * frame (snap, shadow, traffic lights); elsewhere the shell has no trim and the bar draws the frame
 * ({@link #getFrameMode()}).
 *
 * <p>Theme type {@code TitleBar}: {@code background}, {@code foreground}, {@code inactiveBackground},
 * {@code inactiveForeground}, {@code font}, {@code titleFont}, {@code hoverBackground},
 * {@code pressedBackground}, {@code closeHoverBackground}, {@code closeHoverForeground},
 * {@code closePressedBackground}, {@code searchBackground}, {@code searchForeground},
 * {@code searchBorderColor}, {@code avatarBackground}, {@code avatarForeground}, {@code focusColor}
 * and {@code useAccent} (whether {@link #setAccent(RGB)} applies in this theme).
 *
 * <p>Events: {@link SWT#Modify} when the search text changes and {@link SWT#DefaultSelection} when
 * Enter is pressed in it ({@code event.text} is the search text).
 */
public class TitleBar extends Canvas {

    /** Runs instead of the built-in customize dialog when set: see {@code setCustomizeHandler}. */
    private Runnable customizeHandler;

    /** What is at a point of the bar ({@link #getPart(int, int)}). */
    public enum Part {
        /** Outside the bar. */
        NONE,
        /** Empty bar space: drags the window, double-click maximizes. */
        CAPTION,
        SYSTEM_MENU, ITEM, CUSTOMIZE, TITLE, SEARCH, SLOT, AVATAR, MINIMIZE, MAXIMIZE, CLOSE,
        /** The top edge, which resizes the window. */
        RESIZE_TOP, RESIZE_TOP_LEFT, RESIZE_TOP_RIGHT,
        /** Empty space of the quick access toolbar's own row, when it is shown below the bar. */
        TOOLBAR
    }

    /** How the bar and the window frame get along on this platform. */
    public enum FrameMode {
        /** The native frame, with the bar in place of its caption (Windows, macOS). */
        NATIVE,
        /** A {@code NO_TRIM} shell whose frame the bar draws and handles. */
        DRAWN,
        /** The native caption stays above the bar, which hides its icon and window buttons. */
        EMBEDDED
    }

    record Hit(Part part, Rectangle bounds, TitleBarItem item) {
    }

    static final String TYPE = "TitleBar";
    private static final String MINIMIZE_TEXT = "Minimize";
    private static final String MAXIMIZE_TEXT = "Maximize";
    private static final String RESTORE_TEXT = "Restore Down";
    private static final String CLOSE_TEXT = "Close";
    private static final String CUSTOMIZE_TEXT = "Customize Quick Access Toolbar";
    static final String MORE_COMMANDS_TEXT = "&More Commands...";
    static final String SHOW_BELOW_TEXT = "Show &Below the Title Bar";
    static final String SHOW_ABOVE_TEXT = "Show &Above the Title Bar";

    private final Shell shell;
    private final WindowFrame frame;
    private final List<TitleBarItem> items = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Composite slot;
    private final Text search;
    private final Listener shellListener = this::shellEvent;
    private final Listener altSpace = this::altSpace;
    private final Listener focusKeyFilter = this::focusKey;
    private final ThemeChangeListener themeListener = (previous, current) -> themeChanged();

    private RGB accent;
    private String title = "";
    private boolean titleSet;
    private org.eclipse.swt.widgets.Menu titleMenu;
    private String searchHint;
    private boolean searchExpanded;
    private boolean searchCollapsed;
    private TitleBarProfile profile;
    private Shell profilePopup;
    private boolean customizable = true;
    private boolean resizable = true;
    private boolean active;
    private int rowHeight;
    private Font titleFont;
    private TitleBarColors activeColors;
    private TitleBarColors inactiveColors;
    private Hit hover;
    private Hit pressed;
    /** Whether {@link #hover} and {@link #pressed} came from the native frame (see {@link #setFrameState}). */
    private boolean frameHover;
    private boolean framePressed;
    private int focusIndex = -1;
    private Menu customizeMenu;
    private Menu systemMenu;
    private boolean quickAccessBelow;
    private java.nio.file.Path preferences;
    private com.zapthemes.widgets.command.CustomizationStore store;
    private boolean loadingPreferences;
    private TitleBarCustomization defaults;
    private int focusKey = SWT.F6;
    private boolean focusRequested;
    private boolean keyboardFocus;
    private Control focusReturn;

    @SuppressWarnings("this-escape")
    public TitleBar(Shell parent, int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        this.shell = parent;
        slot = new Composite(this, SWT.NONE);
        RowLayout slotLayout = new RowLayout(SWT.HORIZONTAL);
        slotLayout.center = true;
        slotLayout.marginTop = slotLayout.marginBottom = 0;
        slotLayout.marginLeft = slotLayout.marginRight = 0;
        slotLayout.spacing = 6;
        slot.setLayout(slotLayout);
        search = new Text(this, SWT.SINGLE);
        search.setVisible(false);
        ThemeManager.applyOnCreate(slot, TitleBarStylers.SLOT_TYPE);
        ThemeManager.applyOnCreate(search, TitleBarStylers.SEARCH_TYPE);
        super.setLayout(new BarLayout());

        frame = WindowFrame.create(this);
        active = getDisplay().getActiveShell() == shell;

        addListener(SWT.Paint, this::paint);
        addListener(SWT.MouseMove, this::mouseMove);
        // Moving onto a part the native frame tracks (maximize on Windows) leaves the client area
        // too: that exit must not undo the frame's hover.
        addListener(SWT.MouseExit, e -> {
            if (!frameHover) {
                setHover(null);
            }
        });
        addListener(SWT.MouseDown, this::mouseDown);
        addListener(SWT.MouseUp, this::mouseUp);
        addListener(SWT.MouseDoubleClick, this::mouseDoubleClick);
        addListener(SWT.MenuDetect, this::menuDetect);
        addListener(SWT.KeyDown, this::keyDown);
        addListener(SWT.Traverse, this::traverse);
        addListener(SWT.FocusIn, e -> focusIn());
        addListener(SWT.FocusOut, e -> {
            keyboardFocus = false;
            redraw();
        });
        addListener(SWT.Dispose, e -> disposed());
        search.addListener(SWT.DefaultSelection, e -> searchSubmitted());
        search.addListener(SWT.Modify, e -> {
            Event event = new Event();
            event.text = search.getText();
            notifyListeners(SWT.Modify, event);
        });
        search.addListener(SWT.FocusOut, e -> searchFocusOut());
        search.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
                search.setText("");
                e.doit = false;
                returnFocus();
            }
        });
        for (int type : new int[] { SWT.Activate, SWT.Deactivate, SWT.Resize }) {
            shell.addListener(type, shellListener);
        }
        if (frame.mode() == FrameMode.DRAWN) {
            getDisplay().addFilter(SWT.KeyDown, altSpace);
        }
        getDisplay().addFilter(SWT.KeyDown, focusKeyFilter);
        ThemeManager manager = ThemeManager.get(getDisplay());
        manager.addThemeChangeListener(this, themeListener);
        initAccessible();
        ThemeManager.applyOnCreate(this, TYPE);
    }

    /**
     * The style to create the bar's shell with on this platform: {@code style} itself where the bar
     * replaces the native caption (Windows, macOS), else {@link SWT#NO_TRIM} with {@code style}'s
     * modality and {@link SWT#ON_TOP}, since the bar draws the frame.
     */
    public static int shellStyle(int style) {
        if (WindowFrame.nativeSupported()) {
            return style;
        }
        int kept = SWT.APPLICATION_MODAL | SWT.PRIMARY_MODAL | SWT.SYSTEM_MODAL | SWT.ON_TOP | SWT.RIGHT_TO_LEFT | SWT.LEFT_TO_RIGHT;
        return SWT.NO_TRIM | (style & kept);
    }

    @Override
    public void setLayout(Layout layout) {
        // The bar lays out its own children.
        checkWidget();
    }

    public FrameMode getFrameMode() {
        checkWidget();
        return frame.mode();
    }

    WindowFrame frame() {
        return frame;
    }

    // ------------------------------------------------------------------ properties

    /** The title: what {@link #setTitle} set, else the shell's text. */
    public String getTitle() {
        checkWidget();
        if (!titleSet && !shell.isDisposed()) {
            title = shell.getText();
        }
        return title;
    }

    /** The document title; also the shell's text, which the taskbar and window switcher show. */
    public void setTitle(String title) {
        checkWidget();
        this.title = title == null ? "" : title;
        this.titleSet = true;
        shell.setText(this.title);
        relayout();
    }

    public org.eclipse.swt.widgets.Menu getTitleMenu() {
        checkWidget();
        return titleMenu;
    }

    /** A pop-up menu opened from the title (Office's document menu); {@code null} for none. */
    public void setTitleMenu(org.eclipse.swt.widgets.Menu menu) {
        checkWidget();
        this.titleMenu = menu;
        relayout();
    }

    public RGB getAccent() {
        checkWidget();
        return accent;
    }

    /**
     * The app's accent for the bar (Word blue, Excel green), or {@code null} for the theme's bar. The
     * bar's background becomes the accent and its other colors are derived from it, in themes whose
     * {@code TitleBar.useAccent} is true (the light theme; dark and high-contrast themes keep theirs).
     */
    public void setAccent(RGB accent) {
        checkWidget();
        this.accent = accent;
        themeChanged();
    }

    public String getSearchHint() {
        checkWidget();
        return searchHint;
    }

    /** Shows the search box with this hint ("Search"); {@code null} hides it. */
    public void setSearchHint(String hint) {
        checkWidget();
        this.searchHint = hint;
        relayout();
    }

    public String getSearchText() {
        checkWidget();
        return search.getText();
    }

    public void setSearchText(String text) {
        checkWidget();
        search.setText(text == null ? "" : text);
        updateSearchVisibility();
    }

    /** Puts the keyboard focus in the search box (for the app's search shortcut). */
    public void focusSearch() {
        checkWidget();
        if (searchHint == null) {
            return;
        }
        if (searchCollapsed) {
            searchExpanded = true;
            relayout();
        }
        search.setVisible(true);
        search.setFocus();
        redraw();
    }

    /** The search field, for apps that want its full API. */
    public org.eclipse.swt.widgets.Text getSearchField() {
        checkWidget();
        return search;
    }

    public TitleBarProfile getProfile() {
        checkWidget();
        return profile;
    }

    /** Who is signed in: shows the avatar; {@code null} hides it. */
    public void setProfile(TitleBarProfile profile) {
        checkWidget();
        this.profile = profile;
        relayout();
    }

    /** The composite for app controls at the right, before the avatar; it has a centered row layout. */
    public org.eclipse.swt.widgets.Composite getSlot() {
        checkWidget();
        return slot;
    }

    public boolean isCustomizable() {
        checkWidget();
        return customizable;
    }

    /** Whether the quick access toolbar has a customize button (default true). */
    public void setCustomizable(boolean customizable) {
        checkWidget();
        this.customizable = customizable;
        relayout();
    }

    public boolean isResizable() {
        checkWidget();
        return resizable;
    }

    /** Whether the drawn frame lets users resize the window (default true). */
    public void setResizable(boolean resizable) {
        checkWidget();
        this.resizable = resizable;
    }

    /** Every item, quick-access and trailing, shown or not, in order. */
    public TitleBarItem[] getItems() {
        checkWidget();
        return items.toArray(TitleBarItem[]::new);
    }

    /** The quick-access item with {@code id}, or {@code null}. */
    public TitleBarItem getItem(String id) {
        checkWidget();
        return items.stream().filter(i -> !i.isTrail() && i.getId().equals(id)).findFirst().orElse(null);
    }

    /** Quick-access items, in their order on the bar. */
    public List<TitleBarItem> getQuickAccessItems() {
        checkWidget();
        return items.stream().filter(i -> !i.isTrail()).toList();
    }

    /** Puts the quick-access items in this order (others keep theirs, after these). */
    public void setQuickAccessOrder(List<String> ids) {
        checkWidget();
        List<TitleBarItem> quick = new ArrayList<>();
        for (String id : ids) {
            TitleBarItem item = getItem(id);
            if (item != null && !quick.contains(item)) {
                quick.add(item);
            }
        }
        items.stream().filter(i -> !i.isTrail() && !quick.contains(i)).forEach(quick::add);
        List<TitleBarItem> trail = items.stream().filter(TitleBarItem::isTrail).toList();
        items.clear();
        items.addAll(quick);
        items.addAll(trail);
        relayout();
        notifyCustomized();
    }

    /** A shown quick access item for {@code command}; see {@link CommandTitleBarItem}. */
    public TitleBarItem addQuickAccess(com.zapthemes.widgets.command.Command command) {
        return addQuickAccess(command, true);
    }

    /**
     * A quick access item for {@code command}, shown or only offered in the customize menu; the
     * item's id is the command's. Add the shell's commands to {@code CommandRegistry.of(shell)} too,
     * so "More Commands..." offers them and the ribbon shares them.
     */
    public TitleBarItem addQuickAccess(com.zapthemes.widgets.command.Command command, boolean shown) {
        checkWidget();
        TitleBarItem item = CommandTitleBarItem.create(this, command).item();
        if (!shown) {
            boolean was = loadingPreferences;
            loadingPreferences = true;
            try {
                item.setShown(false);
            } finally {
                loadingPreferences = was;
            }
        }
        return item;
    }

    /**
     * Shows {@code command} on the quick access toolbar, as a user's choice (saved with the rest of
     * the customization): the target of {@code Ribbon.setQuickAccessHook(bar::addToQuickAccess)}.
     */
    public void addToQuickAccess(com.zapthemes.widgets.command.Command command) {
        checkWidget();
        TitleBarItem item = getItem(command.id());
        if (item == null) {
            addQuickAccess(command, true);
            notifyCustomized();
        } else if (!item.isShown()) {
            item.setShown(true);
        }
    }

    /**
     * The quick access item {@code id}, created hidden from the shell's {@code CommandRegistry} if
     * the bar has none; {@code null} if neither knows it.
     */
    TitleBarItem itemOrCommand(String id) {
        TitleBarItem item = getItem(id);
        if (item != null) {
            return item;
        }
        return com.zapthemes.widgets.command.CommandRegistry.of(shell).find(id).map(c -> addQuickAccess(c, false)).orElse(null);
    }

    /** The registry's commands the bar has no item for, in the registry's order. */
    List<com.zapthemes.widgets.command.Command> otherCommands() {
        return com.zapthemes.widgets.command.CommandRegistry.of(shell).all().stream()
                .filter(c -> getItem(c.id()) == null).toList();
    }

    void addItem(TitleBarItem item) {
        items.add(item);
        relayout();
    }

    void removeItem(TitleBarItem item) {
        items.remove(item);
        relayout();
    }

    void itemChanged(TitleBarItem item, boolean resize) {
        if (resize) {
            relayout();
        } else {
            redraw();
        }
    }

    void itemShownChanged(TitleBarItem item) {
        relayout();
        notifyCustomized();
    }

    /**
     * The user changed the quick access toolbar: saves the preferences file, if any, and fires
     * {@link SWT#Settings} on the bar so an app keeping the state elsewhere can store it.
     */
    void notifyCustomized() {
        if (isDisposed() || loadingPreferences) {
            return;
        }
        if (store != null) {
            try {
                store.put(TitleBarCustomization.SECTION, getCustomization().toJson());
                store.save();
            } catch (RuntimeException e) {
                System.getLogger(TitleBar.class.getName()).log(System.Logger.Level.WARNING,
                        "Could not save the quick access toolbar to " + store.file(), e);
            }
        }
        if (preferences != null) {
            try {
                getCustomization().save(preferences);
            } catch (java.io.IOException e) {
                System.getLogger(TitleBar.class.getName()).log(System.Logger.Level.WARNING,
                        "Could not save the title bar preferences to " + preferences, e);
            }
        }
        notifyListeners(SWT.Settings, new Event());
    }

    /** Whether the quick access toolbar shows on its own row below the bar. */
    public boolean isQuickAccessBelow() {
        checkWidget();
        return quickAccessBelow;
    }

    public void setQuickAccessBelow(boolean below) {
        checkWidget();
        if (quickAccessBelow != below) {
            quickAccessBelow = below;
            relayout();
            if (!shell.isDisposed()) {
                shell.layout(new Control[] { this });
            }
            notifyCustomized();
        }
    }

    /** What users have customized: the shown commands in their order, and the toolbar's position. */
    public TitleBarCustomization getCustomization() {
        checkWidget();
        return TitleBarCustomization.of(this);
    }

    /** Applies a customization, e.g. one the app kept in its own preferences. */
    public void setCustomization(TitleBarCustomization customization) {
        checkWidget();
        boolean was = loadingPreferences;
        loadingPreferences = true;
        try {
            customization.applyTo(this);
        } finally {
            loadingPreferences = was;
        }
        notifyCustomized();
    }

    public java.nio.file.Path getPreferences() {
        checkWidget();
        return preferences;
    }

    /**
     * The file users' customizations are kept in: loaded now if it exists (commands it doesn't know
     * keep the app's defaults, ids the app no longer has are ignored), and saved on every change.
     * Call it after creating the items. {@code null} stops saving.
     */
    public void setPreferences(java.nio.file.Path file) {
        checkWidget();
        this.preferences = file;
        defaults();
        if (file == null || !java.nio.file.Files.isRegularFile(file)) {
            return;
        }
        loadingPreferences = true;
        try {
            TitleBarCustomization.load(file).applyTo(this);
        } catch (java.io.IOException | RuntimeException e) {
            System.getLogger(TitleBar.class.getName()).log(System.Logger.Level.WARNING,
                    "Could not read the title bar preferences from " + file, e);
        } finally {
            loadingPreferences = false;
        }
    }

    public com.zapthemes.widgets.command.CustomizationStore getCustomizationStore() {
        checkWidget();
        return store;
    }

    /**
     * Keeps users' customizations in {@code store}'s {@code "quickAccessToolbar"} section, the file
     * the ribbon keeps its own in: applied now if the section exists (shown commands the bar has no
     * item for come from the shell's {@code CommandRegistry}; unknown ids are ignored), and saved
     * on every change. Call it after creating the items. {@code null} stops saving.
     */
    public void setCustomizationStore(com.zapthemes.widgets.command.CustomizationStore newStore) {
        checkWidget();
        this.store = newStore;
        defaults();
        com.google.gson.JsonElement section = newStore == null ? null : newStore.section(TitleBarCustomization.SECTION);
        if (section == null) {
            return;
        }
        loadingPreferences = true;
        try {
            TitleBarCustomization.fromJson(section).applyTo(this);
        } catch (RuntimeException e) {
            System.getLogger(TitleBar.class.getName()).log(System.Logger.Level.WARNING,
                    "Could not read the quick access toolbar from " + newStore.file(), e);
        } finally {
            loadingPreferences = false;
        }
    }

    /** The app's own toolbar, before users changed it: taken when the preferences file is set, or on first use. */
    TitleBarCustomization defaults() {
        if (defaults == null) {
            defaults = TitleBarCustomization.of(this);
        }
        return defaults;
    }

    /** Puts the quick access toolbar back as the app made it. */
    public void resetCustomization() {
        checkWidget();
        setCustomization(defaults());
    }

    /**
     * Opens the "More Commands..." dialog: add, remove and reorder the quick access commands. With
     * a handler set ({@link #setCustomizeHandler}), runs that instead.
     */
    public void openCustomizeDialog() {
        checkWidget();
        if (customizeHandler != null) {
            customizeHandler.run();
        } else {
            new QuickAccessDialog(this).open();
        }
    }

    /**
     * Has "More Commands..." run {@code handler} instead of opening its own dialog: an app with
     * an Options window opens it at its {@link #quickAccessPage()}, as Office does. {@code null}
     * goes back to the dialog.
     */
    public void setCustomizeHandler(Runnable handler) {
        checkWidget();
        this.customizeHandler = handler;
    }

    /**
     * The "More Commands..." dialog's contents as a page of an
     * {@link com.zapthemes.widgets.options.OptionsDialog}: OK there applies it to the bar.
     */
    public com.zapthemes.widgets.options.OptionsPage quickAccessPage() {
        checkWidget();
        return new QuickAccessDialog(this);
    }

    // ------------------------------------------------------------------ window buttons

    public void minimize() {
        checkWidget();
        shell.setMinimized(true);
    }

    public void toggleMaximize() {
        checkWidget();
        shell.setMaximized(!shell.getMaximized());
    }

    /** Closes the shell as its close button does: listeners can veto through {@link SWT#Close}. */
    public void close() {
        checkWidget();
        shell.close();
    }

    /** Opens the system menu (Restore, Move, Size, Minimize, Maximize, Close) under the icon. */
    public void openSystemMenu() {
        checkWidget();
        Hit icon = find(Part.SYSTEM_MENU);
        Rectangle bounds = icon != null ? icon.bounds : new Rectangle(0, 0, 0, rowHeight);
        frame.openSystemMenu(toDisplay(bounds.x, bounds.y + bounds.height));
    }

    /** The system menu drawn frames and other platforms use: an SWT menu acting on the shell. */
    void openDrawnSystemMenu(Point location) {
        createSystemMenu();
        systemMenu.setLocation(location);
        systemMenu.setVisible(true);
    }

    /** Builds the drawn system menu without showing it. */
    org.eclipse.swt.widgets.Menu createSystemMenu() {
        if (systemMenu != null && !systemMenu.isDisposed()) {
            systemMenu.dispose();
        }
        systemMenu = new Menu(shell, SWT.POP_UP);
        boolean maximized = shell.getMaximized();
        boolean drawn = frame.mode() == FrameMode.DRAWN;
        menuItem(systemMenu, "&Restore", maximized, () -> shell.setMaximized(false));
        menuItem(systemMenu, "&Move", drawn && !maximized, () -> ((DrawnFrame) frame).startKeyboardMode(false));
        menuItem(systemMenu, "&Size", drawn && !maximized && resizable, () -> ((DrawnFrame) frame).startKeyboardMode(true));
        menuItem(systemMenu, "Mi&nimize", true, this::minimize);
        menuItem(systemMenu, "Ma&ximize", !maximized, () -> shell.setMaximized(true));
        new MenuItem(systemMenu, SWT.SEPARATOR);
        MenuItem close = menuItem(systemMenu, "&Close\tAlt+F4", true, this::close);
        systemMenu.setDefaultItem(close);
        return systemMenu;
    }

    private static MenuItem menuItem(org.eclipse.swt.widgets.Menu menu, String text, boolean enabled, Runnable run) {
        MenuItem item = new MenuItem(menu, SWT.PUSH);
        item.setText(text);
        item.setEnabled(enabled);
        item.addListener(SWT.Selection, e -> run.run());
        return item;
    }

    /** Opens the title's menu under the title. */
    public void openTitleMenu() {
        checkWidget();
        Hit hit = find(Part.TITLE);
        if (titleMenu == null || titleMenu.isDisposed() || hit == null) {
            return;
        }
        titleMenu.setLocation(toDisplay(hit.bounds.x, hit.bounds.y + hit.bounds.height));
        titleMenu.setVisible(true);
    }

    /** Opens the customize menu of the quick access toolbar. */
    public void openCustomizeMenu() {
        checkWidget();
        Hit hit = find(Part.CUSTOMIZE);
        Rectangle bounds = hit != null ? hit.bounds : new Rectangle(0, 0, 0, rowHeight);
        createCustomizeMenu();
        customizeMenu.setLocation(toDisplay(bounds.x, bounds.y + bounds.height));
        customizeMenu.setVisible(true);
    }

    /** Builds the customize menu without showing it. */
    org.eclipse.swt.widgets.Menu createCustomizeMenu() {
        defaults();
        if (customizeMenu != null && !customizeMenu.isDisposed()) {
            customizeMenu.dispose();
        }
        customizeMenu = new Menu(shell, SWT.POP_UP);
        MenuItem header = new MenuItem(customizeMenu, SWT.PUSH);
        header.setText(CUSTOMIZE_TEXT);
        header.setEnabled(false);
        new MenuItem(customizeMenu, SWT.SEPARATOR);
        for (TitleBarItem item : getQuickAccessItems()) {
            MenuItem check = new MenuItem(customizeMenu, SWT.CHECK);
            check.setText(item.plainText());
            check.setSelection(item.isShown());
            check.addListener(SWT.Selection, e -> item.setShown(check.getSelection()));
        }
        fillCustomizeMenu(customizeMenu);
        return customizeMenu;
    }

    /** The customize menu's entries after the command list. */
    void fillCustomizeMenu(org.eclipse.swt.widgets.Menu menu) {
        new MenuItem(menu, SWT.SEPARATOR);
        menuItem(menu, MORE_COMMANDS_TEXT, true, this::openCustomizeDialog);
        menuItem(menu, quickAccessBelow ? SHOW_ABOVE_TEXT : SHOW_BELOW_TEXT, true, () -> setQuickAccessBelow(!quickAccessBelow));
    }

    /** The open customize menu, or {@code null}. */
    org.eclipse.swt.widgets.Menu customizeMenu() {
        return customizeMenu != null && !customizeMenu.isDisposed() ? customizeMenu : null;
    }

    /** Opens the profile pop-up under the avatar and returns its shell; {@code null} without a profile. */
    public Shell openProfile() {
        checkWidget();
        Hit hit = find(Part.AVATAR);
        if (profile == null || hit == null) {
            return null;
        }
        if (profilePopup != null && !profilePopup.isDisposed()) {
            profilePopup.close();
        }
        Point anchor = toDisplay(hit.bounds.x + hit.bounds.width, hit.bounds.y + hit.bounds.height);
        profilePopup = ProfilePopup.open(this, profile, anchor);
        return profilePopup;
    }

    // ------------------------------------------------------------------ hit testing

    /** What is at ({@code x}, {@code y}) in bar coordinates. */
    public Part getPart(int x, int y) {
        checkWidget();
        Rectangle area = getClientArea();
        if (!area.contains(x, y)) {
            return Part.NONE;
        }
        Hit hit = hitAt(x, y);
        if (hit != null) {
            return hit.part;
        }
        return y >= rowHeight() ? Part.TOOLBAR : Part.CAPTION;
    }

    /** The bounds of the first part of this kind, or {@code null} when the bar doesn't show it. */
    public Rectangle getPartBounds(Part part) {
        checkWidget();
        Hit hit = find(part);
        return hit != null ? new Rectangle(hit.bounds.x, hit.bounds.y, hit.bounds.width, hit.bounds.height) : null;
    }

    Hit hitAt(int x, int y) {
        for (Hit hit : hits) {
            if (hit.bounds.contains(x, y)) {
                return hit;
            }
        }
        return null;
    }

    private Hit find(Part part) {
        for (Hit hit : hits) {
            if (hit.part == part) {
                return hit;
            }
        }
        return null;
    }

    List<Hit> hits() {
        return hits;
    }

    /** The bar's scale: 1 at the 32 px row of a 9 pt font. */
    float unit() {
        return rowHeight() / 32f;
    }

    /** The quick access toolbar's own row, when it is below the bar. */
    int belowHeight() {
        return px(34);
    }

    int rowHeight() {
        if (rowHeight == 0) {
            GC gc = new GC(this);
            gc.setFont(getFont());
            int fontHeight = gc.getFontMetrics().getHeight();
            gc.dispose();
            rowHeight = Math.max(24, Math.round(fontHeight * 2.1f));
        }
        return rowHeight;
    }

    // ------------------------------------------------------------------ layout

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        int height = rowHeight() + (quickAccessBelow ? belowHeight() : 0);
        return new Point(wHint == SWT.DEFAULT ? Math.round(640 * unit()) : wHint, hHint == SWT.DEFAULT ? height : hHint);
    }

    private final class BarLayout extends Layout {
        @Override
        protected Point computeSize(org.eclipse.swt.widgets.Composite composite, int wHint, int hHint, boolean flushCache) {
            return TitleBar.this.computeSize(wHint, hHint, flushCache);
        }

        @Override
        protected void layout(org.eclipse.swt.widgets.Composite composite, boolean flushCache) {
            computeLayout();
        }
    }

    private void relayout() {
        if (isDisposed()) {
            return;
        }
        computeLayout();
        redraw();
    }

    private int px(float value) {
        return Math.round(value * unit());
    }

    void computeLayout() {
        if (frame == null) {
            return; // Still in the constructor: children are being created.
        }
        getTitle();
        hits.clear();
        items.forEach(item -> {
            item.bounds.x = item.bounds.y = item.bounds.width = item.bounds.height = 0;
            item.arrowWidth = 0;
        });
        Rectangle area = getClientArea();
        int width = area.width;
        int height = rowHeight();
        boolean maximized = shell.getMaximized();
        GC gc = new GC(this);
        try {
            // Measure as painting draws: antialiasing switches to advanced graphics, whose text is wider.
            gc.setAntialias(SWT.ON);
            gc.setTextAntialias(SWT.ON);
            gc.setFont(getFont());
            if (frame.resizesFromBar() && !maximized && resizable) {
                int border = Math.max(4, px(5));
                int corner = px(16);
                hits.add(new Hit(Part.RESIZE_TOP_LEFT, new Rectangle(0, 0, corner, border), null));
                hits.add(new Hit(Part.RESIZE_TOP_RIGHT, new Rectangle(width - corner, 0, corner, border), null));
                hits.add(new Hit(Part.RESIZE_TOP, new Rectangle(corner, 0, Math.max(0, width - 2 * corner), border), null));
            }

            // Left: icon, quick access toolbar, customize.
            int x = frame.leadingInset();
            if (frame.drawsIcon()) {
                int iconWidth = px(40);
                hits.add(new Hit(Part.SYSTEM_MENU, new Rectangle(x, 0, iconWidth, height), null));
                x += iconWidth;
            } else {
                x += px(8);
            }
            int quickStart = x;

            // Right: window buttons, avatar, trailing items, slot.
            int right = width;
            if (frame.drawsWindowButtons()) {
                int button = px(46);
                hits.add(new Hit(Part.CLOSE, new Rectangle(right - button, 0, button, height), null));
                hits.add(new Hit(Part.MAXIMIZE, new Rectangle(right - 2 * button, 0, button, height), null));
                hits.add(new Hit(Part.MINIMIZE, new Rectangle(right - 3 * button, 0, button, height), null));
                right -= 3 * button;
            } else {
                right -= px(8);
            }
            if (profile != null) {
                int avatar = px(44);
                right -= avatar;
                hits.add(new Hit(Part.AVATAR, new Rectangle(right, 0, avatar, height), null));
            }
            List<TitleBarItem> trail = items.stream().filter(i -> i.isTrail() && i.isShown()).toList();
            for (int i = trail.size() - 1; i >= 0; i--) {
                TitleBarItem item = trail.get(i);
                int w = trailWidth(gc, item);
                right -= w + px(4);
                int h = px(24);
                item.bounds.x = right;
                item.bounds.y = (height - h) / 2;
                item.bounds.width = w;
                item.bounds.height = h;
                hits.add(new Hit(Part.ITEM, item.bounds, item));
            }
            Point slotSize = slotSize();
            if (slotSize.x > 0) {
                right -= slotSize.x + px(8);
                Rectangle bounds = new Rectangle(right, (height - slotSize.y) / 2, slotSize.x, slotSize.y);
                slot.setBounds(bounds);
                slot.setVisible(true);
                hits.add(new Hit(Part.SLOT, bounds, null));
            } else {
                slot.setVisible(false);
            }
            int middleRight = right - px(8);

            // The quick access toolbar, dropping items from its end while the middle is too narrow.
            List<TitleBarItem> quick = new ArrayList<>(items.stream().filter(i -> !i.isTrail() && i.isShown()).toList());
            boolean hasCustomize = customizable && !getQuickAccessItems().isEmpty();
            int customizeWidth = hasCustomize ? px(22) : 0;
            int minimumMiddle = px(searchHint != null ? 40 : 8);
            int rowTop = 0;
            int rowHeight = height;
            int rowEnd = middleRight - minimumMiddle - px(8);
            int afterQuick = quickStart;
            if (quickAccessBelow) {
                quickStart = px(6);
                rowTop = height;
                rowHeight = belowHeight();
                rowEnd = width - px(6);
            }
            while (!quick.isEmpty() && quickStart + quickWidth(gc, quick) + customizeWidth > rowEnd) {
                quick.removeLast();
            }
            x = quickStart;
            int itemHeight = px(28);
            for (TitleBarItem item : quick) {
                int w = quickItemWidth(gc, item);
                item.bounds.x = x;
                item.bounds.y = rowTop + (rowHeight - itemHeight) / 2;
                item.bounds.width = w;
                item.bounds.height = itemHeight;
                item.arrowWidth = item.isDropDown() ? px(14) : 0;
                hits.add(new Hit(Part.ITEM, item.bounds, item));
                x += w + px(2);
            }
            if (hasCustomize) {
                hits.add(new Hit(Part.CUSTOMIZE, new Rectangle(x, rowTop + (rowHeight - itemHeight) / 2, customizeWidth, itemHeight), null));
                x += customizeWidth;
            }
            int middleLeft = (quickAccessBelow ? afterQuick : x) + px(8);

            layoutMiddle(gc, width, height, middleLeft, middleRight);
        } finally {
            gc.dispose();
        }
        updateSearchVisibility();
        if (focusIndex >= focusables().size()) {
            focusIndex = focusables().isEmpty() ? -1 : 0;
        }
        frame.layoutChanged();
    }

    /** The title and search box between the toolbar and the right side. */
    private void layoutMiddle(GC gc, int width, int height, int left, int right) {
        int available = right - left;
        int gap = px(12);
        gc.setFont(titleFont != null ? titleFont : getFont());
        int titleWidth = title.isEmpty() ? 0 : gc.textExtent(title).x + px(16) + (hasTitleMenu() ? px(16) : 0);
        gc.setFont(getFont());
        int titleMinimum = Math.min(titleWidth, px(80));
        searchCollapsed = false;
        Rectangle searchBounds = null;
        int searchHeight = px(26);
        int searchY = (height - searchHeight) / 2;
        if (searchHint != null) {
            int desired = px(370);
            int minimum = px(180);
            if (searchExpanded && available >= minimum) {
                searchBounds = new Rectangle(left, searchY, Math.min(desired, available), searchHeight);
                titleWidth = 0;
            } else if (available >= titleWidth + gap + desired) {
                int x = Math.max(left + titleWidth + gap, (width - desired) / 2);
                x = Math.min(x, right - desired);
                searchBounds = new Rectangle(x, searchY, desired, searchHeight);
            } else if (available >= titleMinimum + gap + minimum) {
                int w = Math.min(desired, Math.max(minimum, available - titleWidth - gap));
                searchBounds = new Rectangle(right - w, searchY, w, searchHeight);
            } else if (available >= px(32)) {
                searchCollapsed = true;
                searchExpanded = false;
                searchBounds = new Rectangle(right - px(32), (height - px(28)) / 2, px(32), px(28));
            }
        }
        int titleRight = searchBounds != null ? searchBounds.x - gap : right;
        int titleSpace = titleRight - left;
        if (titleWidth > 0 && titleSpace >= px(40)) {
            int w = Math.min(titleWidth, titleSpace);
            int x;
            if (searchBounds == null || searchCollapsed) {
                // Centered on the window when it fits, as Office does.
                x = Math.max(left, Math.min((width - w) / 2, titleRight - w));
            } else {
                x = left + (titleSpace - w) / 2;
            }
            hits.add(new Hit(Part.TITLE, new Rectangle(x, (height - px(28)) / 2, w, px(28)), null));
        }
        if (searchBounds != null) {
            hits.add(new Hit(Part.SEARCH, searchBounds, null));
        }
    }

    private int quickWidth(GC gc, List<TitleBarItem> quick) {
        int total = 0;
        for (TitleBarItem item : quick) {
            total += quickItemWidth(gc, item) + px(2);
        }
        return total;
    }

    private int quickItemWidth(GC gc, TitleBarItem item) {
        if (item.isCheck()) {
            return px(8) + gc.textExtent(item.plainText()).x + px(8) + px(30) + px(6) + gc.textExtent("Off").x + px(8);
        }
        int w = item.getImage() != null || item.getGlyph() != null ? px(32) : gc.textExtent(item.plainText()).x + px(16);
        return w + (item.isDropDown() ? px(14) : 0);
    }

    private int trailWidth(GC gc, TitleBarItem item) {
        int w = px(24) + gc.textExtent(item.plainText()).x;
        if (item.getImage() != null || item.getGlyph() != null) {
            w += px(16) + px(6);
        }
        return w;
    }

    private Point slotSize() {
        boolean visibleChild = false;
        for (Control child : slot.getChildren()) {
            visibleChild |= child.getVisible();
        }
        if (!visibleChild) {
            return new Point(0, 0);
        }
        Point size = slot.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        return new Point(size.x, Math.min(size.y, rowHeight()));
    }

    /** Whether the title is part of the caption (it has no menu): dragging it moves the window. */
    boolean titleIsCaption() {
        return !hasTitleMenu();
    }

    /**
     * Hover and press of a part the native frame tracks (the maximize button on Windows). A
     * {@code null} clears only what the frame set, never the bar's own hover or press.
     */
    void setFrameState(Part hoverPart, Part pressedPart) {
        if (hoverPart != null) {
            hover = find(hoverPart);
            frameHover = hover != null;
            setToolTipText(null);
        } else if (frameHover) {
            hover = null;
            frameHover = false;
        }
        if (pressedPart != null) {
            pressed = find(pressedPart);
            framePressed = pressed != null;
        } else if (framePressed) {
            pressed = null;
            framePressed = false;
        }
        redraw();
    }

    /** The part the pointer is over, as painted; {@code null} for none. */
    Part hoverPart() {
        return hover != null ? hover.part : null;
    }

    private boolean hasTitleMenu() {
        return titleMenu != null && !titleMenu.isDisposed();
    }

    private void updateSearchVisibility() {
        Hit hit = find(Part.SEARCH);
        boolean show = hit != null && !searchCollapsed && (search.isFocusControl() || !search.getText().isEmpty() || searchExpanded);
        if (hit != null && !searchCollapsed) {
            Point size = search.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            int textX = hit.bounds.x + px(32);
            int textWidth = Math.max(10, hit.bounds.width - px(32) - px(10));
            int textHeight = Math.min(size.y, hit.bounds.height - 2);
            search.setBounds(textX, hit.bounds.y + (hit.bounds.height - textHeight) / 2, textWidth, textHeight);
        }
        if (search.getVisible() != show) {
            search.setVisible(show);
        }
    }

    private void searchFocusOut() {
        if (search.getText().isEmpty()) {
            boolean wasExpanded = searchExpanded;
            searchExpanded = false;
            if (wasExpanded) {
                relayout();
            } else {
                updateSearchVisibility();
                redraw();
            }
        }
    }

    private void searchSubmitted() {
        Event event = new Event();
        event.text = search.getText();
        notifyListeners(SWT.DefaultSelection, event);
    }

    // ------------------------------------------------------------------ theme

    /** The theme, the accent or the OS colors changed: re-read colors and fonts and lay out again. */
    void themeChanged() {
        if (isDisposed()) {
            return;
        }
        activeColors = null;
        inactiveColors = null;
        rowHeight = 0;
        ThemeManager manager = ThemeManager.get(getDisplay());
        ResolvedStyle style = manager.styleOf(this);
        titleFont = new ThemeContext(manager.resources()).font(style, "titleFont");
        applyColors();
        relayout();
        if (!shell.isDisposed()) {
            shell.layout(new Control[] { this });
            requestLayout();
        }
    }

    /** The toolbar row below the bar takes the window's colors, as Office's does the ribbon's. */
    TitleBarColors belowColors() {
        RGB background = shell.getBackground().getRGB();
        RGB foreground = shell.getForeground().getRGB();
        return TitleBarColors.plain(background, foreground);
    }

    TitleBarColors colors() {
        if (active) {
            if (activeColors == null) {
                activeColors = TitleBarColors.of(this, accent, true);
            }
            return activeColors;
        }
        if (inactiveColors == null) {
            inactiveColors = TitleBarColors.of(this, accent, false);
        }
        return inactiveColors;
    }

    /** Colors of the bar's own children (the slot, the search field) follow the bar. */
    void applyColors() {
        TitleBarColors colors = colors();
        Display display = getDisplay();
        super.setBackground(new Color(display, colors.background()));
        super.setForeground(new Color(display, colors.foreground()));
        slot.setBackground(new Color(display, colors.background()));
        search.setBackground(new Color(display, colors.searchBackground()));
        search.setForeground(new Color(display, colors.searchForeground()));
        search.setFont(getFont());
    }

    private void shellEvent(Event event) {
        switch (event.type) {
            case SWT.Activate, SWT.Deactivate -> {
                active = event.type == SWT.Activate;
                applyColors();
                redraw();
            }
            case SWT.Resize -> relayout();
            default -> {
            }
        }
    }

    private void altSpace(Event event) {
        if ((event.stateMask & SWT.MODIFIER_MASK) == SWT.ALT && event.character == ' ' && getDisplay().getActiveShell() == shell) {
            event.doit = false;
            event.type = SWT.None;
            openSystemMenu();
        }
    }

    private void disposed() {
        frame.dispose();
        getDisplay().removeFilter(SWT.KeyDown, altSpace);
        getDisplay().removeFilter(SWT.KeyDown, focusKeyFilter);
        if (!shell.isDisposed()) {
            for (int type : new int[] { SWT.Activate, SWT.Deactivate, SWT.Resize }) {
                shell.removeListener(type, shellListener);
            }
        }
        ThemeManager.get(getDisplay()).removeThemeChangeListener(this, themeListener);
        for (Menu menu : new Menu[] { customizeMenu, systemMenu }) {
            if (menu != null && !menu.isDisposed()) {
                menu.dispose();
            }
        }
        if (profilePopup != null && !profilePopup.isDisposed()) {
            profilePopup.dispose();
        }
    }

    // ------------------------------------------------------------------ painting

    private void paint(Event event) {
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        TitleBarColors colors = colors();
        Rectangle area = getClientArea();
        Display display = getDisplay();
        Color background = new Color(display, colors.background());
        Color foreground = new Color(display, colors.foreground());
        gc.setBackground(background);
        gc.fillRectangle(area);
        TitleBarColors below = null;
        if (quickAccessBelow) {
            below = belowColors();
            gc.setBackground(new Color(display, below.background()));
            gc.fillRectangle(0, rowHeight(), area.width, area.height - rowHeight());
            gc.setForeground(new Color(display, below.itemBorder()));
            gc.drawLine(0, area.height - 1, area.width, area.height - 1);
        }
        gc.setFont(getFont());
        List<Hit> focusable = focusables();
        // The ring shows only for keyboard focus: a platform may give the bar the focus on its own.
        Hit focused = isFocusControl() && keyboardFocus && focusIndex >= 0 && focusIndex < focusable.size() ? focusable.get(focusIndex) : null;
        for (Hit hit : hits) {
            switch (hit.part) {
                case SYSTEM_MENU -> paintIcon(gc, hit);
                case ITEM -> paintItem(gc, hit, below != null && hit.bounds.y >= rowHeight() ? below : colors);
                case CUSTOMIZE -> {
                    TitleBarColors c = below != null && hit.bounds.y >= rowHeight() ? below : colors;
                    paintHover(gc, hit, c);
                    gc.setForeground(new Color(display, c.foreground()));
                    paintChevron(gc, hit.bounds.x + hit.bounds.width / 2, hit.bounds.y + hit.bounds.height / 2, px(4));
                    gc.drawLine(hit.bounds.x + hit.bounds.width / 2 - px(4), hit.bounds.y + hit.bounds.height / 2 - px(5),
                            hit.bounds.x + hit.bounds.width / 2 + px(4), hit.bounds.y + hit.bounds.height / 2 - px(5));
                }
                case TITLE -> paintTitle(gc, hit, colors);
                case SEARCH -> paintSearch(gc, hit, colors);
                case AVATAR -> paintAvatar(gc, hit, colors);
                case MINIMIZE, MAXIMIZE, CLOSE -> paintWindowButton(gc, hit, colors);
                default -> {
                }
            }
            if (hit == focused) {
                gc.setForeground(new Color(display, colors.focus()));
                Rectangle b = hit.bounds;
                gc.drawRoundRectangle(b.x + 1, b.y + 1, b.width - 3, b.height - 3, px(4), px(4));
            }
        }
    }

    private boolean isHover(Hit hit) {
        return hover != null && hover.part == hit.part && hover.item == hit.item;
    }

    private boolean isPressed(Hit hit) {
        return pressed != null && pressed.part == hit.part && pressed.item == hit.item;
    }

    private void paintHover(GC gc, Hit hit, TitleBarColors colors) {
        if (isPressed(hit) || isHover(hit)) {
            gc.setBackground(new Color(getDisplay(), isPressed(hit) ? colors.pressed() : colors.hover()));
            Rectangle b = hit.bounds;
            gc.fillRoundRectangle(b.x, b.y, b.width, b.height, px(6), px(6));
        }
    }

    private void paintIcon(GC gc, Hit hit) {
        Image image = shell.getImage();
        if (image == null) {
            Image[] images = shell.getImages();
            image = images.length > 0 ? images[0] : null;
        }
        if (image == null || image.isDisposed()) {
            return;
        }
        Rectangle bounds = image.getBounds();
        int size = px(16);
        int x = hit.bounds.x + (hit.bounds.width - size) / 2;
        int y = hit.bounds.y + (hit.bounds.height - size) / 2;
        gc.drawImage(image, 0, 0, bounds.width, bounds.height, x, y, size, size);
    }

    private void paintItem(GC gc, Hit hit, TitleBarColors colors) {
        TitleBarItem item = hit.item;
        Display display = getDisplay();
        RGB fg = item.getEnabled() ? colors.foreground() : TitleBarColors.blend(colors.foreground(), colors.background(), 0.45);
        Color foreground = new Color(display, fg);
        Rectangle b = hit.bounds;
        if (item.isTrail()) {
            boolean hot = item.getEnabled() && (isHover(hit) || isPressed(hit));
            if (hot) {
                gc.setBackground(new Color(display, isPressed(hit) ? colors.pressed() : colors.hover()));
                gc.fillRoundRectangle(b.x, b.y, b.width, b.height, px(6), px(6));
            }
            gc.setForeground(new Color(display, colors.itemBorder()));
            gc.drawRoundRectangle(b.x, b.y, b.width - 1, b.height - 1, px(6), px(6));
            int x = b.x + px(12);
            gc.setForeground(foreground);
            if (item.getImage() != null || item.getGlyph() != null) {
                paintItemGraphic(gc, item, x, b.y + (b.height - px(16)) / 2, px(16));
                x += px(16) + px(6);
            }
            Point extent = gc.textExtent(item.plainText());
            gc.drawText(item.plainText(), x, b.y + (b.height - extent.y) / 2, true);
            return;
        }
        if (item.getEnabled()) {
            paintHover(gc, hit, colors);
        }
        gc.setForeground(foreground);
        if (item.isCheck()) {
            paintSwitch(gc, item, b, colors, fg);
            return;
        }
        int contentWidth = b.width - item.arrowWidth;
        if (item.getImage() != null || item.getGlyph() != null) {
            int size = px(16);
            paintItemGraphic(gc, item, b.x + (contentWidth - size) / 2, b.y + (b.height - size) / 2, size);
        } else {
            Point extent = gc.textExtent(item.plainText());
            gc.drawText(item.plainText(), b.x + (contentWidth - extent.x) / 2, b.y + (b.height - extent.y) / 2, true);
        }
        if (item.isDropDown()) {
            paintChevron(gc, b.x + contentWidth + item.arrowWidth / 2 - px(1), b.y + b.height / 2, px(3));
        }
    }

    private void paintItemGraphic(GC gc, TitleBarItem item, int x, int y, int size) {
        Image image = item.getImage();
        if (image != null && !image.isDisposed()) {
            Rectangle bounds = image.getBounds();
            gc.drawImage(image, 0, 0, bounds.width, bounds.height, x, y, size, size);
        } else if (item.getGlyph() != null) {
            item.getGlyph().paint(gc, x, y, size);
        }
    }

    /** Office's AutoSave: the label, a switch, and "On" or "Off". */
    private void paintSwitch(GC gc, TitleBarItem item, Rectangle b, TitleBarColors colors, RGB fg) {
        Display display = getDisplay();
        String label = item.plainText();
        Point extent = gc.textExtent(label);
        int x = b.x + px(8);
        int textY = b.y + (b.height - extent.y) / 2;
        gc.drawText(label, x, textY, true);
        x += extent.x + px(8);
        int w = px(30);
        int h = px(16);
        int y = b.y + (b.height - h) / 2;
        boolean on = item.getSelection();
        Color fgColor = new Color(display, fg);
        if (on) {
            gc.setBackground(fgColor);
            gc.fillRoundRectangle(x, y, w, h, h, h);
            gc.setBackground(new Color(display, colors.background()));
        } else {
            gc.setForeground(fgColor);
            gc.drawRoundRectangle(x, y, w, h, h, h);
            gc.setBackground(fgColor);
        }
        int knob = h - px(6);
        int knobX = on ? x + w - px(3) - knob : x + px(3);
        gc.fillOval(knobX, y + px(3), knob + 1, knob + 1);
        gc.setForeground(fgColor);
        gc.drawText(on ? "On" : "Off", x + w + px(6), textY, true);
    }

    private void paintChevron(GC gc, int cx, int cy, int half) {
        gc.setLineWidth(Math.max(1, px(1)));
        gc.drawPolyline(new int[] { cx - half, cy - half / 2, cx, cy + half / 2, cx + half, cy - half / 2 });
        gc.setLineWidth(1);
    }

    private void paintTitle(GC gc, Hit hit, TitleBarColors colors) {
        boolean menu = hasTitleMenu();
        if (menu) {
            paintHover(gc, hit, colors);
        }
        Font font = titleFont != null ? titleFont : getFont();
        gc.setFont(font);
        gc.setForeground(new Color(getDisplay(), colors.foreground()));
        Rectangle b = hit.bounds;
        int textSpace = b.width - px(16) - (menu ? px(16) : 0);
        String text = shorten(gc, title, textSpace);
        Point extent = gc.textExtent(text);
        int x = b.x + px(8);
        gc.drawText(text, x, b.y + (b.height - extent.y) / 2, true);
        if (menu) {
            paintChevron(gc, x + extent.x + px(10), b.y + b.height / 2, px(3));
        }
        gc.setFont(getFont());
    }

    static String shorten(GC gc, String text, int width) {
        if (gc.textExtent(text).x <= width) {
            return text;
        }
        String ellipsis = "…";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (gc.textExtent(text.substring(0, mid) + ellipsis).x <= width) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low == 0 ? ellipsis : text.substring(0, low).stripTrailing() + ellipsis;
    }

    private void paintSearch(GC gc, Hit hit, TitleBarColors colors) {
        Display display = getDisplay();
        Rectangle b = hit.bounds;
        if (searchCollapsed) {
            paintHover(gc, hit, colors);
            gc.setForeground(new Color(display, colors.foreground()));
            TitleBarGlyph.SEARCH.paint(gc, b.x + (b.width - px(16)) / 2, b.y + (b.height - px(16)) / 2, px(16));
            return;
        }
        int arc = px(6);
        gc.setBackground(new Color(display, isHover(hit) && !search.getVisible() ? TitleBarColors.blend(colors.foreground(), colors.searchBackground(), 0.06) : colors.searchBackground()));
        gc.fillRoundRectangle(b.x, b.y, b.width, b.height, arc, arc);
        gc.setForeground(new Color(display, colors.searchBorder()));
        gc.drawRoundRectangle(b.x, b.y, b.width - 1, b.height - 1, arc, arc);
        gc.setForeground(new Color(display, colors.searchForeground()));
        TitleBarGlyph.SEARCH.paint(gc, b.x + px(10), b.y + (b.height - px(14)) / 2, px(14));
        if (!search.getVisible()) {
            gc.setForeground(new Color(display, colors.searchHint()));
            String hint = shorten(gc, searchHint, b.width - px(42));
            Point extent = gc.textExtent(hint);
            gc.drawText(hint, b.x + px(32), b.y + (b.height - extent.y) / 2, true);
        }
    }

    private void paintAvatar(GC gc, Hit hit, TitleBarColors colors) {
        paintHover(gc, hit, colors);
        Rectangle b = hit.bounds;
        int d = Math.min(px(26), b.height - px(6));
        paintAvatarCircle(gc, profile, b.x + (b.width - d) / 2, b.y + (b.height - d) / 2, d, colors);
    }

    /** The avatar: the profile's image clipped to a circle, or its initials on a colored circle. */
    static void paintAvatarCircle(GC gc, TitleBarProfile profile, int x, int y, int d, TitleBarColors colors) {
        Display display = gc.getDevice() instanceof Display device ? device : Display.getCurrent();
        Image image = profile.image();
        if (image != null && !image.isDisposed()) {
            Path clip = new Path(display);
            clip.addArc(x, y, d, d, 0, 360);
            gc.setClipping(clip);
            Rectangle bounds = image.getBounds();
            gc.drawImage(image, 0, 0, bounds.width, bounds.height, x, y, d, d);
            gc.setClipping((Rectangle) null);
            clip.dispose();
            return;
        }
        gc.setBackground(new Color(display, colors.avatarBackground()));
        gc.fillOval(x, y, d, d);
        gc.setForeground(new Color(display, colors.avatarForeground()));
        String initials = profile.initials();
        Point extent = gc.textExtent(initials);
        gc.drawText(initials, x + (d - extent.x) / 2, y + (d - extent.y) / 2, true);
    }

    private void paintWindowButton(GC gc, Hit hit, TitleBarColors colors) {
        Display display = getDisplay();
        Rectangle b = hit.bounds;
        boolean close = hit.part == Part.CLOSE;
        boolean hot = isHover(hit) || isPressed(hit);
        RGB fg = colors.foreground();
        if (hot) {
            RGB bg = close ? (isPressed(hit) ? colors.closePressed() : colors.closeHover()) : (isPressed(hit) ? colors.pressed() : colors.hover());
            gc.setBackground(new Color(display, bg));
            gc.fillRectangle(b);
            if (close) {
                fg = colors.closeHoverForeground();
            }
        }
        gc.setForeground(new Color(display, fg));
        gc.setAntialias(SWT.OFF);
        int size = px(10);
        int cx = b.x + b.width / 2;
        int cy = b.y + b.height / 2;
        int x = cx - size / 2;
        int y = cy - size / 2;
        switch (hit.part) {
            case MINIMIZE -> gc.drawLine(x, cy, x + size, cy);
            case MAXIMIZE -> {
                if (shell.getMaximized()) {
                    int s = size - px(2);
                    gc.drawRectangle(x, y + px(2), s, s);
                    gc.drawPolyline(new int[] { x + px(2), y + px(2), x + px(2), y, x + size, y, x + size, y + s, x + s, y + s });
                } else {
                    gc.drawRectangle(x, y, size, size);
                }
            }
            default -> {
                gc.setAntialias(SWT.ON);
                gc.drawLine(x, y, x + size, y + size);
                gc.drawLine(x + size, y, x, y + size);
            }
        }
        gc.setAntialias(SWT.ON);
    }

    // ------------------------------------------------------------------ mouse

    private void setHover(Hit hit) {
        if (hit == null ? hover == null : hover != null && hover.part == hit.part && hover.item == hit.item) {
            return;
        }
        hover = hit;
        frameHover = false;
        setToolTipText(hit != null ? tooltip(hit) : null);
        redraw();
    }

    /** What a part's tooltip and accessible name say. */
    String tooltip(Hit hit) {
        return switch (hit.part) {
            case ITEM -> hit.item.tooltip();
            case CUSTOMIZE -> CUSTOMIZE_TEXT;
            case SYSTEM_MENU -> null;
            case TITLE -> hasTitleMenu() ? title : null;
            case SEARCH -> searchHint;
            case AVATAR -> profile != null ? profile.name() + (profile.email().isEmpty() ? "" : "\n" + profile.email()) : null;
            case MINIMIZE -> MINIMIZE_TEXT;
            case MAXIMIZE -> shell.getMaximized() ? RESTORE_TEXT : MAXIMIZE_TEXT;
            case CLOSE -> CLOSE_TEXT;
            default -> null;
        };
    }

    private void mouseMove(Event event) {
        if (pressed == null && (event.stateMask & SWT.BUTTON1) != 0) {
            frame.captionDragged(event);
            return;
        }
        Hit hit = hitAt(event.x, event.y);
        setHover(hit != null && isInteractive(hit) ? hit : null);
    }

    private static boolean isInteractive(Hit hit) {
        return switch (hit.part) {
            case CAPTION, SLOT, RESIZE_TOP, RESIZE_TOP_LEFT, RESIZE_TOP_RIGHT, TOOLBAR, NONE -> false;
            default -> true;
        };
    }

    private void mouseDown(Event event) {
        if (event.button != 1) {
            return;
        }
        Hit hit = hitAt(event.x, event.y);
        if (hit == null && event.y >= rowHeight()) {
            return;
        }
        if (hit == null || hit.part == Part.TITLE && !hasTitleMenu()) {
            frame.captionPressed(event);
            return;
        }
        switch (hit.part) {
            case SYSTEM_MENU -> {
                if (event.count == 1) {
                    openSystemMenu();
                }
            }
            case TITLE -> openTitleMenu();
            case SEARCH -> {
                if (searchCollapsed) {
                    focusSearch();
                } else {
                    search.setVisible(true);
                    search.setFocus();
                    redraw();
                }
            }
            case RESIZE_TOP, RESIZE_TOP_LEFT, RESIZE_TOP_RIGHT, SLOT -> {
                // Native frames and the drawn frame's filter handle these.
            }
            default -> {
                if (hit.item == null || hit.item.getEnabled()) {
                    pressed = hit;
                    framePressed = false;
                    redraw();
                }
            }
        }
    }

    private void mouseUp(Event event) {
        if (event.button != 1) {
            return;
        }
        frame.captionReleased(event);
        Hit was = pressed;
        if (was == null) {
            return;
        }
        pressed = null;
        framePressed = false;
        redraw();
        Hit hit = hitAt(event.x, event.y);
        if (hit != null && hit.part == was.part && hit.item == was.item) {
            activate(hit, event.x);
        }
    }

    private void mouseDoubleClick(Event event) {
        if (event.button != 1) {
            return;
        }
        Hit hit = hitAt(event.x, event.y);
        if (hit == null && event.y >= rowHeight()) {
            return;
        }
        if (hit == null || hit.part == Part.TITLE && !hasTitleMenu()) {
            frame.captionDoubleClicked();
        } else if (hit.part == Part.SYSTEM_MENU && frame.mode() != FrameMode.EMBEDDED) {
            close();
        }
    }

    private void menuDetect(Event event) {
        Point point = toControl(event.x, event.y);
        Hit hit = hitAt(point.x, point.y);
        if (frame.mode() != FrameMode.EMBEDDED && point.y < rowHeight() && (hit == null || hit.part == Part.TITLE || hit.part == Part.SYSTEM_MENU)) {
            event.doit = false;
            frame.openSystemMenu(new Point(event.x, event.y));
        }
    }

    /** Does what clicking {@code hit} does; {@code x} tells a drop-down item's arrow from its button. */
    void activate(Hit hit, int x) {
        switch (hit.part) {
            case ITEM -> hit.item.activate(hit.item.isDropDown() && x >= hit.bounds.x + hit.bounds.width - hit.item.arrowWidth);
            case CUSTOMIZE -> openCustomizeMenu();
            case TITLE -> openTitleMenu();
            case SEARCH -> focusSearch();
            case AVATAR -> openProfile();
            case MINIMIZE -> minimize();
            case MAXIMIZE -> toggleMaximize();
            case CLOSE -> close();
            case SYSTEM_MENU -> openSystemMenu();
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ keyboard

    /**
     * Like a native caption, the bar is not a tab stop and doesn't take the focus when its shell opens:
     * {@link #focusBar()} (F6 by default) moves the focus to it. Its search field and slot controls are
     * ordinary tab stops.
     */
    @Override
    public boolean setFocus() {
        checkWidget();
        return focusRequested && forceFocus();
    }

    /**
     * Moves the keyboard focus to the bar's parts (arrow keys move between them, Enter or Space
     * activates, Escape or the focus key goes back to where the focus was).
     */
    public void focusBar() {
        checkWidget();
        Control focus = getDisplay().getFocusControl();
        if (focus != null && focus != this && !isAncestorOf(focus)) {
            focusReturn = focus;
        }
        focusRequested = true;
        keyboardFocus = true;
        try {
            forceFocus();
        } finally {
            focusRequested = false;
        }
        if (focusIndex < 0 && !focusables().isEmpty()) {
            focusIndex = 0;
        }
        redraw();
    }

    /** Gives the focus back to the control that had it before {@link #focusBar()}. */
    void returnFocus() {
        if (focusReturn != null && !focusReturn.isDisposed() && focusReturn.setFocus()) {
            return;
        }
        shell.traverse(SWT.TRAVERSE_TAB_NEXT);
    }

    public int getFocusKey() {
        checkWidget();
        return focusKey;
    }

    /** The key that moves the focus to the bar and back ({@link SWT#F6} by default; {@link SWT#NONE} for none). */
    public void setFocusKey(int keyCode) {
        checkWidget();
        this.focusKey = keyCode;
    }

    private boolean isAncestorOf(Control control) {
        for (Control c = control; c != null; c = c.getParent()) {
            if (c == this) {
                return true;
            }
        }
        return false;
    }

    private void focusKey(Event event) {
        if (focusKey == SWT.NONE || event.keyCode != focusKey || (event.stateMask & SWT.MODIFIER_MASK) != 0
                || isDisposed() || getDisplay().getActiveShell() != shell) {
            return;
        }
        event.doit = false;
        event.type = SWT.None;
        if (isFocusControl()) {
            returnFocus();
        } else {
            focusBar();
        }
    }

    /** The parts arrow keys move between, in visual order. */
    List<Hit> focusables() {
        List<Hit> list = new ArrayList<>();
        for (Hit hit : hits) {
            boolean focusable = switch (hit.part) {
                case ITEM -> hit.item.getEnabled();
                case TITLE -> hasTitleMenu();
                case SYSTEM_MENU, CUSTOMIZE, SEARCH, AVATAR, MINIMIZE, MAXIMIZE, CLOSE -> true;
                default -> false;
            };
            if (focusable) {
                list.add(hit);
            }
        }
        list.sort((a, b) -> Integer.compare(a.bounds.x, b.bounds.x));
        return list;
    }

    private void focusIn() {
        if (focusIndex < 0 && !focusables().isEmpty()) {
            focusIndex = 0;
        }
        redraw();
        if (focusIndex >= 0) {
            getAccessible().setFocus(focusIndex);
        }
    }

    private void keyDown(Event event) {
        List<Hit> list = focusables();
        if (list.isEmpty()) {
            return;
        }
        switch (event.keyCode) {
            case SWT.ARROW_LEFT -> moveFocus(list, (getStyle() & SWT.RIGHT_TO_LEFT) != 0 ? 1 : -1);
            case SWT.ARROW_RIGHT -> moveFocus(list, (getStyle() & SWT.RIGHT_TO_LEFT) != 0 ? -1 : 1);
            case SWT.HOME -> moveFocus(list, -focusIndex);
            case SWT.END -> moveFocus(list, list.size() - 1 - focusIndex);
            default -> {
                if (event.character == ' ' || event.character == SWT.CR || event.keyCode == SWT.KEYPAD_CR
                        || event.keyCode == SWT.ARROW_DOWN && (event.stateMask & SWT.ALT) != 0) {
                    if (focusIndex >= 0 && focusIndex < list.size()) {
                        Hit hit = list.get(focusIndex);
                        boolean arrow = event.keyCode == SWT.ARROW_DOWN;
                        activate(hit, arrow ? hit.bounds.x + hit.bounds.width - 1 : hit.bounds.x);
                    }
                }
            }
        }
    }

    private void moveFocus(List<Hit> list, int delta) {
        keyboardFocus = true;
        focusIndex = Math.floorMod(Math.max(0, focusIndex) + delta, list.size());
        redraw();
        getAccessible().setFocus(focusIndex);
    }

    private void traverse(Event event) {
        switch (event.detail) {
            case SWT.TRAVERSE_ESCAPE -> {
                event.doit = false;
                returnFocus();
            }
            case SWT.TRAVERSE_TAB_NEXT, SWT.TRAVERSE_TAB_PREVIOUS, SWT.TRAVERSE_PAGE_NEXT, SWT.TRAVERSE_PAGE_PREVIOUS,
                    SWT.TRAVERSE_MNEMONIC -> event.doit = true;
            case SWT.TRAVERSE_RETURN, SWT.TRAVERSE_ARROW_NEXT, SWT.TRAVERSE_ARROW_PREVIOUS -> event.doit = false;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ accessibility

    String accessibleName(Hit hit) {
        return switch (hit.part) {
            case ITEM -> hit.item.plainText();
            case SYSTEM_MENU -> "System menu";
            case TITLE -> title + ", document menu";
            case SEARCH -> searchHint;
            case AVATAR -> profile != null ? "Account: " + profile.name() : "Account";
            default -> tooltip(hit);
        };
    }

    private void initAccessible() {
        getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                List<Hit> list = focusables();
                if (e.childID == ACC.CHILDID_SELF) {
                    e.result = title.isEmpty() ? "Title bar" : title;
                } else if (e.childID >= 0 && e.childID < list.size()) {
                    e.result = accessibleName(list.get(e.childID));
                }
            }

            @Override
            public void getHelp(AccessibleEvent e) {
                List<Hit> list = focusables();
                if (e.childID >= 0 && e.childID < list.size()) {
                    e.result = tooltip(list.get(e.childID));
                }
            }
        });
        getAccessible().addAccessibleControlListener(new AccessibleControlAdapter() {
            @Override
            public void getChildCount(AccessibleControlEvent e) {
                e.detail = focusables().size();
            }

            @Override
            public void getChildren(AccessibleControlEvent e) {
                int count = focusables().size();
                Object[] children = new Object[count];
                for (int i = 0; i < count; i++) {
                    children[i] = i;
                }
                e.children = children;
            }

            @Override
            public void getChildAtPoint(AccessibleControlEvent e) {
                Point point = toControl(e.x, e.y);
                List<Hit> list = focusables();
                e.childID = ACC.CHILDID_SELF;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).bounds.contains(point)) {
                        e.childID = i;
                    }
                }
            }

            @Override
            public void getLocation(AccessibleControlEvent e) {
                List<Hit> list = focusables();
                Rectangle bounds = e.childID >= 0 && e.childID < list.size() ? list.get(e.childID).bounds : getClientArea();
                Point origin = toDisplay(bounds.x, bounds.y);
                e.x = origin.x;
                e.y = origin.y;
                e.width = bounds.width;
                e.height = bounds.height;
            }

            @Override
            public void getRole(AccessibleControlEvent e) {
                List<Hit> list = focusables();
                if (e.childID >= 0 && e.childID < list.size()) {
                    Hit hit = list.get(e.childID);
                    e.detail = hit.part == Part.ITEM && hit.item.isCheck() ? ACC.ROLE_CHECKBUTTON
                            : hit.part == Part.SEARCH ? ACC.ROLE_TEXT : ACC.ROLE_PUSHBUTTON;
                } else {
                    e.detail = ACC.ROLE_TOOLBAR;
                }
            }

            @Override
            public void getState(AccessibleControlEvent e) {
                List<Hit> list = focusables();
                int state = ACC.STATE_NORMAL;
                if (e.childID >= 0 && e.childID < list.size()) {
                    Hit hit = list.get(e.childID);
                    state = ACC.STATE_FOCUSABLE;
                    if (isFocusControl() && e.childID == focusIndex) {
                        state |= ACC.STATE_FOCUSED;
                    }
                    if (hit.part == Part.ITEM && hit.item.isCheck() && hit.item.getSelection()) {
                        state |= ACC.STATE_CHECKED;
                    }
                }
                e.detail = state;
            }

            @Override
            public void getFocus(AccessibleControlEvent e) {
                e.childID = isFocusControl() ? (focusIndex >= 0 ? focusIndex : ACC.CHILDID_SELF) : ACC.CHILDID_NONE;
            }

            @Override
            public void getDefaultAction(AccessibleControlEvent e) {
                e.result = e.childID == ACC.CHILDID_SELF ? null : "Press";
            }
        });
    }
}
