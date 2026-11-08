package com.zapthemes.widgets.command;

/** Told about every change of a {@link Command}'s properties. */
@FunctionalInterface
public interface CommandListener {

    /** {@code property} is one of {@link Command#ENABLED}, {@link Command#CHECKED}, ... */
    void propertyChanged(Command command, String property);
}
