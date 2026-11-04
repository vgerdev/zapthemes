package com.zapthemes.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.TreeSet;
import java.util.SortedSet;
import java.util.jar.JarFile;

import org.eclipse.swt.widgets.Widget;

/**
 * Every public {@link Widget} subclass in the SWT jar on the classpath. Classes are loaded without
 * being initialized, so no native library is touched.
 */
public final class WidgetCatalog {

    private WidgetCatalog() {
    }

    public static SortedSet<String> swtWidgets() {
        Path jar;
        try {
            jar = Path.of(Widget.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        ClassLoader loader = WidgetCatalog.class.getClassLoader();
        SortedSet<String> widgets = new TreeSet<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            file.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith("org/eclipse/swt/") && name.endsWith(".class"))
                    .filter(name -> !name.contains("$") && !name.contains("/internal/"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .forEach(className -> {
                        try {
                            Class<?> type = Class.forName(className, false, loader);
                            if (Widget.class.isAssignableFrom(type) && Modifier.isPublic(type.getModifiers())) {
                                widgets.add(className);
                            }
                        } catch (ClassNotFoundException | LinkageError e) {
                            // Optional platform parts (e.g. OLE/OpenGL dependencies); not widgets we can proxy.
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Collections.unmodifiableSortedSet(widgets);
    }
}
