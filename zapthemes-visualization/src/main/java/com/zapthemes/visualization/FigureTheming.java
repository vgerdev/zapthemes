package com.zapthemes.visualization;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.LayoutListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

import com.zapthemes.core.ThemeChangeListener;
import com.zapthemes.core.ThemeManager;
import com.zapthemes.core.ThemeResources;
import com.zapthemes.core.ZoomListener;
import com.zapthemes.core.style.ResolvedStyle;
import com.zapthemes.core.style.ThemeContext;

/**
 * Themes draw2d figures, such as Nebula Visualization's {@code XYGraph}, {@code Axis} or
 * {@code GaugeFigure}, which are not SWT widgets and so are out of reach of the control stylers.
 * The theme gives rules for figure types in {@code controls}, by simple class name:
 *
 * <pre>{@code "Axis": { "foreground": "$fg", "majorGridColor": "$border" },
 * "AbstractScaledWidgetFigure": { "foreground": "$fg" }}</pre>
 *
 * For each figure, the rules of its class and of its Nebula Visualization superclasses apply, the
 * most general first. draw2d's own figures ({@code Label}, {@code Button}, ...) are not types here,
 * since their names are SWT controls' too; reach them through a dotted key of their owner. {@code background}, {@code foreground} and {@code font}
 * call {@code setBackgroundColor}, {@code setForegroundColor} and {@code setFont}; any other key
 * {@code fooColor} calls {@code setFooColor(...)} with a {@link Color}, {@link Font}, {@code int},
 * {@code boolean} or {@link String}, and a dotted key reaches into a part
 * ({@code titleLabel.font} calls {@code getTitleLabel().setFont(...)}). Only what the theme says
 * for a figure type applies, never the theme's {@code defaults}. A value a later theme no longer
 * sets is restored to what the figure had before the first theme.
 *
 * <p>Fonts the theme does not set follow the {@linkplain ThemeManager#setZoom zoom} too: every
 * font property of a figure ({@code font}, {@code titleFont}, ...) keeps its family and style at
 * its height times the zoom. A figure's own font, such as XYGraph's 12 pt bold title, is taken as
 * a 100 % size; one the application sets later is taken at the zoom it is set at.
 *
 * <p>Nothing to call for Nebula Visualization: its graphs and widget figures {@link #attach}
 * themselves when shown, which keeps them in step with the theme now, on every theme switch and
 * for figures added later (legends appear with their first trace). {@link #follow} does the same
 * for a figure tree of your own.
 */
public final class FigureTheming {

    private static final System.Logger LOG = System.getLogger(FigureTheming.class.getName());
    // Not draw2d's own classes: Label, Button or Panel would pick up the SWT controls' rules.
    private static final String KNOWN_PACKAGE = "org.eclipse.nebula.visualization.";
    private static final List<Class<?>> VALUE_TYPES = List.of(Color.class, Font.class, int.class, boolean.class, String.class);
    private static final Map<String, String> DEFAULT_SETTERS = Map.of(
            "background", "setBackgroundColor", "foreground", "setForegroundColor", "font", "setFont");
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();
    /** No getter, so nothing to restore. */
    private static final Object NONE = new Object();
    /** The getter returned null. */
    private static final Object UNSET = new Object();

    /** Per figure: values before the first theme, and the keys the last pass set. */
    private static final Map<IFigure, State> STATES = new WeakHashMap<>();

    private FigureTheming() {
    }

    private static final class State {
        final Map<String, Object> originals = new HashMap<>();
        Set<String> applied = Set.of();
        Object theme;
        /** Per font property the theme does not set: the font at 100 % and what the last pass set. */
        final Map<String, ZoomedFont> zoomed = new HashMap<>();
    }

    /** A figure's own font: family, style, height at 100 %, the font itself, and what was set last at which zoom. */
    private record ZoomedFont(String name, int style, double baseHeight, Font original, Font applied, double zoom) {
    }

    /** A font property of a figure class: its key, getter and setter. */
    private record FontProperty(String key, Method getter, Method setter) {
    }

    private static final Map<Class<?>, List<FontProperty>> FONT_PROPERTIES = new ConcurrentHashMap<>();
    /**
     * Fonts seen as a figure's own 100 % font, with that height: the same font often sits on two
     * properties (XYGraph's {@code titleFont} is its title label's {@code font}), and must not be
     * taken as zoomed when the other one puts it back.
     */
    private static final Map<Font, Double> ORIGINAL_HEIGHTS = new java.util.IdentityHashMap<>();

    private static final Map<IFigure, Runnable> ATTACHED = new WeakHashMap<>();

    /**
     * Themes {@code root} and its descendants now, on every theme switch and when figures are added,
     * until {@link #detach}. The forked {@code XYGraph}, {@code ToolbarArmedXYGraph},
     * {@code AbstractScaledWidgetFigure} and {@code IntensityGraphFigure} call this themselves when
     * they are added to a shown figure tree, and {@link #detach} when they are removed. Call it from
     * the UI thread; does nothing elsewhere, or if {@code root} is attached already.
     */
    public static void attach(IFigure root) {
        Display display = Display.getCurrent();
        if (display == null || ATTACHED.containsKey(root)) {
            return;
        }
        ThemeManager manager = ThemeManager.get(display);
        apply(root, manager);
        ThemeChangeListener onSwitch = new ThemeChangeListener() {
            @Override
            public void themeChanged(com.zapthemes.core.model.Theme previous, com.zapthemes.core.model.Theme current) {
                try {
                    apply(root, manager);
                } catch (SWTException e) {
                    // The canvas showing the figure is gone without removing it.
                    detach(root);
                }
            }
        };
        LayoutListener onLayout = new LayoutListener.Stub() {
            @Override
            public void postLayout(IFigure container) {
                // Figures added since the last pass (a legend, an annotation) get the current theme.
                walk(root, manager, true);
            }
        };
        // Theme fonts follow the zoom, so a zoom re-styles the figures like a theme switch.
        ZoomListener onZoom = (previous, current) -> onSwitch.themeChanged(manager.getTheme(), manager.getTheme());
        manager.addThemeChangeListener(onSwitch);
        manager.addZoomListener(onZoom);
        root.addLayoutListener(onLayout);
        ATTACHED.put(root, () -> {
            manager.removeThemeChangeListener(onSwitch);
            manager.removeZoomListener(onZoom);
            root.removeLayoutListener(onLayout);
        });
    }

    /** Stops {@link #attach} for {@code root}; the colors it set stay. */
    public static void detach(IFigure root) {
        Runnable undo = ATTACHED.remove(root);
        if (undo != null) {
            undo.run();
        }
    }

    /**
     * {@link #attach} for a figure tree of your own, until {@code control} (the canvas showing the
     * figures) is disposed.
     */
    public static void follow(Control control, IFigure root) {
        attach(root);
        control.addDisposeListener(e -> detach(root));
    }

    /** Applies the current theme to {@code root} and its descendants once. */
    public static void apply(IFigure root, ThemeManager manager) {
        walk(root, manager, false);
        root.repaint();
    }

    private static void walk(IFigure figure, ThemeManager manager, boolean onlyNew) {
        Object theme = manager.getTheme();
        State state;
        synchronized (STATES) {
            state = STATES.computeIfAbsent(figure, f -> new State());
        }
        if (!onlyNew || state.theme != theme) {
            style(figure, state, manager);
            state.theme = theme;
        }
        for (Object child : new ArrayList<>(figure.getChildren())) {
            walk((IFigure) child, manager, onlyNew);
        }
    }

    private static void style(IFigure figure, State state, ThemeManager manager) {
        ThemeContext context = new ThemeContext(manager.resources());
        Set<String> applied = new HashSet<>();
        for (String type : types(figure.getClass())) {
            ResolvedStyle rule = manager.ruleOf(type);
            for (String key : rule.keys()) {
                if (key.equals(ResolvedStyle.STATES)) {
                    continue;
                }
                Target target = target(figure, key);
                if (target == null) {
                    continue;
                }
                Object value;
                try {
                    value = value(rule, key, target.setter.getParameterTypes()[0], type, context);
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.WARNING, "Theme value of \"" + key + "\" does not fit " + target.setter, e);
                    continue;
                }
                if (value == null) {
                    continue;
                }
                if (!state.originals.containsKey(key)) {
                    state.originals.put(key, target.current());
                }
                target.set(value);
                applied.add(key);
            }
        }
        for (String key : state.applied) {
            Object original = state.originals.get(key);
            if (!applied.contains(key) && original != NONE) {
                Target target = target(figure, key);
                if (target == null) {
                    continue;
                }
                try {
                    // null is a figure's usual "inherit from the parent"; a setter may still refuse it.
                    target.set(original == UNSET ? null : original);
                } catch (RuntimeException e) {
                    LOG.log(System.Logger.Level.DEBUG, "Cannot restore \"" + key + "\" of " + figure, e);
                }
            }
        }
        state.applied = applied;
        zoomFonts(figure, state, manager, applied);
    }

    /** Gives each font property the theme did not set this pass the figure's own font at the zoom. */
    private static void zoomFonts(IFigure figure, State state, ThemeManager manager, Set<String> themed) {
        ORIGINAL_HEIGHTS.keySet().removeIf(Font::isDisposed);
        ThemeResources resources = manager.resources();
        double zoom = resources.zoom();
        for (FontProperty property : fontProperties(figure.getClass())) {
            if (themed.contains(property.key())) {
                state.zoomed.remove(property.key());
                continue;
            }
            Font current;
            try {
                current = (Font) property.getter().invoke(figure);
            } catch (ReflectiveOperationException | ClassCastException e) {
                continue;
            }
            if (current == null || current.isDisposed()) {
                continue;
            }
            ZoomedFont known = state.zoomed.get(property.key());
            if (known == null || current != known.applied()) {
                // First seen, restored to its original, or set by the application since the last pass.
                FontData data = current.getFontData()[0];
                boolean ownDefault = known == null || current == state.originals.get(property.key());
                Double original = ORIGINAL_HEIGHTS.get(current);
                double base = original != null ? original : resources.baseHeight(current)
                        .orElse(ownDefault ? data.getHeight() : data.getHeight() / known.zoom());
                known = new ZoomedFont(data.getName(), data.getStyle(), base, current, current, zoom);
                if (resources.baseHeight(current).isEmpty()) {
                    ORIGINAL_HEIGHTS.put(current, base);
                }
            }
            Font target = zoom == 1 && known.original().getFontData()[0].getHeight() == Math.round(known.baseHeight())
                    ? known.original() : resources.font(known.name(), known.style(), known.baseHeight());
            if (target != current) {
                invoke(property.setter(), figure, target);
            }
            state.zoomed.put(property.key(), new ZoomedFont(known.name(), known.style(), known.baseHeight(), known.original(),
                    target, zoom));
        }
    }

    /** {@code font} (the figure's own, not inherited) and every {@code getFooFont()} with a {@code setFooFont(Font)}. */
    private static List<FontProperty> fontProperties(Class<?> type) {
        return FONT_PROPERTIES.computeIfAbsent(type, t -> {
            List<FontProperty> properties = new ArrayList<>();
            for (Method getter : t.getMethods()) {
                String name = getter.getName();
                if (getter.getParameterCount() != 0 || getter.getReturnType() != Font.class || !name.startsWith("get")
                        || !name.endsWith("Font") || Modifier.isStatic(getter.getModifiers())) {
                    continue;
                }
                Method setter = method(t, "set" + name.substring(3), Font.class);
                if (setter == null) {
                    continue;
                }
                String key = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                if (key.equals("font")) {
                    if (LOCAL_FONT == null) {
                        continue;
                    }
                    getter = LOCAL_FONT;
                }
                properties.add(new FontProperty(key, getter, setter));
            }
            properties.sort(java.util.Comparator.comparing(FontProperty::key));
            return List.copyOf(properties);
        });
    }

    /** Simple names of {@code type} and its Nebula Visualization superclasses, most general first. */
    static List<String> types(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Class<?> k = type; k != null && k != Object.class; k = k.getSuperclass()) {
            String pkg = k.getPackageName() + ".";
            if (!k.isAnonymousClass() && pkg.startsWith(KNOWN_PACKAGE)) {
                names.addFirst(k.getSimpleName());
            }
        }
        return names;
    }

    /** {@code owner}: the theme type, so that sizes (by the catalog) follow the zoom. */
    private static Object value(ResolvedStyle style, String key, Class<?> type, String owner, ThemeContext context) {
        if (type == Color.class) {
            return context.color(style, key);
        }
        if (type == Font.class) {
            return context.font(style, key);
        }
        if (type == int.class) {
            return context.integer(style, owner, key).orElse(null);
        }
        if (type == boolean.class) {
            return style.bool(key).orElse(null);
        }
        return style.string(key).orElse(null);
    }

    private static Target target(Object root, String key) {
        Object object = root;
        String[] path = key.split("\\.");
        for (int i = 0; i < path.length - 1; i++) {
            Method getter = method(object.getClass(), "get" + capitalize(path[i]), null);
            if (getter == null || (object = invoke(getter, object)) == null) {
                return null;
            }
        }
        String last = path[path.length - 1];
        String setterName = DEFAULT_SETTERS.getOrDefault(last, "set" + capitalize(last));
        for (Class<?> type : VALUE_TYPES) {
            Method setter = method(object.getClass(), setterName, type);
            if (setter != null) {
                return new Target(object, setter, getter(object, last, setterName, type));
            }
        }
        return null;
    }

    /**
     * The getter for a key's current value. A figure's colors and font are inherited from its parent
     * when it has none of its own, so for those the original is the figure's own value
     * ({@code getLocalForegroundColor}, ...), which may be null.
     */
    private static Method getter(Object object, String key, String setterName, Class<?> type) {
        if (object instanceof IFigure && DEFAULT_SETTERS.containsKey(key)) {
            return switch (key) {
                case "background" -> method(object.getClass(), "getLocalBackgroundColor", null);
                case "foreground" -> method(object.getClass(), "getLocalForegroundColor", null);
                default -> LOCAL_FONT;
            };
        }
        return method(object.getClass(), (type == boolean.class ? "is" : "get") + setterName.substring(3), null);
    }

    private static final Method LOCAL_FONT = localFont();

    private static Method localFont() {
        try {
            Method method = org.eclipse.draw2d.Figure.class.getDeclaredMethod("getLocalFont");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private record Target(Object object, Method setter, Method getter) {
        Object current() {
            Class<?> wanted = setter.getParameterTypes()[0];
            if (getter == null || !(wanted == getter.getReturnType() || wanted.isAssignableFrom(getter.getReturnType()))) {
                return NONE;
            }
            Object value = invoke(getter, object);
            return value == null ? UNSET : value;
        }

        void set(Object value) {
            invoke(setter, object, value);
        }
    }

    private static Method method(Class<?> owner, String name, Class<?> parameter) {
        String cacheKey = owner.getName() + "#" + name + "(" + (parameter == null ? "" : parameter.getName()) + ")";
        return METHODS.computeIfAbsent(cacheKey, k -> {
            try {
                Method method = parameter == null ? owner.getMethod(name) : owner.getMethod(name, parameter);
                return Modifier.isStatic(method.getModifiers()) ? Optional.empty() : Optional.of(method);
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            LOG.log(System.Logger.Level.DEBUG, "Cannot call " + method, e);
            return null;
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        }
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
