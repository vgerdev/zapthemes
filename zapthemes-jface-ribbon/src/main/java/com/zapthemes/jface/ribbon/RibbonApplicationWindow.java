package com.zapthemes.jface.ribbon;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.ribbon.Ribbon;

/**
 * A JFace {@link ApplicationWindow} with a ribbon in place of its menu bar and cool bar. Call
 * {@link #addRibbon()} in the constructor (instead of {@code addMenuBar()} and
 * {@code addCoolBar(...)}), and fill the ribbon in {@link #createRibbon(Ribbon)}. The ribbon takes
 * the tool bar's place in the window's layout, the status line stays, and the backstage covers
 * the whole window.
 *
 * <pre>{@code
 * class Editor extends RibbonApplicationWindow {
 *     Editor() { super(null); addRibbon(); addStatusLine(); }
 *     protected void createRibbon(Ribbon ribbon) {
 *         RibbonTab home = ribbon.addTab("home", "Home");
 *         RibbonContributions.add(home.addGroup("home.clipboard", "Clipboard"), pasteAction).large();
 *     }
 * }
 * }</pre>
 */
public class RibbonApplicationWindow extends ApplicationWindow {

    private boolean ribbonWanted;
    private Ribbon ribbon;

    public RibbonApplicationWindow(Shell parentShell) {
        super(parentShell);
    }

    /** Asks for a ribbon; call it from the constructor. */
    protected void addRibbon() {
        ribbonWanted = true;
    }

    /** Fills the ribbon; called once, when the shell is created. */
    protected void createRibbon(Ribbon ribbon) {
    }

    /** The ribbon, once the shell is created; {@code null} without {@link #addRibbon()}. */
    public Ribbon getRibbon() {
        return ribbon;
    }

    /** Registers {@code action} as a command of this window's ribbon (see {@link ActionCommands}). */
    protected Command command(IAction action) {
        return ActionCommands.register(ribbon.commands(), action, ribbon);
    }

    @Override
    protected void createTrimWidgets(Shell shell) {
        super.createTrimWidgets(shell);
        if (ribbonWanted) {
            ribbon = new Ribbon(shell, SWT.NONE);
            createRibbon(ribbon);
        }
    }

    @Override
    protected Control getToolBarControl() {
        return ribbon != null ? ribbon : super.getToolBarControl();
    }

    @Override
    protected boolean toolBarChildrenExist() {
        return ribbon != null || super.toolBarChildrenExist();
    }

    @Override
    protected boolean showTopSeperator() {
        return !ribbonWanted && super.showTopSeperator();
    }
}
