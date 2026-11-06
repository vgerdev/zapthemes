package com.zapthemes.widgets.command;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.widgets.Menu;

/**
 * The menu of a drop-down or split command: a list of command ids ({@link #SEPARATOR} for a
 * separator), or code that fills an SWT menu each time it opens.
 */
public sealed interface CommandMenu {

    String SEPARATOR = "|";

    /** Command ids, looked up in the bar's registry; {@code "|"} separates. */
    record Ids(List<String> ids) implements CommandMenu {
        public Ids {
            ids = List.copyOf(ids);
        }
    }

    /** Fills the (empty) menu just before it shows. */
    record Filler(Consumer<Menu> filler) implements CommandMenu {
    }

    static CommandMenu of(String... ids) {
        return new Ids(List.of(ids));
    }

    static CommandMenu of(Consumer<Menu> filler) {
        return new Filler(filler);
    }
}
