package com.zapthemes.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.widgets.Caret;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Decorations;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.widgets.Widget;

/**
 * Gives widgets their images at the zoom: any widget with {@code getImage()} and
 * {@code setImage(Image)} (Label, Button, CLabel, ToolItem, CTabItem, Nebula widgets, ...) gets a
 * copy of its image made for the zoom. The copy asks the original for its pixels at that zoom
 * ({@link Image#getImageData(int)}), so an image loaded with {@code @1.5x} / {@code @2x} variants
 * (JFace's {@code ImageDescriptor.createFromURL}, an {@code ImageFileNameProvider}) shows the
 * sharp variant, and any other image is scaled smoothly. At 100 % the widget gets its own image
 * back.
 *
 * <p>Left out: shell icons, carets, and table and tree items, whose native image lists keep the
 * size of their first image. An image the application sets is taken as a 100 % image. The
 * copies are owned here: one per original and zoom level, disposed with the display or once their
 * original is disposed.
 */
final class ImageZoom {

    /** The application's image, at 100 %. */
    private static final String ORIGINAL = "zapthemes.originalImage";
    /** The image this class set, to notice one the application set since. */
    private static final String APPLIED = "zapthemes.zoomedImage";
    private static final Map<Class<?>, Optional<Method[]>> ACCESSORS = new ConcurrentHashMap<>();

    private final Display display;
    private final Map<Image, Map<Integer, Image>> copies = new IdentityHashMap<>();

    ImageZoom(Display display) {
        this.display = display;
    }

    /** Whether {@code widget} has an image this class handles. */
    static boolean handles(Widget widget) {
        return !(widget instanceof Decorations || widget instanceof Caret || widget instanceof TableItem
                || widget instanceof TreeItem) && ACCESSORS.computeIfAbsent(widget.getClass(), ImageZoom::accessors).isPresent();
    }

    /** Gives {@code widget} its image at {@code zoom}. */
    void rescale(Widget widget, double zoom) {
        if (widget.isDisposed() || !handles(widget)) {
            return;
        }
        Method[] accessors = ACCESSORS.get(widget.getClass()).get();
        try {
            Image current = (Image) accessors[0].invoke(widget);
            if (current == null) {
                widget.setData(ORIGINAL, null);
                widget.setData(APPLIED, null);
                return;
            }
            Image original = widget.getData(ORIGINAL) instanceof Image o && widget.getData(APPLIED) == current ? o : current;
            if (original.isDisposed()) {
                return;
            }
            Image target = zoom == 1 ? original : copy(original, zoom);
            if (target != current) {
                accessors[1].invoke(widget, target);
            }
            widget.setData(ORIGINAL, original);
            widget.setData(APPLIED, target);
        } catch (IllegalAccessException | InvocationTargetException | ClassCastException e) {
            // Not an image accessor after all; leave the widget as it is.
        }
    }

    /** Whether {@code widget} and its parents let images follow the zoom. */
    static boolean allowed(Widget widget) {
        if (Boolean.FALSE.equals(widget.getData(ThemeKeys.ZOOM_IMAGES))) {
            return false;
        }
        Control control = widget instanceof Control c ? c : parentOf(widget);
        for (; control != null; control = control.getParent()) {
            if (Boolean.FALSE.equals(control.getData(ThemeKeys.ZOOM_IMAGES))) {
                return false;
            }
        }
        return true;
    }

    private static Control parentOf(Widget item) {
        try {
            Method parent = item.getClass().getMethod("getParent");
            return parent.invoke(item) instanceof Control c ? c : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private Image copy(Image original, double zoom) {
        int percent = (int) Math.round(zoom * 100);
        return copies.computeIfAbsent(original, o -> new HashMap<>()).computeIfAbsent(percent, p -> {
            ImageData fallback = original.getImageData(100);
            ImageDataProvider provider = deviceZoom -> {
                int wanted = Math.max(1, (int) Math.round(deviceZoom * zoom));
                if (!original.isDisposed()) {
                    return original.getImageData(wanted);
                }
                return fallback.scaledTo(Math.max(1, fallback.width * wanted / 100), Math.max(1, fallback.height * wanted / 100));
            };
            return new Image(display, provider);
        });
    }

    /** Disposes the copies of originals the application disposed. */
    void sweep() {
        for (Iterator<Map.Entry<Image, Map<Integer, Image>>> it = copies.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Image, Map<Integer, Image>> entry = it.next();
            if (entry.getKey().isDisposed()) {
                entry.getValue().values().forEach(Image::dispose);
                it.remove();
            }
        }
    }

    /** Number of copies held; used by leak tests. */
    int copyCount() {
        return copies.values().stream().mapToInt(Map::size).sum();
    }

    void dispose() {
        copies.values().forEach(byZoom -> byZoom.values().forEach(Image::dispose));
        copies.clear();
    }

    private static Optional<Method[]> accessors(Class<?> type) {
        try {
            Method getter = type.getMethod("getImage");
            Method setter = type.getMethod("setImage", Image.class);
            return getter.getReturnType() == Image.class ? Optional.of(new Method[] { getter, setter }) : Optional.empty();
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }
}
