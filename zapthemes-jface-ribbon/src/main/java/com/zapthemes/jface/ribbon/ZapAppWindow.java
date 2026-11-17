package com.zapthemes.jface.ribbon;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.jface.TitleBarWindow;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandKeyBindings;
import com.zapthemes.widgets.command.CustomizationStore;
import com.zapthemes.widgets.ribbon.Backstage;
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.titlebar.TitleBar;

/**
 * JFace's {@link org.eclipse.jface.window.ApplicationWindow} with the Office look: the
 * {@link TitleBar} with its quick access toolbar at the top, a {@link Ribbon} in place of the menu
 * bar and the tool bar or cool bar, the contents, and JFace's status line at the bottom. Change an
 * application window's base class to this one: its menus and tool bar become the ribbon
 * ({@link RibbonConversion}). Then adjust or replace that ribbon in {@link #createRibbon(Ribbon)}
 * and add backstage pages in {@link #createBackstage(Backstage)}.
 *
 * <pre>{@code
 * class Editor extends ZapAppWindow {
 *     Editor() { super(null); addStatusLine(); }
 *     protected void createRibbon(Ribbon ribbon) {
 *         RibbonTab home = ribbon.addTab("home", "Home");
 *         RibbonContributions.add(home.addGroup("home.clipboard", "Clipboard"), pasteAction).large();
 *     }
 * }
 * }</pre>
 *
 * <p>What the {@code ApplicationWindow} calls do here:
 *
 * <table>
 *   <caption>ApplicationWindow calls</caption>
 *   <tr><th>Call</th><th>In a ZapAppWindow</th></tr>
 *   <tr><td>{@code addMenuBar()}, {@code createMenuManager()}, {@code getMenuBarManager()}</td>
 *       <td>The manager is created and kept, and no native menu bar is set on the shell. Its File
 *       menu becomes the backstage and its other menus tabs ({@link RibbonConversion}).</td></tr>
 *   <tr><td>{@code addToolBar(int)}, {@code addCoolBar(int)}, {@code create*Manager*}, {@code get*Manager*}</td>
 *       <td>The managers are created and kept, and no {@code ToolBar} or {@code CoolBar} is created; their
 *       items become a Home tab, first. The ribbon is the tool bar control, so JFace's layout puts it
 *       under the title bar.</td></tr>
 *   <tr><td>{@code addStatusLine()}, {@code getStatusLineManager()}, {@code setStatus(String)}</td>
 *       <td>Unchanged: the status line stays at the bottom, and shows the description of the ribbon
 *       command under the mouse ({@link #setRibbonStatusMessages}).</td></tr>
 *   <tr><td>{@code run(fork, cancelable, runnable)}</td>
 *       <td>As in JFace, with the ribbon and the commands' shortcuts disabled while it runs.</td></tr>
 * </table>
 *
 * <p>Command shortcuts (an action's accelerator) run through {@link CommandKeyBindings}, since there
 * is no menu bar to run them. The backstage, opened from the File tab, covers the window below
 * the title bar.
 */
public class ZapAppWindow extends TitleBarWindow {

    /** The id of the backstage's Close entry when the window has no File menu. */
    public static final String CLOSE_COMMAND = RibbonConversion.PREFIX + "file.close";

    private MenuManager menuBarManager;
    private Ribbon ribbon;
    private RibbonConversion conversion;
    private boolean ribbonStatusMessages = true;
    private boolean showingDescription;

    public ZapAppWindow(Shell parentShell) {
        super(parentShell);
    }

    // ------------------------------------------------------------------ the ribbon

    /** Fills the ribbon; called once, while the shell is created, after the title bar exists. */
    protected void createRibbon(Ribbon ribbon) {
    }

    /** The ribbon; {@code null} until the shell is created. */
    public Ribbon getRibbon() {
        return ribbon;
    }

    /** Registers {@code action} as a command of this window's ribbon (see {@link ActionCommands}). */
    protected Command command(IAction action) {
        if (ribbon == null) {
            throw new IllegalStateException("The ribbon is created with the shell: register actions in createRibbon");
        }
        return ActionCommands.register(ribbon.commands(), action, ribbon);
    }

    /**
     * The file users' ribbon and quick access toolbar customizations are kept in, a
     * {@link CustomizationStore} with a section per bar; {@code null} (the default) keeps none. The
     * quick access toolbar keeps its own in a separate file only when
     * {@link #getTitleBarPreferences()} names a different one.
     */
    protected Path getRibbonPreferences() {
        return null;
    }

    // ------------------------------------------------------------------ ApplicationWindow

    /** Creates and keeps the menu manager ({@link #createMenuManager()}); no native menu bar is made. */
    @Override
    protected void addMenuBar() {
        if (getShell() == null && menuBarManager == null) {
            menuBarManager = createMenuManager();
        }
    }

    /** The menu manager from {@link #addMenuBar()}, shown by no native menu bar; {@code null} without one. */
    @Override
    public MenuManager getMenuBarManager() {
        return menuBarManager;
    }

    /**
     * Creates the ribbon where the tool bar would be (the tool bar manager, if any, gets no control),
     * fills it from the menu and tool bar managers ({@link #convertMenus()}), sets the File tab, then
     * calls {@link #createRibbon(Ribbon)}.
     */
    @Override
    protected Control createToolBarControl(Composite parent) {
        ribbon = new Ribbon(parent, SWT.NONE);
        TitleBar titleBar = getTitleBar();
        if (titleBar != null) {
            ribbon.setBackstageHeader(titleBar);
        }
        ribbon.addHoverListener(this::ribbonHovered);
        conversion = createRibbonConversion();
        if (convertMenus()) {
            conversion.fillTabs(ribbon, menuBarManager, toolBarOrCoolBar());
        }
        setFileTab();
        createRibbon(ribbon);
        return ribbon;
    }

    private IContributionManager toolBarOrCoolBar() {
        return getToolBarManager2() != null ? getToolBarManager2() : getCoolBarManager2();
    }

    private void setFileTab() {
        String label = getFileTabLabel();
        if (label == null) {
            return;
        }
        ribbon.setFileTab(label, backstage -> {
            if (convertMenus()) {
                conversion.fillBackstage(backstage, menuBarManager);
            }
            createBackstage(backstage);
            if (backstage.entryIds().isEmpty()) {
                // Neither a File menu nor the app gave it entries: one to close the window.
                Command close = ribbon.commands().find(CLOSE_COMMAND).orElse(null);
                if (close == null) {
                    close = ribbon.commands().add(new Command(CLOSE_COMMAND, "&Close").onRun(e -> close()));
                }
                backstage.addCommand(close.id());
            }
        });
    }

    /**
     * Builds the ribbon's automatic part again from the menu and tool bar managers, after the app
     * added or removed items in them (enabled, checked and label changes need no call: they reach
     * the ribbon through the actions). Groups and tabs {@code createRibbon} added stay; the backstage
     * is made again on its next use.
     */
    public void refreshRibbon() {
        if (ribbon == null || ribbon.isDisposed() || !convertMenus()) {
            return;
        }
        conversion.clear(ribbon);
        conversion.fillTabs(ribbon, menuBarManager, toolBarOrCoolBar());
        setFileTab();
    }

    /**
     * Whether the menu bar and tool bar managers are turned into the ribbon's tabs, groups and
     * backstage; {@code true}. Return {@code false} when {@code createRibbon} builds it all and the
     * managers are kept only for other code.
     */
    protected boolean convertMenus() {
        return true;
    }

    /** The rules {@link #convertMenus() conversion} follows; override to change them. */
    protected RibbonConversion createRibbonConversion() {
        return new RibbonConversion();
    }

    /** The File tab's label, {@code "File"}; {@code null} for no File tab and no backstage. */
    protected String getFileTabLabel() {
        return "File";
    }

    /**
     * Adds the app's own backstage pages (Info, New, Open, Options); called when the backstage is
     * first used, after the File menu's entries. A backstage left with no entries gets a
     * <em>Close</em> entry for the window.
     */
    protected void createBackstage(Backstage backstage) {
    }

    /** Whether hovering a ribbon command shows its description in the status line; on by default. */
    public void setRibbonStatusMessages(boolean show) {
        ribbonStatusMessages = show;
    }

    private void ribbonHovered(Command command) {
        if (!ribbonStatusMessages || getStatusLineManager() == null) {
            return;
        }
        String description = command == null ? null : command.description();
        if (description != null && !description.isBlank()) {
            getStatusLineManager().setMessage(description);
            showingDescription = true;
        } else if (showingDescription) {
            getStatusLineManager().setMessage(null);
            showingDescription = false;
        }
    }

    /** No cool bar: the cool bar manager, if any, gets no control. */
    @Override
    protected Control createCoolBarControl(Composite composite) {
        return null;
    }

    @Override
    protected Control getToolBarControl() {
        return ribbon;
    }

    @Override
    protected Control getCoolBarControl() {
        return null;
    }

    @Override
    protected boolean toolBarChildrenExist() {
        return ribbon != null && !ribbon.isDisposed();
    }

    @Override
    protected boolean coolBarChildrenExist() {
        return false;
    }

    @Override
    protected void createTrimWidgets(Shell shell) {
        boolean resized = resizeHasOccurred;
        super.createTrimWidgets(shell);
        Path preferences = getRibbonPreferences();
        if (preferences != null && ribbon != null) {
            CustomizationStore store = CustomizationStore.open(preferences);
            ribbon.setCustomizationStore(store);
            TitleBar titleBar = getTitleBar();
            if (titleBar != null && (titleBar.getPreferences() == null || titleBar.getPreferences().equals(preferences))) {
                // One file, a section per bar; the title bar's own file format is a different one.
                titleBar.setPreferences(null);
                titleBar.setCustomizationStore(store);
            }
        }
        resizeHasOccurred = resized;
    }

    /** As JFace's, with the commands' shortcuts off while it runs (the ribbon is disabled by JFace). */
    @Override
    public void run(boolean fork, boolean cancelable, IRunnableWithProgress runnable)
            throws InvocationTargetException, InterruptedException {
        CommandKeyBindings bindings = ribbon == null || ribbon.isDisposed() ? null
                : CommandKeyBindings.install(ribbon.getShell(), ribbon.commands());
        boolean wasEnabled = bindings != null && bindings.isEnabled();
        if (bindings != null) {
            bindings.setEnabled(false);
        }
        try {
            super.run(fork, cancelable, runnable);
        } finally {
            if (bindings != null) {
                bindings.setEnabled(wasEnabled);
            }
        }
    }

    @Override
    public boolean close() {
        MenuManager menus = menuBarManager;
        if (!super.close()) {
            return false;
        }
        if (menus != null) {
            menus.dispose();
        }
        menuBarManager = null;
        ribbon = null;
        return true;
    }
}
