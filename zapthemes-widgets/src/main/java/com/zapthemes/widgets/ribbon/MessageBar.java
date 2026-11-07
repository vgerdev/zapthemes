package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.swt.SWT;
import org.eclipse.swt.accessibility.ACC;
import org.eclipse.swt.accessibility.AccessibleAdapter;
import org.eclipse.swt.accessibility.AccessibleControlAdapter;
import org.eclipse.swt.accessibility.AccessibleControlEvent;
import org.eclipse.swt.accessibility.AccessibleEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.widgets.FlatButton;

/**
 * A message bar under the ribbon, like Office's "RECOVERED UNSAVED FILE … Save": an icon, a bold
 * title, a message, buttons and a close button. The app places it (usually right under the
 * ribbon) and disposes or hides it.
 *
 * <p>Theme type {@code RibbonMessageBar}: {@code background}, {@code foreground},
 * {@code borderColor}, {@code iconColor}, {@code titleFont}, with the states {@code info},
 * {@code warning}, {@code error} and {@code success} for each {@link Kind}.
 */
public final class MessageBar extends Canvas {

    public static final String THEME_TYPE = "RibbonMessageBar";

    public enum Kind { INFO, WARNING, ERROR, SUCCESS }

    private Kind kind;
    private String title = "";
    private String message = "";
    private final List<FlatButton> buttons = new ArrayList<>();
    private boolean closeHover;
    private Runnable onClose;

    @SuppressWarnings("this-escape")
    public MessageBar(Composite parent, int style, Kind kind) {
        super(parent, style | SWT.DOUBLE_BUFFERED);
        this.kind = Objects.requireNonNull(kind);
        ThemeManager.applyOnCreate(this, THEME_TYPE);
        addListener(SWT.Paint, this::paint);
        addListener(SWT.Resize, e -> layoutButtons());
        addListener(SWT.MouseMove, e -> {
            boolean hover = closeBounds().contains(e.x, e.y);
            if (hover != closeHover) {
                closeHover = hover;
                redraw();
            }
        });
        addListener(SWT.MouseUp, e -> {
            if (e.button == 1 && closeBounds().contains(e.x, e.y)) {
                close();
            }
        });
        getAccessible().addAccessibleListener(new AccessibleAdapter() {
            @Override
            public void getName(AccessibleEvent e) {
                e.result = title + ": " + message;
            }
        });
        getAccessible().addAccessibleControlListener(new AccessibleControlAdapter() {
            @Override
            public void getRole(AccessibleControlEvent e) {
                e.detail = ACC.ROLE_ALERT;
            }
        });
    }

    public Kind kind() {
        return kind;
    }

    public void setKind(Kind value) {
        kind = Objects.requireNonNull(value);
        redraw();
    }

    public void setTitle(String text) {
        title = text == null ? "" : text;
        layoutButtons();
        redraw();
    }

    public String getTitle() {
        return title;
    }

    public void setMessage(String text) {
        message = text == null ? "" : text;
        layoutButtons();
        redraw();
    }

    public String getMessage() {
        return message;
    }

    /** Adds a button after the message; {@code action} runs when it is pressed. */
    public FlatButton addButton(String text, Runnable action) {
        FlatButton button = new FlatButton(this, SWT.PUSH);
        button.setText(text);
        button.addListener(SWT.Selection, e -> action.run());
        buttons.add(button);
        layoutButtons();
        return button;
    }

    /** Runs when the close button is pressed; by default the bar hides and its parent relays out. */
    public void onClose(Runnable action) {
        onClose = action;
    }

    public void close() {
        if (onClose != null) {
            onClose.run();
        } else {
            setVisible(false);
            if (getLayoutData() instanceof org.eclipse.swt.layout.GridData data) {
                data.exclude = true;
            }
            getParent().layout(true, true);
        }
    }

    private Themed theme() {
        return Themed.of(this, kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    private Font titleFont() {
        return theme().font("titleFont", getFont());
    }

    private int height(GC gc) {
        return gc.getFontMetrics().getHeight() + 16;
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        GC gc = new GC(this);
        try {
            int height = height(gc);
            for (FlatButton button : buttons) {
                height = Math.max(height, button.computeSize(SWT.DEFAULT, SWT.DEFAULT).y + 8);
            }
            return new Point(wHint == SWT.DEFAULT ? 400 : wHint, hHint == SWT.DEFAULT ? height : hHint);
        } finally {
            gc.dispose();
        }
    }

    private int textEnd(GC gc) {
        int x = 12 + gc.getFontMetrics().getHeight() + 8;
        gc.setFont(titleFont());
        x += gc.textExtent(title.toUpperCase(java.util.Locale.ROOT)).x + 12;
        gc.setFont(getFont());
        x += gc.textExtent(message).x + 12;
        return x;
    }

    private void layoutButtons() {
        if (isDisposed()) {
            return;
        }
        GC gc = new GC(this);
        int x;
        try {
            x = textEnd(gc);
        } finally {
            gc.dispose();
        }
        Rectangle area = getClientArea();
        for (FlatButton button : buttons) {
            Point size = button.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            button.setBounds(x, Math.max(0, (area.height - size.y) / 2), size.x, size.y);
            x += size.x + 8;
        }
    }

    Rectangle closeBounds() {
        Rectangle area = getClientArea();
        int size = Math.max(16, area.height - 12);
        return new Rectangle(area.width - size - 8, (area.height - size) / 2, size, size);
    }

    private void paint(Event event) {
        GC gc = event.gc;
        gc.setAntialias(SWT.ON);
        gc.setTextAntialias(SWT.ON);
        Themed theme = theme();
        Rectangle area = getClientArea();
        Color background = theme.color("background", getBackground());
        Color foreground = theme.color("foreground", getForeground());
        gc.setBackground(background);
        gc.fillRectangle(area);
        gc.setForeground(theme.color("borderColor", foreground));
        gc.drawRectangle(0, 0, area.width - 1, area.height - 1);
        int height = gc.getFontMetrics().getHeight();
        int y = (area.height - height) / 2;
        Color icon = theme.color("iconColor", foreground);
        gc.setForeground(icon);
        gc.setLineWidth(Math.max(1, height / 12));
        gc.drawOval(12, y, height - 1, height - 1);
        int cx = 12 + height / 2;
        if (kind == Kind.INFO || kind == Kind.SUCCESS) {
            gc.drawLine(cx, y + height / 2 - 1, cx, y + height - 4);
            gc.drawPoint(cx, y + 3);
        } else {
            gc.drawLine(cx, y + 3, cx, y + height / 2 + 1);
            gc.drawPoint(cx, y + height - 4);
        }
        gc.setLineWidth(1);
        int x = 12 + height + 8;
        gc.setForeground(foreground);
        gc.setFont(titleFont());
        String upper = title.toUpperCase(java.util.Locale.ROOT);
        gc.drawText(upper, x, (area.height - gc.getFontMetrics().getHeight()) / 2, SWT.DRAW_TRANSPARENT);
        x += gc.textExtent(upper).x + 12;
        gc.setFont(getFont());
        gc.drawText(message, x, y, SWT.DRAW_TRANSPARENT);
        Rectangle close = closeBounds();
        if (closeHover) {
            gc.setBackground(RibbonColors.mix(background, foreground, 0.12));
            gc.fillRectangle(close);
        }
        gc.setForeground(foreground);
        int inset = close.width / 3;
        gc.drawLine(close.x + inset, close.y + inset, close.x + close.width - inset, close.y + close.height - inset);
        gc.drawLine(close.x + close.width - inset, close.y + inset, close.x + inset, close.y + close.height - inset);
    }
}
