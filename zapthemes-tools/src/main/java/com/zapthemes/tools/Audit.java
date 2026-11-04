package com.zapthemes.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

/**
 * Finds {@code new <SWT widget>(...)} and {@code <SWT widget>::new} expressions that bypass the
 * proxies. A line containing {@code zapthemes:allow-raw} is exempt.
 *
 * <pre>
 * Audit --source-root src     # prints raw widget creations per widget class; exit 1 if any
 * </pre>
 */
public final class Audit {

    public static final String ALLOW_MARKER = "zapthemes:allow-raw";

    public record Violation(String file, int line, String widgetClass) {
    }

    private final Set<String> widgetClasses;
    private final TypeNames typeNames;
    private final JavaParser parser = Codemod.newParser();

    Audit(Set<String> widgetClasses, TypeNames typeNames) {
        this.widgetClasses = widgetClasses;
        this.typeNames = typeNames;
    }

    public List<Violation> scan(String source, String file) {
        CompilationUnit unit = Codemod.parse(parser, source);
        String[] lines = source.split("\\R", -1);
        List<Violation> violations = new ArrayList<>();
        for (ClassOrInterfaceType type : Codemod.instantiatedTypes(unit)) {
            typeNames.resolve(unit, type, widgetClasses)
                    .filter(widgetClasses::contains)
                    .ifPresent(widgetClass -> {
                        int line = type.getBegin().map(p -> p.line).orElse(-1);
                        boolean allowed = line > 0 && line <= lines.length && lines[line - 1].contains(ALLOW_MARKER);
                        if (!allowed) {
                            violations.add(new Violation(file, line, widgetClass));
                        }
                    });
        }
        return violations;
    }

    /**
     * {@code Audit [--list] --source-root <dir> [--source-root <dir> ...]}: prints a count per widget
     * class (and, with {@code --list}, every violation as {@code file:line}); exits 1 if any.
     */
    public static void main(String[] args) throws Exception {
        List<Path> roots = new ArrayList<>();
        boolean list = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source-root" -> roots.add(Path.of(args[++i]));
                case "--list" -> list = true;
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("Usage: Audit [--list] --source-root <dir> [--source-root <dir> ...]");
        }
        List<Violation> violations = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            Audit audit = new Audit(WidgetCatalog.themeableWidgets(), TypeNames.index(root));
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).sorted().toList()) {
                    violations.addAll(audit.scan(Files.readString(file), root.relativize(file).toString()));
                }
            }
        }
        Map<String, Integer> byClass = new TreeMap<>();
        Map<String, Integer> byFile = new TreeMap<>();
        violations.forEach(v -> {
            byClass.merge(v.widgetClass(), 1, Integer::sum);
            byFile.merge(v.file(), 1, Integer::sum);
        });
        if (list) {
            violations.forEach(v -> System.out.printf("%s:%d: new %s (use the com.zapthemes proxy, or mark the line %s)%n",
                    v.file(), v.line(), v.widgetClass(), ALLOW_MARKER));
        }
        byClass.forEach((type, count) -> System.out.printf("%4d  %s%n", count, type));
        System.out.printf("%d raw widget creations in %d files%n", violations.size(), byFile.size());
        if (!violations.isEmpty()) {
            System.exit(1);
        }
    }
}
