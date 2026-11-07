package com.zapthemes.jface.ribbon;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.widgets.Control;

import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.ribbon.RibbonButton;
import com.zapthemes.widgets.ribbon.RibbonGroup;
import com.zapthemes.widgets.ribbon.RibbonRow;

/**
 * Fills ribbon groups and rows from JFace contribution items: an {@link ActionContributionItem}
 * becomes a button for its action ({@link ActionCommands}), a {@link MenuManager} a drop-down
 * button whose menu is the manager's, and a {@link Separator} a line (in rows; groups ignore it).
 * Other items, and invisible ones, are skipped.
 */
public final class RibbonContributions {

    private static final System.Logger LOG = System.getLogger(RibbonContributions.class.getName());

    private RibbonContributions() {
    }

    /** Adds a button per item to {@code group}; returns them. */
    public static List<RibbonButton> fill(RibbonGroup group, IContributionItem... items) {
        List<RibbonButton> buttons = new ArrayList<>();
        for (IContributionItem item : items) {
            Command command = command(group, item);
            if (command != null) {
                buttons.add(group.add(command));
            }
        }
        return buttons;
    }

    /** Adds the items to {@code row}, separators included. */
    public static RibbonRow fill(RibbonRow row, IContributionItem... items) {
        for (IContributionItem item : items) {
            if (item instanceof Separator && item.isVisible()) {
                row.separator();
                continue;
            }
            Command command = command(row.group(), item);
            if (command != null) {
                row.add(command);
            }
        }
        return row;
    }

    /** Adds a button for {@code action}. */
    public static RibbonButton add(RibbonGroup group, IAction action) {
        return group.add(ActionCommands.register(group.ribbon().commands(), action, group.ribbon()));
    }

    private static Command command(RibbonGroup group, IContributionItem item) {
        if (item == null || !item.isVisible()) {
            return null;
        }
        Control owner = group.ribbon();
        if (item instanceof ActionContributionItem actionItem) {
            return ActionCommands.register(group.ribbon().commands(), actionItem.getAction(), owner);
        }
        if (item instanceof MenuManager manager) {
            if (manager.getId() == null) {
                LOG.log(Level.WARNING, "Skipping a menu manager without an id: " + manager.getMenuText());
                return null;
            }
            Command command = group.ribbon().commands().find(manager.getId()).orElse(null);
            if (command == null) {
                String text = manager.getMenuText() == null ? manager.getId() : manager.getMenuText();
                command = new Command(manager.getId(), text).style(Command.Style.DROP_DOWN)
                        .menu(new CommandMenu.Popup(control -> {
                            org.eclipse.swt.widgets.Menu menu = manager.createContextMenu(control);
                            manager.update(true);
                            return menu;
                        }));
                if (manager.getImageDescriptor() != null) {
                    org.eclipse.jface.resource.LocalResourceManager resources = new org.eclipse.jface.resource.LocalResourceManager(
                            org.eclipse.jface.resource.JFaceResources.getResources(), owner);
                    org.eclipse.swt.graphics.Image image = resources.create(manager.getImageDescriptor());
                    command.images(com.zapthemes.widgets.command.Images.of(image));
                }
                group.ribbon().commands().add(command);
            }
            return command;
        }
        if (!(item instanceof Separator)) {
            LOG.log(Level.DEBUG, "Skipping contribution item " + item.getId() + ": only actions and menu managers become ribbon buttons");
        }
        return null;
    }
}
