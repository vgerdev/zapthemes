package com.zapthemes.widgets.titlebar;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

/**
 * The frame of a {@code NO_TRIM} shell, drawn and handled by ZapThemes: the bar drags the window and
 * maximizes it on double-click, a display filter gives the shell resize edges on all four sides,
 * and the system menu's Move and Size work with the arrow keys.
 */
final class DrawnFrame extends WindowFrame {

    /** Which edges a point is on: bits of {@link SWT#LEFT}, {@link SWT#RIGHT}, {@link SWT#TOP}, {@link SWT#BOTTOM}. */
    static final int NONE = 0;

    private final Listener filter = this::filter;
    private final Listener keys = this::keyMode;
    private Point dragStart;
    private Point shellStart;
    private boolean dragged;

    private int resizeEdges = NONE;
    private Point resizeStart;
    private Rectangle boundsStart;
    private Control cursorControl;
    private Cursor cursorBefore;

    /** {@code SWT.TOP} for keyboard Move, {@code SWT.BOTTOM | SWT.RIGHT} for Size, 0 when off. */
    private int keyboardMode;

    DrawnFrame(TitleBar bar) {
        super(bar);
        Display display = shell.getDisplay();
        display.addFilter(SWT.MouseMove, filter);
        display.addFilter(SWT.MouseDown, filter);
        display.addFilter(SWT.MouseUp, filter);
        display.addFilter(SWT.MouseExit, filter);
    }

    @Override
    TitleBar.FrameMode mode() {
        return TitleBar.FrameMode.DRAWN;
    }

    @Override
    void dispose() {
        Display display = shell.getDisplay();
        display.removeFilter(SWT.MouseMove, filter);
        display.removeFilter(SWT.MouseDown, filter);
        display.removeFilter(SWT.MouseUp, filter);
        display.removeFilter(SWT.MouseExit, filter);
        display.removeFilter(SWT.KeyDown, keys);
        restoreCursor();
    }

    // ------------------------------------------------------------------ dragging

    @Override
    void captionPressed(Event event) {
        dragStart = bar.toDisplay(event.x, event.y);
        shellStart = shell.getLocation();
        dragged = false;
    }

    @Override
    void captionDragged(Event event) {
        if (dragStart == null) {
            return;
        }
        Point now = bar.toDisplay(event.x, event.y);
        int dx = now.x - dragStart.x;
        int dy = now.y - dragStart.y;
        if (!dragged && Math.abs(dx) < 4 && Math.abs(dy) < 4) {
            return;
        }
        if (!dragged && shell.getMaximized()) {
            // Restore, keeping the pointer at the same fraction of the bar's width.
            int width = bar.getSize().x;
            double fraction = width > 0 ? (double) event.x / width : 0.5;
            shell.setMaximized(false);
            int restored = shell.getSize().x;
            shellStart = new Point(now.x - (int) Math.round(restored * fraction), now.y - event.y);
            dragStart = now;
            dx = 0;
            dy = 0;
        }
        dragged = true;
        shell.setLocation(shellStart.x + dx, shellStart.y + dy);
    }

    @Override
    void captionReleased(Event event) {
        dragStart = null;
    }

    // ------------------------------------------------------------------ resize edges

    /** The edges within {@code border} of {@code bounds} that {@code point} is on. */
    static int edgesAt(Rectangle bounds, Point point, int border) {
        if (!bounds.contains(point)) {
            return NONE;
        }
        int edges = NONE;
        if (point.x < bounds.x + border) {
            edges |= SWT.LEFT;
        } else if (point.x >= bounds.x + bounds.width - border) {
            edges |= SWT.RIGHT;
        }
        if (point.y < bounds.y + border) {
            edges |= SWT.TOP;
        } else if (point.y >= bounds.y + bounds.height - border) {
            edges |= SWT.BOTTOM;
        }
        // Corners are easier to catch: a wider zone along the edge one is already on.
        int corner = border * 3;
        if (edges == SWT.TOP || edges == SWT.BOTTOM) {
            if (point.x < bounds.x + corner) {
                edges |= SWT.LEFT;
            } else if (point.x >= bounds.x + bounds.width - corner) {
                edges |= SWT.RIGHT;
            }
        } else if (edges == SWT.LEFT || edges == SWT.RIGHT) {
            if (point.y < bounds.y + corner) {
                edges |= SWT.TOP;
            } else if (point.y >= bounds.y + bounds.height - corner) {
                edges |= SWT.BOTTOM;
            }
        }
        return edges;
    }

    private int border() {
        return Math.max(4, Math.round(bar.unit() * 5));
    }

    private boolean canResize() {
        return bar.isResizable() && !shell.getMaximized() && !shell.getMinimized() && !shell.getFullScreen();
    }

    private void filter(Event event) {
        if (!(event.widget instanceof Control control) || control.isDisposed() || control.getShell() != shell) {
            return;
        }
        Point point = control.toDisplay(event.x, event.y);
        switch (event.type) {
            case SWT.MouseMove -> {
                if (resizeEdges != NONE) {
                    resizeTo(point);
                    event.type = SWT.None;
                } else {
                    updateCursor(control, canResize() ? edgesAt(shell.getBounds(), point, border()) : NONE);
                }
            }
            case SWT.MouseDown -> {
                int edges = event.button == 1 && canResize() ? edgesAt(shell.getBounds(), point, border()) : NONE;
                if (edges != NONE) {
                    resizeEdges = edges;
                    resizeStart = point;
                    boundsStart = shell.getBounds();
                    event.type = SWT.None;
                }
            }
            case SWT.MouseUp -> {
                if (resizeEdges != NONE) {
                    resizeEdges = NONE;
                    event.type = SWT.None;
                }
            }
            case SWT.MouseExit -> {
                if (resizeEdges == NONE && control == cursorControl) {
                    restoreCursor();
                }
            }
            default -> {
            }
        }
    }

    private void resizeTo(Point point) {
        int dx = point.x - resizeStart.x;
        int dy = point.y - resizeStart.y;
        shell.setBounds(resized(boundsStart, resizeEdges, dx, dy, shell.getMinimumSize()));
    }

    /** {@code start} with {@code edges} moved by ({@code dx}, {@code dy}), no smaller than {@code minimum}. */
    static Rectangle resized(Rectangle start, int edges, int dx, int dy, Point minimum) {
        int minWidth = Math.max(minimum.x, 120);
        int minHeight = Math.max(minimum.y, 60);
        int x = start.x;
        int y = start.y;
        int width = start.width;
        int height = start.height;
        if ((edges & SWT.LEFT) != 0) {
            int w = Math.max(minWidth, width - dx);
            x += width - w;
            width = w;
        } else if ((edges & SWT.RIGHT) != 0) {
            width = Math.max(minWidth, width + dx);
        }
        if ((edges & SWT.TOP) != 0) {
            int h = Math.max(minHeight, height - dy);
            y += height - h;
            height = h;
        } else if ((edges & SWT.BOTTOM) != 0) {
            height = Math.max(minHeight, height + dy);
        }
        return new Rectangle(x, y, width, height);
    }

    private void updateCursor(Control control, int edges) {
        if (edges == NONE) {
            if (control == cursorControl) {
                restoreCursor();
            }
            return;
        }
        if (cursorControl != control) {
            restoreCursor();
            cursorControl = control;
            cursorBefore = control.getCursor();
        }
        control.setCursor(control.getDisplay().getSystemCursor(cursorFor(edges)));
    }

    static int cursorFor(int edges) {
        return switch (edges) {
            case SWT.LEFT -> SWT.CURSOR_SIZEW;
            case SWT.RIGHT -> SWT.CURSOR_SIZEE;
            case SWT.TOP -> SWT.CURSOR_SIZEN;
            case SWT.BOTTOM -> SWT.CURSOR_SIZES;
            case SWT.TOP | SWT.LEFT -> SWT.CURSOR_SIZENW;
            case SWT.TOP | SWT.RIGHT -> SWT.CURSOR_SIZENE;
            case SWT.BOTTOM | SWT.LEFT -> SWT.CURSOR_SIZESW;
            default -> SWT.CURSOR_SIZESE;
        };
    }

    private void restoreCursor() {
        if (cursorControl != null && !cursorControl.isDisposed()) {
            cursorControl.setCursor(cursorBefore);
        }
        cursorControl = null;
        cursorBefore = null;
    }

    // ------------------------------------------------------------------ keyboard Move and Size

    /** The system menu's Move ({@code size == false}) or Size: arrow keys, Enter or Escape to end. */
    void startKeyboardMode(boolean size) {
        keyboardMode = size ? SWT.BOTTOM | SWT.RIGHT : SWT.TOP;
        shell.getDisplay().removeFilter(SWT.KeyDown, keys);
        shell.getDisplay().addFilter(SWT.KeyDown, keys);
    }

    boolean inKeyboardMode() {
        return keyboardMode != 0;
    }

    private void keyMode(Event event) {
        if (keyboardMode == 0 || shell.isDisposed()) {
            return;
        }
        int step = (event.stateMask & SWT.CTRL) != 0 ? 1 : 10;
        int dx = 0;
        int dy = 0;
        switch (event.keyCode) {
            case SWT.ARROW_LEFT -> dx = -step;
            case SWT.ARROW_RIGHT -> dx = step;
            case SWT.ARROW_UP -> dy = -step;
            case SWT.ARROW_DOWN -> dy = step;
            default -> {
                if (event.character == SWT.CR || event.character == SWT.ESC) {
                    keyboardMode = 0;
                    shell.getDisplay().removeFilter(SWT.KeyDown, keys);
                    event.doit = false;
                    event.type = SWT.None;
                }
                return;
            }
        }
        if (keyboardMode == SWT.TOP) {
            Point location = shell.getLocation();
            shell.setLocation(location.x + dx, location.y + dy);
        } else {
            shell.setBounds(resized(shell.getBounds(), keyboardMode, dx, dy, shell.getMinimumSize()));
        }
        event.doit = false;
        event.type = SWT.None;
    }
}
