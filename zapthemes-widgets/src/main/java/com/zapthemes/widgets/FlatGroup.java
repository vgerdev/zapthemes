package com.zapthemes.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;

/**
 * A group box drawn on a canvas, for where the native {@code Group} keeps its title color
 * (Windows). Children and layouts work as with {@code Group}: the client area excludes the title
 * and border.
 *
 * <p>Theme type {@code "FlatGroup"}. Properties: {@code background}, {@code foreground},
 * {@code borderColor}, {@code titleForeground} (defaults to {@code foreground}), {@code titleFont}
 * (defaults to {@code font}), {@code arc}.
 */
public class FlatGroup extends Composite {

    private static final int MARGIN = 6;

    private String text = "";

    @SuppressWarnings("this-escape")
    public FlatGroup(Composite parent, int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        addListener(SWT.Paint, this::paint);
        getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                e.result = text;
            }
        });
        getAccessible().addAccessibleControlListener(new AccessibleControlAdapter() {
            @Override
            public void getRole(AccessibleControlEvent e) {
                e.detail = ACC.ROLE_GROUP;
            }
        });
        ThemeManager.applyOnCreate(this, "FlatGroup");
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
        layout(true);
        redraw();
    }

    @Override
    public Rectangle getClientArea() {
        Rectangle area = super.getClientArea();
        int margin = FlatPalette.px(this, MARGIN);
        int top = titleHeight() + margin;
        return new Rectangle(area.x + margin + 1, area.y + top, Math.max(0, area.width - 2 * margin - 2),
                Math.max(0, area.height - top - margin - 1));
    }

    @Override
    public Rectangle computeTrim(int x, int y, int width, int height) {
        Rectangle trim = super.computeTrim(x, y, width, height);
        int margin = FlatPalette.px(this, MARGIN);
        int top = titleHeight() + margin;
        return new Rectangle(trim.x - margin - 1, trim.y - top, trim.width + 2 * margin + 2, trim.height + top + margin + 1);
    }

    private int titleHeight() {
        GC gc = new GC(this);
        try {
            gc.setFont(FlatPalette.of(this, null).font("titleFont", getFont()));
            return text.isEmpty() ? gc.getFontMetrics().getHeight() / 2 : gc.textExtent(text).y;
        } finally {
            gc.dispose();
        }
    }

    private void paint(Event event) {
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        FlatPalette colors = FlatPalette.of(this, isEnabled() ? null : "disabled");
        Rectangle area = super.getClientArea();
        gc.setFont(colors.font("titleFont", getFont()));
        Point title = text.isEmpty() ? new Point(0, gc.getFontMetrics().getHeight()) : gc.textExtent(text);
        int lineY = area.y + title.y / 2;
        int arc = colors.size("arc", 6);

        gc.setForeground(colors.border());
        gc.drawRoundRectangle(area.x, lineY, area.width - 1, area.height - lineY - 1, arc, arc);
        if (!text.isEmpty()) {
            int x = area.x + FlatPalette.px(this, MARGIN) + 2;
            gc.setBackground(colors.background());
            gc.fillRectangle(x - FlatPalette.px(this, 3), area.y, title.x + FlatPalette.px(this, 6), title.y);
            gc.setForeground(colors.color("titleForeground", colors.foreground()));
            gc.drawText(text, x, area.y, true);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        redraw();
    }
}
