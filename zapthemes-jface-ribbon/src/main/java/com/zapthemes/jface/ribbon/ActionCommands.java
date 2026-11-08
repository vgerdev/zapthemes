package com.zapthemes.jface.ribbon;

import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.CommandRegistry;
import com.zapthemes.widgets.command.Images;

/**
 * JFace actions as ribbon and quick access toolbar {@link Command}s. The command follows the
 * action, and the action the command:
 *
 * <ul>
 *   <li>its id is the action's id, else its definition id (one is required: customizations store it);</li>
 *   <li>text (without the accelerator part after a tab), tooltip (as the description), enabled,
 *       checked, image (16 px) and the large image ({@link #setLargeImage}, 32 px) follow the action;</li>
 *   <li>{@code AS_CHECK_BOX} and {@code AS_RADIO_BUTTON} are toggles; {@code AS_DROP_DOWN_MENU}
 *       with an {@link IMenuCreator} is a split button whose arrow opens the creator's menu;</li>
 *   <li>running the command runs the action ({@code runWithEvent}); a toggle's new checked state
 *       is set on the action first, as JFace's own items do.</li>
 * </ul>
 */
public final class ActionCommands {

    private static final Map<IAction, ImageDescriptor> LARGE = new WeakHashMap<>();

    private ActionCommands() {
    }

    /** The image the ribbon draws for {@code action}'s large buttons (32 px). Set it before registering. */
    public static void setLargeImage(IAction action, ImageDescriptor large) {
        LARGE.put(action, large);
    }

    /**
     * Registers a command for {@code action} in {@code registry}, or returns the one already
     * registered for it. Images are created through a resource manager disposed with {@code owner}
     * (usually the ribbon or the shell).
     */
    public static Command register(CommandRegistry registry, IAction action, Control owner) {
        String id = idOf(action);
        Command existing = registry.find(id).orElse(null);
        if (existing != null && SYNCED.containsKey(existing)) {
            return existing;
        }
        Command command = new Command(id, text(action));
        sync(command, action, owner);
        registry.add(command);
        return command;
    }

    private static final Map<Command, Boolean> SYNCED = new WeakHashMap<>();

    /** The command id of {@code action}: its id, else its definition id. */
    public static String idOf(IAction action) {
        String id = action.getId() != null ? action.getId() : action.getActionDefinitionId();
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("A ribbon action needs an id (setId): " + action.getText());
        }
        return id;
    }

    static String text(IAction action) {
        String text = action.getText() == null ? "" : action.getText();
        int tab = text.indexOf('\t');
        return tab >= 0 ? text.substring(0, tab) : text;
    }

    private static void sync(Command command, IAction action, Control owner) {
        SYNCED.put(command, true);
        LocalResourceManager resources = new LocalResourceManager(JFaceResources.getResources(), owner);
        command.style(switch (action.getStyle()) {
            case IAction.AS_CHECK_BOX, IAction.AS_RADIO_BUTTON -> Command.Style.TOGGLE;
            case IAction.AS_DROP_DOWN_MENU -> action.getMenuCreator() != null ? Command.Style.SPLIT : Command.Style.PUSH;
            default -> Command.Style.PUSH;
        });
        command.description(action.getToolTipText() != null ? action.getToolTipText() : action.getDescription());
        command.shortcut(action.getAccelerator());
        command.images(images(action, resources));
        command.setEnabled(action.isEnabled());
        command.setChecked(action.isChecked());
        IMenuCreator creator = action.getMenuCreator();
        if (creator != null) {
            command.menu(new CommandMenu.Popup(creator::getMenu));
        }
        boolean[] updating = { false };
        IPropertyChangeListener listener = event -> {
            if (updating[0] || owner.isDisposed()) {
                return;
            }
            updating[0] = true;
            try {
                switch (event.getProperty()) {
                    case IAction.TEXT -> command.label(text(action));
                    case IAction.ENABLED -> command.setEnabled(action.isEnabled());
                    case IAction.CHECKED -> command.setChecked(action.isChecked());
                    case IAction.IMAGE -> command.images(images(action, resources));
                    case IAction.TOOL_TIP_TEXT, IAction.DESCRIPTION -> command.description(
                            action.getToolTipText() != null ? action.getToolTipText() : action.getDescription());
                    default -> {
                    }
                }
            } finally {
                updating[0] = false;
            }
        };
        action.addPropertyChangeListener(listener);
        owner.addListener(org.eclipse.swt.SWT.Dispose, e -> action.removePropertyChangeListener(listener));
        command.addListener((c, property) -> {
            if (updating[0] || !Command.CHECKED.equals(property)) {
                return;
            }
            updating[0] = true;
            try {
                action.setChecked(c.isChecked());
            } finally {
                updating[0] = false;
            }
        });
        command.onRun(e -> {
            Event event = e.event() != null ? e.event() : new Event();
            if (event.widget == null) {
                event.widget = e.widget();
            }
            action.runWithEvent(event);
        });
    }

    private static Images images(IAction action, LocalResourceManager resources) {
        ImageDescriptor small = action.getImageDescriptor();
        ImageDescriptor large = LARGE.get(action);
        Image[] cache = new Image[2];
        return size -> {
            boolean wantLarge = size > 24;
            ImageDescriptor descriptor = wantLarge && large != null ? large : small != null ? small : large;
            if (descriptor == null || resources.getDevice() == null) {
                return null;
            }
            int slot = descriptor == large ? 1 : 0;
            if (cache[slot] == null || cache[slot].isDisposed()) {
                cache[slot] = resources.create(descriptor);
            }
            return cache[slot];
        };
    }
}
