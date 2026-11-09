package com.zapthemes.jface;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Layout;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.widgets.titlebar.TitleBar;
import com.zapthemes.widgets.titlebar.TitleBarItem;
import com.zapthemes.widgets.titlebar.TitleBarProfile;

/**
 * An {@link ApplicationWindow} with an Office-style {@link TitleBar} in place of the native caption,
 * above its tool bar or cool bar, contents and status line.
 *
 * <ul>
 *   <li>{@link #createTitleBar(TitleBar)} is the hook for the title, accent, search, slots and
 *       profile; {@link #addQuickAccess(IAction)} puts actions in the quick access toolbar, kept in
 *       sync with the action (text, image, tooltip, enabled, checked).</li>
 *   <li>{@link #setTitleMenu(MenuManager)} gives the title its document menu. A menu bar
 *       ({@link #addMenuBar()}) becomes the title menu too, since the bar replaces the caption the
 *       native menu bar would hang from.</li>
 *   <li>{@link #getTitleBarPreferences()} names the file users' toolbar customizations go in.</li>
 *   <li>The shell is created with {@link TitleBar#shellStyle(int)}; the window's title is the
 *       shell's text until {@code getTitleBar().setTitle} says otherwise.</li>
 * </ul>
 */
public class TitleBarWindow extends ApplicationWindow {

    private TitleBar titleBar;
    private final List<IAction> pendingQuickAccess = new ArrayList<>();
    private MenuManager titleMenuManager;

    public TitleBarWindow(Shell parentShell) {
        super(parentShell);
        setShellStyle(TitleBar.shellStyle(getShellStyle()));
    }

    @Override
    public void setShellStyle(int newShellStyle) {
        super.setShellStyle(TitleBar.shellStyle(newShellStyle));
    }

    /** The bar; {@code null} until the shell is created. */
    public TitleBar getTitleBar() {
        return titleBar;
    }

    /** Called once the bar exists, before the tool bar and contents: set it up here. */
    protected void createTitleBar(TitleBar bar) {
    }

    /** The file the quick access customizations are kept in; {@code null} (the default) keeps none. */
    protected Path getTitleBarPreferences() {
        return null;
    }

    /**
     * Adds {@code action} to the quick access toolbar: a check item for {@link IAction#AS_CHECK_BOX}
     * actions, a drop-down for {@link IAction#AS_DROP_DOWN_MENU}, else a push item. Before the shell
     * exists the action is remembered and added then; the item is returned once it exists.
     */
    public TitleBarItem addQuickAccess(IAction action) {
        if (titleBar == null || titleBar.isDisposed()) {
            pendingQuickAccess.add(action);
            return null;
        }
        return ActionTitleBarItem.create(titleBar, action, SWT.NONE).item();
    }

    /** Adds {@code action} as a quick access command that users can show from the customize menu. */
    public TitleBarItem addQuickAccess(IAction action, boolean shown) {
        TitleBarItem item = addQuickAccess(action);
        if (item != null) {
            item.setShown(shown);
        } else if (!shown) {
            pendingHidden.add(action);
        }
        return item;
    }

    private final List<IAction> pendingHidden = new ArrayList<>();

    /** The title's document menu; its contents are built from the manager each time it opens. */
    public void setTitleMenu(MenuManager manager) {
        this.titleMenuManager = manager;
        if (titleBar != null && !titleBar.isDisposed()) {
            installTitleMenu();
        }
    }

    /** A profile action that runs a JFace action. */
    public static TitleBarProfile.Action profileAction(IAction action) {
        return new TitleBarProfile.Action(action.getText().replace("&", ""), action::run);
    }

    @Override
    protected boolean showTopSeperator() {
        return false;
    }

    @Override
    protected void createTrimWidgets(Shell shell) {
        // Installing the native frame resizes the client area, which would make Window skip
        // initializeBounds (it keeps sizes set before opening); ApplicationWindow does the same for
        // its menu bar.
        boolean resized = resizeHasOccurred;
        titleBar = new TitleBar(shell, SWT.NONE);
        for (IAction action : pendingQuickAccess) {
            TitleBarItem item = ActionTitleBarItem.create(titleBar, action, SWT.NONE).item();
            if (pendingHidden.contains(action)) {
                item.setShown(false);
            }
        }
        pendingQuickAccess.clear();
        pendingHidden.clear();
        createTitleBar(titleBar);
        super.createTrimWidgets(shell);
        // The native menu bar would sit between the frame and the bar: its menus move to the title.
        org.eclipse.swt.widgets.Menu menuBar = shell.getMenuBar();
        if (menuBar != null && titleMenuManager == null && getMenuBarManager() != null) {
            shell.setMenuBar(null);
            titleMenuManager = getMenuBarManager();
        }
        if (titleMenuManager != null) {
            installTitleMenu();
        }
        Path preferences = getTitleBarPreferences();
        if (preferences != null) {
            titleBar.setPreferences(preferences);
        }
        resizeHasOccurred = resized;
    }

    private void installTitleMenu() {
        Menu old = titleBar.getTitleMenu();
        Menu menu = titleMenuManager.createContextMenu(titleBar);
        titleBar.setTitleMenu(menu);
        if (old != null && old != menu && !old.isDisposed()) {
            old.dispose();
        }
    }

    @Override
    protected Layout getLayout() {
        return new TitleBarWindowLayout();
    }

    /**
     * {@code ApplicationWindowLayout} with the bar first: the bar across the top, then the tool bar
     * or cool bar, the contents and the status line, as JFace lays them out.
     */
    private final class TitleBarWindowLayout extends Layout {
        private static final int VGAP = 2;
        private static final int BAR_SIZE = 23;

        @Override
        protected Point computeSize(Composite composite, int wHint, int hHint, boolean flushCache) {
            if (wHint != SWT.DEFAULT && hHint != SWT.DEFAULT) {
                return new Point(wHint, hHint);
            }
            Point result = new Point(0, 0);
            for (Control w : composite.getChildren()) {
                if (w == titleBar) {
                    Point e = w.computeSize(wHint, SWT.DEFAULT, flushCache);
                    result.y += e.y;
                    continue;
                }
                boolean hide = false;
                if (getToolBarControl() == w && !toolBarChildrenExist()) {
                    hide = true;
                    result.y += BAR_SIZE;
                } else if (getCoolBarControl() == w && !coolBarChildrenExist()) {
                    hide = true;
                    result.y += BAR_SIZE;
                }
                if (!hide) {
                    Point e = w.computeSize(wHint, hHint, flushCache);
                    result.x = Math.max(result.x, e.x);
                    result.y += e.y + VGAP;
                }
            }
            if (wHint != SWT.DEFAULT) {
                result.x = wHint;
            }
            if (hHint != SWT.DEFAULT) {
                result.y = hHint;
            }
            return result;
        }

        @Override
        protected void layout(Composite composite, boolean flushCache) {
            Rectangle area = composite.getClientArea();
            Control status = getStatusLineManager() != null ? getStatusLineManager().getControl() : null;
            for (Control w : composite.getChildren()) {
                if (w == titleBar) {
                    int h = w.computeSize(area.width, SWT.DEFAULT, flushCache).y;
                    w.setBounds(area.x, area.y, area.width, h);
                    area.y += h;
                    area.height -= h;
                } else if (w == getSeperator1()) {
                    Point e = w.computeSize(SWT.DEFAULT, SWT.DEFAULT, flushCache);
                    w.setBounds(area.x, area.y, area.width, e.y);
                    area.y += e.y;
                    area.height -= e.y;
                } else if (getToolBarControl() == w) {
                    if (toolBarChildrenExist()) {
                        Point e = w.computeSize(SWT.DEFAULT, SWT.DEFAULT, flushCache);
                        w.setBounds(area.x, area.y, area.width, e.y);
                        area.y += e.y + VGAP;
                        area.height -= e.y + VGAP;
                    }
                } else if (getCoolBarControl() == w) {
                    if (coolBarChildrenExist()) {
                        Point e = w.computeSize(area.width, SWT.DEFAULT, flushCache);
                        w.setBounds(area.x, area.y, area.width, e.y);
                        area.y += e.y + VGAP;
                        area.height -= e.y + VGAP;
                    }
                } else if (status != null && status == w) {
                    Point e = w.computeSize(SWT.DEFAULT, SWT.DEFAULT, flushCache);
                    w.setBounds(area.x, area.y + area.height - e.y, area.width, e.y);
                    area.height -= e.y + VGAP;
                } else {
                    w.setBounds(area.x, area.y + VGAP, area.width, area.height - VGAP);
                }
            }
        }
    }
}
