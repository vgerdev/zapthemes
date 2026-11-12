package com.zapthemes.widgets.titlebar;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Shell;
import com.zapthemes.widgets.titlebar.TitleBar.Part;

/** The native Windows frame, through the real window messages. Windows only. */
class Win32FrameTest {

    private static final int HTLEFT = 10;

    private Display display;
    private Shell shell;
    private TitleBar bar;
    private Win32Frame.Api os;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        Assumptions.assumeTrue("win32".equals(SWT.getPlatform()) && Win32Frame.available());
        display = Display.getDefault();
        shell = new Shell(display, TitleBar.shellStyle(SWT.SHELL_TRIM));
        GridLayout layout = new GridLayout();
        layout.marginWidth = layout.marginHeight = 0;
        shell.setLayout(layout);
        bar = new TitleBar(shell, SWT.NONE);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        bar.setTitle("Native");
        TitleBarItem save = new TitleBarItem(bar, SWT.PUSH);
        save.setText("Save");
        save.setGlyph(TitleBarGlyph.SAVE);
        Label body = new Label(shell, SWT.NONE);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        shell.setBounds(100, 100, 900, 500);
        shell.open();
        settle();
        os = new Win32Frame.Api();
    }

    @AfterEach
    void tearDown() {
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }

    @Test
    void trimmedShellsGetTheNativeFrame() {
        assertThat(bar.getFrameMode()).isEqualTo(TitleBar.FrameMode.NATIVE);
        assertThat(TitleBar.shellStyle(SWT.SHELL_TRIM)).isEqualTo(SWT.SHELL_TRIM);
    }

    @Test
    void theClientAreaCoversTheCaption() {
        // The bar starts at the window's top (at most the 1 px DWM border below it).
        int captionLeft = shell.toDisplay(0, 0).y - shell.getBounds().y;
        assertThat(captionLeft).isBetween(0, 1);
        assertThat(bar.getLocation().y).isZero();
    }

    @Test
    void hitTestsTellCaptionButtonsEdgesAndControlsApart() throws Exception {
        assertThat(hitTest(center(bar.getPartBounds(Part.CLOSE)))).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(hitTest(center(bar.getPartBounds(Part.MINIMIZE)))).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(hitTest(center(bar.getPartBounds(Part.MAXIMIZE)))).isEqualTo(Win32Frame.HTMAXBUTTON);
        assertThat(hitTest(center(bar.getPartBounds(Part.SYSTEM_MENU)))).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(hitTest(center(bar.getItems()[0].getBounds()))).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(hitTest(center(bar.getPartBounds(Part.TITLE)))).isEqualTo(Win32Frame.HTCAPTION);
        Rectangle customize = bar.getPartBounds(Part.CUSTOMIZE);
        assertThat(hitTest(new Point(customize.x + customize.width + 4, bar.getSize().y / 2))).isEqualTo(Win32Frame.HTCAPTION);
        assertThat(hitTest(new Point(bar.getSize().x / 2, 1))).isEqualTo(Win32Frame.HTTOP);
        assertThat(hitTest(new Point(2, 1))).isEqualTo(Win32Frame.HTTOPLEFT);
        // Below the bar: ordinary client area; left of the client area: the native resize border.
        assertThat(hitTest(new Point(bar.getSize().x / 2, bar.getSize().y + 40))).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(hitTest(new Point(-3, bar.getSize().y + 40))).isEqualTo(HTLEFT);
    }

    @Test
    void theMaximizeButtonWorksThroughNonClientClicks() throws Exception {
        long lParam = lParam(center(bar.getPartBounds(Part.MAXIMIZE)));
        long hwnd = os.handle(shell);
        os.sendMessage(hwnd, Win32Frame.WM_NCLBUTTONDOWN, Win32Frame.HTMAXBUTTON, lParam);
        os.sendMessage(hwnd, Win32Frame.WM_NCLBUTTONUP, Win32Frame.HTMAXBUTTON, lParam);
        settle();
        assertThat(shell.getMaximized()).isTrue();
        // Maximized, nothing is cut off at the top and there is no top resize edge.
        assertThat(shell.toDisplay(0, 0).y).isGreaterThanOrEqualTo(shell.getMonitor().getClientArea().y);
        assertThat(bar.getPartBounds(Part.RESIZE_TOP)).isNull();

        lParam = lParam(center(bar.getPartBounds(Part.MAXIMIZE)));
        os.sendMessage(hwnd, Win32Frame.WM_NCLBUTTONDOWN, Win32Frame.HTMAXBUTTON, lParam);
        os.sendMessage(hwnd, Win32Frame.WM_NCLBUTTONUP, Win32Frame.HTMAXBUTTON, lParam);
        settle();
        assertThat(shell.getMaximized()).isFalse();
    }

    @Test
    void theSystemMenuOpensFromAClickAndClosesWithoutCrashing() throws Exception {
        long hwnd = os.handle(shell);
        boolean[] ran = new boolean[1];
        // The menu loop is modal: end it from a timer that fires inside it.
        display.timerExec(500, () -> {
            ran[0] = true;
            try {
                os.postMessage(hwnd, 0x001F /* WM_CANCELMODE */, 0, 0);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        // Click the icon as the mouse does: down and up on the bar, in its client pixels.
        long barHwnd = os.handle(bar);
        Point icon = center(bar.getPartBounds(Part.SYSTEM_MENU));
        float scale = os.clientWidth(barHwnd) / (float) bar.getSize().x;
        long xy = (Math.round(icon.x * scale) & 0xFFFFL) | ((Math.round(icon.y * scale) & 0xFFFFL) << 16);
        os.postMessage(barHwnd, 0x0201 /* WM_LBUTTONDOWN */, 1, xy);
        os.postMessage(barHwnd, 0x0202 /* WM_LBUTTONUP */, 0, xy);
        long end = System.currentTimeMillis() + 3000;
        while (!ran[0] && System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        settle();
        assertThat(ran[0]).isTrue();
        assertThat(shell.isDisposed()).isFalse();
    }

    @Test
    void closingTheWindowFromInsideTheSystemMenuDoesNotCrash() throws Exception {
        boolean[] ran = new boolean[1];
        display.timerExec(500, () -> {
            ran[0] = true;
            shell.dispose();
        });
        bar.openSystemMenu();
        long end = System.currentTimeMillis() + 3000;
        while (!ran[0] && System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        settle();
        assertThat(shell.isDisposed()).isTrue();
    }

    @Test
    void clickingTheIconOpensTheSystemMenuAndItsCommandsWork() {
        // Real input (SendInput): a held mouse button kept SC_KEYMENU from opening the menu.
        Assumptions.assumeTrue(System.getenv("CI") == null, "needs the interactive desktop");
        shell.forceActive();
        settle();
        Point icon = bar.toDisplay(center(bar.getPartBounds(Part.SYSTEM_MENU)));
        post(SWT.MouseMove, e -> {
            e.x = icon.x;
            e.y = icon.y;
        });
        settle();
        post(SWT.MouseDown, e -> e.button = 1);
        // The menu is modal: drive it from timers that fire inside its loop.
        display.timerExec(300, () -> post(SWT.MouseUp, e -> e.button = 1));
        display.timerExec(600, () -> {
            // Up wraps to the last item, Close; Enter chooses it.
            key(SWT.ARROW_UP);
            key(SWT.CR);
        });
        long end = System.currentTimeMillis() + 3000;
        while (!shell.isDisposed() && System.currentTimeMillis() < end) {
            if (!display.readAndDispatch()) {
                display.timerExec(20, () -> { });
                display.sleep();
            }
        }
        assertThat(shell.isDisposed()).isTrue();
    }

    @Test
    void theWindowButtonsLightUpUnderTheRealPointer() {
        // Real input: maximize is non-client (snap layouts), the others are the bar's. Moving
        // between them used to drop the hover: the bar's MouseExit undid maximize's, and leaving
        // maximize cleared close's.
        Assumptions.assumeTrue(System.getenv("CI") == null, "needs the interactive desktop");
        shell.forceActive();
        settle();
        for (Part part : new Part[] { Part.MINIMIZE, Part.MAXIMIZE, Part.CLOSE, Part.MAXIMIZE, Part.MINIMIZE }) {
            Point at = bar.toDisplay(center(bar.getPartBounds(part)));
            for (int dx = -6; dx <= 0; dx += 2) {
                int x = at.x + dx;
                post(SWT.MouseMove, e -> {
                    e.x = x;
                    e.y = at.y;
                });
                settle();
            }
            assertThat(bar.hoverPart()).as("hover over " + part).isEqualTo(part);
        }
        Point away = shell.toDisplay(400, 300);
        post(SWT.MouseMove, e -> {
            e.x = away.x;
            e.y = away.y;
        });
        settle();
        assertThat(bar.hoverPart()).isNull();
    }

    @Test
    void systemMenuCommandsComeBackAsSystemCommands() throws Exception {
        os.sendMessage(os.handle(shell), Win32Frame.WM_COMMAND, Win32Frame.SC_MAXIMIZE, 0);
        settle();
        assertThat(shell.getMaximized()).isTrue();
        os.sendMessage(os.handle(shell), Win32Frame.WM_COMMAND, Win32Frame.SC_RESTORE, 0);
        settle();
        assertThat(shell.getMaximized()).isFalse();
    }

    private void post(int type, java.util.function.Consumer<org.eclipse.swt.widgets.Event> setup) {
        org.eclipse.swt.widgets.Event event = new org.eclipse.swt.widgets.Event();
        event.type = type;
        setup.accept(event);
        display.post(event);
    }

    private void key(int code) {
        post(SWT.KeyDown, e -> e.keyCode = code);
        post(SWT.KeyUp, e -> e.keyCode = code);
    }

    @Test
    void hitCodesPerPart() {
        assertThat(Win32Frame.hitCode(Part.CAPTION, true, false)).isEqualTo(Win32Frame.HTCAPTION);
        assertThat(Win32Frame.hitCode(Part.TITLE, false, false)).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(Win32Frame.hitCode(Part.SEARCH, true, false)).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(Win32Frame.hitCode(Part.RESIZE_TOP_RIGHT, true, false)).isEqualTo(Win32Frame.HTTOPRIGHT);
        assertThat(Win32Frame.hitCode(Part.AVATAR, true, false)).isEqualTo(Win32Frame.HTCLIENT);
    }

    @Test
    void closingTheWindowWhileAButtonShowsItsTooltipDoesNotCrash() {
        // The pointer on close: the bar sets its tooltip, and comctl32 subclasses the bar's window
        // over Win32Frame's procedure. Freeing that procedure's callback while the subclass still
        // called it ended the process with 0xC000041D during DestroyWindow (this test's JVM too).
        Point close = center(bar.getPartBounds(Part.CLOSE));
        org.eclipse.swt.widgets.Event move = new org.eclipse.swt.widgets.Event();
        move.x = close.x;
        move.y = close.y;
        bar.notifyListeners(SWT.MouseMove, move);
        assertThat(bar.getToolTipText()).isNotNull();
        Win32Frame frame = (Win32Frame) bar.frame();

        shell.dispose();
        settle();
        assertThat(shell.isDisposed()).isTrue();
        // Freed once the bar's window got its last message.
        assertThat(frame.holdsCallbacks()).isFalse();
    }

    @Test
    void disposingTheBarWithItsTooltipSetBringsTheCaptionBack() {
        Point close = center(bar.getPartBounds(Part.CLOSE));
        org.eclipse.swt.widgets.Event move = new org.eclipse.swt.widgets.Event();
        move.x = close.x;
        move.y = close.y;
        bar.notifyListeners(SWT.MouseMove, move);
        Win32Frame frame = (Win32Frame) bar.frame();
        bar.dispose();
        settle();
        assertThat(shell.toDisplay(0, 0).y - shell.getBounds().y).isGreaterThan(10);
        assertThat(frame.holdsCallbacks()).isFalse();
    }

    @Test
    void disposingTheBarBringsTheCaptionBack() {
        bar.dispose();
        settle();
        assertThat(shell.toDisplay(0, 0).y - shell.getBounds().y).isGreaterThan(10);
    }

    private long hitTest(Point barPoint) throws Exception {
        return os.sendMessage(os.handle(shell), Win32Frame.WM_NCHITTEST, 0, lParam(barPoint));
    }

    /** A bar point as the screen pixels a mouse message carries. */
    private long lParam(Point barPoint) throws Exception {
        long barHwnd = os.handle(bar);
        Point origin = os.screenToClient(barHwnd, 0, 0);
        float scale = os.clientWidth(barHwnd) / (float) bar.getSize().x;
        int x = Math.round(barPoint.x * scale) - origin.x;
        int y = Math.round(barPoint.y * scale) - origin.y;
        return (x & 0xFFFFL) | ((y & 0xFFFFL) << 16);
    }

    private static Point center(Rectangle r) {
        return new Point(r.x + r.width / 2, r.y + r.height / 2);
    }

    private void settle() {
        for (int i = 0; i < 10; i++) {
            while (display.readAndDispatch()) {
                // Run pending events.
            }
            display.timerExec(20, () -> { });
            display.sleep();
        }
    }
}
