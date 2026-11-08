package com.zapthemes.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Widget;

/**
 * Scales column widths with the zoom: {@code TableColumn}, {@code TreeColumn} and any other item
 * whose class name ends in {@code Column} with {@code getWidth()} and {@code setWidth(int)} (Nebula's
 * {@code GridColumn}, for one).
 *
 * <p>Each column keeps its width at 100 % as a fraction, so zooming in and out never drifts. A width
 * that changed since the last zoom (the user dragged the column, or the application set it or
 * packed it) is taken as being at the zoom it was changed at.
 */
final class ColumnZoom {

    /** The width at 100 %, a {@link Double}. */
    private static final String BASE = "zapthemes.columnBaseWidth";
    /** The width the last zoom set, an {@link Integer}, to notice widths changed since. */
    private static final String SET = "zapthemes.columnZoomedWidth";
    private static final Map<Class<?>, Optional<Method[]>> ACCESSORS = new ConcurrentHashMap<>();

    private ColumnZoom() {
    }

    static void rescale(Widget widget, double previous, double zoom) {
        if (!(widget instanceof Item item) || !item.getClass().getSimpleName().endsWith("Column")) {
            return;
        }
        Optional<Method[]> accessors = ACCESSORS.computeIfAbsent(item.getClass(), ColumnZoom::accessors);
        if (accessors.isEmpty()) {
            return;
        }
        try {
            int width = (int) accessors.get()[0].invoke(item);
            double base = item.getData(BASE) instanceof Double b && item.getData(SET) instanceof Integer set && set == width
                    ? b : width / previous;
            int zoomed = (int) Math.round(base * zoom);
            accessors.get()[1].invoke(item, zoomed);
            item.setData(BASE, base);
            item.setData(SET, (int) accessors.get()[0].invoke(item));
        } catch (IllegalAccessException | InvocationTargetException e) {
            // Not a width accessor after all; leave the column as it is.
        }
    }

    private static Optional<Method[]> accessors(Class<?> type) {
        try {
            Method getter = type.getMethod("getWidth");
            Method setter = type.getMethod("setWidth", int.class);
            return getter.getReturnType() == int.class ? Optional.of(new Method[] { getter, setter }) : Optional.empty();
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
