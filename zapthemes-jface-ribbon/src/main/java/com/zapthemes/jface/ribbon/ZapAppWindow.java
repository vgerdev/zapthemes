package com.zapthemes.jface.ribbon;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import org.eclipse.jface.action.IAction;
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
import com.zapthemes.widgets.ribbon.Ribbon;
import com.zapthemes.widgets.titlebar.TitleBar;

/**
 * JFace's {@link org.eclipse.jface.window.ApplicationWindow} with the Office look: the
 * {@link TitleBar} with its quick access toolbar at the top, a {@link Ribbon} in place of the menu
 * bar and the tool bar or cool bar, the contents, and JFace's status line at the bottom. Change an
 * application window's base class to this one and fill the ribbon in {@link #createRibbon(Ribbon)}.
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
 *       <td>The manager is created and kept, and no native menu bar is set on the shell.</td></tr>
 *   <tr><td>{@code addToolBar(int)}, {@code addCoolBar(int)}, {@code create*Manager*}, {@code get*Manager*}</td>
 *       <td>The managers are created and kept, and no {@code ToolBar} or {@code CoolBar} is created. The
 *       ribbon is the tool bar control, so JFace's layout puts it under the title bar.</td></tr>
 *   <tr><td>{@code addStatusLine()}, {@code getStatusLineManager()}, {@code setStatus(String)}</td>
 *       <td>Unchanged: the status line stays at the bottom.</td></tr>
 *   <tr><td>{@code run(fork, cancelable, runnable)}</td>
 *       <td>As in JFace, with the ribbon and the commands' shortcuts disabled while it runs.</td></tr>
 * </table>
 *
 * <p>Command shortcuts (an action's accelerator) run through {@link CommandKeyBindings}, since there
 * is no menu bar to run them. The backstage, opened from the File tab, covers the window below
 * the title bar.
 */
public class ZapAppWindow extends TitleBarWindow {

    private MenuManager menuBarManager;
    private Ribbon ribbon;

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

    /** Creates the ribbon where the tool bar would be; the tool bar manager, if any, gets no control. */
    @Override
    protected Control createToolBarControl(Composite parent) {
        ribbon = new Ribbon(parent, SWT.NONE);
        TitleBar titleBar = getTitleBar();
        if (titleBar != null) {
            ribbon.setBackstageHeader(titleBar);
        }
        createRibbon(ribbon);
        return ribbon;
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
