package com.zapthemes.core.style;

import java.lang.System.Logger.Level;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Widget;

/**
 * Styles widgets of libraries that expose their look through setters, such as Nebula: theme key
 * {@code fooColor} calls {@code setFooColor(...)}, with the value converted to the setter's parameter
 * type ({@link Color}, {@link Font}, {@code int}, {@code boolean} or {@link String}). A dotted key
 * reaches into a part: {@code itemRenderer.selectionBackgroundColor} calls
 * {@code getItemRenderer().setSelectionBackgroundColor(...)}.
 *
 * <p>The first time a key is applied, the current value (from {@code getFoo()}/{@code isFoo()}) is
 * remembered; when a later theme no longer sets the key it is restored. Keys without a matching
 * setter are ignored. {@code background}, {@code foreground} and {@code font} of controls are left
 * to the {@link DefaultStyler}.
 */
public class PropertyStyler extends DefaultStyler {

    private static final System.Logger LOG = System.getLogger(PropertyStyler.class.getName());
    private static final String ORIGINALS = "zapthemes.propertyOriginals";
    private static final String APPLIED = "zapthemes.propertyApplied";
    private static final Object NONE = new Object();
    private static final Set<String> RESERVED = Set.of(StyleResolver.INHERIT, ResolvedStyle.STATES, BACKGROUND_MODE);
    private static final Set<String> DEFAULT_KEYS = Set.of(BACKGROUND, FOREGROUND, FONT);
    private static final List<Class<?>> VALUE_TYPES = List.of(Color.class, Font.class, int.class, boolean.class, String.class);
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public void apply(Widget widget, ResolvedStyle style, ThemeContext context) {
        super.apply(widget, style, context);
        Map<String, Object> originals = widget.getData(ORIGINALS) instanceof Map<?, ?> m
                ? (Map<String, Object>) m : new HashMap<>();
        Set<String> previous = widget.getData(APPLIED) instanceof Set<?> s ? (Set<String>) s : Set.of();
        Set<String> applied = new HashSet<>();
        String owner = widget.getData(com.zapthemes.core.ThemeKeys.THEME_CONTROL_TYPE) instanceof String t ? t : null;

        for (String key : style.keys()) {
            if (RESERVED.contains(key) || (widget instanceof Control && DEFAULT_KEYS.contains(key))) {
                continue;
            }
            Target target = target(widget, key);
            if (target == null) {
                continue;
            }
            Object value;
            try {
                value = value(style, key, target.setter.getParameterTypes()[0], owner, context);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Theme value of \"" + key + "\" does not fit " + target.setter, e);
                continue;
            }
            if (value == null) {
                continue;
            }
            if (!originals.containsKey(key)) {
                originals.put(key, target.current());
            }
            target.set(value);
            applied.add(key);
        }
        for (String key : previous) {
            Object original = originals.get(key);
            if (!applied.contains(key) && original != null && original != NONE) {
                Target target = target(widget, key);
                if (target != null) {
                    target.set(original);
                }
            }
        }
        widget.setData(ORIGINALS, originals);
        widget.setData(APPLIED, applied);
        if (widget instanceof Control control) {
            control.redraw();
        }
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

    /** The object and setter a (possibly dotted) key addresses, or {@code null}. */
    private static Target target(Object root, String key) {
        Object object = root;
        String[] path = key.split("\\.");
        for (int i = 0; i < path.length - 1; i++) {
            Method getter = method(object.getClass(), "get" + capitalize(path[i]), null);
            if (getter == null) {
                return null;
            }
            object = invoke(getter, object);
            if (object == null) {
                return null;
            }
        }
        String property = capitalize(path[path.length - 1]);
        for (Class<?> type : VALUE_TYPES) {
            Method setter = method(object.getClass(), "set" + property, type);
            if (setter != null) {
                Method getter = method(object.getClass(), (type == boolean.class ? "is" : "get") + property, null);
                return new Target(object, setter, getter);
            }
        }
        return null;
    }

    private record Target(Object object, Method setter, Method getter) {
        Object current() {
            Class<?> wanted = setter.getParameterTypes()[0];
            if (getter == null || !(wanted == getter.getReturnType() || wanted.isAssignableFrom(getter.getReturnType()))) {
                return NONE;
            }
            Object value = invoke(getter, object);
            return value == null ? NONE : value;
        }

        void set(Object value) {
            invoke(setter, object, value);
        }
    }

    /** A public method {@code name} taking {@code parameter} (or nothing, if {@code null}). */
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
            // A public method of a non-public class: callable through its public declaration only.
            LOG.log(Level.DEBUG, "Cannot call " + method, e);
            return null;
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        }
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
