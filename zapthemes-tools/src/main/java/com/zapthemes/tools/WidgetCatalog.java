package com.zapthemes.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.eclipse.swt.widgets.Widget;

/**
 * The SWT widget classes in the SWT jar on the classpath. Classes are loaded without being
 * initialized, so no native library is touched.
 */
public final class WidgetCatalog {

    /** Widgets with no appearance a theme could set; they get no proxy and the audit ignores them. */
    public static final Set<String> NOT_THEMEABLE = Set.of(
            "org.eclipse.swt.dnd.DragSource",
            "org.eclipse.swt.dnd.DropTarget",
            "org.eclipse.swt.widgets.Caret",
            "org.eclipse.swt.widgets.IME",
            "org.eclipse.swt.widgets.Tracker",
            "org.eclipse.swt.widgets.Tray",
            "org.eclipse.swt.widgets.TrayItem",
            "org.eclipse.swt.widgets.TaskBar",
            "org.eclipse.swt.widgets.TaskItem",
            "org.eclipse.swt.widgets.ScrollBar");

    private WidgetCatalog() {
    }

    /** Every public {@link Widget} subclass in SWT, including abstract and non-themeable ones. */
    public static SortedSet<String> swtWidgets() {
        Path jar;
        try {
            jar = Path.of(Widget.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        SortedSet<String> widgets = new TreeSet<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            file.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith("org/eclipse/swt/") && name.endsWith(".class"))
                    .filter(name -> !name.contains("$") && !name.contains("/internal/"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .forEach(className -> {
                        Class<?> type = load(className);
                        if (type != null && Widget.class.isAssignableFrom(type) && Modifier.isPublic(type.getModifiers())) {
                            widgets.add(className);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Collections.unmodifiableSortedSet(widgets);
    }

    /** SWT widgets a theme can style; creating one directly (not via a proxy) is an audit violation. */
    public static SortedSet<String> themeableWidgets() {
        SortedSet<String> widgets = new TreeSet<>(swtWidgets());
        widgets.removeAll(NOT_THEMEABLE);
        return Collections.unmodifiableSortedSet(widgets);
    }

    /**
     * The widgets of {@code packageName} that get a proxy: themeable, public, concrete, non-final,
     * not deprecated, and with at least one public constructor. Works for SWT and for any library on
     * the classpath (Nebula), found through the class loader.
     */
    public static SortedSet<String> proxiable(String packageName) {
        SortedSet<String> widgets = new TreeSet<>();
        for (String className : classesIn(packageName)) {
            if (NOT_THEMEABLE.contains(className)) {
                continue;
            }
            Class<?> type = load(className);
            if (type == null || !Widget.class.isAssignableFrom(type)) {
                continue;
            }
            int modifiers = type.getModifiers();
            if (Modifier.isPublic(modifiers) && !Modifier.isAbstract(modifiers) && !Modifier.isFinal(modifiers)
                    && !type.isAnnotationPresent(Deprecated.class) && type.getConstructors().length > 0) {
                widgets.add(className);
            }
        }
        return Collections.unmodifiableSortedSet(widgets);
    }

    /** The top-level class names of {@code packageName} (not sub-packages) in jars and directories. */
    static SortedSet<String> classesIn(String packageName) {
        String path = packageName.replace('.', '/');
        SortedSet<String> classes = new TreeSet<>();
        try {
            Enumeration<URL> roots = WidgetCatalog.class.getClassLoader().getResources(path);
            while (roots.hasMoreElements()) {
                URL root = roots.nextElement();
                if (root.getProtocol().equals("jar")) {
                    JarURLConnection connection = (JarURLConnection) root.openConnection();
                    connection.setUseCaches(false);
                    try (JarFile jar = connection.getJarFile()) {
                        jar.stream().map(entry -> entry.getName())
                                .filter(name -> name.startsWith(path + "/") && name.indexOf('/', path.length() + 1) < 0)
                                .forEach(name -> addClass(classes, packageName, name.substring(path.length() + 1)));
                    }
                } else if (root.getProtocol().equals("file")) {
                    try (Stream<Path> files = Files.list(Path.of(root.toURI()))) {
                        files.forEach(file -> addClass(classes, packageName, file.getFileName().toString()));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        return classes;
    }

    private static void addClass(SortedSet<String> classes, String packageName, String fileName) {
        if (fileName.endsWith(".class") && !fileName.contains("$")) {
            classes.add(packageName + "." + fileName.substring(0, fileName.length() - ".class".length()));
        }
    }

    private static Class<?> load(String className) {
        try {
            return Class.forName(className, false, WidgetCatalog.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            // Optional platform parts (e.g. OLE/OpenGL dependencies); not widgets we can proxy.
            return null;
        }
    }
}
