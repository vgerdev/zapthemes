package com.zapthemes.widgets.command;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;

/**
 * Something the user can do, shown by ribbon buttons, quick access toolbar buttons and menu items:
 * an id, a label, images, a description, a shortcut, a style, and enabled, checked and visible
 * states. Bars listen to it, so an app changes the command and every bar showing it follows.
 *
 * <p>The id must be stable across runs: customizations store it.
 *
 * <pre>{@code
 * Command bold = new Command("home.bold", "&Bold").style(Command.Style.TOGGLE)
 *         .images(Images.of(bold16, bold32)).description("Make your text bold.")
 *         .shortcut(SWT.MOD1 | 'B').onRun(e -> editor.toggleBold());
 * }</pre>
 *
 * Not thread-safe: use it on the UI thread.
 */
public final class Command {

    public static final String ENABLED = "enabled";
    public static final String CHECKED = "checked";
    public static final String VISIBLE = "visible";
    public static final String LABEL = "label";
    public static final String IMAGES = "images";
    public static final String DESCRIPTION = "description";
    public static final String SHORTCUT = "shortcut";
    public static final String STYLE = "style";

    /** How bars draw and run a command. */
    public enum Style {
        /** Runs when clicked. */
        PUSH,
        /** Flips its checked state, then runs. */
        TOGGLE,
        /** A main part that runs, and an arrow that opens its menu. */
        SPLIT,
        /** Opens its menu. */
        DROP_DOWN
    }

    private final String id;
    private String label;
    private String description;
    private String category = "";
    private int shortcut;
    private Style style = Style.PUSH;
    private Images images = Images.NONE;
    private CommandMenu menu;
    private Consumer<CommandEvent> handler;
    private boolean enabled = true;
    private boolean checked;
    private boolean visible = true;
    private final List<CommandListener> listeners = new CopyOnWriteArrayList<>();

    public Command(String id, String label) {
        if (id == null || id.isBlank() || id.equals(CommandMenu.SEPARATOR)) {
            throw new IllegalArgumentException("A command needs an id: " + id);
        }
        this.id = id;
        this.label = Objects.requireNonNull(label, "label");
    }

    public String id() {
        return id;
    }

    /** The label, with an {@code &} before its mnemonic. */
    public String label() {
        return label;
    }

    /** The label without mnemonic markers. */
    public String plainLabel() {
        return stripMnemonic(label);
    }

    public String description() {
        return description;
    }

    public String category() {
        return category;
    }

    /** The SWT accelerator ({@code SWT.MOD1 | 'B'}), or 0. */
    public int shortcut() {
        return shortcut;
    }

    public Style style() {
        return style;
    }

    public Images images() {
        return images;
    }

    public CommandMenu menu() {
        return menu;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isChecked() {
        return checked;
    }

    public boolean isVisible() {
        return visible;
    }

    // ------------------------------------------------------------------ fluent setters

    public Command label(String newLabel) {
        Objects.requireNonNull(newLabel, "label");
        if (!newLabel.equals(label)) {
            label = newLabel;
            fire(LABEL);
        }
        return this;
    }

    public Command description(String text) {
        if (!Objects.equals(text, description)) {
            description = text;
            fire(DESCRIPTION);
        }
        return this;
    }

    public Command category(String name) {
        category = name == null ? "" : name;
        return this;
    }

    public Command shortcut(int accelerator) {
        if (accelerator != shortcut) {
            shortcut = accelerator;
            fire(SHORTCUT);
        }
        return this;
    }

    public Command style(Style newStyle) {
        Objects.requireNonNull(newStyle, "style");
        if (newStyle != style) {
            style = newStyle;
            fire(STYLE);
        }
        return this;
    }

    public Command images(Images newImages) {
        images = newImages == null ? Images.NONE : newImages;
        fire(IMAGES);
        return this;
    }

    public Command menu(CommandMenu newMenu) {
        menu = newMenu;
        return this;
    }

    public Command onRun(Consumer<CommandEvent> newHandler) {
        handler = newHandler;
        return this;
    }

    public void setEnabled(boolean value) {
        if (value != enabled) {
            enabled = value;
            fire(ENABLED);
        }
    }

    public void setChecked(boolean value) {
        if (value != checked) {
            checked = value;
            fire(CHECKED);
        }
    }

    public void setVisible(boolean value) {
        if (value != visible) {
            visible = value;
            fire(VISIBLE);
        }
    }

    // ------------------------------------------------------------------ running

    /**
     * Runs the command as a bar does: nothing when disabled; a toggle flips its checked state
     * first (unless the arrow of a split button was used); then the handler runs.
     */
    public void run(CommandEvent event) {
        if (!enabled) {
            return;
        }
        if (style == Style.TOGGLE && !event.arrow()) {
            setChecked(!checked);
        }
        if (handler != null) {
            handler.accept(event);
        }
    }

    public void addListener(CommandListener listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void removeListener(CommandListener listener) {
        listeners.remove(listener);
    }

    private void fire(String property) {
        listeners.forEach(l -> l.propertyChanged(this, property));
    }

    /** The shortcut as text: {@code "Ctrl+B"} ({@code "⌘B"} on macOS), or empty. */
    public String shortcutText() {
        return acceleratorText(shortcut);
    }

    /** An SWT accelerator as text, as menus show it. */
    public static String acceleratorText(int accelerator) {
        if (accelerator == 0) {
            return "";
        }
        boolean mac = "cocoa".equals(SWT.getPlatform());
        StringBuilder text = new StringBuilder();
        if ((accelerator & SWT.CTRL) != 0) {
            text.append(mac ? "⌃" : "Ctrl+");
        }
        if ((accelerator & SWT.ALT) != 0) {
            text.append(mac ? "⌥" : "Alt+");
        }
        if ((accelerator & SWT.SHIFT) != 0) {
            text.append(mac ? "⇧" : "Shift+");
        }
        if ((accelerator & SWT.COMMAND) != 0) {
            text.append(mac ? "⌘" : "Win+");
        }
        int key = accelerator & SWT.KEY_MASK;
        if (key >= SWT.F1 && key <= SWT.F20) {
            text.append('F').append(key - SWT.F1 + 1);
        } else {
            text.append(switch (key) {
                case SWT.DEL -> "Del";
                case SWT.ESC -> "Esc";
                case SWT.CR -> "Enter";
                case SWT.TAB -> "Tab";
                case SWT.HOME -> "Home";
                case SWT.END -> "End";
                case ' ' -> "Space";
                default -> String.valueOf(Character.toUpperCase((char) key));
            });
        }
        return text.toString();
    }

    /** {@code "&Bold"} to {@code "Bold"}; {@code "&&"} stays one {@code &}. */
    public static String stripMnemonic(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&') {
                if (i + 1 >= text.length()) {
                    continue;
                }
                i++;
                c = text.charAt(i);
            }
            result.append(c);
        }
        return result.toString();
    }

    @Override
    public String toString() {
        return "Command[" + id + "]";
    }
}
