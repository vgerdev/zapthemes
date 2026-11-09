package com.zapthemes.widgets.titlebar;

import java.lang.System.Logger.Level;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import com.zapthemes.widgets.titlebar.TitleBar.Part;

/**
 * Windows: the native frame with its caption replaced by the bar. The shell keeps its styles, so
 * the shadow, rounded corners, Aero snap and shake, snap layouts, the system menu and the
 * minimize/maximize animations all stay native.
 *
 * <ul>
 *   <li>The shell's and the bar's window procedures are subclassed through SWT's internal
 *       {@code Callback}; every other message goes on to SWT.</li>
 *   <li>{@code WM_NCCALCSIZE} lets the default procedure compute the frame, then puts the top
 *       back: the side and bottom borders stay non-client (resizing, shadow), the caption becomes
 *       client area. A maximized window keeps the frame's thickness at the top, since Windows
 *       places it that far beyond the monitor.</li>
 *   <li>{@code WM_NCHITTEST}: the bar answers {@code HTTRANSPARENT} over caption, top-edge and
 *       maximize parts, so the shell gets the test and answers {@code HTCAPTION} (drag,
 *       double-click, snap, the caption menu), {@code HTTOP*} (resize) or {@code HTMAXBUTTON}
 *       (Windows 11 snap layouts). Everything else is client area, handled by the bar.</li>
 *   <li>The maximize button's hover and press come as non-client mouse messages, which the bar
 *       paints and acts on.</li>
 *   <li>The icon and a right-click open the native system menu ({@code GetSystemMenu} and
 *       {@code TrackPopupMenu}, its items enabled for the window's state); the chosen command comes
 *       back as {@code WM_COMMAND} and goes on as {@code WM_SYSCOMMAND}. Alt+Space stays Windows'.</li>
 * </ul>
 *
 * Everything goes through reflection on {@code org.eclipse.swt.internal.win32.OS}, as
 * {@code Win32ThemeSupport} does; without it (another SWT) the bar is {@code EMBEDDED} under the
 * native caption.
 */
final class Win32Frame extends WindowFrame {

    private static final System.Logger LOG = System.getLogger(Win32Frame.class.getName());

    static final int WM_NCCALCSIZE = 0x0083;
    static final int WM_NCHITTEST = 0x0084;
    static final int WM_NCMOUSEMOVE = 0x00A0;
    static final int WM_NCLBUTTONDOWN = 0x00A1;
    static final int WM_NCLBUTTONUP = 0x00A2;
    static final int WM_NCLBUTTONDBLCLK = 0x00A3;
    static final int WM_NCMOUSELEAVE = 0x02A2;
    static final int WM_SYSCOMMAND = 0x0112;
    static final int SC_KEYMENU = 0xF100;
    static final int WM_COMMAND = 0x0111;
    static final int SC_SIZE = 0xF000;
    static final int SC_MOVE = 0xF010;
    static final int SC_MINIMIZE = 0xF020;
    static final int SC_MAXIMIZE = 0xF030;
    static final int SC_CLOSE = 0xF060;
    static final int SC_RESTORE = 0xF120;
    private static final int MF_GRAYED = 0x1;
    private static final int TPM_RIGHTBUTTON = 0x2;

    static final int HTTRANSPARENT = -1;
    static final int HTCLIENT = 1;
    static final int HTCAPTION = 2;
    static final int HTMAXBUTTON = 9;
    static final int HTTOP = 12;
    static final int HTTOPLEFT = 13;
    static final int HTTOPRIGHT = 14;

    private static final int GWLP_WNDPROC = -4;
    private static final int SWP_NOSIZE = 0x1;
    private static final int SWP_NOMOVE = 0x2;
    private static final int SWP_NOZORDER = 0x4;
    private static final int SWP_NOACTIVATE = 0x10;
    private static final int SWP_FRAMECHANGED = 0x20;
    private static final int SM_CYFRAME = 33;
    private static final int SM_CXPADDEDBORDER = 92;

    private static Api api;
    private static boolean lookedUp;
    private static boolean failureLogged;

    private long shellHandle;
    private long barHandle;
    private Object shellCallback;
    private Object barCallback;
    private long shellAddress;
    private long barAddress;
    private long shellOld;
    private long barOld;
    private Part nativeHover;
    private Part nativePressed;
    /** The pointer is back on the bar's own parts (client moves can still come while it's over maximize). */
    private final Listener clientMouse = e -> {
        if (nativeHover == null || bar.getPart(e.x, e.y) != nativeHover) {
            setNative(null, null);
        }
    };

    Win32Frame(TitleBar bar) {
        super(bar);
    }

    static boolean available() {
        return "win32".equals(SWT.getPlatform()) && api() != null;
    }

    private static synchronized Api api() {
        if (!lookedUp) {
            lookedUp = true;
            try {
                api = new Api();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.log(Level.WARNING, "SWT's win32 internals are not available; title bars keep the native caption", e);
            }
        }
        return api;
    }

    @Override
    TitleBar.FrameMode mode() {
        return TitleBar.FrameMode.NATIVE;
    }

    @Override
    boolean resizesFromBar() {
        return true;
    }

    @Override
    void install() throws ReflectiveOperationException {
        Api os = api();
        shellHandle = os.handle(shell);
        barHandle = os.handle(bar);
        shellCallback = os.callback(this, "shellProc");
        shellAddress = os.address(shellCallback);
        barCallback = os.callback(this, "barProc");
        barAddress = os.address(barCallback);
        shellOld = os.setWindowLongPtr(shellHandle, GWLP_WNDPROC, shellAddress);
        barOld = os.setWindowLongPtr(barHandle, GWLP_WNDPROC, barAddress);
        bar.addListener(SWT.MouseMove, clientMouse);
        frameChanged();
    }

    @Override
    void dispose() {
        Api os = api();
        try {
            if (barOld != 0 && os.getWindowLongPtr(barHandle, GWLP_WNDPROC) == barAddress) {
                os.setWindowLongPtr(barHandle, GWLP_WNDPROC, barOld);
            }
            if (shellOld != 0 && os.getWindowLongPtr(shellHandle, GWLP_WNDPROC) == shellAddress) {
                os.setWindowLongPtr(shellHandle, GWLP_WNDPROC, shellOld);
                if (!shell.isDisposed()) {
                    frameChanged();
                }
            }
        } catch (ReflectiveOperationException e) {
            LOG.log(Level.DEBUG, "Could not restore the window procedures", e);
        } finally {
            os.dispose(barCallback);
            os.dispose(shellCallback);
            barCallback = shellCallback = null;
        }
    }

    private void frameChanged() throws ReflectiveOperationException {
        api().setWindowPos(shellHandle, SWP_FRAMECHANGED | SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE);
    }

    // ------------------------------------------------------------------ window procedures

    /** The shell's window procedure; called by native code through SWT's {@code Callback}. */
    long shellProc(long hwnd, long msg, long wParam, long lParam) {
        try {
            switch ((int) msg) {
                case WM_COMMAND -> {
                    // A system menu command chosen in openSystemMenu: Windows carries it out.
                    int id = (int) (wParam & 0xFFFF);
                    if (lParam == 0 && (wParam >>> 16 & 0xFFFF) == 0 && id >= SC_SIZE) {
                        api().postMessage(hwnd, WM_SYSCOMMAND, id, 0);
                        return 0;
                    }
                }
                case WM_NCCALCSIZE -> {
                    if (wParam != 0) {
                        return calcSize(hwnd, msg, wParam, lParam);
                    }
                }
                case WM_NCHITTEST -> {
                    long result = api().callWindowProc(shellOld, hwnd, (int) msg, wParam, lParam);
                    if (result != HTCLIENT) {
                        return result;
                    }
                    return hitCode(partAt(lParam), bar.titleIsCaption(), false);
                }
                case WM_NCMOUSEMOVE -> setNative(wParam == HTMAXBUTTON ? Part.MAXIMIZE : null, nativePressed);
                case WM_NCMOUSELEAVE -> setNative(null, null);
                case WM_NCLBUTTONDOWN, WM_NCLBUTTONDBLCLK -> {
                    if (wParam == HTMAXBUTTON) {
                        setNative(Part.MAXIMIZE, Part.MAXIMIZE);
                        return 0;
                    }
                }
                case WM_NCLBUTTONUP -> {
                    if (wParam == HTMAXBUTTON) {
                        boolean click = nativePressed == Part.MAXIMIZE;
                        setNative(Part.MAXIMIZE, null);
                        if (click) {
                            bar.toggleMaximize();
                        }
                        return 0;
                    }
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            logOnce(t);
        }
        return callOld(shellOld, hwnd, msg, wParam, lParam);
    }

    /** The bar's window procedure: caption parts are transparent, so the shell hit-tests them. */
    long barProc(long hwnd, long msg, long wParam, long lParam) {
        try {
            if ((int) msg == WM_NCHITTEST && hitCode(partAt(lParam), bar.titleIsCaption(), false) != HTCLIENT) {
                return HTTRANSPARENT;
            }
        } catch (Throwable t) {
            logOnce(t);
        }
        return callOld(barOld, hwnd, msg, wParam, lParam);
    }

    private static long callOld(long old, long hwnd, long msg, long wParam, long lParam) {
        try {
            return api().callWindowProc(old, hwnd, (int) msg, wParam, lParam);
        } catch (Throwable t) {
            logOnce(t);
            return 0;
        }
    }

    /** The default frame, but with the top edge where the window's top is: no caption. */
    private long calcSize(long hwnd, long msg, long wParam, long lParam) throws Throwable {
        Api os = api();
        int[] rect = new int[4];
        os.read(rect, lParam);
        int top = rect[1];
        long result = os.callWindowProc(shellOld, hwnd, (int) msg, wParam, lParam);
        os.read(rect, lParam);
        rect[1] = top + (os.isZoomed(hwnd) ? os.frameThickness(hwnd) : 0);
        os.write(lParam, rect);
        return result;
    }

    /**
     * The Windows hit-test code for {@code part}: caption, top edge and maximize go to the frame,
     * everything else is client area the bar handles.
     */
    static int hitCode(Part part, boolean titleIsCaption, boolean maximized) {
        return switch (part) {
            case CAPTION -> HTCAPTION;
            case TITLE -> titleIsCaption ? HTCAPTION : HTCLIENT;
            case MAXIMIZE -> HTMAXBUTTON;
            case RESIZE_TOP -> HTTOP;
            case RESIZE_TOP_LEFT -> HTTOPLEFT;
            case RESIZE_TOP_RIGHT -> HTTOPRIGHT;
            default -> HTCLIENT;
        };
    }

    /** The bar part under the screen point packed in {@code lParam} (physical pixels). */
    private Part partAt(long lParam) throws ReflectiveOperationException {
        if (bar.isDisposed()) {
            return Part.NONE;
        }
        int x = (short) (lParam & 0xFFFF);
        int y = (short) ((lParam >> 16) & 0xFFFF);
        Point point = api().screenToClient(barHandle, x, y);
        float scale = scale();
        return bar.getPart(Math.round(point.x / scale), Math.round(point.y / scale));
    }

    /** Physical pixels per SWT point on the bar's monitor. */
    private float scale() throws ReflectiveOperationException {
        int width = bar.getSize().x;
        int pixels = api().clientWidth(barHandle);
        return width > 0 && pixels > 0 ? pixels / (float) width : 1f;
    }

    // ------------------------------------------------------------------ the maximize button

    private void setNative(Part hover, Part pressed) {
        if (hover == nativeHover && pressed == nativePressed) {
            return;
        }
        nativeHover = hover;
        nativePressed = pressed;
        if (!bar.isDisposed()) {
            bar.setFrameState(hover, pressed);
            if (hover != null) {
                watchCursor();
            }
        }
    }

    /** Non-client leave messages are not guaranteed: clear the hover once the pointer is elsewhere. */
    private void watchCursor() {
        bar.getDisplay().timerExec(150, () -> {
            if (bar.isDisposed() || nativeHover == null) {
                return;
            }
            Point cursor = bar.toControl(bar.getDisplay().getCursorLocation());
            if (bar.getPart(cursor.x, cursor.y) != nativeHover) {
                setNative(null, null);
            } else {
                watchCursor();
            }
        });
    }

    // ------------------------------------------------------------------ system menu

    /**
     * The native system menu at {@code location} (display coordinates). {@code SC_KEYMENU} would open
     * it the way Alt+Space does, but not while a mouse button is down, so the bar tracks it itself and
     * sets its items as Windows would.
     */
    @Override
    void openSystemMenu(Point location) {
        try {
            Api os = api();
            long menu = os.getSystemMenu(shellHandle);
            if (menu == 0) {
                throw new IllegalStateException("The shell has no system menu");
            }
            boolean maximized = shell.getMaximized();
            boolean minimized = shell.getMinimized();
            boolean normal = !maximized && !minimized;
            os.enableMenuItem(menu, SC_RESTORE, !normal);
            os.enableMenuItem(menu, SC_MOVE, normal);
            os.enableMenuItem(menu, SC_SIZE, normal && (shell.getStyle() & SWT.RESIZE) != 0);
            os.enableMenuItem(menu, SC_MINIMIZE, !minimized);
            os.enableMenuItem(menu, SC_MAXIMIZE, !maximized);
            os.enableMenuItem(menu, SC_CLOSE, true);
            os.setMenuDefaultItem(menu, SC_CLOSE);
            // Display coordinates are points; the menu wants screen pixels.
            Point local = bar.toControl(location);
            float scale = os.clientWidth(barHandle) / (float) Math.max(1, bar.getSize().x);
            Point origin = os.screenToClient(barHandle, 0, 0);
            int x = Math.round(local.x * scale) - origin.x;
            int y = Math.round(local.y * scale) - origin.y;
            os.trackPopupMenu(menu, TPM_RIGHTBUTTON, x, y, shellHandle);
        } catch (ReflectiveOperationException | RuntimeException e) {
            logOnce(e);
            super.openSystemMenu(location);
        }
    }

    @Override
    void captionDoubleClicked() {
        // Windows does this on the caption itself.
    }

    private static void logOnce(Throwable t) {
        if (!failureLogged) {
            failureLogged = true;
            LOG.log(Level.WARNING, "Title bar window procedure failed", t);
        }
    }

    // ------------------------------------------------------------------ SWT internals

    /** {@code org.eclipse.swt.internal.win32.OS} and {@code org.eclipse.swt.internal.Callback}, by reflection. */
    static final class Api {
        private final Field handle;
        private final Constructor<?> callbackConstructor;
        private final Method callbackAddress;
        private final Method callbackDispose;
        private final MethodHandle setWindowLongPtr;
        private final MethodHandle getWindowLongPtr;
        private final MethodHandle callWindowProc;
        private final MethodHandle setWindowPos;
        private final MethodHandle isZoomed;
        private final MethodHandle getDpiForWindow;
        private final MethodHandle getSystemMetricsForDpi;
        private final MethodHandle screenToClient;
        private final MethodHandle getClientRect;
        private final MethodHandle postMessage;
        private final MethodHandle sendMessage;
        private final MethodHandle readInts;
        private final MethodHandle getSystemMenu;
        private final MethodHandle enableMenuItem;
        private final MethodHandle setMenuDefaultItem;
        private final MethodHandle trackPopupMenu;
        private final MethodHandle writeInts;
        private final Class<?> pointClass;
        private final Class<?> rectClass;
        private final Field pointX;
        private final Field pointY;
        private final Field rectRight;

        Api() throws ReflectiveOperationException {
            Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
            Class<?> callback = Class.forName("org.eclipse.swt.internal.Callback");
            pointClass = Class.forName("org.eclipse.swt.internal.win32.POINT");
            rectClass = Class.forName("org.eclipse.swt.internal.win32.RECT");
            handle = Control.class.getField("handle");
            callbackConstructor = callback.getConstructor(Object.class, String.class, int.class);
            callbackAddress = callback.getMethod("getAddress");
            callbackDispose = callback.getMethod("dispose");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            setWindowLongPtr = lookup.findStatic(os, "SetWindowLongPtr", MethodType.methodType(long.class, long.class, int.class, long.class));
            getWindowLongPtr = lookup.findStatic(os, "GetWindowLongPtr", MethodType.methodType(long.class, long.class, int.class));
            callWindowProc = lookup.findStatic(os, "CallWindowProc",
                    MethodType.methodType(long.class, long.class, long.class, int.class, long.class, long.class));
            setWindowPos = lookup.findStatic(os, "SetWindowPos",
                    MethodType.methodType(boolean.class, long.class, long.class, int.class, int.class, int.class, int.class, int.class));
            isZoomed = lookup.findStatic(os, "IsZoomed", MethodType.methodType(boolean.class, long.class));
            getDpiForWindow = lookup.findStatic(os, "GetDpiForWindow", MethodType.methodType(int.class, long.class));
            getSystemMetricsForDpi = lookup.findStatic(os, "GetSystemMetricsForDpi", MethodType.methodType(int.class, int.class, int.class));
            screenToClient = lookup.findStatic(os, "ScreenToClient", MethodType.methodType(boolean.class, long.class, pointClass));
            getClientRect = lookup.findStatic(os, "GetClientRect", MethodType.methodType(boolean.class, long.class, rectClass));
            postMessage = lookup.findStatic(os, "PostMessage", MethodType.methodType(boolean.class, long.class, int.class, long.class, long.class));
            sendMessage = lookup.findStatic(os, "SendMessage", MethodType.methodType(long.class, long.class, int.class, long.class, long.class));
            readInts = lookup.findStatic(os, "MoveMemory", MethodType.methodType(void.class, int[].class, long.class, int.class));
            writeInts = lookup.findStatic(os, "MoveMemory", MethodType.methodType(void.class, long.class, int[].class, int.class));
            getSystemMenu = lookup.findStatic(os, "GetSystemMenu", MethodType.methodType(long.class, long.class, boolean.class));
            enableMenuItem = lookup.findStatic(os, "EnableMenuItem", MethodType.methodType(boolean.class, long.class, int.class, int.class));
            setMenuDefaultItem = lookup.findStatic(os, "SetMenuDefaultItem", MethodType.methodType(boolean.class, long.class, int.class, int.class));
            trackPopupMenu = lookup.findStatic(os, "TrackPopupMenu",
                    MethodType.methodType(boolean.class, long.class, int.class, int.class, int.class, int.class, long.class, rectClass));
            pointX = pointClass.getField("x");
            pointY = pointClass.getField("y");
            rectRight = rectClass.getField("right");
        }

        long handle(Control control) throws IllegalAccessException {
            return handle.getLong(control);
        }

        Object callback(Object target, String method) throws ReflectiveOperationException {
            Object callback = callbackConstructor.newInstance(target, method, 4);
            if (address(callback) == 0) {
                throw new IllegalStateException("No native callbacks left for " + method);
            }
            return callback;
        }

        long address(Object callback) throws ReflectiveOperationException {
            return (long) callbackAddress.invoke(callback);
        }

        void dispose(Object callback) {
            if (callback == null) {
                return;
            }
            try {
                callbackDispose.invoke(callback);
            } catch (ReflectiveOperationException e) {
                LOG.log(Level.DEBUG, "Could not dispose a callback", e);
            }
        }

        long setWindowLongPtr(long hwnd, int index, long value) throws ReflectiveOperationException {
            return (long) invoke(setWindowLongPtr, hwnd, index, value);
        }

        long getWindowLongPtr(long hwnd, int index) throws ReflectiveOperationException {
            return (long) invoke(getWindowLongPtr, hwnd, index);
        }

        long callWindowProc(long old, long hwnd, int msg, long wParam, long lParam) throws ReflectiveOperationException {
            return (long) invoke(callWindowProc, old, hwnd, msg, wParam, lParam);
        }

        void setWindowPos(long hwnd, int flags) throws ReflectiveOperationException {
            invoke(setWindowPos, hwnd, 0L, 0, 0, 0, 0, flags);
        }

        boolean isZoomed(long hwnd) throws ReflectiveOperationException {
            return (boolean) invoke(isZoomed, hwnd);
        }

        /** The resize frame's thickness, which a maximized window has beyond the monitor. */
        int frameThickness(long hwnd) throws ReflectiveOperationException {
            int dpi = (int) invoke(getDpiForWindow, hwnd);
            return (int) invoke(getSystemMetricsForDpi, SM_CYFRAME, dpi) + (int) invoke(getSystemMetricsForDpi, SM_CXPADDEDBORDER, dpi);
        }

        Point screenToClient(long hwnd, int x, int y) throws ReflectiveOperationException {
            Object point = pointClass.getConstructor().newInstance();
            pointX.setInt(point, x);
            pointY.setInt(point, y);
            invoke(screenToClient, hwnd, point);
            return new Point(pointX.getInt(point), pointY.getInt(point));
        }

        int clientWidth(long hwnd) throws ReflectiveOperationException {
            Object rect = rectClass.getConstructor().newInstance();
            invoke(getClientRect, hwnd, rect);
            return rectRight.getInt(rect);
        }

        void postMessage(long hwnd, int msg, long wParam, long lParam) throws ReflectiveOperationException {
            invoke(postMessage, hwnd, msg, wParam, lParam);
        }

        long sendMessage(long hwnd, int msg, long wParam, long lParam) throws ReflectiveOperationException {
            return (long) invoke(sendMessage, hwnd, msg, wParam, lParam);
        }

        long getSystemMenu(long hwnd) throws ReflectiveOperationException {
            return (long) invoke(getSystemMenu, hwnd, false);
        }

        void enableMenuItem(long menu, int id, boolean enabled) throws ReflectiveOperationException {
            invoke(enableMenuItem, menu, id, enabled ? 0 : MF_GRAYED);
        }

        void setMenuDefaultItem(long menu, int id) throws ReflectiveOperationException {
            invoke(setMenuDefaultItem, menu, id, 0);
        }

        /** Modal until the menu closes; the chosen item comes to {@code owner} as {@code WM_COMMAND}. */
        void trackPopupMenu(long menu, int flags, int x, int y, long owner) throws ReflectiveOperationException {
            invoke(trackPopupMenu, menu, flags, x, y, 0, owner, null);
        }

        void read(int[] into, long address) throws ReflectiveOperationException {
            invoke(readInts, into, address, into.length * 4);
        }

        void write(long address, int[] from) throws ReflectiveOperationException {
            invoke(writeInts, address, from, from.length * 4);
        }

        private static Object invoke(MethodHandle handle, Object... args) throws ReflectiveOperationException {
            try {
                return handle.invokeWithArguments(args);
            } catch (ReflectiveOperationException | RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new ReflectiveOperationException(t);
            }
        }
    }
}
