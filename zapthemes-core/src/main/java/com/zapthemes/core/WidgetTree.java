package com.zapthemes.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Widget;

/**
 * Depth-first walk over a control tree, including items: any widget with a public no-argument
 * {@code getItems()} or {@code getColumns()} returning widgets has those visited too, recursively
 * (TreeItem children, Grid and Gallery items, PShelf items, ...). This covers SWT and libraries
 * such as Nebula without knowing their classes.
 */
final class WidgetTree {

    private static final List<String> ITEM_METHODS = List.of("getColumns", "getItems");
    private static final Map<Class<?>, List<Method>> METHODS = new ConcurrentHashMap<>();

    private WidgetTree() {
    }

    static void walk(Widget widget, Consumer<Widget> visitor) {
        if (widget.isDisposed()) {
            return;
        }
        visitor.accept(widget);
        for (Widget item : items(widget)) {
            walk(item, visitor);
        }
        if (widget instanceof Composite composite) {
            for (var child : composite.getChildren()) {
                walk(child, visitor);
            }
        }
    }

    private static List<Widget> items(Widget widget) {
        List<Widget> items = new ArrayList<>();
        for (Method method : METHODS.computeIfAbsent(widget.getClass(), WidgetTree::itemMethods)) {
            try {
                for (Object item : (Object[]) method.invoke(widget)) {
                    if (item instanceof Widget child && child != widget) {
                        items.add(child);
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException e) {
                // Not an item accessor after all (or not accessible); nothing to walk.
            }
        }
        return items;
    }

    private static List<Method> itemMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>();
        for (String name : ITEM_METHODS) {
            try {
                Method method = type.getMethod(name);
                if (method.getReturnType().isArray()
                        && Widget.class.isAssignableFrom(method.getReturnType().getComponentType())
                        && !Modifier.isStatic(method.getModifiers())) {
                    methods.add(method);
                }
            } catch (NoSuchMethodException e) {
                // No items of that kind.
            }
        }
        return methods;
    }
}
