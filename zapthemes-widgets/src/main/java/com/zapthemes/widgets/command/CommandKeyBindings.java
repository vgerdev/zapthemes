package com.zapthemes.widgets.command;

import java.lang.System.Logger.Level;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

/**
 * Runs a shell's commands by their {@link Command#shortcut() shortcuts}, as a menu bar runs its
 * items' accelerators. A window whose ribbon replaces its menu bar has nothing else to run them:
 * SWT only knows accelerators of menu items.
 *
 * <ul>
 *   <li>A display filter on {@code KeyDown} looks at keys typed in the shell (not in its dialogs or
 *       other shells), matches the key and modifiers against every visible, enabled command, and
 *       runs the match ({@link Command#run}). The key is then consumed, so the focus control does
 *       not also get it, as with a menu accelerator on Windows.</li>
 *   <li>Letters match without regard to case: {@code SWT.MOD1 | 'S'} and {@code SWT.MOD1 | 's'}
 *       are the same shortcut.</li>
 *   <li>Two commands on one shortcut: the one registered first wins, and the clash is logged once.</li>
 *   <li>A shortcut that an item of the shell's own menu bar also has is left to the menu bar, so a
 *       window with both does not run the command twice.</li>
 * </ul>
 *
 * The {@code Ribbon} installs the bindings of its shell; others call {@link #install}.
 */
public final class CommandKeyBindings {

    private static final System.Logger LOG = System.getLogger(CommandKeyBindings.class.getName());
    private static final String KEY = CommandKeyBindings.class.getName();

    private final Shell shell;
    private final CommandRegistry registry;
    private final Listener filter = this::filter;
    private final Set<Integer> reportedClashes = new HashSet<>();
    private boolean enabled = true;

    private CommandKeyBindings(Shell shell, CommandRegistry registry) {
        this.shell = shell;
        this.registry = registry;
    }

    /** The bindings of {@code shell}'s {@link CommandRegistry#of(Shell) registry}. */
    public static CommandKeyBindings install(Shell shell) {
        return install(shell, CommandRegistry.of(shell));
    }

    /**
     * Runs {@code registry}'s commands by their shortcuts while the focus is in {@code shell}, until
     * the shell is disposed. Installing the same pair again returns the bindings already there.
     */
    @SuppressWarnings("unchecked")
    public static CommandKeyBindings install(Shell shell, CommandRegistry registry) {
        Map<CommandRegistry, CommandKeyBindings> installed;
        if (shell.getData(KEY) instanceof Map<?, ?> map) {
            installed = (Map<CommandRegistry, CommandKeyBindings>) map;
        } else {
            installed = new LinkedHashMap<>();
            shell.setData(KEY, installed);
        }
        CommandKeyBindings existing = installed.get(registry);
        if (existing != null) {
            return existing;
        }
        CommandKeyBindings bindings = new CommandKeyBindings(shell, registry);
        installed.put(registry, bindings);
        Display display = shell.getDisplay();
        display.addFilter(SWT.KeyDown, bindings.filter);
        shell.addListener(SWT.Dispose, e -> display.removeFilter(SWT.KeyDown, bindings.filter));
        return bindings;
    }

    public CommandRegistry registry() {
        return registry;
    }

    /** Whether keys run commands; on by default. Apps turn it off while they handle keys themselves. */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * The command that {@code accelerator} ({@code SWT.MOD1 | 'S'}) runs now: the first visible,
     * enabled command with that shortcut, unless the shell's menu bar has it.
     */
    public Optional<Command> find(int accelerator) {
        int wanted = normalize(accelerator);
        if (wanted == 0 || menuBarHas(wanted)) {
            return Optional.empty();
        }
        Command found = null;
        int matches = 0;
        for (Command command : registry.all()) {
            if (command.isVisible() && command.shortcut() != 0 && normalize(command.shortcut()) == wanted) {
                matches++;
                if (found == null && command.isEnabled()) {
                    found = command;
                }
            }
        }
        if (matches > 1 && reportedClashes.add(wanted)) {
            LOG.log(Level.WARNING, () -> Command.acceleratorText(accelerator) + " is the shortcut of " + clashing(wanted)
                    + "; the first one runs");
        }
        return Optional.ofNullable(found);
    }

    /** Every shortcut that runs a command now, with its command, in registration order. */
    public Map<Integer, Command> bindings() {
        Map<Integer, Command> result = new LinkedHashMap<>();
        for (Command command : registry.all()) {
            int key = normalize(command.shortcut());
            if (key != 0 && command.isVisible() && command.isEnabled() && !result.containsKey(key) && !menuBarHas(key)) {
                result.put(key, command);
            }
        }
        return result;
    }

    /**
     * Runs the command for a {@code KeyDown} event typed in the shell, as the display filter does;
     * returns whether one ran. The event is consumed when it did.
     */
    public boolean handle(Event event) {
        if (!enabled || event.type != SWT.KeyDown || !(event.widget instanceof Control control) || control.isDisposed()
                || control.getShell() != shell) {
            return false;
        }
        int accelerator = accelerator(event);
        if (accelerator == 0) {
            return false;
        }
        Command command = find(accelerator).orElse(null);
        if (command == null) {
            return false;
        }
        event.doit = false;
        Event copy = new Event();
        copy.type = SWT.KeyDown;
        copy.widget = event.widget;
        copy.display = event.display;
        copy.keyCode = event.keyCode;
        copy.character = event.character;
        copy.stateMask = event.stateMask;
        copy.time = event.time;
        event.type = SWT.None;
        command.run(new CommandEvent(command, copy.widget, copy, false, null));
        return true;
    }

    private void filter(Event event) {
        if (!shell.isDisposed()) {
            handle(event);
        }
    }

    /** The accelerator a key event stands for, or 0 for a modifier key alone. */
    static int accelerator(Event event) {
        int key = event.keyCode;
        if ((key & SWT.MODIFIER_MASK) != 0 && (key & ~SWT.MODIFIER_MASK) == 0) {
            return 0;
        }
        if (key == 0) {
            key = event.character;
        }
        if (key == 0) {
            return 0;
        }
        return normalize((event.stateMask & SWT.MODIFIER_MASK) | key);
    }

    /** {@code accelerator} with a letter key upper-cased. */
    static int normalize(int accelerator) {
        int key = accelerator & SWT.KEY_MASK;
        if (key == 0) {
            return 0;
        }
        if ((key & SWT.KEYCODE_BIT) == 0 && Character.isLetter(key)) {
            key = Character.toUpperCase(key);
        }
        return (accelerator & SWT.MODIFIER_MASK) | key;
    }

    private boolean menuBarHas(int accelerator) {
        Menu bar = shell.isDisposed() ? null : shell.getMenuBar();
        return bar != null && menuHas(bar, accelerator);
    }

    private static boolean menuHas(Menu menu, int accelerator) {
        for (MenuItem item : menu.getItems()) {
            if (item.getAccelerator() != 0 && normalize(item.getAccelerator()) == accelerator) {
                return true;
            }
            if (item.getMenu() != null && menuHas(item.getMenu(), accelerator)) {
                return true;
            }
        }
        return false;
    }

    private String clashing(int accelerator) {
        StringBuilder ids = new StringBuilder();
        for (Command command : registry.all()) {
            if (command.isVisible() && normalize(command.shortcut()) == accelerator) {
                ids.append(ids.isEmpty() ? "" : ", ").append(command.id());
            }
        }
        return ids.toString();
    }
}
