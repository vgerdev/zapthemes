package com.zapthemes.widgets.ribbon;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

import com.zapthemes.swt.widgets.Shell;

/**
 * A ribbon popup: a borderless shell under an anchor (a collapsed group, the collapsed ribbon's
 * tab, a gallery, a color button, a combo box). It closes when another window takes the focus,
 * unless that window is a popup opened from it.
 */
abstract class PopupShell {

    /**
     * Whether a popup closes when its window loses activation. Tests turn it off: without a window
     * manager (CI), activation comes and goes on its own.
     */
    static boolean closeOnDeactivate = true;

    final Ribbon ribbon;
    final RibbonSurface origin;
    final Shell shell;
    private final Rectangle anchor;

    PopupShell(RibbonSurface origin, Rectangle anchor) {
        this.origin = origin;
        this.ribbon = origin.ribbon();
        this.anchor = anchor;
        shell = new Shell(ribbon.getShell(), SWT.NO_TRIM | SWT.ON_TOP);
        shell.setLayout(new FillLayout());
        shell.addListener(SWT.Deactivate, e -> shell.getDisplay().asyncExec(this::closeIfInactive));
    }

    /** Creates the content; returns the control that gets the focus. */
    abstract Control createContent();

    /** The popup's size; by default the content's preferred size. */
    Point size() {
        return shell.computeSize(SWT.DEFAULT, SWT.DEFAULT);
    }

    /** Opens it under the anchor, kept on the anchor's monitor. */
    void open() {
        Control focus = createContent();
        Point size = size();
        Display display = shell.getDisplay();
        Rectangle area = display.getPrimaryMonitor().getClientArea();
        for (Monitor monitor : display.getMonitors()) {
            if (monitor.getBounds().contains(anchor.x, anchor.y)) {
                area = monitor.getClientArea();
            }
        }
        int x = Math.max(area.x, Math.min(anchor.x, area.x + area.width - size.x));
        int y = anchor.y + anchor.height;
        if (y + size.y > area.y + area.height) {
            y = Math.max(area.y, anchor.y - size.y);
        }
        shell.setBounds(x, y, size.x, size.y);
        shell.open();
        if (focus != null && !focus.isDisposed()) {
            focus.setFocus();
        }
    }

    boolean isOpen() {
        return !shell.isDisposed();
    }

    /** Whether {@code control} is in this popup. */
    boolean contains(Control control) {
        return control != null && !control.isDisposed() && !shell.isDisposed() && control.getShell() == shell;
    }

    void close() {
        ribbon.popupClosed(this);
    }

    /** Disposes the shell; only {@link Ribbon} calls it. */
    void dispose() {
        if (!shell.isDisposed()) {
            shell.dispose();
        }
        if (!origin.isDisposed()) {
            origin.redraw();
        }
    }

    private void closeIfInactive() {
        if (shell.isDisposed() || !closeOnDeactivate) {
            return;
        }
        org.eclipse.swt.widgets.Shell active = shell.getDisplay().getActiveShell();
        if (active == shell || ribbon.isPopupShell(active)) {
            return;
        }
        close();
    }
}
