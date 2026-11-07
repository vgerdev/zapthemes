package com.zapthemes.widgets.ribbon;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.widgets.command.Command;

/**
 * Office's "super tooltip": the command's label in bold with its shortcut, then its description,
 * shown under the item after a hover delay. Theme type {@code RibbonToolTip} ({@code background},
 * {@code foreground}, {@code borderColor}, {@code titleFont}).
 */
final class SuperToolTip {

    static final int DELAY = 600;

    private final RibbonSurface surface;
    private Hot pending;
    private Shell shell;
    private Runnable timer;

    SuperToolTip(RibbonSurface surface) {
        this.surface = surface;
    }

    void schedule(Hot hot) {
        cancel();
        if (shell != null && !shell.isDisposed()) {
            hide();
        }
        if (hot == null || hot.kind() == Hot.Kind.GROUP || surface.ribbon().keyTips().isShowing() || surface.ribbon().hasOpenPopup(surface)) {
            return;
        }
        pending = hot;
        timer = () -> {
            timer = null;
            if (!surface.isDisposed() && pending != null) {
                show(pending);
            }
        };
        surface.getDisplay().timerExec(DELAY, timer);
    }

    private void cancel() {
        if (timer != null && !surface.isDisposed()) {
            surface.getDisplay().timerExec(-1, timer);
        }
        timer = null;
    }

    /** The tooltip's title for {@code hot}: the label and the shortcut. */
    String title(Hot hot) {
        String title = surface.nameOf(hot);
        Command command = surface.commandOf(hot);
        if (command != null && command.shortcut() != 0) {
            title += " (" + command.shortcutText() + ")";
        }
        return title;
    }

    void show(Hot hot) {
        hide();
        String title = title(hot);
        String body = surface.descriptionOf(hot);
        if ((title == null || title.isEmpty()) && body == null) {
            return;
        }
        shell = new Shell(surface.getShell(), SWT.ON_TOP | SWT.TOOL | SWT.NO_FOCUS);
        shell.setLayout(new org.eclipse.swt.layout.FillLayout());
        Tip tip = new Tip(shell, title, body);
        Point size = tip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        Rectangle at = surface.toDisplay(hot.bounds());
        Rectangle monitor = surface.getMonitor().getClientArea();
        int x = Math.max(monitor.x, Math.min(at.x, monitor.x + monitor.width - size.x));
        int y = at.y + at.height + 4;
        if (y + size.y > monitor.y + monitor.height) {
            y = at.y - size.y - 4;
        }
        shell.setBounds(x, y, size.x, size.y);
        shell.setVisible(true);
    }

    boolean isShowing() {
        return shell != null && !shell.isDisposed() && shell.isVisible();
    }

    /** The shell while it shows, for tests. */
    Shell shell() {
        return shell;
    }

    void hide() {
        cancel();
        pending = null;
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
        shell = null;
    }

    void dispose() {
        hide();
    }

    static List<String> wrap(GC gc, String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && gc.textExtent(candidate).x > width) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    /** The tooltip's canvas. */
    static final class Tip extends Canvas {

        private final String title;
        private final String body;

        Tip(Composite parent, String title, String body) {
            super(parent, SWT.DOUBLE_BUFFERED);
            this.title = title;
            this.body = body;
            ThemeManager.applyOnCreate(this, "RibbonToolTip");
            addListener(SWT.Paint, this::paint);
        }

        private int pad(GC gc) {
            return Math.max(6, gc.getFontMetrics().getHeight() / 2);
        }

        private int wrapWidth(GC gc) {
            return gc.getFontMetrics().getHeight() * 18;
        }

        private Font titleFont() {
            return Themed.of(this).font("titleFont", getFont());
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            GC gc = new GC(this);
            try {
                int pad = pad(gc);
                gc.setFont(titleFont());
                Point titleSize = title == null ? new Point(0, 0) : gc.textExtent(title);
                int width = titleSize.x;
                int height = titleSize.y;
                gc.setFont(getFont());
                if (body != null) {
                    for (String line : wrap(gc, body, wrapWidth(gc))) {
                        Point extent = gc.textExtent(line.isEmpty() ? " " : line);
                        width = Math.max(width, extent.x);
                        height += extent.y;
                    }
                    height += pad / 2;
                }
                return new Point(width + 2 * pad, height + 2 * pad);
            } finally {
                gc.dispose();
            }
        }

        private void paint(Event event) {
            GC gc = event.gc;
            Themed theme = Themed.of(this);
            Rectangle area = getClientArea();
            gc.setBackground(theme.color("background", getBackground()));
            gc.fillRectangle(area);
            gc.setForeground(theme.color("borderColor", getForeground()));
            gc.drawRectangle(0, 0, area.width - 1, area.height - 1);
            int pad = pad(gc);
            int y = pad;
            gc.setForeground(theme.color("foreground", getForeground()));
            if (title != null) {
                gc.setFont(titleFont());
                gc.drawText(title, pad, y, SWT.DRAW_TRANSPARENT);
                y += gc.textExtent(title).y + pad / 2;
            }
            gc.setFont(getFont());
            if (body != null) {
                for (String line : wrap(gc, body, wrapWidth(gc))) {
                    gc.drawText(line, pad, y, SWT.DRAW_TRANSPARENT);
                    y += gc.textExtent(line.isEmpty() ? " " : line).y;
                }
            }
        }
    }
}
