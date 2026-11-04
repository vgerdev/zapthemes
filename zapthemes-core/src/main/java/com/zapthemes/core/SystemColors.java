package com.zapthemes.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Display;

/**
 * Themed SWT system colors. The Nebula fork's codemod rewrites
 * {@code display.getSystemColor(SWT.COLOR_X)} to {@link #get(Device, int)}, so widgets that paint
 * with system colors (Grid renderers, CDateTime, Gallery, ...) follow the theme's
 * {@code registries.systemColors} block, keyed by the constant's name:
 *
 * <pre>{@code "registries": { "systemColors": { "COLOR_LIST_SELECTION": "$selection" } }}</pre>
 *
 * Constants the theme does not map return the real system color. A themed color is a new
 * {@link Color} per call (colors hold no OS resource since SWT 3.115): widget code keeps system
 * colors in fields, and some libraries dispose colors they hold, which must not affect others.
 */
public final class SystemColors {

    /** The registry block holding the mapping. */
    public static final String REGISTRY = "systemColors";

    private static final Map<Integer, String> NAMES = constantNames();

    private SystemColors() {
    }

    /**
     * The theme's color for system color {@code id}, or {@link Device#getSystemColor(int)}. Only a
     * {@link Display} is themed (code also asks a GC's device, which may be a printer).
     */
    public static Color get(Device device, int id) {
        String name = NAMES.get(id);
        if (name != null && device instanceof Display display) {
            Optional<RGBA> themed = ThemeManager.get(display).registry(REGISTRY).color(name);
            if (themed.isPresent()) {
                return new Color(display, themed.get());
            }
        }
        return device.getSystemColor(id);
    }

    /** Whether {@code name} is an SWT system color constant, e.g. {@code COLOR_LIST_SELECTION}. */
    public static boolean isConstant(String name) {
        return NAMES.containsValue(name);
    }

    private static Map<Integer, String> constantNames() {
        Map<Integer, String> names = new HashMap<>();
        for (Field field : SWT.class.getFields()) {
            if (field.getName().startsWith("COLOR_") && field.getType() == int.class && Modifier.isStatic(field.getModifiers())) {
                try {
                    names.putIfAbsent(field.getInt(null), field.getName());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return Map.copyOf(names);
    }
}
