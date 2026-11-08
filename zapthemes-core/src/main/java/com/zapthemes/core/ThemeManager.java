package com.zapthemes.core;

import java.lang.System.Logger.Level;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.model.Theme;
import com.zapthemes.core.platform.PlatformThemeSupport;
import com.zapthemes.core.style.DefaultStyler;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.StyleResolver;
import com.zapthemes.core.style.StylerRegistry;
import com.zapthemes.core.style.ThemeContext;

/**
 * Keeps the current theme of a {@link Display} and applies it to widgets.
 *
 * <ul>
 *   <li>Proxies call {@link #applyOnCreate(Widget, String)} at the end of their constructor.</li>
 *   <li>{@link #setTheme(Theme)} walks the control tree of every shell and re-styles each widget
 *       according to its {@code getData("THEME_CONTROL_TYPE")}.</li>
 *   <li>Widgets created without a proxy are tagged and themed from the {@link SWT#Skin} event,
 *       which SWT sends for every new widget before the next event dispatch.</li>
 *   <li>The theme's {@code platform} settings (title bars, scrollbars, Windows menu bar) are applied
 *       through {@link PlatformThemeSupport}.</li>
 *   <li>{@link ThemeExtension}s found by {@link ServiceLoader} are installed when the manager is
 *       created, e.g. zapthemes-jface's sync of the JFace color and font registries.</li>
 *   <li>A Chrome-style {@linkplain #setZoom(double) zoom} scales every theme font, column widths,
 *       theme sizes and widget images; Ctrl + mouse wheel, Ctrl+= / Ctrl+- / Ctrl+0 (Cmd on
 *       macOS) and a trackpad pinch change it unless
 *       {@linkplain #setZoomShortcutsEnabled(boolean) turned off}.</li>
 * </ul>
 *
 * All methods except {@link #setTheme(Theme)} must be called on the display's UI thread.
 */
public final class ThemeManager {

    private static final System.Logger LOG = System.getLogger(ThemeManager.class.getName());
    private static final Map<Display, ThemeManager> MANAGERS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> RETRY_LOGGED = ConcurrentHashMap.newKeySet();

    /** Chrome's zoom levels, as factors; {@link #zoomIn()} and {@link #zoomOut()} step through them. */
    public static final List<Double> ZOOM_LEVELS = List.of(
            0.25, 0.33, 0.5, 0.67, 0.75, 0.8, 0.9, 1.0, 1.1, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0, 4.0, 5.0);
    /** Smallest and largest zoom {@link #setZoom(double)} accepts. */
    public static final double MIN_ZOOM = 0.25;
    public static final double MAX_ZOOM = 5.0;
    /** System property: {@code false} makes the zoom shortcuts start turned off on every display. */
    public static final String ZOOM_SHORTCUTS_PROPERTY = "zapthemes.zoomShortcuts";
    private static final double EPSILON = 1e-6;

    private final Display display;
    private final StylerRegistry stylers = StylerRegistry.withDefaults();
    private final ControlTypeResolver typeResolver = new ControlTypeResolver();
    private final List<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ZoomListener> zoomListeners = new CopyOnWriteArrayList<>();
    private final PlatformThemeSupport platform = PlatformThemeSupport.forCurrentPlatform();

    private StyleResolver resolver = new StyleResolver(Theme.EMPTY);
    private final ThemeResources resources;
    private boolean zoomShortcuts = !"false".equalsIgnoreCase(System.getProperty(ZOOM_SHORTCUTS_PROPERTY));
    private boolean zoomImages = true;
    private final ImageZoom images;
    /** The zoom when the current pinch began, 0 when there is none. */
    private double pinchStart;

    private ThemeManager(Display display) {
        this.display = display;
        this.resources = new ThemeResources(display);
        this.images = new ImageZoom(display);
        display.addListener(SWT.Skin, this::onSkin);
        display.addFilter(SWT.MouseWheel, this::onZoomWheel);
        display.addFilter(SWT.KeyDown, this::onZoomKey);
        display.addFilter(SWT.Gesture, this::onZoomGesture);
        display.disposeExec(() -> {
            MANAGERS.remove(display);
            resources.dispose();
            images.dispose();
        });
    }

    /** The manager of {@code display}; created on first use. Call on the UI thread. */
    public static ThemeManager get(Display display) {
        ThemeManager existing = MANAGERS.get(display);
        if (existing != null) {
            return existing;
        }
        if (display.getThread() != Thread.currentThread()) {
            throw new SWTException(SWT.ERROR_THREAD_INVALID_ACCESS);
        }
        // Registered before extensions are installed, so an extension may call get() itself.
        ThemeManager manager = new ThemeManager(display);
        MANAGERS.put(display, manager);
        List<ThemeExtension> extensions = ServiceLoader.load(ThemeExtension.class).stream().map(ServiceLoader.Provider::get).toList();
        extensions.forEach(extension -> extension.install(manager));
        extensions.stream().map(extension -> extension.initialTheme(display)).filter(Objects::nonNull).findFirst()
                .ifPresent(manager::setTheme);
        return manager;
    }

    /**
     * Tags {@code widget} with {@code type} and applies the current theme immediately. Called by
     * every proxy constructor right after {@code super(...)}.
     *
     * <p>If the styler throws — typically because a user subclass of a proxy overrides a setter that
     * reads fields its own constructor has not initialized yet — the failure is logged once per
     * class and the theme is applied again from {@link Display#asyncExec(Runnable)}, before the
     * widget is first painted.
     */
    public static void applyOnCreate(Widget widget, String type) {
        widget.setData(ThemeKeys.THEME_CONTROL_TYPE, type);
        ThemeManager manager = get(widget.getDisplay());
        try {
            manager.apply(widget);
        } catch (RuntimeException e) {
            if (RETRY_LOGGED.add(widget.getClass())) {
                LOG.log(Level.WARNING, "Theming " + widget.getClass().getName()
                        + " in its constructor failed; retrying after construction", e);
            }
            manager.display.asyncExec(() -> {
                if (!widget.isDisposed()) {
                    manager.apply(widget);
                }
            });
        }
    }

    public Theme getTheme() {
        return resolver.theme();
    }

    /**
     * Makes {@code theme} current and re-themes every widget of every shell. May be called from any
     * thread; the work runs synchronously on the UI thread.
     */
    public void setTheme(Theme theme) {
        Objects.requireNonNull(theme, "theme");
        if (display.getThread() != Thread.currentThread()) {
            display.syncExec(() -> setTheme(theme));
            return;
        }
        Theme previous = resolver.theme();
        resolver = new StyleResolver(theme);
        reapplyAll();
        listeners.forEach(listener -> listener.themeChanged(previous, theme));
    }

    /** The zoom factor of every theme font on this display; {@code 1.0} (100 %) by default. */
    public double getZoom() {
        return resources.zoom();
    }

    /**
     * Scales every theme font of this display by {@code zoom} (clamped to {@link #MIN_ZOOM} ..
     * {@link #MAX_ZOOM}), on top of the heights the theme gives them, and re-themes and re-lays out
     * every shell. The zoom survives theme switches. May be called from any thread; the work runs
     * synchronously on the UI thread.
     */
    public void setZoom(double zoom) {
        if (!(zoom > 0)) {
            throw new IllegalArgumentException("zoom must be positive: " + zoom);
        }
        if (display.getThread() != Thread.currentThread()) {
            display.syncExec(() -> setZoom(zoom));
            return;
        }
        double clamped = Math.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        double previous = resources.zoom();
        if (Math.abs(clamped - previous) < EPSILON) {
            return;
        }
        resources.setZoom(clamped);
        reapplyAll(previous);
        zoomListeners.forEach(listener -> listener.zoomChanged(previous, clamped));
    }

    /** The next larger of Chrome's {@link #ZOOM_LEVELS}, e.g. 100 % to 110 %. */
    public void zoomIn() {
        double zoom = getZoom();
        setZoom(ZOOM_LEVELS.stream().filter(level -> level > zoom + EPSILON).findFirst().orElse(MAX_ZOOM));
    }

    /** The next smaller of Chrome's {@link #ZOOM_LEVELS}, e.g. 100 % to 90 %. */
    public void zoomOut() {
        double zoom = getZoom();
        setZoom(ZOOM_LEVELS.reversed().stream().filter(level -> level < zoom - EPSILON).findFirst().orElse(MIN_ZOOM));
    }

    /** Back to 100 %. */
    public void resetZoom() {
        setZoom(1.0);
    }

    public void addZoomListener(ZoomListener listener) {
        zoomListeners.add(listener);
    }

    public void removeZoomListener(ZoomListener listener) {
        zoomListeners.remove(listener);
    }

    /**
     * Whether widget images follow the zoom (see the guide's Zoom section); on by default. Turned
     * off, every widget gets its own image back. {@link ThemeKeys#ZOOM_IMAGES} turns it off for one
     * widget and its children.
     */
    public boolean isZoomImagesEnabled() {
        return zoomImages;
    }

    public void setZoomImagesEnabled(boolean enabled) {
        if (enabled == zoomImages) {
            return;
        }
        zoomImages = enabled;
        for (Shell shell : display.getShells()) {
            WidgetTree.walk(shell, this::rescaleImage);
        }
        images.sweep();
    }

    /**
     * The image {@code widget} should show for {@code image} at the current zoom: a zoomed copy, or
     * {@code image} itself at 100 %, when images are off, or for a widget left out. Proxies call it
     * from {@code setImage}, so a widget sizes itself around the zoomed image from the start.
     */
    public static org.eclipse.swt.graphics.Image zoomedImage(Widget widget, org.eclipse.swt.graphics.Image image) {
        ThemeManager manager = MANAGERS.get(widget.getDisplay());
        if (manager == null) {
            return image;
        }
        double zoom = manager.zoomImages && ImageZoom.allowed(widget) ? manager.resources.zoom() : 1;
        return manager.images.forSet(widget, image, zoom);
    }

    /** Number of zoomed image copies held; used by leak tests. */
    public int zoomedImageCount() {
        return images.copyCount();
    }

    private void rescaleImage(Widget widget) {
        if (!widget.isDisposed()) {
            images.rescale(widget, zoomImages && ImageZoom.allowed(widget) ? resources.zoom() : 1);
        }
    }

    /** Whether Ctrl + mouse wheel, Ctrl+= / Ctrl+- / Ctrl+0 (Cmd on macOS) and a pinch zoom; on by default. */
    public boolean isZoomShortcutsEnabled() {
        return zoomShortcuts;
    }

    /**
     * Turns the zoom shortcuts on or off for the whole display; {@link #setZoom(double)} and friends
     * keep working. To leave the shortcuts to a single control instead, set
     * {@link ThemeKeys#ZOOM_SHORTCUTS} to {@link Boolean#FALSE} on it. They start on unless the
     * system property {@value #ZOOM_SHORTCUTS_PROPERTY} is {@code false}.
     */
    public void setZoomShortcutsEnabled(boolean enabled) {
        zoomShortcuts = enabled;
    }

    /** Re-applies the current theme to {@code widget} only (not its children). */
    public void reapply(Widget widget) {
        apply(widget);
    }

    public void addThemeChangeListener(ThemeChangeListener listener) {
        listeners.add(listener);
    }

    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * The current theme's rule for {@code type} alone ({@code inherit} followed; no {@code defaults},
     * style bits or classes), e.g. for draw2d figures: empty when the theme has no such rule.
     */
    public ResolvedStyle ruleOf(String type) {
        return resolver.rule(type);
    }

    /** A {@code registries} block of the current theme, e.g. {@code "colors"} or {@code "fonts"}. */
    public ResolvedStyle registry(String name) {
        return resolver.registry(name);
    }

    /** The colors and fonts of this display's themes (kept for the display's lifetime). */
    public ThemeResources resources() {
        return resources;
    }

    private void reapplyAll() {
        reapplyAll(0);
    }

    /** {@code previousZoom}: the zoom before, when the zoom changed (else 0); columns are rescaled. */
    private void reapplyAll(double previousZoom) {
        platform.beforeTheme(display, resolver.platform(), resources);
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Shell shell : display.getShells()) {
            reapplyTree(shell, visited, previousZoom);
        }
        if (previousZoom > 0) {
            images.sweep();
        }
        platform.afterTheme(display, resolver.platform());
    }

    private void reapplyTree(Shell shell, Set<Widget> visited, double previousZoom) {
        if (shell.isDisposed() || !visited.add(shell)) {
            return;
        }
        shell.setRedraw(false);
        try {
            WidgetTree.walk(shell, widget -> {
                if (widget == shell || visited.add(widget)) {
                    if (previousZoom > 0) {
                        ColumnZoom.rescale(widget, previousZoom, resources.zoom());
                        rescaleImage(widget);
                    }
                    apply(widget);
                }
            });
        } finally {
            shell.setRedraw(true);
        }
        shell.layout(true, true);
    }

    /**
     * Sets the widget's style classes ({@code THEME_CLASS}, whitespace-separated, e.g.
     * {@code "primary large"}) and re-applies the theme to it.
     */
    public void setThemeClass(Widget widget, String themeClasses) {
        widget.setData(ThemeKeys.THEME_CLASS, themeClasses);
        if (widget instanceof Control control) {
            // A class is a request for the theme's look, its font included.
            DefaultStyler.followThemeFont(control);
        }
        apply(widget);
    }

    /**
     * Called by every proxy control's {@code setFont}, before SWT's: records {@code font} as the
     * application's own font for {@code control} ({@link ThemeKeys#APP_FONT}), kept through theme
     * switches and scaled by the zoom. {@code null}, or a font like the theme's, follows the theme.
     */
    public static void fontSetByApplication(Control control, org.eclipse.swt.graphics.Font font) {
        ThemeManager manager = MANAGERS.get(control.getDisplay());
        if (manager == null) {
            return;
        }
        ThemeContext context = new ThemeContext(manager.resources);
        org.eclipse.swt.graphics.Font theme = context.font(manager.styleOf(control), DefaultStyler.FONT);
        DefaultStyler.fontSetByApplication(control, font, theme != null ? theme
                : context.defaultFont() != null ? context.defaultFont() : control.getDisplay().getSystemFont(), context);
    }

    /**
     * Drops a font the application set on {@code control} ({@link ThemeKeys#APP_FONT}), so that it
     * takes the theme's font, at the current zoom, again.
     */
    public void followThemeFont(Control control) {
        DefaultStyler.followThemeFont(control);
        apply(control);
    }

    /**
     * The current theme's resolved style for {@code widget} (its type, style bits and classes), e.g.
     * for custom painting code. Tags the widget with its inferred type if it has none.
     */
    public ResolvedStyle styleOf(Widget widget) {
        String classes = widget.getData(ThemeKeys.THEME_CLASS) instanceof String c ? c : null;
        return resolver.resolve(typeOf(widget), widget.getStyle(), classes);
    }

    void apply(Widget widget) {
        ResolvedStyle style = styleOf(widget);
        // The native theme first: on Windows SetWindowTheme makes a tool bar reload its default
        // font, so the styler's font must come after it.
        platform.apply(widget, resolver.platform());
        stylers.get(typeOf(widget)).apply(widget, style, new ThemeContext(resources));
    }

    private String typeOf(Widget widget) {
        if (widget.getData(ThemeKeys.THEME_CONTROL_TYPE) instanceof String type) {
            return type;
        }
        String type = typeResolver.resolve(widget);
        widget.setData(ThemeKeys.THEME_CONTROL_TYPE, type);
        return type;
    }

    private void onZoomWheel(Event event) {
        if (event.count == 0 || (event.stateMask & SWT.MODIFIER_MASK) != SWT.MOD1 || !zoomShortcutsFor(event.widget)) {
            return;
        }
        consume(event);
        if (event.count > 0) {
            zoomIn();
        } else {
            zoomOut();
        }
    }

    private void onZoomKey(Event event) {
        int modifiers = event.stateMask & SWT.MODIFIER_MASK;
        // Shift is allowed, so Ctrl++ (Shift and =) works as in Chrome; Alt is not (AltGr is Ctrl+Alt).
        if ((modifiers & ~SWT.SHIFT) != SWT.MOD1) {
            return;
        }
        Runnable action = switch (event.keyCode) {
            case '=', '+', SWT.KEYPAD_ADD -> this::zoomIn;
            case '-', SWT.KEYPAD_SUBTRACT -> this::zoomOut;
            case '0', SWT.KEYPAD_0 -> this::resetZoom;
            default -> null;
        };
        if (action == null || !zoomShortcutsFor(event.widget)) {
            return;
        }
        consume(event);
        action.run();
    }

    /**
     * A trackpad pinch: the zoom when it began times the gesture's magnification, snapped to the
     * nearest of Chrome's levels.
     */
    private void onZoomGesture(Event event) {
        switch (event.detail) {
            case SWT.GESTURE_BEGIN -> pinchStart = 0;
            case SWT.GESTURE_END -> pinchStart = 0;
            case SWT.GESTURE_MAGNIFY -> {
                if (!zoomShortcutsFor(event.widget) || !(event.magnification > 0)) {
                    return;
                }
                if (pinchStart == 0) {
                    pinchStart = getZoom();
                }
                double level = nearestLevel(pinchStart * event.magnification);
                event.doit = false;
                setZoom(level);
            }
            default -> {
                // Pan, rotate, swipe: not a zoom.
            }
        }
    }

    /** The one of {@link #ZOOM_LEVELS} closest to {@code zoom}, by ratio. */
    static double nearestLevel(double zoom) {
        double best = 1;
        for (double level : ZOOM_LEVELS) {
            if (Math.abs(Math.log(level / zoom)) < Math.abs(Math.log(best / zoom))) {
                best = level;
            }
        }
        return best;
    }

    /** Whether the shortcuts are on and neither {@code widget} nor a parent keeps them for itself. */
    private boolean zoomShortcutsFor(Widget widget) {
        if (!zoomShortcuts) {
            return false;
        }
        for (Control control = widget instanceof Control c ? c : null; control != null; control = control.getParent()) {
            if (Boolean.FALSE.equals(control.getData(ThemeKeys.ZOOM_SHORTCUTS))) {
                return false;
            }
        }
        return true;
    }

    private static void consume(Event event) {
        event.doit = false;
        event.type = SWT.None;
    }

    private void onSkin(Event event) {
        Widget widget = event.widget;
        if (widget == null || widget.getData(ThemeKeys.THEME_CONTROL_TYPE) != null) {
            return;
        }
        // SWT may send Skin while the widget's constructor is still running (from a layout it
        // triggers), so theme it once construction is over. Proxies have tagged themselves by then.
        display.asyncExec(() -> {
            if (widget.isDisposed() || widget.getData(ThemeKeys.THEME_CONTROL_TYPE) != null) {
                return;
            }
            try {
                apply(widget);
                if (getZoom() != 1) {
                    rescaleImage(widget);
                }
            } catch (RuntimeException e) {
                if (RETRY_LOGGED.add(widget.getClass())) {
                    LOG.log(Level.WARNING, "Theming " + widget.getClass().getName() + " from SWT.Skin failed", e);
                }
            }
        });
    }
}
