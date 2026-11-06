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
    void hitCodesPerPart() {
        assertThat(Win32Frame.hitCode(Part.CAPTION, true, false)).isEqualTo(Win32Frame.HTCAPTION);
        assertThat(Win32Frame.hitCode(Part.TITLE, false, false)).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(Win32Frame.hitCode(Part.SEARCH, true, false)).isEqualTo(Win32Frame.HTCLIENT);
        assertThat(Win32Frame.hitCode(Part.RESIZE_TOP_RIGHT, true, false)).isEqualTo(Win32Frame.HTTOPRIGHT);
        assertThat(Win32Frame.hitCode(Part.AVATAR, true, false)).isEqualTo(Win32Frame.HTCLIENT);
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
