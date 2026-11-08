package com.zapthemes.widgets.command;

import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Widget;

/**
 * How a command was run: the widget that ran it (a ribbon, a quick access toolbar, a menu item),
 * the SWT event when there was one, whether the arrow part of a split button was used, and a
 * value for commands that carry one (a combo's text, a gallery's index, a color button's RGB).
 */
public record CommandEvent(Command command, Widget widget, Event event, boolean arrow, Object value) {

    public CommandEvent(Command command, Widget widget) {
        this(command, widget, null, false, null);
    }
}
