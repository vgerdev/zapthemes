package com.zapthemes.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

/**
 * Rewrites widget instantiations in forked JFace/Nebula source to use the proxies:
 * {@code new Button(p, s)} → {@code new com.zapthemes.swt.widgets.Button(p, s)}, including
 * anonymous subclasses and constructor references ({@code Button::new}). Only the {@code new} expression changes — imports, field, parameter and
 * return types stay as upstream, so the fork's public API is unchanged. The type name is replaced
 * in place by its source range, so everything else stays byte-for-byte identical to upstream.
 *
 * <pre>
 * Codemod --source-root src --proxies proxies.txt --out build/codemod [relative/File.java ...]
 * </pre>
 */
public final class Codemod {

    /** One rewritten {@code new} expression. */
    public record Change(int line, String from, String to) {
    }

    public record Result(String source, List<Change> changes) {
    }

    private final Map<String, String> proxies;
    private final TypeNames typeNames;
    private final JavaParser parser;

    Codemod(Map<String, String> proxies, TypeNames typeNames) {
        this.proxies = proxies;
        this.typeNames = typeNames;
        this.parser = newParser();
    }

    static JavaParser newParser() {
        return new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));
    }

    public Result rewrite(String source) {
        CompilationUnit unit = parse(parser, source);
        Set<String> candidates = new HashSet<>(proxies.keySet());
        List<Change> changes = new ArrayList<>();
        List<Edit> edits = new ArrayList<>();
        for (ClassOrInterfaceType type : instantiatedTypes(unit)) {
            typeNames.resolve(unit, type, candidates)
                    .filter(proxies::containsKey)
                    .ifPresent(original -> {
                        String proxy = proxies.get(original);
                        Range range = type.getRange()
                                .orElseThrow(() -> new IllegalStateException("No source range for " + type));
                        changes.add(new Change(range.begin.line, type.asString(), proxy));
                        edits.add(new Edit(range, proxy));
                    });
        }
        return new Result(apply(source, edits), changes);
    }

    /** The types in {@code new T(...)} and {@code T::new} expressions. */
    static List<ClassOrInterfaceType> instantiatedTypes(CompilationUnit unit) {
        List<ClassOrInterfaceType> types = new ArrayList<>();
        unit.findAll(ObjectCreationExpr.class).forEach(creation -> types.add(creation.getType()));
        for (MethodReferenceExpr reference : unit.findAll(MethodReferenceExpr.class)) {
            if (reference.getIdentifier().equals("new")
                    && reference.getScope() instanceof TypeExpr typeExpr
                    && typeExpr.getType() instanceof ClassOrInterfaceType type) {
                types.add(type);
            }
        }
        return types;
    }

    private record Edit(Range range, String replacement) {
    }

    /** Applies the edits back to front so earlier offsets stay valid. */
    private static String apply(String source, List<Edit> edits) {
        int[] lineStarts = lineStarts(source);
        StringBuilder out = new StringBuilder(source);
        edits.stream()
                .sorted((a, b) -> b.range.begin.compareTo(a.range.begin))
                .forEach(edit -> {
                    int start = lineStarts[edit.range.begin.line - 1] + edit.range.begin.column - 1;
                    int end = lineStarts[edit.range.end.line - 1] + edit.range.end.column;
                    out.replace(start, end, edit.replacement);
                });
        return out.toString();
    }

    private static int[] lineStarts(String source) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\n' || (c == '\r' && (i + 1 == source.length() || source.charAt(i + 1) != '\n'))) {
                starts.add(i + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    static CompilationUnit parse(JavaParser parser, String source) {
        ParseResult<CompilationUnit> result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new IllegalArgumentException("Cannot parse source: " + result.getProblems());
        }
        return result.getResult().get();
    }

    public static void main(String[] args) throws Exception {
        Path sourceRoot = null;
        Path proxyList = null;
        Path out = null;
        List<String> files = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source-root" -> sourceRoot = Path.of(args[++i]);
                case "--proxies" -> proxyList = Path.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                default -> files.add(args[i]);
            }
        }
        if (sourceRoot == null || proxyList == null || out == null || files.isEmpty()) {
            throw new IllegalArgumentException("Usage: Codemod --source-root <dir> --proxies <file> --out <dir> <files...>");
        }
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalStateException("Source root not found (run scripts/fetch-upstream.sh): " + sourceRoot);
        }
        Codemod codemod = new Codemod(ProxyNames.mapping(proxyList), TypeNames.index(sourceRoot));
        Audit audit = new Audit(WidgetCatalog.swtWidgets(), TypeNames.index(sourceRoot));
        for (String file : files) {
            Result result = codemod.rewrite(Files.readString(sourceRoot.resolve(file)));
            Path target = out.resolve(file);
            Files.createDirectories(target.getParent());
            Files.writeString(target, result.source());
            List<Audit.Violation> remaining = audit.scan(result.source(), file);
            System.out.printf("%s: %d rewritten, %d raw widget creations left (no proxy yet)%n",
                    file, result.changes().size(), remaining.size());
            result.changes().forEach(c -> System.out.printf("    line %d: new %s -> new %s%n", c.line(), c.from(), c.to()));
            remaining.forEach(v -> System.out.printf("    left  %d: new %s%n", v.line(), v.widgetClass()));
        }
    }
}
