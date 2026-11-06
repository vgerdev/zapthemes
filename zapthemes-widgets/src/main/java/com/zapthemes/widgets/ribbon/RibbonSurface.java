package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.Accessible;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.swt.widgets.Menu;
import com.zapthemes.swt.widgets.MenuItem;
import com.zapthemes.swt.widgets.Text;
import com.zapthemes.widgets.command.Command;
import com.zapthemes.widgets.command.CommandEvent;
import com.zapthemes.widgets.command.CommandMenu;
import com.zapthemes.widgets.command.Images;

/**
 * A canvas that paints ribbon groups and items and handles the mouse, the keyboard and screen
 * readers for them: the {@link Ribbon} itself, and the popups that show a collapsed group or the
 * collapsed ribbon's tab. Subclasses lay out {@link Hot}s and paint what is not an item.
 */
abstract class RibbonSurface extends Canvas {

    private List<Hot> hots = List.of();
    private boolean valid;
    private Hot hover;
    private boolean hoverArrow;
    private Hot pressed;
    private boolean pressedArrow;
    private Hot focus;
    private boolean keyboardUsed;
    private RibbonMetrics paintMetrics;
    private final Map<Image, Image> disabledImages = new HashMap<>();
    private SuperToolTip toolTip;
    private Text editor;

    RibbonSurface(Composite parent, int style, String themeType) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        addListener(SWT.Paint, this::paint);
        addListener(SWT.MouseMove, this::mouseMove);
        addListener(SWT.MouseExit, e -> mouseExit());
        addListener(SWT.MouseDown, this::mouseDown);
        addListener(SWT.MouseUp, this::mouseUp);
        addListener(SWT.MouseDoubleClick, this::mouseDoubleClick);
        addListener(SWT.MenuDetect, this::menuDetect);
        addListener(SWT.KeyDown, this::keyDown);
        addListener(SWT.Traverse, this::traverse);
        addListener(SWT.FocusIn, e -> focusIn());
        addListener(SWT.FocusOut, e -> focusOut());
        addListener(SWT.Resize, e -> invalidate());
        addListener(SWT.Dispose, e -> disposed());
        initAccessible();
        ThemeManager.applyOnCreate(this, themeType);
    }

    /** The ribbon this surface belongs to. */
    abstract Ribbon ribbon();

    /** Lays out the surface's hot parts in reading order (the keyboard's order). */
    abstract List<Hot> computeHots(RibbonMetrics m);

    /** Paints everything but the items, launchers and group buttons, which {@link #paintPanel} paints. */
    abstract void paintSurface(GC gc, RibbonMetrics m, List<Hot> hots);

    /** A tab, file tab, right command, collapse, pin or scroll button was activated. */
    abstract void activateOther(Hot hot, Event event);

    /** The hot part that gets focus when the surface does. */
    abstract Hot initialFocus(List<Hot> hots);

    /** Esc with nothing open: what to do (go back to the tab row, close the popup, leave). */
    abstract void escape();

    RibbonColors colors() {
        return ribbon().colors();
    }

    /** The metrics of the paint in progress; items use it while painting. */
    RibbonMetrics metrics() {
        if (paintMetrics == null) {
            throw new IllegalStateException("Only while painting");
        }
        return paintMetrics;
    }

    RibbonMetrics newMetrics(GC gc) {
        RibbonColors colors = colors();
        return new RibbonMetrics(gc, getFont(), colors.tabFont, colors.groupLabelFont);
    }

    /** Computes the metrics with a temporary GC. */
    <T> T measure(java.util.function.Function<RibbonMetrics, T> function) {
        GC gc = new GC(this);
        try {
            return function.apply(newMetrics(gc));
        } finally {
            gc.dispose();
        }
    }

    /** The hot parts, laid out if needed. */
    List<Hot> hots() {
        checkWidget();
        if (!valid) {
            valid = true;
            hots = measure(this::computeHots);
            hover = match(hover);
            pressed = match(pressed);
            focus = match(focus);
        }
        return hots;
    }

    /** The current hot equivalent to {@code old} (same kind and target), or {@code null}. */
    private Hot match(Hot old) {
        if (old == null) {
            return null;
        }
        for (Hot hot : hots) {
            if (hot.sameAs(old)) {
                return hot;
            }
        }
        return null;
    }

    /** Lays out again before the next paint. */
    void invalidate() {
        valid = false;
        if (!isDisposed()) {
            redraw();
        }
    }

    Hot hitTest(int x, int y) {
        Hot found = null;
        for (Hot hot : hots()) {
            if (hot.kind() != Hot.Kind.GROUP && hot.bounds().contains(x, y)) {
                found = hot;
            }
        }
        return found;
    }

    Hot focusHot() {
        return focus;
    }

    // ------------------------------------------------------------------ painting

    private void paint(Event event) {
        List<Hot> current = hots();
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        paintMetrics = newMetrics(gc);
        try {
            paintSurface(gc, paintMetrics, current);
            if (focus != null && isFocusControl() && keyboardUsed) {
                Rectangle b = focus.bounds();
                gc.setForeground(colors().focus);
                gc.setLineWidth(Math.max(1, paintMetrics.px(1)));
                gc.drawRoundRectangle(b.x, b.y, b.width - 1, b.height - 1, colors().arc, colors().arc);
                gc.setLineWidth(1);
            }
            ribbon().keyTips().paint(this, gc, paintMetrics);
        } finally {
            paintMetrics = null;
        }
    }

    /** Paints group labels, separators, items, launchers and collapsed group buttons. */
    void paintPanel(GC gc, RibbonMetrics m, List<Hot> hots, Rectangle clip) {
        RibbonColors colors = colors();
        Rectangle saved = gc.getClipping();
        gc.setClipping(clip);
        List<Hot> groups = hots.stream().filter(h -> h.kind() == Hot.Kind.GROUP).toList();
        for (int i = 0; i < groups.size(); i++) {
            Hot group = groups.get(i);
            Rectangle b = group.bounds();
            boolean collapsed = hots.stream().anyMatch(h -> h.kind() == Hot.Kind.GROUP_BUTTON && h.target() == group.target());
            if (!collapsed) {
                RibbonGroup model = (RibbonGroup) group.target();
                gc.setFont(m.groupFont);
                gc.setForeground(colors.groupLabel);
                String label = Command.stripMnemonic(model.label());
                int width = m.groupText(label);
                int launcher = model.dialogLauncher() != null ? m.px(15) : 0;
                int textX = b.x + (b.width - width) / 2;
                if (launcher > 0) {
                    textX = Math.min(textX, b.x + b.width - launcher - width - m.px(2));
                }
                int labelTop = b.y + m.contentTop + m.contentHeight + m.px(2);
                gc.setFont(m.groupFont);
                gc.drawText(label, Math.max(b.x + m.px(2), textX), labelTop + (m.groupLabelHeight - gc.getFontMetrics().getHeight()) / 2,
                        SWT.DRAW_TRANSPARENT);
                gc.setFont(m.font);
            }
            if (i < groups.size() - 1 || ribbonSeparatorAfterLast()) {
                int x = b.x + b.width + m.groupGap / 2;
                gc.setForeground(colors.separator);
                gc.drawLine(x, b.y + m.px(4), x, b.y + b.height - m.px(4));
            }
        }
        for (Hot hot : hots) {
            switch (hot.kind()) {
                case ITEM -> hot.box().item().paint(this, gc, hot.box());
                case LAUNCHER -> paintLauncher(gc, m, hot);
                case GROUP_BUTTON -> paintGroupButton(gc, m, hot);
                default -> {
                }
            }
        }
        gc.setClipping(saved);
    }

    boolean ribbonSeparatorAfterLast() {
        return false;
    }

    private void paintLauncher(GC gc, RibbonMetrics m, Hot hot) {
        Rectangle b = hot.bounds();
        RibbonColors colors = colors();
        RibbonGroup group = (RibbonGroup) hot.target();
        Command command = group.dialogLauncher();
        boolean enabled = command != null && command.isEnabled();
        if (enabled && isHovered(hot)) {
            gc.setBackground(isPressed(hot) ? colors.pressed : colors.hover);
            gc.fillRoundRectangle(b.x, b.y, b.width, b.height, colors.arc, colors.arc);
        }
        int size = m.px(7);
        int x = b.x + (b.width - size) / 2;
        int y = b.y + (b.height - size) / 2;
        gc.setForeground(enabled ? colors.groupLabel : colors.disabled);
        gc.drawPolyline(new int[] { x, y + size / 2, x, y, x + size / 2, y });
        gc.drawLine(x + 1, y + 1, x + size, y + size);
        gc.drawPolyline(new int[] { x + size - 3, y + size, x + size, y + size, x + size, y + size - 3 });
    }

    private void paintGroupButton(GC gc, RibbonMetrics m, Hot hot) {
        RibbonColors colors = colors();
        RibbonGroup group = (RibbonGroup) hot.target();
        Rectangle b = hot.bounds();
        if (isHovered(hot) || ribbon().isGroupPopupOpen(group)) {
            gc.setBackground(isPressed(hot) || ribbon().isGroupPopupOpen(group) ? colors.pressed : colors.hover);
            gc.fillRoundRectangle(b.x, b.y, b.width, b.height, colors.arc, colors.arc);
        }
        int iconX = b.x + (b.width - m.largeIcon) / 2;
        int iconY = b.y + m.px(3);
        Image image = group.image().image(m.largeIcon);
        if (image != null) {
            drawImage(gc, group.image(), m.largeIcon, iconX, iconY, true);
        } else {
            gc.setForeground(colors.border);
            gc.drawRoundRectangle(iconX + m.px(4), iconY + m.px(4), m.largeIcon - m.px(8), m.largeIcon - m.px(8), colors.arc, colors.arc);
        }
        String[] lines = m.lines(group.label(), true);
        int y = iconY + m.largeIcon + m.px(3);
        gc.setForeground(colors.foreground);
        gc.setFont(m.font);
        for (int i = 0; i < lines.length; i++) {
            boolean last = i == lines.length - 1;
            int width = m.text(lines[i]);
            int total = width + (last ? (width > 0 ? m.px(4) : 0) + m.arrow : 0);
            int x = b.x + (b.width - total) / 2;
            gc.drawText(lines[i], x, y, SWT.DRAW_TRANSPARENT);
            if (last) {
                drawArrow(gc, x + total - m.arrow, y + (m.fontHeight - m.arrow / 2) / 2, m.arrow, colors.foreground);
            }
            y += m.fontHeight;
        }
    }

    /** Hover, pressed and checked backgrounds of an item (a split item has two parts). */
    void paintItemBackground(GC gc, ItemBox box, Rectangle arrow, boolean checked) {
        RibbonColors colors = colors();
        Rectangle b = box.bounds();
        boolean enabled = box.item().isEnabled();
        int arc = colors.arc;
        if (checked) {
            gc.setBackground(colors.checked);
            gc.fillRoundRectangle(b.x, b.y, b.width, b.height, arc, arc);
            gc.setForeground(colors.checkedBorder);
            gc.drawRoundRectangle(b.x, b.y, b.width - 1, b.height - 1, arc, arc);
        }
        boolean hot = enabled && (isHovered(box) || isOpen(box));
        if (!hot) {
            return;
        }
        if (arrow == null) {
            boolean down = isPressed(box, false) || isOpen(box);
            gc.setBackground(down ? colors.pressed : checked ? colors.pressed : colors.hover);
            gc.fillRoundRectangle(b.x, b.y, b.width, b.height, arc, arc);
            if (checked) {
                gc.setForeground(colors.checkedBorder);
                gc.drawRoundRectangle(b.x, b.y, b.width - 1, b.height - 1, arc, arc);
            }
            return;
        }
        Rectangle main = mainPart(b, arrow);
        gc.setBackground(isPressed(box, false) ? colors.pressed : colors.hover);
        gc.fillRoundRectangle(main.x, main.y, main.width, main.height, arc, arc);
        gc.setBackground(isPressed(box, true) || isOpen(box) ? colors.pressed : colors.hover);
        gc.fillRoundRectangle(arrow.x, arrow.y, arrow.width, arrow.height, arc, arc);
        gc.setForeground(colors.border);
        if (arrow.y > b.y) {
            gc.drawLine(b.x + 2, arrow.y, b.x + b.width - 3, arrow.y);
        } else {
            gc.drawLine(arrow.x, b.y + 2, arrow.x, b.y + b.height - 3);
        }
    }

    static Rectangle mainPart(Rectangle b, Rectangle arrow) {
        if (arrow.y > b.y) {
            return new Rectangle(b.x, b.y, b.width, arrow.y - b.y);
        }
        return new Rectangle(b.x, b.y, arrow.x - b.x, b.height);
    }

    /** Draws an image of {@code images} at {@code size} pixels, scaled when it is another size. */
    void drawImage(GC gc, Images images, int size, int x, int y, boolean enabled) {
        Image image = images.image(size);
        if (image == null || image.isDisposed()) {
            return;
        }
        if (!enabled) {
            image = disabledImages.computeIfAbsent(image, i -> new Image(getDisplay(), i, SWT.IMAGE_DISABLE));
        }
        Rectangle bounds = image.getBounds();
        if (bounds.width == size && bounds.height == size) {
            gc.drawImage(image, x, y);
        } else {
            gc.setInterpolation(SWT.HIGH);
            gc.drawImage(image, 0, 0, bounds.width, bounds.height, x, y, size, size);
        }
    }

    /** A downward chevron {@code width} wide, with its top at {@code y}. */
    void drawArrow(GC gc, int x, int y, int width, Color color) {
        gc.setForeground(color);
        int half = width / 2;
        gc.drawPolyline(new int[] { x, y, x + half, y + half, x + 2 * half, y });
    }

    // ------------------------------------------------------------------ state queries for painting

    boolean isHovered(ItemBox box) {
        return hover != null && hover.box() == box;
    }

    boolean isHovered(ItemBox box, boolean arrow) {
        return isHovered(box) && hoverArrow == arrow;
    }

    boolean isHovered(Hot hot) {
        return hot.sameAs(hover);
    }

    boolean isPressed(ItemBox box, boolean arrow) {
        return pressed != null && pressed.box() == box && pressedArrow == arrow && hover != null && hover.box() == box;
    }

    boolean isPressed(Hot hot) {
        return hot.sameAs(pressed) && hot.sameAs(hover);
    }

    boolean isFocused(ItemBox box) {
        return focus != null && focus.box() == box && isFocusControl();
    }

    /** Whether the item's menu, list or palette is open. */
    boolean isOpen(ItemBox box) {
        return ribbon().isOpenFor(box.item());
    }

    // ------------------------------------------------------------------ actions

    /** Runs {@code command} from this surface: closes popups and KeyTips first. */
    void run(Command command, Event event, boolean arrow, Object value) {
        Ribbon ribbon = ribbon();
        ribbon.closePopups();
        ribbon.keyTips().hide();
        if (command != null) {
            command.run(new CommandEvent(command, ribbon, event, arrow, value));
        }
    }

    void activate(Hot hot, boolean arrow, Event event) {
        switch (hot.kind()) {
            case ITEM -> hot.box().item().activate(this, hot.box(), arrow, event);
            case LAUNCHER -> run(((RibbonGroup) hot.target()).dialogLauncher(), event, false, null);
            case GROUP_BUTTON -> openGroupPopup(hot);
            case GALLERY_UP, GALLERY_DOWN, GALLERY_MORE, GALLERY_CELL -> ((RibbonGallery) hot.target()).activatePart(this, hot);
            default -> activateOther(hot, event);
        }
    }

    Rectangle toDisplay(Rectangle r) {
        Point origin = toDisplay(r.x, r.y);
        return new Rectangle(origin.x, origin.y, r.width, r.height);
    }

    void openMenu(RibbonButton button, ItemBox box) {
        openMenu(button.command(), button, box.bounds());
    }

    /** Opens {@code command}'s menu under {@code bounds}; {@code owner} is drawn as open meanwhile. */
    void openMenu(Command command, Object owner, Rectangle bounds) {
        CommandMenu model = command.menu();
        if (model == null) {
            return;
        }
        org.eclipse.swt.widgets.Menu menu;
        boolean owned = !(model instanceof CommandMenu.Popup);
        if (model instanceof CommandMenu.Popup popup) {
            menu = popup.factory().apply(ribbon());
            if (menu == null) {
                return;
            }
        } else {
            Menu created = new Menu(ribbon().getShell(), SWT.POP_UP);
            menu = created;
            if (model instanceof CommandMenu.Ids ids) {
                for (String id : ids.ids()) {
                    if (id.equals(CommandMenu.SEPARATOR)) {
                        new MenuItem(created, SWT.SEPARATOR);
                        continue;
                    }
                    ribbon().commands().find(id).filter(Command::isVisible).ifPresent(c -> addMenuItem(created, c));
                }
            } else if (model instanceof CommandMenu.Filler filler) {
                filler.filler().accept(created);
            }
        }
        Rectangle at = toDisplay(bounds);
        ribbon().menuOpened(owner, menu);
        menu.setLocation(at.x, at.y + at.height);
        Listener[] hide = new Listener[1];
        hide[0] = e -> getDisplay().asyncExec(() -> {
            if (!isDisposed()) {
                ribbon().menuClosed(menu);
            }
            if (!menu.isDisposed()) {
                menu.removeListener(SWT.Hide, hide[0]);
                if (owned) {
                    menu.dispose();
                }
            }
        });
        menu.addListener(SWT.Hide, hide[0]);
        menu.setVisible(true);
    }

    private void addMenuItem(Menu menu, Command command) {
        MenuItem item = new MenuItem(menu, command.style() == Command.Style.TOGGLE ? SWT.CHECK : SWT.PUSH);
        String shortcut = command.shortcutText();
        item.setText(command.label() + (shortcut.isEmpty() ? "" : "\t" + shortcut));
        Image image = command.images().image(16);
        if (image != null && command.style() != Command.Style.TOGGLE) {
            item.setImage(image);
        }
        item.setEnabled(command.isEnabled());
        item.setSelection(command.isChecked());
        item.addListener(SWT.Selection, e -> run(command, e, false, null));
    }

    void openColorPalette(RibbonColorButton button, ItemBox box) {
        ribbon().openPopup(new ColorPalettePopup(this, button, toDisplay(box.bounds())), button);
    }

    void openComboList(RibbonComboBox combo, ItemBox box) {
        ribbon().openPopup(new ComboListPopup(this, combo, toDisplay(combo.field(measure(m -> m), box))), combo);
    }

    void openGroupPopup(Hot hot) {
        RibbonGroup group = (RibbonGroup) hot.target();
        ribbon().openPopup(new GroupPopup(this, group, toDisplay(hot.bounds())), group);
    }

    /** Opens a text editor over the combo's field; Enter commits, Esc and focus loss cancel. */
    void editCombo(RibbonComboBox combo, ItemBox box) {
        closeEditor();
        Rectangle field = measure(m -> combo.field(m, box));
        Rectangle arrow = measure(m -> combo.arrowBounds(m, box));
        Text text = new Text(this, SWT.SINGLE);
        editor = text;
        text.setText(combo.text());
        text.setBounds(field.x + 1, field.y + 1, field.width - arrow.width - 2, field.height - 2);
        text.selectAll();
        text.addListener(SWT.DefaultSelection, e -> {
            String value = text.getText();
            closeEditor();
            setFocus();
            combo.commit(value);
        });
        text.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
                e.doit = false;
                closeEditor();
                setFocus();
            }
        });
        text.addListener(SWT.FocusOut, e -> getDisplay().asyncExec(() -> {
            if (editor == text) {
                closeEditor();
            }
        }));
        text.setFocus();
    }

    /** The open combo editor, for tests. */
    Text editor() {
        return editor;
    }

    void closeEditor() {
        if (editor != null && !editor.isDisposed()) {
            editor.dispose();
        }
        editor = null;
    }

    // ------------------------------------------------------------------ mouse

    private void mouseMove(Event event) {
        Hot hot = hitTest(event.x, event.y);
        boolean arrow = hot != null && hot.arrow() != null && hot.arrow().contains(event.x, event.y);
        if (!sameHot(hot, hover) || arrow != hoverArrow) {
            hover = hot;
            hoverArrow = arrow;
            redraw();
            toolTip().schedule(hot);
        }
    }

    private static boolean sameHot(Hot a, Hot b) {
        return a == null ? b == null : a.sameAs(b);
    }

    private void mouseExit() {
        hover = null;
        redraw();
        toolTip().hide();
    }

    private void mouseDown(Event event) {
        toolTip().hide();
        ribbon().keyTips().hide();
        keyboardUsed = false;
        if (event.button != 1) {
            return;
        }
        Hot hot = hitTest(event.x, event.y);
        if (hot == null) {
            return;
        }
        boolean arrow = hot.arrow() != null && hot.arrow().contains(event.x, event.y);
        switch (hot.kind()) {
            case TAB, FILE_TAB, SCROLL_LEFT, SCROLL_RIGHT, GALLERY_UP, GALLERY_DOWN -> {
                activate(hot, arrow, event);
                return;
            }
            default -> {
            }
        }
        pressed = hot;
        pressedArrow = arrow;
        redraw();
    }

    private void mouseUp(Event event) {
        if (event.button != 1 || pressed == null) {
            return;
        }
        Hot hot = hitTest(event.x, event.y);
        boolean arrow = hot != null && hot.arrow() != null && hot.arrow().contains(event.x, event.y);
        Hot was = pressed;
        boolean wasArrow = pressedArrow;
        pressed = null;
        redraw();
        if (hot != null && hot.sameAs(was) && arrow == wasArrow) {
            activate(hot, arrow, event);
        }
    }

    private void mouseDoubleClick(Event event) {
        Hot hot = hitTest(event.x, event.y);
        if (hot != null && hot.kind() == Hot.Kind.TAB) {
            tabDoubleClicked(hot);
        }
    }

    void tabDoubleClicked(Hot hot) {
    }

    private void menuDetect(Event event) {
        Point point = toControl(event.x, event.y);
        Hot hot = event.detail == SWT.MENU_KEYBOARD ? focus : hitTest(point.x, point.y);
        event.doit = false;
        ribbon().showContextMenu(this, hot, event.x, event.y);
    }

    SuperToolTip toolTip() {
        if (toolTip == null) {
            toolTip = new SuperToolTip(this);
        }
        return toolTip;
    }

    // ------------------------------------------------------------------ keyboard

    private void focusIn() {
        if (focus == null) {
            focus = initialFocus(hots());
        }
        redraw();
        fireFocus();
    }

    private void focusOut() {
        redraw();
    }

    /** Moves keyboard focus to {@code hot} (focusable, from {@link #hots()}). */
    void setFocusHot(Hot hot) {
        focus = hot;
        keyboardUsed = true;
        if (!isFocusControl()) {
            setFocus();
        }
        focusChanged(hot);
        redraw();
        fireFocus();
    }

    void focusChanged(Hot hot) {
    }

    boolean keyboardUsed() {
        return keyboardUsed;
    }

    void setKeyboardUsed(boolean used) {
        keyboardUsed = used;
    }

    private void fireFocus() {
        if (focus != null) {
            int index = hots.indexOf(focus);
            if (index >= 0) {
                getAccessible().setFocus(index);
            }
        }
    }

    List<Hot> focusable() {
        return hots().stream().filter(Hot::focusable).toList();
    }

    void moveFocus(int delta) {
        List<Hot> order = focusable();
        if (order.isEmpty()) {
            return;
        }
        int index = focus == null ? -1 : indexOf(order, focus);
        int next = index < 0 ? (delta > 0 ? 0 : order.size() - 1) : Math.floorMod(index + delta, order.size());
        setFocusHot(order.get(next));
    }

    static int indexOf(List<Hot> hots, Hot hot) {
        for (int i = 0; i < hots.size(); i++) {
            if (hots.get(i).sameAs(hot)) {
                return i;
            }
        }
        return -1;
    }

    private void keyDown(Event event) {
        keyboardUsed = true;
        if (ribbon().keyTips().handleKey(this, event)) {
            return;
        }
        hots();
        switch (event.keyCode) {
            case SWT.ARROW_LEFT -> moveFocus(-1);
            case SWT.ARROW_RIGHT -> moveFocus(1);
            case SWT.ARROW_DOWN -> arrowDown(event);
            case SWT.ARROW_UP -> arrowUp();
            case SWT.HOME -> moveFocusTo(0);
            case SWT.END -> moveFocusTo(-1);
            case SWT.CR, SWT.KEYPAD_CR -> activateFocus(event);
            default -> {
                if (event.character == ' ') {
                    activateFocus(event);
                }
            }
        }
    }

    private void moveFocusTo(int index) {
        List<Hot> order = focusable();
        if (!order.isEmpty()) {
            setFocusHot(order.get(index < 0 ? order.size() - 1 : index));
        }
    }

    void arrowDown(Event event) {
        if (focus != null && focus.kind() == Hot.Kind.ITEM && focus.box().item().hasPopup()) {
            activate(focus, true, event);
            return;
        }
        moveFocus(1);
    }

    void arrowUp() {
        moveFocus(-1);
    }

    private void activateFocus(Event event) {
        if (focus != null) {
            activate(focus, false, event);
        }
    }

    private void traverse(Event event) {
        switch (event.detail) {
            case SWT.TRAVERSE_TAB_NEXT -> {
                event.doit = false;
                keyboardUsed = true;
                moveFocus(1);
            }
            case SWT.TRAVERSE_TAB_PREVIOUS -> {
                event.doit = false;
                keyboardUsed = true;
                moveFocus(-1);
            }
            case SWT.TRAVERSE_ESCAPE -> {
                event.doit = false;
                if (!ribbon().keyTips().back()) {
                    escape();
                }
            }
            case SWT.TRAVERSE_RETURN -> event.doit = false;
            case SWT.TRAVERSE_ARROW_NEXT, SWT.TRAVERSE_ARROW_PREVIOUS -> event.doit = false;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ accessibility

    String nameOf(Hot hot) {
        return switch (hot.kind()) {
            case FILE_TAB -> ribbon().fileTabLabel();
            case TAB -> {
                RibbonTab tab = (RibbonTab) hot.target();
                yield tab.contextualSet() == null ? tab.label() : tab.label() + " (" + tab.contextualSet().label() + ")";
            }
            case RIGHT -> ((Command) hot.target()).plainLabel();
            case ITEM -> hot.box().item().label();
            case LAUNCHER -> {
                Command launcher = ((RibbonGroup) hot.target()).dialogLauncher();
                yield launcher != null ? launcher.plainLabel() : ((RibbonGroup) hot.target()).label() + " settings";
            }
            case GROUP_BUTTON, GROUP -> ((RibbonGroup) hot.target()).label();
            case COLLAPSE -> ribbon().isCollapsed() ? "Expand the Ribbon" : "Collapse the Ribbon";
            case PIN -> "Pin the Ribbon";
            case SCROLL_LEFT -> "Scroll left";
            case SCROLL_RIGHT -> "Scroll right";
            case GALLERY_UP -> "Previous row";
            case GALLERY_DOWN -> "Next row";
            case GALLERY_MORE -> "More";
            case GALLERY_CELL -> hot.target() instanceof RibbonGallery g ? g.itemLabel(hot.index()) : "";
        };
    }

    /** The super tooltip's body for {@code hot}. */
    String descriptionOf(Hot hot) {
        return switch (hot.kind()) {
            case ITEM -> hot.box().item().description();
            case RIGHT -> ((Command) hot.target()).description();
            case LAUNCHER -> {
                Command launcher = ((RibbonGroup) hot.target()).dialogLauncher();
                yield launcher == null ? null : launcher.description();
            }
            case GROUP_BUTTON -> "Shows the " + ((RibbonGroup) hot.target()).label() + " group.";
            case COLLAPSE -> "Show only the tab names. Ctrl+F1";
            case PIN -> "Keep the ribbon open. Ctrl+F1";
            case TAB -> ((RibbonTab) hot.target()).contextualSet() != null ? ((RibbonTab) hot.target()).contextualSet().label() : null;
            default -> null;
        };
    }

    Command commandOf(Hot hot) {
        return switch (hot.kind()) {
            case ITEM -> hot.box().item().command();
            case RIGHT -> (Command) hot.target();
            case LAUNCHER -> ((RibbonGroup) hot.target()).dialogLauncher();
            default -> null;
        };
    }

    private boolean isEnabled(Hot hot) {
        return switch (hot.kind()) {
            case ITEM -> hot.box().item().isEnabled();
            case RIGHT -> ((Command) hot.target()).isEnabled();
            case LAUNCHER -> {
                Command launcher = ((RibbonGroup) hot.target()).dialogLauncher();
                yield launcher != null && launcher.isEnabled();
            }
            default -> true;
        };
    }

    private int roleOf(Hot hot) {
        return switch (hot.kind()) {
            case FILE_TAB, TAB -> ACC.ROLE_TABITEM;
            case ITEM -> hot.box().item().accessibleRole();
            case GROUP -> ACC.ROLE_GROUP;
            case GALLERY_CELL -> ACC.ROLE_LISTITEM;
            default -> ACC.ROLE_PUSHBUTTON;
        };
    }

    private void initAccessible() {
        Accessible accessible = getAccessible();
        accessible.addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                Hot hot = child(e.childID);
                e.result = hot == null ? accessibleName() : nameOf(hot);
            }

            @Override
            public void getDescription(AccessibleEvent e) {
                Hot hot = child(e.childID);
                if (hot != null) {
                    e.result = descriptionOf(hot);
                }
            }

            @Override
            public void getKeyboardShortcut(AccessibleEvent e) {
                Hot hot = child(e.childID);
                if (hot == null) {
                    return;
                }
                StringBuilder text = new StringBuilder();
                String keyTip = ribbon().keyTips().pathOf(RibbonSurface.this, hot);
                if (keyTip != null) {
                    text.append(keyTip);
                }
                Command command = commandOf(hot);
                if (command != null && command.shortcut() != 0) {
                    text.append(text.isEmpty() ? "" : "; ").append(command.shortcutText());
                }
                e.result = text.isEmpty() ? null : text.toString();
            }

            @Override
            public void getHelp(AccessibleEvent e) {
                getDescription(e);
            }
        });
        accessible.addAccessibleControlListener(new AccessibleControlAdapter() {
            @Override
            public void getChildAtPoint(AccessibleControlEvent e) {
                Point point = toControl(e.x, e.y);
                Hot hot = hitTest(point.x, point.y);
                e.childID = hot == null ? (getClientArea().contains(point) ? ACC.CHILDID_SELF : ACC.CHILDID_NONE) : hots().indexOf(hot);
            }

            @Override
            public void getChildCount(AccessibleControlEvent e) {
                e.detail = hots().size();
            }

            @Override
            public void getChildren(AccessibleControlEvent e) {
                Object[] children = new Object[hots().size()];
                for (int i = 0; i < children.length; i++) {
                    children[i] = i;
                }
                e.children = children;
            }

            @Override
            public void getFocus(AccessibleControlEvent e) {
                if (!isFocusControl()) {
                    e.childID = ACC.CHILDID_NONE;
                } else {
                    int index = focus == null ? -1 : hots().indexOf(focus);
                    e.childID = index < 0 ? ACC.CHILDID_SELF : index;
                }
            }

            @Override
            public void getLocation(AccessibleControlEvent e) {
                Hot hot = child(e.childID);
                Rectangle bounds = hot == null ? getClientArea() : hot.bounds();
                Point origin = RibbonSurface.this.toDisplay(bounds.x, bounds.y);
                e.x = origin.x;
                e.y = origin.y;
                e.width = bounds.width;
                e.height = bounds.height;
            }

            @Override
            public void getRole(AccessibleControlEvent e) {
                Hot hot = child(e.childID);
                e.detail = hot == null ? accessibleRole() : roleOf(hot);
            }

            @Override
            public void getState(AccessibleControlEvent e) {
                Hot hot = child(e.childID);
                if (hot == null) {
                    e.detail = ACC.STATE_FOCUSABLE | (isFocusControl() ? ACC.STATE_FOCUSED : 0);
                    return;
                }
                int state = hot.focusable() ? ACC.STATE_FOCUSABLE : 0;
                if (hot.sameAs(focus) && isFocusControl()) {
                    state |= ACC.STATE_FOCUSED;
                }
                if (!isEnabled(hot)) {
                    state |= ACC.STATE_DISABLED;
                }
                if (hot.sameAs(hover)) {
                    state |= ACC.STATE_HOTTRACKED;
                }
                if (hot.kind() == Hot.Kind.TAB && hot.target() == ribbon().getSelection()) {
                    state |= ACC.STATE_SELECTED;
                }
                if (hot.kind() == Hot.Kind.ITEM && hot.box().item() instanceof RibbonButton button && button.isChecked()) {
                    state |= ACC.STATE_CHECKED | ACC.STATE_PRESSED;
                }
                if (hot.kind() == Hot.Kind.ITEM && isOpen(hot.box())) {
                    state |= ACC.STATE_EXPANDED;
                }
                if (hot.kind() == Hot.Kind.GALLERY_CELL && hot.target() instanceof RibbonGallery g && g.selection() == hot.index()) {
                    state |= ACC.STATE_SELECTED;
                }
                e.detail = state;
            }

            @Override
            public void getDefaultAction(AccessibleControlEvent e) {
                Hot hot = child(e.childID);
                if (hot != null) {
                    e.result = switch (hot.kind()) {
                        case TAB, FILE_TAB -> "Switch";
                        case GROUP -> null;
                        default -> hot.kind() == Hot.Kind.ITEM && hot.box().item().hasPopup() ? "Open" : "Press";
                    };
                }
            }

            @Override
            public void getValue(AccessibleControlEvent e) {
                Hot hot = child(e.childID);
                if (hot != null && hot.kind() == Hot.Kind.ITEM && hot.box().item() instanceof RibbonComboBox combo) {
                    e.result = combo.text();
                }
            }
        });
    }

    String accessibleName() {
        return "Ribbon";
    }

    int accessibleRole() {
        return ACC.ROLE_TOOLBAR;
    }

    private Hot child(int childID) {
        List<Hot> current = hots();
        return childID >= 0 && childID < current.size() ? current.get(childID) : null;
    }

    // ------------------------------------------------------------------ hot builders

    /** Adds the hots of a laid out panel, shifted by {@code dx}. */
    static void addPanelHots(RibbonMetrics m, RibbonLayout.PanelLayout panel, int dx, List<Hot> out) {
        for (RibbonLayout.GroupBox raw : panel.groups()) {
            RibbonLayout.GroupBox group = dx == 0 ? raw : raw.translated(dx);
            out.add(new Hot(Hot.Kind.GROUP, group.group(), group.bounds()));
            if (group.button() != null) {
                out.add(new Hot(Hot.Kind.GROUP_BUTTON, group.group(), group.button()));
                continue;
            }
            for (ItemBox box : group.items()) {
                out.add(new Hot(Hot.Kind.ITEM, box.item(), box, box.bounds(), box.item().arrowBounds(m, box), -1));
                if (box.item() instanceof RibbonGallery gallery) {
                    gallery.addHots(m, box, out);
                }
            }
            if (group.launcher() != null) {
                out.add(new Hot(Hot.Kind.LAUNCHER, group.group(), group.launcher()));
            }
        }
    }

    /** The ITEM hot for {@code item}, or {@code null}. */
    Hot hotOf(RibbonItem item) {
        for (Hot hot : hots()) {
            if (hot.kind() == Hot.Kind.ITEM && hot.box().item() == item) {
                return hot;
            }
        }
        return null;
    }

    List<Hot> hotsOf(Hot.Kind kind) {
        List<Hot> result = new ArrayList<>();
        for (Hot hot : hots()) {
            if (hot.kind() == kind) {
                result.add(hot);
            }
        }
        return result;
    }

    private void disposed() {
        disabledImages.values().forEach(Image::dispose);
        disabledImages.clear();
        if (toolTip != null) {
            toolTip.dispose();
        }
        closeEditor();
    }
}
