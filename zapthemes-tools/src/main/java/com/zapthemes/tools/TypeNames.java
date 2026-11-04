package com.zapthemes.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

/**
 * Resolves a type name in a compilation unit to a fully qualified name without a full symbol
 * solver: qualified name → single-type import → type declared in the same file or package (never
 * an SWT widget) → on-demand imports. Accurate enough for {@code new X(...)} expressions, where the
 * only ambiguity that matters is a local class shadowing an SWT class (e.g. JFace's own
 * {@code org.eclipse.jface.window.ToolTip}).
 */
final class TypeNames {

    private final Map<String, Set<String>> typesByPackage;

    private TypeNames(Map<String, Set<String>> typesByPackage) {
        this.typesByPackage = typesByPackage;
    }

    static TypeNames empty() {
        return new TypeNames(Map.of());
    }

    /** Indexes the top-level type names of every {@code .java} file under {@code sourceRoot}. */
    static TypeNames index(Path sourceRoot) {
        Map<String, Set<String>> index = new HashMap<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(file -> file.toString().endsWith(".java")).forEach(file -> {
                Path relative = sourceRoot.relativize(file);
                String pkg = relative.getParent() == null ? "" : relative.getParent().toString().replace('\\', '.').replace('/', '.');
                String name = relative.getFileName().toString().replace(".java", "");
                index.computeIfAbsent(pkg, p -> new HashSet<>()).add(name);
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new TypeNames(index);
    }

    /**
     * @param candidates fully qualified names that on-demand imports may resolve to
     * @return the fully qualified name, or empty if the type is local or cannot be resolved
     */
    Optional<String> resolve(CompilationUnit unit, ClassOrInterfaceType type, Set<String> candidates) {
        if (type.getScope().isPresent()) {
            return Optional.of(type.getNameWithScope());
        }
        String name = type.getNameAsString();
        for (ImportDeclaration imp : unit.getImports()) {
            if (!imp.isAsterisk() && !imp.isStatic() && imp.getName().getIdentifier().equals(name)) {
                return Optional.of(imp.getNameAsString());
            }
        }
        boolean declaredInFile = unit.findAll(TypeDeclaration.class).stream()
                .anyMatch(declaration -> declaration.getNameAsString().equals(name));
        String pkg = unit.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        if (declaredInFile || typesByPackage.getOrDefault(pkg, Set.of()).contains(name)) {
            return Optional.empty();
        }
        for (ImportDeclaration imp : unit.getImports()) {
            if (imp.isAsterisk() && !imp.isStatic()) {
                String candidate = imp.getNameAsString() + "." + name;
                if (candidates.contains(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }
}
