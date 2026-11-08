package com.zapthemes.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;

/**
 * A canvas-drawn button that follows the theme on every platform, for where the native
 * {@code Button} ignores colors (Windows and macOS push buttons, check and radio boxes).
 * Styles: {@link SWT#PUSH} (default), {@link SWT#TOGGLE}, {@link SWT#CHECK}, {@link SWT#RADIO}.
 *
 * <p>Theme type {@code "FlatButton"} (style-bit rules such as {@code FlatButton[CHECK]} apply).
 * Properties: {@code background}, {@code foreground}, {@code borderColor}, {@code accentColor}
 * (check/radio mark), {@code boxBackground} (check/radio box), {@code arc} (corner radius) and the
 * states {@code hover}, {@code pressed}, {@code selected} (toggle), {@code disabled},
 * {@code focused} (border only).
 *
 * <p>Keyboard: Space activates, Enter activates push buttons, mnemonics ({@code &File}) work, Tab
 * traverses. Radio buttons in the same parent are exclusive unless the parent has
 * {@link SWT#NO_RADIO_GROUP}. Accessible as a push, check or radio button.
 */
public class FlatButton extends Canvas {

    private static final int KINDS = SWT.PUSH | SWT.TOGGLE | SWT.CHECK | SWT.RADIO;
    // Sizes at 100 %; px(...) gives them at the current zoom.
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 4;
    private static final int BOX = 13;
    private static final int GAP = 6;

    private final int kind;
    private String text = "";
    private Image image;
    private boolean selection;
    private boolean hover;
    private boolean pressed;

    @SuppressWarnings("this-escape")
    public FlatButton(Composite parent, int style) {
        super(parent, (style & ~KINDS) | SWT.DOUBLE_BUFFERED);
        kind = kindOf(style);
        addListener(SWT.Paint, this::paint);
        addListener(SWT.MouseEnter, e -> setHover(true));
        addListener(SWT.MouseExit, e -> setHover(false));
        addListener(SWT.MouseDown, this::mouseDown);
        addListener(SWT.MouseUp, this::mouseUp);
        addListener(SWT.KeyDown, this::keyDown);
        addListener(SWT.Traverse, this::traverse);
        addListener(SWT.FocusIn, e -> redraw());
        addListener(SWT.FocusOut, e -> redraw());
        initAccessible();
        ThemeManager.applyOnCreate(this, "FlatButton");
    }

    private static int kindOf(int style) {
        if ((style & SWT.CHECK) != 0) {
            return SWT.CHECK;
        }
        if ((style & SWT.RADIO) != 0) {
            return SWT.RADIO;
        }
        if ((style & SWT.TOGGLE) != 0) {
            return SWT.TOGGLE;
        }
        return SWT.PUSH;
    }

    /** Includes the button kind, so theme rules like {@code FlatButton[CHECK]} match. */
    @Override
    public int getStyle() {
        return super.getStyle() | kind;
    }

    public String getText() {
        checkWidget();
        return text;
    }

    public void setText(String text) {
        checkWidget();
        if (text == null) {
            SWT.error(SWT.ERROR_NULL_ARGUMENT);
        }
        this.text = text;
        redraw();
    }

    public Image getImage() {
        checkWidget();
        return image;
    }

    public void setImage(Image image) {
        checkWidget();
        this.image = image;
        redraw();
    }

    /** Checked state of a check, radio or toggle button; always {@code false} for push buttons. */
    public boolean getSelection() {
        checkWidget();
        return selection;
    }

    public void setSelection(boolean selected) {
        checkWidget();
        if (kind != SWT.PUSH) {
            selection = selected;
            redraw();
        }
    }

    public void addSelectionListener(SelectionListener listener) {
        checkWidget();
        if (listener == null) {
            SWT.error(SWT.ERROR_NULL_ARGUMENT);
        }
        addTypedListener(listener, SWT.Selection, SWT.DefaultSelection);
    }

    public void removeSelectionListener(SelectionListener listener) {
        checkWidget();
        removeTypedListener(SWT.Selection, listener);
        removeTypedListener(SWT.DefaultSelection, listener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        redraw();
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        checkWidget();
        GC gc = new GC(this);
        Point extent = text.isEmpty() ? new Point(0, gc.getFontMetrics().getHeight()) : gc.textExtent(text, SWT.DRAW_MNEMONIC);
        gc.dispose();
        int width;
        int height = extent.y;
        if (image != null) {
            Rectangle bounds = image.getBounds();
            height = Math.max(height, bounds.height);
            extent.x += bounds.width + (text.isEmpty() ? 0 : px(GAP));
        }
        if (isBox()) {
            width = px(BOX) + (extent.x > 0 ? px(GAP) + extent.x : 0) + 2;
            height = Math.max(height, px(BOX)) + 2;
        } else {
            width = extent.x + 2 * px(PADDING_X);
            height += 2 * px(PADDING_Y);
        }
        return new Point(wHint == SWT.DEFAULT ? width : wHint, hHint == SWT.DEFAULT ? height : hHint);
    }

    /** Simulates a click: what a mouse click, Space or the mnemonic does. */
    public void activate() {
        checkWidget();
        switch (kind) {
            case SWT.CHECK, SWT.TOGGLE -> selection = !selection;
            case SWT.RADIO -> {
                if (selection) {
                    return;
                }
                selection = true;
                deselectSiblings();
            }
            default -> {
            }
        }
        redraw();
        notifyListeners(SWT.Selection, new Event());
    }

    // ------------------------------------------------------------------ painting

    private boolean isBox() {
        return kind == SWT.CHECK || kind == SWT.RADIO;
    }

    private String state() {
        if (!isEnabled()) {
            return "disabled";
        }
        if (pressed) {
            return "pressed";
        }
        if (kind == SWT.TOGGLE && selection) {
            return ThemeManager.get(getDisplay()).styleOf(this).states().contains("selected") ? "selected" : "pressed";
        }
        return hover ? "hover" : null;
    }

    private void paint(Event event) {
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        Rectangle area = getClientArea();
        FlatPalette colors = FlatPalette.of(this, state());
        Color border = isFocusControl() && isEnabled() ? FlatPalette.of(this, "focused").border() : colors.border();

        if (isBox()) {
            paintBox(gc, area, colors, border);
            return;
        }
        int arc = colors.size("arc", 4);
        gc.setBackground(getParent().getBackground());
        gc.fillRectangle(area);
        gc.setBackground(colors.background());
        gc.fillRoundRectangle(area.x, area.y, area.width - 1, area.height - 1, arc, arc);
        gc.setForeground(border);
        gc.drawRoundRectangle(area.x, area.y, area.width - 1, area.height - 1, arc, arc);
        Point content = contentSize(gc);
        paintContent(gc, colors.foreground(), area.x + (area.width - content.x) / 2, area.y + (area.height - content.y) / 2, content.y);
    }

    private void paintBox(GC gc, Rectangle area, FlatPalette colors, Color border) {
        gc.setBackground(colors.background());
        gc.fillRectangle(area);
        int box = px(BOX);
        int y = area.y + (area.height - box) / 2;
        gc.setBackground(colors.color("boxBackground", colors.background()));
        Color accent = colors.accent();
        if (kind == SWT.CHECK) {
            if (selection) {
                gc.setBackground(accent);
            }
            gc.fillRoundRectangle(area.x, y, box, box, px(3), px(3));
            gc.setForeground(selection ? accent : border);
            gc.drawRoundRectangle(area.x, y, box, box, px(3), px(3));
            if (selection) {
                gc.setForeground(colors.color("checkColor", getDisplay().getSystemColor(SWT.COLOR_WHITE)));
                gc.setLineWidth(px(2));
                gc.drawPolyline(new int[] { area.x + px(3), y + px(7), area.x + px(6), y + px(10), area.x + px(11), y + px(4) });
                gc.setLineWidth(1);
            }
        } else {
            gc.fillOval(area.x, y, box, box);
            gc.setForeground(selection ? accent : border);
            gc.drawOval(area.x, y, box, box);
            if (selection) {
                gc.setBackground(accent);
                gc.fillOval(area.x + px(3), y + px(3), box - px(5), box - px(5));
            }
        }
        Point content = contentSize(gc);
        int gap = px(GAP);
        paintContent(gc, colors.foreground(), area.x + box + gap, area.y + (area.height - content.y) / 2, content.y);
        if (isFocusControl()) {
            gc.setForeground(border);
            gc.drawFocus(area.x + box + gap - 2, area.y, Math.min(content.x + 4, area.width - box - gap + 2), area.height);
        }
    }

    private int px(int pixels) {
        return FlatPalette.px(this, pixels);
    }

    private Point contentSize(GC gc) {
        Point extent = text.isEmpty() ? new Point(0, 0) : gc.textExtent(text, SWT.DRAW_MNEMONIC);
        if (image != null) {
            Rectangle bounds = image.getBounds();
            extent.x += bounds.width + (text.isEmpty() ? 0 : px(GAP));
            extent.y = Math.max(extent.y, bounds.height);
        }
        return extent;
    }

    private void paintContent(GC gc, Color foreground, int x, int y, int height) {
        if (image != null) {
            Rectangle bounds = image.getBounds();
            gc.drawImage(image, x, y + (height - bounds.height) / 2);
            x += bounds.width + px(GAP);
        }
        if (!text.isEmpty()) {
            gc.setForeground(foreground);
            int textHeight = gc.textExtent(text, SWT.DRAW_MNEMONIC).y;
            gc.drawText(text, x, y + (height - textHeight) / 2, SWT.DRAW_MNEMONIC | SWT.DRAW_TRANSPARENT);
        }
    }

    // ------------------------------------------------------------------ input

    private void setHover(boolean hover) {
        this.hover = hover;
        redraw();
    }

    private void mouseDown(Event event) {
        if (event.button == 1 && isEnabled()) {
            pressed = true;
            setFocus();
            redraw();
        }
    }

    private void mouseUp(Event event) {
        if (event.button != 1 || !pressed) {
            return;
        }
        pressed = false;
        redraw();
        if (getClientArea().contains(event.x, event.y)) {
            activate();
        }
    }

    private void keyDown(Event event) {
        if (event.character == ' ' || (kind == SWT.PUSH && (event.character == SWT.CR || event.keyCode == SWT.KEYPAD_CR))) {
            activate();
        }
    }

    private void traverse(Event event) {
        switch (event.detail) {
            case SWT.TRAVERSE_TAB_NEXT, SWT.TRAVERSE_TAB_PREVIOUS, SWT.TRAVERSE_ESCAPE,
                    SWT.TRAVERSE_PAGE_NEXT, SWT.TRAVERSE_PAGE_PREVIOUS -> event.doit = true;
            case SWT.TRAVERSE_RETURN -> event.doit = kind != SWT.PUSH;
            case SWT.TRAVERSE_MNEMONIC -> {
                if (matchesMnemonic(event.character)) {
                    event.doit = true;
                    event.detail = SWT.TRAVERSE_NONE;
                    setFocus();
                    activate();
                }
            }
            default -> {
            }
        }
    }

    boolean matchesMnemonic(char character) {
        int index = text.indexOf('&');
        while (index >= 0 && index + 1 < text.length()) {
            char next = text.charAt(index + 1);
            if (next != '&') {
                return Character.toLowerCase(next) == Character.toLowerCase(character);
            }
            index = text.indexOf('&', index + 2);
        }
        return false;
    }

    private void deselectSiblings() {
        Composite parent = getParent();
        if ((parent.getStyle() & SWT.NO_RADIO_GROUP) != 0) {
            return;
        }
        for (Control sibling : parent.getChildren()) {
            if (sibling != this && sibling instanceof FlatButton button && button.kind == SWT.RADIO && button.selection) {
                button.selection = false;
                button.redraw();
                button.notifyListeners(SWT.Selection, new Event());
            }
        }
    }

    // ------------------------------------------------------------------ accessibility

    private void initAccessible() {
        getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                e.result = text.replaceAll("&(.)", "$1");
            }
        });
        getAccessible().addAccessibleControlListener(new AccessibleControlAdapter() {
            @Override
            public void getRole(AccessibleControlEvent e) {
                e.detail = switch (kind) {
                    case SWT.CHECK -> ACC.ROLE_CHECKBUTTON;
                    case SWT.RADIO -> ACC.ROLE_RADIOBUTTON;
                    default -> ACC.ROLE_PUSHBUTTON;
                };
            }

            @Override
            public void getState(AccessibleControlEvent e) {
                int state = ACC.STATE_FOCUSABLE;
                if (isFocusControl()) {
                    state |= ACC.STATE_FOCUSED;
                }
                if (selection) {
                    state |= kind == SWT.TOGGLE ? ACC.STATE_PRESSED : ACC.STATE_CHECKED;
                }
                if (!isEnabled()) {
                    state |= ACC.STATE_DISABLED;
                }
                e.detail = state;
            }
        });
    }
}
