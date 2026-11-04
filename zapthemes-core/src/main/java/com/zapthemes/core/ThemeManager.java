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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Widget;

import com.zapthemes.core.model.Theme;
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
 *   <li>{@link ThemeExtension}s found by {@link ServiceLoader} are installed when the manager is
 *       created, e.g. zapthemes-jface's sync of the JFace color and font registries.</li>
 * </ul>
 *
 * All methods except {@link #setTheme(Theme)} must be called on the display's UI thread.
 */
public final class ThemeManager {

    private static final System.Logger LOG = System.getLogger(ThemeManager.class.getName());
    private static final Map<Display, ThemeManager> MANAGERS = new ConcurrentHashMap<>();
    private static final Set<Class<?>> RETRY_LOGGED = ConcurrentHashMap.newKeySet();

    private final Display display;
    private final StylerRegistry stylers = StylerRegistry.withDefaults();
    private final ControlTypeResolver typeResolver = new ControlTypeResolver();
    private final List<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

    private StyleResolver resolver = new StyleResolver(Theme.EMPTY);
    private ThemeResources resources;

    private ThemeManager(Display display) {
        this.display = display;
        this.resources = new ThemeResources(display);
        display.addListener(SWT.Skin, this::onSkin);
        display.disposeExec(() -> {
            MANAGERS.remove(display);
            resources.dispose();
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
        ServiceLoader.load(ThemeExtension.class).forEach(extension -> extension.install(manager));
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
        ThemeResources oldResources = resources;
        resolver = new StyleResolver(theme);
        resources = new ThemeResources(display);

        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Shell shell : display.getShells()) {
            reapplyTree(shell, visited);
        }
        listeners.forEach(listener -> listener.themeChanged(previous, theme));
        oldResources.dispose();
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

    /** A {@code registries} block of the current theme, e.g. {@code "colors"} or {@code "fonts"}. */
    public ResolvedStyle registry(String name) {
        return resolver.registry(name);
    }

    /** The resources of the current theme; replaced (and the old ones disposed) on theme change. */
    public ThemeResources resources() {
        return resources;
    }

    private void reapplyTree(Shell shell, Set<Widget> visited) {
        if (shell.isDisposed() || !visited.add(shell)) {
            return;
        }
        shell.setRedraw(false);
        try {
            WidgetTree.walk(shell, widget -> {
                if (widget == shell || visited.add(widget)) {
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
        apply(widget);
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

    private void onSkin(Event event) {
        Widget widget = event.widget;
        // Proxies were themed in their constructor; only widgets created some other way are left.
        if (widget != null && !widget.isDisposed() && widget.getData(ThemeKeys.THEME_CONTROL_TYPE) == null) {
            apply(widget);
        }
    }
}
