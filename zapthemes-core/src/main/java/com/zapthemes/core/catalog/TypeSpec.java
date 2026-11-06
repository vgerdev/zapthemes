package com.zapthemes.core.catalog;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;

/**
 * The properties a theme can set on one control type ({@code "Table"}, {@code "CDateTime"}, a
 * figure type such as {@code "Axis"}), as tools such as the theme editor list them.
 *
 * @param type the {@code THEME_CONTROL_TYPE}: the key of the type's rule in {@code controls}
 * @param group the library it belongs to, for grouping: {@code "SWT"}, {@code "Nebula"}, ...
 * @param base the properties every type of its kind has, besides its own
 * @param inherits a type whose rule applies first (a figure's superclass), or null
 * @param properties its own properties, besides the {@code base} ones
 * @param variants SWT style bits worth a rule of their own, written as in a rule key
 *        ({@code "CHECK"}, {@code "MULTI|WRAP"}); a theme may write others
 * @param states the owner-draw states it paints ({@code "hover"}, {@code "pressed"}, ...)
 * @param description one line; may be empty
 */
public record TypeSpec(String type, String group, Base base, String inherits, List<PropertySpec> properties,
        List<String> variants, List<String> states, String description) {

    /** What a kind of type has before its own properties. */
    public enum Base {
        /** {@code background}, {@code foreground}, {@code font}. */
        CONTROL(true, true, true, false),
        /** A control's, plus {@code backgroundMode}. */
        COMPOSITE(true, true, true, true),
        /** {@code background}, {@code foreground}, {@code font}, set on an item such as {@code TableItem}. */
        ITEM(true, true, true, false),
        /** {@code foreground} and {@code font} only ({@code CTabItem}). */
        TEXT_ITEM(false, true, true, false),
        /** A draw2d figure's {@code background}, {@code foreground}, {@code font}. */
        FIGURE(true, true, true, false),
        /** Nothing: the type is styled through its parent, or only through its own properties. */
        NONE(false, false, false, false);

        private final List<PropertySpec> properties;

        Base(boolean background, boolean foreground, boolean font, boolean backgroundMode) {
            List<PropertySpec> list = new ArrayList<>();
            if (background) {
                list.add(PropertySpec.color("background", "background", "Background"));
            }
            if (foreground) {
                list.add(PropertySpec.color("foreground", "foreground", "Text and foreground"));
            }
            if (font) {
                list.add(PropertySpec.font("font", "foreground", "Font"));
            }
            if (backgroundMode) {
                list.add(PropertySpec.choice("backgroundMode", null,
                        "How children inherit the background (Composite.setBackgroundMode)", "none", "default", "force"));
            }
            this.properties = List.copyOf(list);
        }

        public List<PropertySpec> properties() {
            return properties;
        }

        /**
         * The base of a widget class themed through its setters, as by
         * {@link com.zapthemes.core.style.PropertyStyler}: a composite's or a control's, or for
         * another widget, an item's when it has {@code setBackground}, {@code setForeground} and
         * {@code setFont}, a text item's when it has the latter two, else none.
         */
        public static Base bySetters(Class<?> widget) {
            if (org.eclipse.swt.widgets.Composite.class.isAssignableFrom(widget)) {
                return COMPOSITE;
            }
            if (org.eclipse.swt.widgets.Control.class.isAssignableFrom(widget)) {
                return CONTROL;
            }
            boolean fg = hasSetter(widget, "setForeground", Color.class);
            boolean font = hasSetter(widget, "setFont", Font.class);
            if (fg && font) {
                return hasSetter(widget, "setBackground", Color.class) ? ITEM : TEXT_ITEM;
            }
            return NONE;
        }

        private static boolean hasSetter(Class<?> type, String name, Class<?> parameter) {
            try {
                return !Modifier.isStatic(type.getMethod(name, parameter).getModifiers());
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    }

    public TypeSpec {
        Objects.requireNonNull(type, "type");
        group = group == null ? "" : group;
        base = base == null ? Base.NONE : base;
        properties = properties == null ? List.of() : List.copyOf(properties);
        variants = variants == null ? List.of() : List.copyOf(variants);
        states = states == null ? List.of() : List.copyOf(states);
        description = description == null ? "" : description;
    }

    /** A type with the base properties of its kind and nothing else yet. */
    public static TypeSpec of(String type, String group, Base base) {
        return new TypeSpec(type, group, base, null, null, null, null, null);
    }

    /**
     * A type whose look is set through setters, as {@link com.zapthemes.core.style.PropertyStyler}
     * and figure theming do it: one color or font property per public {@code setFooColor(Color)} or
     * {@code setTitleFont(Font)} of {@code cls} (key {@code fooColor}, {@code titleFont}), besides
     * the base ones. Use {@link #defaults} to give them default names and to add dotted keys.
     */
    public static TypeSpec reflect(String type, String group, Base base, Class<?> cls) {
        Set<String> baseKeys = Set.of("background", "foreground", "font", "backgroundColor", "foregroundColor");
        Map<String, PropertySpec> found = new LinkedHashMap<>();
        Method[] methods = cls.getMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                    || !method.getName().startsWith("set") || method.getName().length() < 4) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            PropertyKind kind = parameter == Color.class ? PropertyKind.COLOR : parameter == Font.class ? PropertyKind.FONT : null;
            String key = Character.toLowerCase(method.getName().charAt(3)) + method.getName().substring(4);
            if (kind != null && !baseKeys.contains(key)) {
                found.putIfAbsent(key, new PropertySpec(key, kind, null, null, null, ""));
            }
        }
        return new TypeSpec(type, group, base, null, List.copyOf(found.values()), null, null, null);
    }

    /**
     * Default names, as {@code "key=name"}: {@code "pickerTodayColor=accent"}. A key the type does
     * not have yet is added, typed by its name ({@link PropertyKind#ofKey}), else as a color (a font
     * if the name is a font-only base name).
     */
    public TypeSpec defaults(String... keyEqualsName) {
        Map<String, PropertySpec> byKey = new LinkedHashMap<>();
        properties.forEach(p -> byKey.put(p.key(), p));
        for (String pair : keyEqualsName) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException(type + ": write defaults as key=name, got: " + pair);
            }
            String key = pair.substring(0, eq);
            String name = pair.substring(eq + 1);
            PropertySpec existing = byKey.get(key);
            if (existing == null && base.properties().stream().anyMatch(p -> p.key().equals(key))) {
                existing = base.properties().stream().filter(p -> p.key().equals(key)).findFirst().orElseThrow();
            }
            if (existing == null) {
                // Not named as a color or font (textColorSelected, buttonBackgroundColorTop): a default name
                // is a color unless the name holds only a font.
                PropertyKind kind = PropertyKind.ofKey(key);
                if (kind == null) {
                    kind = BaseNames.get(name).filter(n -> !n.hasColor()).isPresent() ? PropertyKind.FONT : PropertyKind.COLOR;
                }
                existing = new PropertySpec(key, kind, null, null, null, "");
            }
            byKey.put(key, existing.withDefaultName(name));
        }
        return withProperties(List.copyOf(byKey.values()));
    }

    /** This type with {@code more} properties added; one with the same key replaces the old one. */
    public TypeSpec with(PropertySpec... more) {
        Map<String, PropertySpec> byKey = new LinkedHashMap<>();
        properties.forEach(p -> byKey.put(p.key(), p));
        for (PropertySpec p : more) {
            byKey.put(p.key(), p);
        }
        return withProperties(List.copyOf(byKey.values()));
    }

    public TypeSpec withProperties(List<PropertySpec> list) {
        return new TypeSpec(type, group, base, inherits, list, variants, states, description);
    }

    public TypeSpec inheriting(String parent) {
        return new TypeSpec(type, group, base, parent, properties, variants, states, description);
    }

    public TypeSpec withVariants(String... bits) {
        return new TypeSpec(type, group, base, inherits, properties, List.of(bits), states, description);
    }

    public TypeSpec withStates(String... names) {
        return new TypeSpec(type, group, base, inherits, properties, variants, List.of(names), description);
    }

    public TypeSpec described(String text) {
        return new TypeSpec(type, group, base, inherits, properties, variants, states, text);
    }

    /** The base properties and its own, its own winning. */
    public List<PropertySpec> allProperties() {
        Map<String, PropertySpec> byKey = new LinkedHashMap<>();
        base.properties().forEach(p -> byKey.put(p.key(), p));
        properties.forEach(p -> byKey.merge(p.key(), p, (old, mine) -> mine.mergedWith(old)));
        return List.copyOf(byKey.values());
    }

    /** {@code other} merged in: properties, variants and states united, this one's fields first. */
    TypeSpec mergedWith(TypeSpec other) {
        Map<String, PropertySpec> byKey = new LinkedHashMap<>();
        properties.forEach(p -> byKey.put(p.key(), p));
        other.properties.forEach(p -> byKey.merge(p.key(), p, PropertySpec::mergedWith));
        return new TypeSpec(type, group.isEmpty() ? other.group : group, base == Base.NONE ? other.base : base,
                inherits != null ? inherits : other.inherits, List.copyOf(byKey.values()), union(variants, other.variants),
                union(states, other.states), description.isEmpty() ? other.description : description);
    }

    private static List<String> union(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        b.stream().filter(x -> !all.contains(x)).forEach(all::add);
        return List.copyOf(all);
    }
}
