package com.zapthemes.widgets.titlebar;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandEvent;
import com.zapthemes.widgets.command.CommandListener;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.CommandRegistry;

/**
 * A {@link TitleBarItem} bound to a {@link Command}, the model the ribbon uses: the label, tooltip,
 * image, enabled and checked state follow the command, and clicking the item runs it. The item's id
 * is the command's, so customizations store command ids.
 *
 * <ul>
 *   <li>{@link Command.Style#TOGGLE} commands are check items.</li>
 *   <li>{@link Command.Style#SPLIT} commands are drop-down items: the main part runs the command,
 *       the arrow opens its {@link CommandMenu}. {@link Command.Style#DROP_DOWN} commands open the menu
 *       from anywhere on the item.</li>
 *   <li>A menu's ids are looked up in the shell's {@link CommandRegistry}.</li>
 * </ul>
 *
 * The command's {@code visible} state is the app's to keep, not the user's: the item doesn't follow
 * it (whether the item shows is the user's quick access customization). Disposing the item stops
 * listening to the command.
 */
public final class CommandTitleBarItem {

    private static final String KEY = CommandTitleBarItem.class.getName();

    private final Command command;
    private final TitleBarItem item;
    private final CommandListener listener = (c, property) -> update(property);

    private CommandTitleBarItem(TitleBar bar, Command command, int extraStyle) {
        this.command = command;
        this.item = new TitleBarItem(bar, styleOf(command) | extraStyle);
        item.setId(command.id());
        item.setData(KEY, this);
        update(null);
        command.addListener(listener);
        item.addListener(SWT.Selection, this::selected);
        item.addListener(SWT.Dispose, e -> command.removeListener(listener));
    }

    /** A new quick access item on {@code bar} for {@code command}. */
    public static CommandTitleBarItem create(TitleBar bar, Command command) {
        return create(bar, command, SWT.NONE);
    }

    /** A new item for {@code command}; {@link SWT#TRAIL} in {@code extraStyle} puts it on the right. */
    public static CommandTitleBarItem create(TitleBar bar, Command command, int extraStyle) {
        return new CommandTitleBarItem(bar, command, extraStyle);
    }

    /** The binding of {@code item}, or {@code null} if it isn't bound to a command. */
    public static CommandTitleBarItem of(TitleBarItem item) {
        return item != null && !item.isDisposed() && item.getData(KEY) instanceof CommandTitleBarItem bound ? bound : null;
    }

    public Command command() {
        return command;
    }

    public TitleBarItem item() {
        return item;
    }

    static int styleOf(Command command) {
        return switch (command.style()) {
            case TOGGLE -> SWT.CHECK;
            case SPLIT, DROP_DOWN -> SWT.DROP_DOWN;
            default -> SWT.PUSH;
        };
    }

    /** Copies the command's {@code property} (all of them for {@code null}) to the item. */
    void update(String property) {
        if (item.isDisposed()) {
            return;
        }
        if (item.getDisplay().getThread() != Thread.currentThread()) {
            item.getDisplay().asyncExec(() -> update(property));
            return;
        }
        if (property == null || Command.LABEL.equals(property)) {
            item.setText(command.label());
        }
        if (property == null || Command.LABEL.equals(property) || Command.DESCRIPTION.equals(property)
                || Command.SHORTCUT.equals(property)) {
            item.setToolTipText(toolTip(command));
        }
        if (property == null || Command.IMAGES.equals(property)) {
            item.setImage(command.images().image(16));
        }
        if (property == null || Command.ENABLED.equals(property)) {
            item.setEnabled(command.isEnabled());
        }
        if (property == null || Command.CHECKED.equals(property)) {
            item.setSelection(command.isChecked());
        }
    }

    /** {@code "Save (Ctrl+S)"}, then the description on its own line. */
    static String toolTip(Command command) {
        String text = command.plainLabel();
        String shortcut = command.shortcutText();
        if (!shortcut.isEmpty()) {
            text += " (" + shortcut + ")";
        }
        String description = command.description();
        if (description != null && !description.isEmpty()) {
            text += "\n" + description;
        }
        return text;
    }

    private void selected(Event event) {
        boolean arrow = event.detail == SWT.ARROW;
        if (command.menu() != null && (arrow || command.style() == Command.Style.DROP_DOWN)) {
            openMenu();
            return;
        }
        // A check item has flipped its selection already; Command.run flips the command, whose
        // CHECKED change brings the item back in line with it.
        command.run(new CommandEvent(command, item.getParent(), event, arrow, null));
        if (!item.isDisposed()) {
            item.setSelection(command.isChecked());
        }
    }

    /** Opens the command's menu under the item. */
    void openMenu() {
        TitleBar bar = item.getParent();
        boolean owned = !(command.menu() instanceof CommandMenu.Popup);
        org.eclipse.swt.widgets.Menu menu = createMenu();
        if (menu == null) {
            return;
        }
        org.eclipse.swt.graphics.Rectangle bounds = item.getBounds();
        Point at = bar.toDisplay(bounds.x, bounds.y + bounds.height);
        menu.setLocation(at);
        if (owned) {
            Listener[] hide = new Listener[1];
            hide[0] = e -> bar.getDisplay().asyncExec(() -> {
                if (!menu.isDisposed()) {
                    menu.dispose();
                }
            });
            menu.addListener(SWT.Hide, hide[0]);
        }
        menu.setVisible(true);
    }

    /**
     * The command's menu, not yet shown: built from its ids (looked up in the shell's registry) or
     * its filler, or made by its popup factory; {@code null} when there is none.
     */
    org.eclipse.swt.widgets.Menu createMenu() {
        TitleBar bar = item.getParent();
        CommandMenu model = command.menu();
        if (model == null) {
            return null;
        }
        if (model instanceof CommandMenu.Popup popup) {
            return popup.factory().apply(bar);
        }
        Menu created = new Menu(bar.getShell(), SWT.POP_UP);
        if (model instanceof CommandMenu.Ids ids) {
            CommandRegistry registry = CommandRegistry.of(bar.getShell());
            for (String id : ids.ids()) {
                if (id.equals(CommandMenu.SEPARATOR)) {
                    new MenuItem(created, SWT.SEPARATOR);
                } else {
                    registry.find(id).filter(Command::isVisible).ifPresent(c -> addMenuItem(created, c));
                }
            }
        } else if (model instanceof CommandMenu.Filler filler) {
            filler.filler().accept(created);
        }
        return created;
    }

    private void addMenuItem(Menu menu, Command entry) {
        MenuItem menuItem = new MenuItem(menu, entry.style() == Command.Style.TOGGLE ? SWT.CHECK : SWT.PUSH);
        String shortcut = entry.shortcutText();
        menuItem.setText(entry.label() + (shortcut.isEmpty() ? "" : "\t" + shortcut));
        Image image = entry.images().image(16);
        if (image != null && entry.style() != Command.Style.TOGGLE) {
            menuItem.setImage(image);
        }
        menuItem.setEnabled(entry.isEnabled());
        menuItem.setSelection(entry.isChecked());
        menuItem.addListener(SWT.Selection, e -> entry.run(new CommandEvent(entry, item.getParent(), e, false, null)));
    }
}
