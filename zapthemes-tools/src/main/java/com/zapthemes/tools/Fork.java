package com.zapthemes.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Generates a fork from pristine upstream sources:
 * <ol>
 *   <li>every {@code .java} file under the source root is copied with the {@link Codemod} applied
 *       and line endings normalized to LF;</li>
 *   <li>the {@code *.patch} files of the patches directory are applied in name order with
 *       {@code git apply} (unified diffs against the codemodded output, paths {@code a/<relative>});</li>
 *   <li>the result is {@link Audit audited}: fails if a themeable widget is still created without
 *       its proxy, unless that creation is listed in an allow-list file
 *       ({@code relative/path/File.java:line} per line).</li>
 * </ol>
 *
 * <pre>
 * Fork --source-root third_party/.../src --proxies proxies.txt [--proxies more.txt ...]
 *      --out build/generated/fork --report build/reports/fork.txt [--allow allow.txt] [--patches dir]
 * </pre>
 */
public final class Fork {

    private Fork() {
    }

    public static void main(String[] args) throws Exception {
        Path sourceRoot = null;
        List<Path> proxyLists = new ArrayList<>();
        Path out = null;
        Path report = null;
        Path allowList = null;
        Path patches = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--source-root" -> sourceRoot = Path.of(args[++i]);
                case "--proxies" -> proxyLists.add(Path.of(args[++i]));
                case "--out" -> out = Path.of(args[++i]);
                case "--report" -> report = Path.of(args[++i]);
                case "--allow" -> allowList = Path.of(args[++i]);
                case "--patches" -> patches = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (sourceRoot == null || proxyLists.isEmpty() || out == null || report == null) {
            throw new IllegalArgumentException(
                    "Usage: Fork --source-root <dir> --proxies <file> --out <dir> --report <file> [--allow <file>]");
        }
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalStateException("Upstream sources not found (run scripts/fetch-upstream.sh): " + sourceRoot);
        }
        List<String> allowed = allowList != null && Files.exists(allowList)
                ? Files.readAllLines(allowList).stream().map(String::strip)
                        .filter(l -> !l.isEmpty() && !l.startsWith("#")).toList()
                : List.of();

        Map<String, String> proxies = new LinkedHashMap<>();
        for (Path proxyList : proxyLists) {
            proxies.putAll(ProxyNames.mapping(proxyList));
        }
        TypeNames typeNames = TypeNames.index(sourceRoot);
        Codemod codemod = new Codemod(proxies, typeNames);
        Audit audit = new Audit(WidgetCatalog.themeableWidgets(), typeNames);

        clean(out);
        List<String> lines = new ArrayList<>();
        List<Audit.Violation> violations = new ArrayList<>();
        int files = 0;
        int rewritten = 0;
        List<Path> sources;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            sources = walk.filter(f -> f.toString().endsWith(".java")).sorted().toList();
        }
        for (Path source : sources) {
            String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
            Codemod.Result result = codemod.rewrite(Files.readString(source));
            Path target = out.resolve(relative);
            Files.createDirectories(target.getParent());
            // LF everywhere, so patches apply the same whatever git's checkout line endings were.
            Files.writeString(target, result.source().replace("\r\n", "\n"));
            files++;
            rewritten += result.changes().size();
            for (Codemod.Change change : result.changes()) {
                lines.add("rewrote " + relative + ":" + change.line() + " new " + change.from() + " -> " + change.to());
            }
        }
        int patched = applyPatches(patches, out, lines);
        for (Path source : sources) {
            String relative = sourceRoot.relativize(source).toString().replace('\\', '/');
            for (Audit.Violation violation : audit.scan(Files.readString(out.resolve(relative)), relative)) {
                String key = relative + ":" + violation.line();
                if (allowed.contains(key)) {
                    lines.add("allowed " + key + " new " + violation.widgetClass());
                } else {
                    violations.add(violation);
                }
            }
        }
        violations.forEach(v -> lines.add("RAW     " + v.file() + ":" + v.line() + " new " + v.widgetClass()));
        String summary = "%d files, %d widget creations rewritten to proxies, %d patches, %d raw creations left"
                .formatted(files, rewritten, patched, violations.size());
        lines.add(summary);
        Files.createDirectories(report.getParent());
        Files.write(report, lines);
        System.out.println(summary + " (report: " + report + ")");
        if (!violations.isEmpty()) {
            violations.forEach(v -> System.out.println("  " + v.file() + ":" + v.line() + " new " + v.widgetClass()));
            System.exit(1);
        }
    }

    /** Applies every {@code *.patch} in {@code patches} (if it exists) to {@code out}; returns the count. */
    private static int applyPatches(Path patches, Path out, List<String> lines) throws IOException, InterruptedException {
        if (patches == null || !Files.isDirectory(patches)) {
            return 0;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(patches)) {
            files = list.filter(p -> p.toString().endsWith(".patch")).sorted().toList();
        }
        for (Path patch : files) {
            ProcessBuilder builder = new ProcessBuilder("git", "apply", "--verbose", "--whitespace=nowarn",
                    patch.toAbsolutePath().toString())
                    .directory(out.toFile())
                    .redirectErrorStream(true);
            // The output usually sits inside the project's own git work tree, where `git apply` would
            // resolve paths against that repository and silently skip ours. Stop repository discovery.
            builder.environment().put("GIT_CEILING_DIRECTORIES", out.toAbsolutePath().getParent().toString());
            Process git = builder.start();
            String output = new String(git.getInputStream().readAllBytes());
            if (git.waitFor() != 0 || output.contains("Skipped patch") || !output.contains("Applied patch")) {
                throw new IllegalStateException("Patch " + patch.getFileName()
                        + " does not apply to the codemodded upstream (refresh it after an upstream bump):\n" + output);
            }
            lines.add("patched " + patch.getFileName());
        }
        return files.size();
    }

    private static void clean(Path dir) throws IOException {
        if (Files.isDirectory(dir)) {
            try (Stream<Path> paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(dir);
    }
}
