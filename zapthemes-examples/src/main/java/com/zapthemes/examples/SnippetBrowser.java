package com.zapthemes.examples;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.widgets.WidgetFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.swt.widgets.Label;

import com.zapthemes.themes.SystemPropertyTheme;

/**
 * The JFace snippets ({@code org.eclipse.jface.snippets}), grouped by package. Each snippet is a
 * program of its own, with its own {@code Display}, so <b>Run</b> starts it in a new JVM on the same
 * class path, in the theme {@code theme} names at that moment ({@code -Dzapthemes.theme}).
 */
public final class SnippetBrowser extends com.zapthemes.swt.widgets.Composite {

    static final String PACKAGE = "org.eclipse.jface.snippets";

    private final Supplier<String> theme;
    private final TreeViewer tree;
    private final Label status;

    public SnippetBrowser(Composite parent, Supplier<String> theme) {
        super(parent, SWT.NONE);
        this.theme = theme;
        GridLayoutFactory.fillDefaults().numColumns(2).margins(8, 8).applyTo(this);

        Map<String, List<String>> snippets = snippets();
        tree = new TreeViewer(this, SWT.BORDER | SWT.SINGLE);
        tree.setContentProvider(new ITreeContentProvider() {
            @Override
            public Object[] getElements(Object input) {
                return snippets.keySet().toArray();
            }

            @Override
            public Object[] getChildren(Object element) {
                return snippets.getOrDefault(element, List.of()).toArray();
            }

            @Override
            public Object getParent(Object element) {
                return null;
            }

            @Override
            public boolean hasChildren(Object element) {
                return snippets.containsKey(element);
            }
        });
        tree.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                String name = element.toString();
                return snippets.containsKey(name) ? name : name.substring(name.lastIndexOf('.') + 1);
            }
        });
        tree.setInput(snippets);
        tree.expandAll();
        tree.addDoubleClickListener(e -> runSelected());
        GridDataFactory.fillDefaults().grab(true, true).span(2, 1).applyTo(tree.getControl());

        Button run = WidgetFactory.button(SWT.PUSH).text("&Run").onSelect(e -> runSelected()).create(this);
        status = WidgetFactory.label(SWT.NONE).text(count(snippets) + " snippets; double-click or Run starts one in the current theme.")
                .create(this);
        GridDataFactory.fillDefaults().grab(true, false).align(SWT.FILL, SWT.CENTER).applyTo(status);
        getShell().setDefaultButton(run);
    }

    /** The number of snippets listed. */
    public int snippetCount() {
        return count(snippets());
    }

    private void runSelected() {
        Object element = tree.getStructuredSelection().getFirstElement();
        if (element == null || !element.toString().contains(".")) {
            return;
        }
        String snippet = element.toString();
        try {
            run(snippet, theme.get(), false);
            status.setText("Started " + snippet.substring(snippet.lastIndexOf('.') + 1) + " in " + theme.get() + ".");
        } catch (IOException e) {
            status.setText("Could not start " + snippet + ": " + e.getMessage());
        }
    }

    /**
     * Starts {@code snippet} in a new JVM on this one's class path, themed with {@code themeId}; with
     * {@code capture} its output (stdout and stderr) is read from the process instead of shown.
     */
    static Process run(String snippet, String themeId, boolean capture, String... jvmOptions) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(List.of(jvmOptions));
        command.add("-D" + SystemPropertyTheme.PROPERTY + "=" + themeId);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(snippet);
        ProcessBuilder builder = new ProcessBuilder(command);
        return (capture ? builder.redirectErrorStream(true) : builder.inheritIO()).start();
    }

    /** Snippet class names by package, sorted. */
    static Map<String, List<String>> snippets() {
        Map<String, List<String>> byPackage = new TreeMap<>();
        URL root = SnippetBrowser.class.getClassLoader().getResource(PACKAGE.replace('.', '/'));
        if (root == null) {
            return byPackage;
        }
        try (Classes classes = Classes.at(root.toURI())) {
            try (Stream<Path> files = Files.walk(classes.root())) {
                files.map(p -> classes.root().relativize(p).toString().replace('\\', '/'))
                        .filter(p -> p.endsWith(".class") && !p.contains("$") && p.substring(p.lastIndexOf('/') + 1).startsWith("Snippet"))
                        .sorted()
                        .forEach(p -> {
                            String name = PACKAGE + "." + p.substring(0, p.length() - ".class".length()).replace('/', '.');
                            String pkg = name.substring(PACKAGE.length() + 1, name.lastIndexOf('.'));
                            byPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add(name);
                        });
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        return byPackage;
    }

    private static int count(Map<String, List<String>> snippets) {
        return snippets.values().stream().mapToInt(List::size).sum();
    }

    /** The snippets package in a classes directory or in a jar. */
    private record Classes(Path root, FileSystem jar) implements AutoCloseable {
        static Classes at(URI uri) throws IOException {
            if (uri.getScheme().equals("jar")) {
                FileSystem jar = FileSystems.newFileSystem(uri, Map.of());
                return new Classes(Path.of(uri), jar);
            }
            return new Classes(Path.of(uri), null);
        }

        @Override
        public void close() throws IOException {
            if (jar != null) {
                jar.close();
            }
        }
    }
}
