package com.zapthemes.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.swt.widgets.Widget;

/**
 * Infers the {@code THEME_CONTROL_TYPE} of a widget that was not created through a proxy (native
 * dialog children, third-party code, user subclasses of original SWT classes): the simple name of
 * the nearest named superclass from SWT, Nebula or ZapThemes.
 */
final class ControlTypeResolver {

    private static final List<String> KNOWN_PACKAGES = List.of("org.eclipse.swt.", "org.eclipse.nebula.", "com.zapthemes.");

    private final Map<Class<?>, String> cache = new ConcurrentHashMap<>();

    String resolve(Widget widget) {
        return cache.computeIfAbsent(widget.getClass(), ControlTypeResolver::nearestKnownType);
    }

    private static String nearestKnownType(Class<?> type) {
        for (Class<?> k = type; k != null; k = k.getSuperclass()) {
            if (!k.isAnonymousClass() && !k.isLocalClass() && isKnown(k.getPackageName() + ".")) {
                return k.getSimpleName();
            }
        }
        return type.getSimpleName();
    }

    private static boolean isKnown(String packagePrefix) {
        return KNOWN_PACKAGES.stream().anyMatch(packagePrefix::startsWith);
    }
}
