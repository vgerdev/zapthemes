package com.zapthemes.files.ui;

import java.lang.reflect.Method;

import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Scrollable;

/**
 * The vertical scroll bar of a native control as Windows draws it, through {@code PrintWindow}
 * on the window that holds the scroll bars (a Tree's {@code hwndParent} when it has one). Unlike
 * reading the screen, this does not depend on what else is on top: Gradle runs the modules'
 * GUI tests in parallel, and their windows and pop-ups may cover this one.
 */
final class ScrollBarPixels {

    /** Renders what DWM shows for the window, scroll bars included, as list boxes and edits do not print them on WM_PRINT. */
    private static final int PW_RENDERFULLCONTENT = 2;

    private ScrollBarPixels() {
    }

    /** The mean luminance of the lower part of the vertical bar's track: below the thumb, above the down arrow. */
    static double track(Scrollable scrollable) throws ReflectiveOperationException {
        Class<?> os = Class.forName("org.eclipse.swt.internal.win32.OS");
        Class<?> rectClass = Class.forName("org.eclipse.swt.internal.win32.RECT");
        Method topHandle = Control.class.getDeclaredMethod("topHandle");
        topHandle.setAccessible(true);
        long hwnd = (long) topHandle.invoke(scrollable);
        Object rect = rectClass.getConstructor().newInstance();
        os.getMethod("GetWindowRect", long.class, rectClass).invoke(null, hwnd, rect);
        int width = rectClass.getField("right").getInt(rect) - rectClass.getField("left").getInt(rect);
        int height = rectClass.getField("bottom").getInt(rect) - rectClass.getField("top").getInt(rect);
        Image image = new Image(scrollable.getDisplay(), width, height);
        GC gc = new GC(image);
        try {
            os.getMethod("PrintWindow", long.class, long.class, int.class).invoke(null, hwnd, gc.handle, PW_RENDERFULLCONTENT);
        } finally {
            gc.dispose();
        }
        ImageData data = image.getImageData();
        image.dispose();
        // In pixels (the window's), not SWT's points: the bar is the right edge of the window.
        int bar = (int) os.getMethod("GetSystemMetrics", int.class).invoke(null, 2 /* SM_CXVSCROLL */);
        int left = width - bar + 2;
        int right = width - 2;
        int top = height / 2;
        int bottom = height - 2 * bar - 4;
        double sum = 0;
        int count = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                RGB rgb = data.palette.getRGB(data.getPixel(x, y));
                sum += 0.2126 * linear(rgb.red) + 0.7152 * linear(rgb.green) + 0.0722 * linear(rgb.blue);
                count++;
            }
        }
        return sum / count;
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
