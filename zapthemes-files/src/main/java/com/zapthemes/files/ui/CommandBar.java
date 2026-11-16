package com.zapthemes.files.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.files.ui.FileIcons.Glyph;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandEvent;
import com.zapthemes.widgets.command.CommandListener;
import com.zapthemes.widgets.command.CommandMenu;

/**
 * The browser's command bar, drawn on one canvas like Explorer's: New ▾, then Cut, Copy, Paste,
 * Rename, Share and Delete as icon buttons (disabled when they don't apply), Sort ▾, View ▾ and
 * "…", and at the right the Details toggle. Commands an application adds go before "…". When the
 * bar is too narrow, labels go first, then buttons move into the "…" menu.
 *
 * <p>Theme type {@code FileCommandBar}: {@code background}, {@code foreground}, {@code font},
 * {@code hoverBackground}, {@code pressedBackground}, {@code disabledForeground},
 * {@code borderColor} (the separators).
 */
final class CommandBar extends Canvas {

    private static final int HEIGHT = 44;
    private static final int MARGIN = 8;
    private static final int BUTTON = 36;
    private static final int GLYPH = 16;
    private static final int PAD = 10;
    private static final int GAP = 6;
    private static final int SEP = 13;
    static final String SEPARATOR = CommandMenu.SEPARATOR;

    /** An item laid out: its command (null for a separator), bounds, whether it shows its label. */
    record Slot(Command command, Rectangle bounds, boolean label) {
    }

    private final FileBrowser browser;
    private final FileActions actions;
    private final List<String> left = new ArrayList<>();
    private final List<String> right = new ArrayList<>();
    private final List<Command> added = new ArrayList<>();
    private final CommandListener repaint = (command, property) -> {
        if (!isDisposed()) {
            redraw();
        }
    };
    private Slot hover;
    private Slot pressed;
    private Command menuOpenFor;

    @SuppressWarnings("this-escape")
    CommandBar(FileBrowser browser, FileActions actions) {
        super(browser, SWT.DOUBLE_BUFFERED);
        this.browser = browser;
        this.actions = actions;
        left.addAll(List.of(FileActions.NEW, SEPARATOR, FileActions.CUT, FileActions.COPY, FileActions.PASTE, FileActions.RENAME,
                FileActions.SHARE, FileActions.DELETE, SEPARATOR, FileActions.SORT, FileActions.VIEW, FileActions.MORE));
        right.add(FileActions.DETAILS_PANE);
        actions.commands().forEach(c -> c.addListener(repaint));
        addListener(SWT.Paint, this::paint);
        addListener(SWT.MouseMove, e -> setHover(slotAt(e.x, e.y)));
        addListener(SWT.MouseExit, e -> setHover(null));
        addListener(SWT.MouseDown, e -> {
            if (e.button == 1) {
                pressed = slotAt(e.x, e.y);
                redraw();
            }
        });
        addListener(SWT.MouseUp, this::mouseUp);
        addListener(SWT.Resize, e -> redraw());
        addListener(SWT.Dispose, e -> {
            actions.commands().forEach(c -> c.removeListener(repaint));
            added.forEach(c -> c.removeListener(repaint));
        });
        ThemeManager.applyOnCreate(this, "FileCommandBar");
    }

    /** Adds an application's command before "…". */
    void add(Command command) {
        added.add(command);
        command.addListener(repaint);
        redraw();
    }

    void remove(Command command) {
        added.remove(command);
        command.removeListener(repaint);
        redraw();
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        return new Point(wHint != SWT.DEFAULT ? wHint : px(600), hHint != SWT.DEFAULT ? hHint : px(HEIGHT));
    }

    private int px(int pixels) {
        return ThemeManager.get(getDisplay()).resources().zoomedPixels(pixels);
    }

    private Command command(String id) {
        return actions.command(id);
    }

    private List<Command> leftCommands() {
        List<Command> list = new ArrayList<>();
        for (String id : left) {
            if (id.equals(FileActions.MORE) && !added.isEmpty()) {
                list.add(null);
                list.addAll(added.stream().filter(Command::isVisible).toList());
            }
            list.add(id.equals(SEPARATOR) ? null : command(id));
        }
        return list;
    }

    /** Whether {@code command} is drawn with its label at full width (New, Sort, View, Details, app commands with one). */
    private boolean wantsLabel(Command command) {
        String id = command.id();
        return id.equals(FileActions.NEW) || id.equals(FileActions.SORT) || id.equals(FileActions.VIEW)
                || id.equals(FileActions.DETAILS_PANE) || added.contains(command) && actions.glyph(id) == null;
    }

    private int width(GC gc, Command command, boolean label) {
        int width = px(PAD) * 2 + px(GLYPH);
        if (label) {
            width += px(GAP) + gc.textExtent(Command.stripMnemonic(command.label())).x;
        }
        if (command.style() == Command.Style.DROP_DOWN && !command.id().equals(FileActions.MORE)) {
            width += px(GAP) + px(8);
        }
        return Math.max(width, label ? 0 : px(BUTTON));
    }

    /** The items as laid out at the current width; those that don't fit are left out (they go into "…"). */
    List<Slot> slots() {
        Rectangle area = getClientArea();
        GC gc = new GC(this);
        gc.setFont(getFont());
        try {
            int height = px(BUTTON);
            int y = (area.height - height) / 2;
            List<Command> commands = leftCommands();
            // Right side first: it stays.
            List<Slot> rightSlots = new ArrayList<>();
            int rx = area.width - px(MARGIN);
            for (String id : right) {
                Command command = command(id);
                int w = width(gc, command, true);
                rx -= w;
                rightSlots.add(new Slot(command, new Rectangle(rx, y, w, height), true));
            }
            int limit = rx - px(MARGIN);
            for (boolean labels : new boolean[] { true, false }) {
                List<Slot> slots = layout(gc, commands, labels, y, height, limit, false);
                if (slots != null) {
                    slots.addAll(rightSlots);
                    return slots;
                }
            }
            List<Slot> slots = layout(gc, commands, false, y, height, limit, true);
            slots.addAll(rightSlots);
            return slots;
        } finally {
            gc.dispose();
        }
    }

    /** Lays out {@code commands} from the left; null if they don't fit, unless {@code drop} (then the ones that don't fit before "…" are dropped). */
    private List<Slot> layout(GC gc, List<Command> commands, boolean labels, int y, int height, int limit, boolean drop) {
        List<Slot> slots = new ArrayList<>();
        int x = px(MARGIN);
        int moreWidth = px(BUTTON);
        for (Command command : commands) {
            if (command == null) {
                slots.add(new Slot(null, new Rectangle(x, y, px(SEP), height), false));
                x += px(SEP);
                continue;
            }
            boolean label = labels && wantsLabel(command);
            int w = width(gc, command, label);
            boolean isMore = command.id().equals(FileActions.MORE);
            if (x + w + (isMore ? 0 : moreWidth) > limit && !isMore) {
                if (!drop) {
                    return null;
                }
                continue;
            }
            slots.add(new Slot(command, new Rectangle(x, y, w, height), label));
            x += w;
        }
        // No two separators in a row, none last.
        List<Slot> clean = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.command() == null && (clean.isEmpty() || clean.getLast().command() == null)) {
                continue;
            }
            clean.add(slot);
        }
        return clean;
    }

    /** The commands that didn't fit and go into the "…" menu. */
    List<Command> overflow() {
        List<Slot> slots = slots();
        List<Command> shown = slots.stream().map(Slot::command).filter(c -> c != null).toList();
        return leftCommands().stream().filter(c -> c != null && !shown.contains(c)).toList();
    }

    Slot slotAt(int x, int y) {
        for (Slot slot : slots()) {
            if (slot.command() != null && slot.bounds().contains(x, y)) {
                return slot;
            }
        }
        return null;
    }

    /** The slot of command {@code id}, or null (tests). */
    Slot slot(String id) {
        return slots().stream().filter(s -> s.command() != null && s.command().id().equals(id)).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ painting

    private void paint(Event event) {
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        BarColors colors = BarColors.of(this, false);
        Rectangle area = getClientArea();
        gc.setBackground(colors.background());
        gc.fillRectangle(area);
        int arc = px(8);
        int glyph = px(GLYPH);
        int textHeight = gc.getFontMetrics().getHeight();
        for (Slot slot : slots()) {
            Rectangle r = slot.bounds();
            Command command = slot.command();
            if (command == null) {
                gc.setForeground(colors.border());
                int x = r.x + r.width / 2;
                gc.drawLine(x, r.y + px(8), x, r.y + r.height - px(8));
                continue;
            }
            boolean enabled = command.isEnabled();
            boolean checked = command.style() == Command.Style.TOGGLE && command.isChecked();
            boolean open = menuOpenFor == command;
            if (enabled && (open || checked || same(slot, hover))) {
                gc.setBackground(open || checked || same(slot, pressed) ? colors.pressed() : colors.hover());
                gc.fillRoundRectangle(r.x, r.y, r.width, r.height, arc, arc);
            }
            org.eclipse.swt.graphics.Color fg = enabled ? colors.foreground() : colors.disabled();
            int x = r.x + px(PAD);
            if (!slot.label() && command.style() != Command.Style.DROP_DOWN || command.id().equals(FileActions.MORE)) {
                x = r.x + (r.width - glyph) / 2;
            }
            Glyph g = actions.glyph(command.id());
            Image image = g == null ? command.images().image(glyph) : null;
            if (g != null) {
                FileIcons.draw(gc, g, x, r.y + (r.height - glyph) / 2, glyph, fg);
            } else if (image != null) {
                Rectangle b = image.getBounds();
                gc.drawImage(image, 0, 0, b.width, b.height, x, r.y + (r.height - glyph) / 2, glyph, glyph);
            }
            x += glyph;
            if (slot.label()) {
                x += px(GAP);
                gc.setForeground(fg);
                String label = Command.stripMnemonic(command.label());
                gc.drawText(label, x, r.y + (r.height - textHeight) / 2, SWT.DRAW_TRANSPARENT);
                x += gc.textExtent(label).x;
            }
            if (command.style() == Command.Style.DROP_DOWN && !command.id().equals(FileActions.MORE)) {
                int size = px(8);
                FileIcons.draw(gc, Glyph.CHEVRON_DOWN, x + px(GAP), r.y + (r.height - size) / 2, size, fg);
            }
        }
    }

    private static boolean same(Slot a, Slot b) {
        return a != null && b != null && a.command() == b.command();
    }

    // ------------------------------------------------------------------ input

    private void setHover(Slot slot) {
        if (same(slot, hover) && (slot == null) == (hover == null)) {
            return;
        }
        hover = slot;
        if (slot == null) {
            setToolTipText(null);
        } else {
            Command c = slot.command();
            String shortcut = c.shortcutText();
            String tip = Command.stripMnemonic(c.label()) + (shortcut.isEmpty() ? "" : " (" + shortcut + ")");
            setToolTipText(c.description() != null && !c.isEnabled() ? tip + " — " + c.description() : tip);
        }
        redraw();
    }

    private void mouseUp(Event event) {
        if (event.button != 1 || pressed == null) {
            return;
        }
        Slot slot = pressed;
        pressed = null;
        redraw();
        if (same(slot, slotAt(event.x, event.y))) {
            activate(slot);
        }
    }

    /** Does what a click on {@code slot} does: runs its command, or opens its menu under it. */
    void activate(Slot slot) {
        Command command = slot.command();
        if (command == null || !command.isEnabled()) {
            return;
        }
        if (command.style() == Command.Style.DROP_DOWN) {
            Menu menu = menuFor(command);
            if (menu == null) {
                return;
            }
            Rectangle r = slot.bounds();
            menu.setLocation(toDisplay(r.x, r.y + r.height));
            menuOpenFor = command;
            redraw();
            menu.addListener(SWT.Hide, e -> getDisplay().asyncExec(() -> {
                menuOpenFor = null;
                if (!isDisposed()) {
                    redraw();
                }
                if (!menu.isDisposed()) {
                    menu.dispose();
                }
            }));
            menu.setVisible(true);
            return;
        }
        command.run(new CommandEvent(command, this));
    }

    /** A new popup menu for drop-down {@code command}, filled; the caller shows and disposes it. */
    Menu menuFor(Command command) {
        Menu menu = new com.zapthemes.swt.widgets.Menu(this);
        Consumer<Menu> filler = switch (command.id()) {
            case FileActions.NEW -> actions::fillNewMenu;
            case FileActions.SORT -> actions::fillSortMenu;
            case FileActions.VIEW -> actions::fillViewMenu;
            case FileActions.MORE -> this::fillMore;
            default -> command.menu() instanceof CommandMenu.Filler f ? f.filler() : null;
        };
        if (filler == null) {
            menu.dispose();
            return null;
        }
        filler.accept(menu);
        return menu;
    }

    /** "…": the commands that didn't fit, then the browser's other commands. */
    private void fillMore(Menu menu) {
        actions.update();
        for (Command command : overflow()) {
            if (command.id().equals(FileActions.MORE)) {
                continue;
            }
            if (command.style() == Command.Style.DROP_DOWN) {
                org.eclipse.swt.widgets.MenuItem cascade = new com.zapthemes.swt.widgets.MenuItem(menu, SWT.CASCADE);
                cascade.setText(command.label());
                cascade.setEnabled(command.isEnabled());
                Menu sub = new com.zapthemes.swt.widgets.Menu(cascade);
                cascade.setMenu(sub);
                switch (command.id()) {
                    case FileActions.NEW -> actions.fillNewMenu(sub);
                    case FileActions.SORT -> actions.fillSortMenu(sub);
                    case FileActions.VIEW -> actions.fillViewMenu(sub);
                    default -> {
                        if (command.menu() instanceof CommandMenu.Filler f) {
                            f.filler().accept(sub);
                        }
                    }
                }
            } else if (actions.command(command.id()) == command) {
                actions.item(menu, command.id(), SWT.PUSH);
            } else {
                org.eclipse.swt.widgets.MenuItem item = new com.zapthemes.swt.widgets.MenuItem(menu, SWT.PUSH);
                item.setText(command.label());
                item.setEnabled(command.isEnabled());
                item.addListener(SWT.Selection, e -> command.run(new CommandEvent(command, item, e, false, null)));
            }
        }
        if (menu.getItemCount() > 0) {
            new com.zapthemes.swt.widgets.MenuItem(menu, SWT.SEPARATOR);
        }
        actions.fillMoreMenu(menu);
    }
}
