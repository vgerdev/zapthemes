package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.widgets.command.Command;

/**
 * A button showing a {@link Command}. The command's style makes it a push, toggle, split or
 * drop-down button; its label, images, enabled and checked states are drawn live.
 */
public class RibbonButton extends RibbonItem {

    private final Command command;

    public RibbonButton(Command command) {
        super(command.id());
        this.command = command;
    }

    @Override
    public Command command() {
        return command;
    }

    @Override
    public String label() {
        return command.plainLabel();
    }

    boolean isSplit() {
        return command.style() == Command.Style.SPLIT;
    }

    boolean hasArrow() {
        return isSplit() || command.style() == Command.Style.DROP_DOWN;
    }

    @Override
    boolean hasPopup() {
        return hasArrow();
    }

    boolean isChecked() {
        return command.style() == Command.Style.TOGGLE && command.isChecked();
    }

    @Override
    int accessibleRole() {
        if (command.style() == Command.Style.TOGGLE) {
            return ACC.ROLE_CHECKBUTTON;
        }
        if (isSplit()) {
            return ACC.ROLE_SPLITBUTTON;
        }
        return ACC.ROLE_PUSHBUTTON;
    }

    private boolean hasImage(RibbonMetrics metrics, Size size) {
        return command.images().image(size == Size.LARGE ? metrics.largeIcon : metrics.smallIcon) != null;
    }

    @Override
    Point measure(RibbonMetrics m, Size size) {
        int arrow = hasArrow() ? m.arrow : 0;
        switch (size) {
            case LARGE -> {
                String[] lines = m.lines(command.label(), hasArrow());
                int width = m.largeIcon;
                for (int i = 0; i < lines.length; i++) {
                    int line = m.text(lines[i]);
                    if (i == lines.length - 1 && arrow > 0) {
                        line += (line > 0 ? m.px(4) : 0) + arrow;
                    }
                    width = Math.max(width, line);
                }
                return new Point(width + m.px(10), m.contentHeight);
            }
            case MEDIUM -> {
                int width = m.px(4) + (hasImage(m, size) ? m.smallIcon + m.px(4) : 0) + m.text(command.label()) + m.px(5);
                if (arrow > 0) {
                    width += (isSplit() ? m.px(8) : m.px(4)) + arrow;
                }
                return new Point(width, m.row);
            }
            default -> {
                int width = m.px(3) + (hasImage(m, size) ? m.smallIcon : m.text(command.label())) + m.px(3);
                if (arrow > 0) {
                    width += (isSplit() ? m.px(6) : m.px(2)) + arrow;
                }
                return new Point(width, m.row);
            }
        }
    }

    @Override
    Rectangle arrowBounds(RibbonMetrics m, ItemBox box) {
        if (!isSplit()) {
            return null;
        }
        Rectangle b = box.bounds();
        if (box.size() == Size.LARGE) {
            int top = b.y + m.px(3) + m.largeIcon + m.px(2);
            return new Rectangle(b.x, top, b.width, b.y + b.height - top);
        }
        int width = m.arrow + m.px(6);
        return new Rectangle(b.x + b.width - width, b.y, width, b.height);
    }

    @Override
    void paint(RibbonSurface surface, GC gc, ItemBox box) {
        RibbonMetrics m = surface.metrics();
        RibbonColors colors = surface.colors();
        Rectangle b = box.bounds();
        boolean enabled = isEnabled();
        Rectangle arrow = arrowBounds(m, box);
        surface.paintItemBackground(gc, box, arrow, isChecked());
        Color foreground = enabled ? colors.foreground : colors.disabled;
        gc.setForeground(foreground);
        gc.setFont(m.font);
        int arrowWidth = hasArrow() ? m.arrow : 0;
        switch (box.size()) {
            case LARGE -> {
                int iconX = b.x + (b.width - m.largeIcon) / 2;
                int iconY = b.y + m.px(3);
                surface.drawImage(gc, command.images(), m.largeIcon, iconX, iconY, enabled);
                paintLargeExtras(surface, gc, m, iconX, iconY, m.largeIcon);
                String[] lines = m.lines(command.label(), hasArrow());
                int y = iconY + m.largeIcon + m.px(3);
                int lineHeight = m.fontHeight;
                for (int i = 0; i < lines.length; i++) {
                    int width = m.text(lines[i]);
                    boolean last = i == lines.length - 1;
                    int total = width + (last && arrowWidth > 0 ? (width > 0 ? m.px(4) : 0) + arrowWidth : 0);
                    int x = b.x + (b.width - total) / 2;
                    gc.setForeground(foreground);
                    gc.drawText(lines[i], x, y, SWT.DRAW_TRANSPARENT);
                    if (last && arrowWidth > 0) {
                        surface.drawArrow(gc, x + total - arrowWidth, y + (lineHeight - arrowWidth / 2) / 2, arrowWidth, foreground);
                    }
                    y += lineHeight;
                }
            }
            case MEDIUM, SMALL -> {
                int x = b.x + (box.size() == Size.MEDIUM ? m.px(4) : m.px(3));
                int iconY = b.y + (b.height - m.smallIcon) / 2;
                boolean image = hasImage(m, box.size());
                if (image) {
                    surface.drawImage(gc, command.images(), m.smallIcon, x, iconY, enabled);
                    paintSmallExtras(surface, gc, m, x, iconY, m.smallIcon);
                    x += m.smallIcon;
                }
                if (box.size() == Size.MEDIUM || !image) {
                    x += image ? m.px(4) : 0;
                    String text = command.plainLabel();
                    gc.setForeground(foreground);
                    gc.drawText(text, x, b.y + (b.height - m.fontHeight) / 2, SWT.DRAW_TRANSPARENT);
                    x += m.text(text);
                }
                if (arrowWidth > 0) {
                    int ax = arrow != null ? arrow.x + (arrow.width - arrowWidth) / 2 : x + m.px(box.size() == Size.MEDIUM ? 4 : 2);
                    surface.drawArrow(gc, ax, b.y + (b.height - arrowWidth / 2) / 2, arrowWidth, foreground);
                }
            }
            default -> {
            }
        }
    }

    /** Hook for subclasses: paints over the large icon (a color bar). */
    void paintLargeExtras(RibbonSurface surface, GC gc, RibbonMetrics m, int x, int y, int size) {
    }

    /** Hook for subclasses: paints over the small icon (a color bar). */
    void paintSmallExtras(RibbonSurface surface, GC gc, RibbonMetrics m, int x, int y, int size) {
    }

    @Override
    void activate(RibbonSurface surface, ItemBox box, boolean arrow, Event event) {
        if (!isEnabled()) {
            return;
        }
        if (command.style() == Command.Style.DROP_DOWN || (isSplit() && arrow)) {
            surface.openMenu(this, box);
        } else {
            surface.run(command, event, false, value());
        }
    }

    /** The value a run carries: none for plain buttons. */
    Object value() {
        return null;
    }
}
