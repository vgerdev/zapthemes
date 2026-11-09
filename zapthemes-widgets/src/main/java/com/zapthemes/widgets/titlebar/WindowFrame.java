package com.zapthemes.widgets.titlebar;

import java.lang.System.Logger.Level;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;

/**
 * How a {@link TitleBar} gets along with its shell's window frame on this platform: a native frame
 * whose caption the bar replaces, a frame the bar draws on a {@code NO_TRIM} shell, or the native
 * caption kept above the bar.
 */
abstract class WindowFrame {

    private static final System.Logger LOG = System.getLogger(WindowFrame.class.getName());
    static final String NATIVE_PROPERTY = "zapthemes.titleBar.native";

    final TitleBar bar;
    final Shell shell;

    WindowFrame(TitleBar bar) {
        this.bar = bar;
        this.shell = bar.getShell();
    }

    /** Whether this platform can replace the caption of a trimmed shell (Windows, macOS). */
    static boolean nativeSupported() {
        if (!Boolean.parseBoolean(System.getProperty(NATIVE_PROPERTY, "true"))) {
            return false;
        }
        return switch (SWT.getPlatform()) {
            case "win32" -> Win32Frame.available();
            case "cocoa" -> CocoaFrame.available();
            default -> false;
        };
    }

    static WindowFrame create(TitleBar bar) {
        Shell shell = bar.getShell();
        int style = shell.getStyle();
        if ((style & SWT.TITLE) == 0) {
            return new DrawnFrame(bar);
        }
        if (nativeSupported()) {
            try {
                WindowFrame frame = "win32".equals(SWT.getPlatform()) ? new Win32Frame(bar) : new CocoaFrame(bar);
                frame.install();
                return frame;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.log(Level.WARNING, "The native title bar is not available; the window keeps its caption", e);
            }
        }
        return new EmbeddedFrame(bar);
    }

    abstract TitleBar.FrameMode mode();

    void install() throws ReflectiveOperationException {
    }

    void dispose() {
    }

    /** Whether the bar draws the minimize, maximize and close buttons. */
    boolean drawsWindowButtons() {
        return true;
    }

    /** Whether the bar shows the app icon and opens the system menu from it. */
    boolean drawsIcon() {
        return true;
    }

    /** Room left free at the start of the bar, in bar pixels (the macOS traffic lights). */
    int leadingInset() {
        return 0;
    }

    /** Whether the top pixels of the bar resize the window (the bar answers {@code RESIZE_*} parts). */
    boolean resizesFromBar() {
        return false;
    }

    /** The mouse went down on the caption; {@code event} is in bar coordinates. */
    void captionPressed(Event event) {
    }

    void captionDragged(Event event) {
    }

    void captionReleased(Event event) {
    }

    void captionDoubleClicked() {
        bar.toggleMaximize();
    }

    /** Opens the system menu at {@code location} (display coordinates). */
    void openSystemMenu(Point location) {
        bar.openDrawnSystemMenu(location);
    }

    /** The bar's layout changed: native frames update what they know about its parts. */
    void layoutChanged() {
    }

    /** The native caption stays above the bar: no icon, no window buttons. */
    static final class EmbeddedFrame extends WindowFrame {

        EmbeddedFrame(TitleBar bar) {
            super(bar);
        }

        @Override
        TitleBar.FrameMode mode() {
            return TitleBar.FrameMode.EMBEDDED;
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
        void captionDoubleClicked() {
            // The native caption does this.
        }
    }
}
