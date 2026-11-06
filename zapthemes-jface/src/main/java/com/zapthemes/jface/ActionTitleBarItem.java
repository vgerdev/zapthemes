package com.zapthemes.jface;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;

import com.zapthemes.widgets.titlebar.TitleBar;
import com.zapthemes.widgets.titlebar.TitleBarItem;

/**
 * A {@link TitleBarItem} bound to an {@link IAction}: text, tooltip, image, enabled and checked follow
 * the action; selecting the item runs it. Drop-down actions open their menu creator's menu from the
 * arrow. Disposing the item stops listening to the action.
 */
public final class ActionTitleBarItem {

    private final IAction action;
    private final TitleBarItem item;
    private final LocalResourceManager resources;
    private final IPropertyChangeListener listener = this::propertyChanged;

    private ActionTitleBarItem(TitleBar bar, IAction action, int extraStyle) {
        this.action = action;
        this.item = new TitleBarItem(bar, styleOf(action) | extraStyle);
        this.resources = new LocalResourceManager(JFaceResources.getResources(bar.getDisplay()));
        if (action.getId() != null) {
            item.setId(action.getId());
        }
        update(null);
        action.addPropertyChangeListener(listener);
        item.addListener(SWT.Selection, this::selected);
        item.addListener(SWT.Dispose, e -> {
            action.removePropertyChangeListener(listener);
            resources.dispose();
        });
    }

    /** Binds {@code action} to a new item on {@code bar}; {@link SWT#TRAIL} in {@code extraStyle} puts it on the right. */
    public static ActionTitleBarItem create(TitleBar bar, IAction action, int extraStyle) {
        return new ActionTitleBarItem(bar, action, extraStyle);
    }

    public TitleBarItem item() {
        return item;
    }

    public IAction action() {
        return action;
    }

    private static int styleOf(IAction action) {
        return switch (action.getStyle()) {
            case IAction.AS_CHECK_BOX -> SWT.CHECK;
            case IAction.AS_DROP_DOWN_MENU -> SWT.DROP_DOWN;
            default -> SWT.PUSH;
        };
    }

    private void propertyChanged(PropertyChangeEvent event) {
        if (item.isDisposed()) {
            return;
        }
        if (item.getDisplay().getThread() != Thread.currentThread()) {
            item.getDisplay().asyncExec(() -> {
                if (!item.isDisposed()) {
                    update(event.getProperty());
                }
            });
            return;
        }
        update(event.getProperty());
    }

    /** Copies the action's {@code property} (all of them for {@code null}) to the item. */
    void update(String property) {
        if (property == null || IAction.TEXT.equals(property)) {
            String text = action.getText() != null ? action.getText() : "";
            // Drop JFace's accelerator suffix ("Save\tCtrl+S"): the bar shows names only.
            int tab = text.indexOf('\t');
            item.setText(tab >= 0 ? text.substring(0, tab) : text);
        }
        if (property == null || IAction.TOOL_TIP_TEXT.equals(property)) {
            item.setToolTipText(action.getToolTipText());
        }
        if (property == null || IAction.IMAGE.equals(property)) {
            ImageDescriptor descriptor = action.getImageDescriptor();
            item.setImage(descriptor != null ? resources.create(descriptor) : null);
        }
        if (property == null || IAction.ENABLED.equals(property)) {
            item.setEnabled(action.isEnabled());
        }
        if (property == null || IAction.CHECKED.equals(property)) {
            item.setSelection(action.isChecked());
        }
    }

    private void selected(Event event) {
        if (event.detail == SWT.ARROW && action.getStyle() == IAction.AS_DROP_DOWN_MENU) {
            IMenuCreator creator = action.getMenuCreator();
            if (creator != null) {
                TitleBar bar = item.getParent();
                Menu menu = creator.getMenu(bar);
                if (menu != null) {
                    Point location = bar.toDisplay(item.getBounds().x, item.getBounds().y + item.getBounds().height);
                    menu.setLocation(location);
                    menu.setVisible(true);
                }
                return;
            }
        }
        if (action.getStyle() == IAction.AS_CHECK_BOX) {
            action.setChecked(item.getSelection());
        }
        action.runWithEvent(event);
    }
}
