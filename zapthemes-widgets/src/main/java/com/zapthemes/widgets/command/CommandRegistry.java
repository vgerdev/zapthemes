package com.zapthemes.widgets.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.eclipse.swt.widgets.Shell;

/**
 * The commands of a window, by id. The ribbon and the title bar's quick access toolbar of one
 * shell share {@link #of(Shell)}, so commands registered for one show in the other's
 * customization lists.
 */
public final class CommandRegistry {

    private static final String KEY = CommandRegistry.class.getName();

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final List<BiConsumer<Command, Boolean>> listeners = new CopyOnWriteArrayList<>();

    /** The registry of {@code shell}, kept in its data; created on first use. */
    public static CommandRegistry of(Shell shell) {
        if (shell.getData(KEY) instanceof CommandRegistry registry) {
            return registry;
        }
        CommandRegistry registry = new CommandRegistry();
        shell.setData(KEY, registry);
        return registry;
    }

    /** Adds {@code command}, replacing one with the same id; returns it. */
    public Command add(Command command) {
        Command previous = commands.put(command.id(), command);
        if (previous == command) {
            return command;
        }
        if (previous != null) {
            listeners.forEach(l -> l.accept(previous, false));
        }
        listeners.forEach(l -> l.accept(command, true));
        return command;
    }

    public void remove(String id) {
        Command removed = commands.remove(id);
        if (removed != null) {
            listeners.forEach(l -> l.accept(removed, false));
        }
    }

    public Optional<Command> find(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    /** The command with {@code id}; throws if there is none. */
    public Command get(String id) {
        Command command = commands.get(id);
        if (command == null) {
            throw new IllegalArgumentException("No command " + id);
        }
        return command;
    }

    /** Every command, in the order they were added. */
    public Collection<Command> all() {
        return List.copyOf(commands.values());
    }

    /** The categories of the commands, in the order they first appear; no empty one. */
    public List<String> categories() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        commands.values().forEach(c -> {
            if (!c.category().isEmpty()) {
                names.add(c.category());
            }
        });
        return new ArrayList<>(names);
    }

    /** Told {@code (command, true)} when one is added and {@code (command, false)} when removed. */
    public void addListener(BiConsumer<Command, Boolean> listener) {
        listeners.add(listener);
    }

    public void removeListener(BiConsumer<Command, Boolean> listener) {
        listeners.remove(listener);
    }
}
