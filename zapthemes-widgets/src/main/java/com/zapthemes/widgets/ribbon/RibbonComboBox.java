package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;

/**
 * A combo box drawn on the ribbon, like Office's font name and size boxes: a field and an arrow.
 * The arrow opens a list; when editable, clicking the field opens an editor over it (Enter
 * commits, Esc cancels). Listeners get the new text.
 */
public class RibbonComboBox extends RibbonItem {

    private final String label;
    private final List<String> items = new ArrayList<>();
    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private String text = "";
    private String description;
    private boolean editable = true;
    private boolean enabled = true;
    private int width = 100;

    /** {@code label} is its accessible name and tooltip title ("Font", "Font Size"). */
    @SuppressWarnings("this-escape")
    public RibbonComboBox(String id, String label) {
        super(id);
        this.label = Objects.requireNonNull(label, "label");
        defaultSizes(Size.SMALL, Size.SMALL);
    }

    @Override
    public String label() {
        return label;
    }

    /** The field's width at 100 %, in pixels (default 100); scaled with the font. */
    public RibbonComboBox width(int pixels) {
        width = pixels;
        changed();
        return this;
    }

    public RibbonComboBox items(String... values) {
        return items(List.of(values));
    }

    public RibbonComboBox items(List<String> values) {
        items.clear();
        items.addAll(values);
        return this;
    }

    public List<String> items() {
        return List.copyOf(items);
    }

    public String text() {
        return text;
    }

    /** Sets the text without telling listeners. */
    public RibbonComboBox text(String value) {
        text = value == null ? "" : value;
        repaint();
        return this;
    }

    public RibbonComboBox editable(boolean value) {
        editable = value;
        return this;
    }

    public boolean isEditable() {
        return editable;
    }

    public RibbonComboBox description(String value) {
        description = value;
        return this;
    }

    @Override
    public String description() {
        return description;
    }

    public void setEnabled(boolean value) {
        enabled = value;
        repaint();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** Told the new text when the user picks or types one. */
    public RibbonComboBox onChange(Consumer<String> listener) {
        listeners.add(Objects.requireNonNull(listener));
        return this;
    }

    /** Sets the text as the user would: listeners are told. */
    public void commit(String value) {
        text(value);
        listeners.forEach(l -> l.accept(text));
    }

    @Override
    boolean hasPopup() {
        return true;
    }

    @Override
    int accessibleRole() {
        return ACC.ROLE_COMBOBOX;
    }

    @Override
    Point measure(RibbonMetrics m, Size size) {
        return new Point(m.px(width) + m.arrow + m.px(8), m.row);
    }

    /** The field, without the arrow. */
    Rectangle field(RibbonMetrics m, ItemBox box) {
        Rectangle b = box.bounds();
        int inset = m.px(2);
        return new Rectangle(b.x + 1, b.y + inset, b.width - 2, b.height - 2 * inset);
    }

    @Override
    Rectangle arrowBounds(RibbonMetrics m, ItemBox box) {
        Rectangle field = field(m, box);
        int width = m.arrow + m.px(8);
        return new Rectangle(field.x + field.width - width, field.y, width, field.height);
    }

    @Override
    void paint(RibbonSurface surface, GC gc, ItemBox box) {
        RibbonMetrics m = surface.metrics();
        RibbonColors colors = surface.colors();
        Rectangle field = field(m, box);
        Rectangle arrow = arrowBounds(m, box);
        gc.setBackground(colors.fieldBackground);
        gc.fillRectangle(field);
        boolean hot = enabled && (surface.isHovered(box) || surface.isFocused(box));
        if (enabled && surface.isHovered(box, true)) {
            gc.setBackground(colors.hover);
            gc.fillRectangle(arrow);
        }
        gc.setForeground(hot ? colors.checkedBorder : colors.border);
        gc.drawRectangle(field.x, field.y, field.width - 1, field.height - 1);
        gc.setFont(m.font);
        gc.setForeground(enabled ? colors.foreground : colors.disabled);
        Rectangle clip = gc.getClipping();
        gc.setClipping(field.x + 2, field.y, field.width - arrow.width - 3, field.height);
        gc.drawText(text, field.x + m.px(4), field.y + (field.height - m.fontHeight) / 2, SWT.DRAW_TRANSPARENT);
        gc.setClipping(clip);
        surface.drawArrow(gc, arrow.x + (arrow.width - m.arrow) / 2, arrow.y + (arrow.height - m.arrow / 2) / 2, m.arrow,
                enabled ? colors.foreground : colors.disabled);
    }

    @Override
    void activate(RibbonSurface surface, ItemBox box, boolean arrow, Event event) {
        if (!enabled) {
            return;
        }
        if (editable && !arrow) {
            surface.editCombo(this, box);
        } else {
            surface.openComboList(this, box);
        }
    }
}
