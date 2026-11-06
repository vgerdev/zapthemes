package com.zapthemes.widgets.titlebar;

import java.lang.System.Logger.Level;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;

/**
 * macOS: the native window with a full-size content view, so the bar sits where the title bar was,
 * under a transparent title bar whose title is hidden. The traffic lights stay native (the bar leaves
 * room for them and draws no window buttons or icon); dragging the bar's empty space moves the window
 * ({@code performWindowDragWithEvent:}) and double-clicking zooms it ({@code performZoom:}).
 *
 * <p>Reaches {@code org.eclipse.swt.internal.cocoa} by reflection (this class is compiled on every
 * platform); without it the bar works under the native title bar ({@code EMBEDDED}).
 */
final class CocoaFrame extends WindowFrame {

    private static final System.Logger LOG = System.getLogger(CocoaFrame.class.getName());
    private static final long FULL_SIZE_CONTENT_VIEW = 1L << 15;
    private static final long TITLE_HIDDEN = 1;
    /** Room for the close, minimize and zoom buttons at the standard size, in points. */
    private static final int TRAFFIC_LIGHTS = 78;

    private static Api api;
    private static boolean lookedUp;

    private long window;
    private long originalMask;

    CocoaFrame(TitleBar bar) {
        super(bar);
    }

    static boolean available() {
        return "cocoa".equals(SWT.getPlatform()) && api() != null;
    }

    private static synchronized Api api() {
        if (!lookedUp) {
            lookedUp = true;
            try {
                api = new Api();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.log(Level.WARNING, "SWT's cocoa internals are not available; title bars keep the native title bar", e);
            }
        }
        return api;
    }

    @Override
    TitleBar.FrameMode mode() {
        return TitleBar.FrameMode.NATIVE;
    }

    @Override
    void install() throws ReflectiveOperationException {
        Api os = api();
        window = os.window(shell);
        if (window == 0) {
            throw new IllegalStateException("The shell has no NSWindow");
        }
        originalMask = os.send(window, "styleMask");
        os.send(window, "setStyleMask:", originalMask | FULL_SIZE_CONTENT_VIEW);
        os.send(window, "setTitlebarAppearsTransparent:", true);
        os.send(window, "setTitleVisibility:", TITLE_HIDDEN);
        // The content view now covers the title bar: let SWT lay out into it.
        Point size = shell.getSize();
        shell.setSize(size.x, size.y + 1);
        shell.setSize(size);
    }

    @Override
    void dispose() {
        if (window == 0 || shell.isDisposed()) {
            return;
        }
        try {
            Api os = api();
            os.send(window, "setTitleVisibility:", 0L);
            os.send(window, "setTitlebarAppearsTransparent:", false);
            os.send(window, "setStyleMask:", originalMask);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.log(Level.DEBUG, "Could not restore the title bar", e);
        }
    }

    @Override
    boolean drawsWindowButtons() {
        return false;
    }

    @Override
    boolean drawsIcon() {
        return false;
    }

    @Override
    int leadingInset() {
        return shell.getFullScreen() ? 0 : TRAFFIC_LIGHTS;
    }

    @Override
    void captionPressed(Event event) {
        try {
            Api os = api();
            long current = os.currentEvent();
            if (current != 0) {
                os.send(window, "performWindowDragWithEvent:", current);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.log(Level.DEBUG, "Could not drag the window", e);
        }
    }

    @Override
    void captionDoubleClicked() {
        try {
            api().send(window, "performZoom:", 0L);
        } catch (ReflectiveOperationException | RuntimeException e) {
            bar.toggleMaximize();
        }
    }

    /** {@code org.eclipse.swt.internal.cocoa.OS} and the shell's {@code NSWindow}, by reflection. */
    static final class Api {
        private final Field shellWindow;
        private final Field id;
        private final Method selRegisterName;
        private final Method msgSend;
        private final Method msgSendLong;
        private final Method msgSendBoolean;
        private final Method sharedApplication;
        private final Method currentEvent;

        Api() throws ReflectiveOperationException {
            Class<?> os = Class.forName("org.eclipse.swt.internal.cocoa.OS");
            Class<?> idClass = Class.forName("org.eclipse.swt.internal.cocoa.id");
            Class<?> application = Class.forName("org.eclipse.swt.internal.cocoa.NSApplication");
            shellWindow = Shell.class.getDeclaredField("window");
            shellWindow.setAccessible(true);
            id = idClass.getField("id");
            selRegisterName = os.getMethod("sel_registerName", String.class);
            msgSend = os.getMethod("objc_msgSend", long.class, long.class);
            msgSendLong = os.getMethod("objc_msgSend", long.class, long.class, long.class);
            msgSendBoolean = os.getMethod("objc_msgSend", long.class, long.class, boolean.class);
            sharedApplication = application.getMethod("sharedApplication");
            currentEvent = application.getMethod("currentEvent");
        }

        long window(Shell shell) throws ReflectiveOperationException {
            Object window = shellWindow.get(shell);
            return window != null ? id.getLong(window) : 0;
        }

        private long selector(String name) throws ReflectiveOperationException {
            return (long) selRegisterName.invoke(null, name);
        }

        long send(long target, String selector) throws ReflectiveOperationException {
            return (long) msgSend.invoke(null, target, selector(selector));
        }

        long send(long target, String selector, long argument) throws ReflectiveOperationException {
            return (long) msgSendLong.invoke(null, target, selector(selector), argument);
        }

        long send(long target, String selector, boolean argument) throws ReflectiveOperationException {
            return (long) msgSendBoolean.invoke(null, target, selector(selector), argument);
        }

        long currentEvent() throws ReflectiveOperationException {
            Object app = sharedApplication.invoke(null);
            Object event = app != null ? currentEvent.invoke(app) : null;
            return event != null ? id.getLong(event) : 0;
        }
    }
}
