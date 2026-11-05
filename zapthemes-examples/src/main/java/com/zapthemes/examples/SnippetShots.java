package com.zapthemes.examples;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Runs every JFace snippet in its own JVM in one theme ({@link SnippetShot}): each must open, have
 * every control tagged, and close when asked. Saves {@code <dir>/<theme>/<Snippet>.png}.
 * Arguments: {@code <dir> <theme> [name filter]}. A snippet whose JVM keeps running after its window
 * closed (a non-daemon thread of its own) is stopped and still counts.
 */
public final class SnippetShots {

    private static final long TIMEOUT = 30;

    /** Snippets that fail without a theme too, and why. */
    private static final Map<String, String> BROKEN_UPSTREAM = brokenUpstream();

    private static Map<String, String> brokenUpstream() {
        Map<String, String> broken = new java.util.HashMap<>();
        broken.put("Snippet060DialogWithURLImageButtons", "its OK image, http://www.eclipse.org/home/images/enterprise.gif, no longer exists");
        if (org.eclipse.swt.SWT.getPlatform().equals("gtk")) {
            broken.put("Snippet035TableCursorCellHighlighter", "on GTK its TableCursor paints before a cell is selected and reads getSelectedCells()[0]");
        }
        return broken;
    }

    private SnippetShots() {
    }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]).resolve(args[1]);
        String theme = args[1];
        Files.createDirectories(dir);
        List<String> problems = new ArrayList<>();
        int ok = 0;
        for (List<String> group : SnippetBrowser.snippets().values()) {
            for (String snippet : group) {
                String name = snippet.substring(snippet.lastIndexOf('.') + 1);
                if (args.length > 2 && !name.contains(args[2])) {
                    continue;
                }
                if (BROKEN_UPSTREAM.containsKey(name)) {
                    System.out.println(name + ": skipped, broken upstream: " + BROKEN_UPSTREAM.get(name));
                    continue;
                }
                Process process = SnippetBrowser.run(snippet, theme, true,
                        "-D" + SnippetShot.PROPERTY + "=" + dir.resolve(name + ".png"));
                List<String> output = new ArrayList<>();
                Thread reader = Thread.ofVirtual().start(() -> {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                        in.lines().forEach(output::add);
                    } catch (Exception e) {
                        output.add("(output lost: " + e + ")");
                    }
                });
                boolean exited = process.waitFor(TIMEOUT, TimeUnit.SECONDS);
                if (!exited) {
                    process.destroyForcibly().waitFor();
                }
                reader.join();
                if (args.length > 2) {
                    // Filtered to a few snippets: show everything they printed.
                    output.forEach(l -> System.out.println("  | " + l));
                }
                String result = output.stream().filter(l -> l.startsWith("SNIPPET ")).findFirst().orElse(null);
                List<String> untagged = output.stream().filter(l -> l.startsWith("UNTAGGED ")).toList();
                if (result == null && !exited) {
                    problems.add(name + ": no window after " + TIMEOUT + " s" + tail(output));
                } else if (result == null) {
                    problems.add(name + ": exit " + process.exitValue() + " before any window" + tail(output));
                } else if (!untagged.isEmpty()) {
                    problems.add(name + ": " + result + " " + untagged);
                } else {
                    ok++;
                    System.out.println(name + ": " + result.substring("SNIPPET ".length()));
                }
            }
        }
        problems.forEach(p -> System.out.println("SNIPPET PROBLEM: " + p));
        System.out.println((problems.isEmpty() ? "SMOKE OK: " : "SMOKE FAILED: ") + ok + " snippets ran fully tagged in " + theme
                + (problems.isEmpty() ? "" : ", " + problems.size() + " with problems"));
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    /** The first exception line and the last few lines. */
    private static String tail(List<String> output) {
        List<String> last = new ArrayList<>(output.subList(Math.max(0, output.size() - 6), output.size()));
        output.stream().filter(l -> l.contains("Exception") && !l.startsWith("	at ")).findFirst()
                .filter(l -> !last.contains(l)).ifPresent(l -> last.addFirst(l));
        return last.isEmpty() ? "" : "\n    " + String.join("\n    ", last);
    }
}
