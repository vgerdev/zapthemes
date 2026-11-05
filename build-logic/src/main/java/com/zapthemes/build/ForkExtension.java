package com.zapthemes.build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;

/**
 * {@code zapthemesFork { upstream("jface", sourceDir, resourceDirs...) }}.
 *
 * <p>For each upstream this registers:
 * <ul>
 *   <li>{@code fork<Name>Sources}: copies every {@code .java} file with the codemod applied (widget
 *       {@code new} expressions → proxies) and fails if the audit still finds a raw creation of a
 *       themeable widget. Creations that must stay raw are listed in
 *       {@code src/fork/<name>-allow.txt} as {@code relative/File.java:line}. The report is written
 *       to {@code build/reports/fork/<name>.txt}. Hand edits live as unified diffs in
 *       {@code src/fork/<name>-patches/*.patch}, applied after the codemod.</li>
 *   <li>{@code fork<Name>Resources}: copies the non-Java files of the source dir, plus each extra
 *       resource: a directory under its own name (e.g. {@code icons/}), a file at the root.</li>
 * </ul>
 * Both outputs are added to the {@code main} source set.
 */
public class ForkExtension {

    private final Project project;
    private final Configuration tools;
    private final List<File> proxyLists = new ArrayList<>();

    @Inject
    public ForkExtension(Project project, Configuration tools) {
        this.project = project;
        this.tools = tools;
    }

    /** Adds a proxy list whose classes the codemod rewrites to; zapthemes-swt's list is always included. */
    public void proxies(Object file) {
        proxyLists.add(project.file(file));
    }

    public void upstream(String name, Object sourceDir, Object... resourceDirs) {
        File source = project.file(sourceDir);
        String taskName = Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);
        File out = project.getLayout().getBuildDirectory().dir("generated/sources/fork/" + name + "/java").get().getAsFile();
        File resourcesOut = project.getLayout().getBuildDirectory().dir("generated/resources/fork/" + name).get().getAsFile();
        File report = project.getLayout().getBuildDirectory().file("reports/fork/" + name + ".txt").get().getAsFile();
        File allow = project.file("src/fork/" + name + "-allow.txt");
        File patches = project.file("src/fork/" + name + "-patches");
        List<File> lists = List.copyOf(proxyLists);

        TaskProvider<JavaExec> sources = project.getTasks().register("fork" + taskName + "Sources", JavaExec.class, task -> {
            task.setGroup("build");
            task.setDescription("Generates the " + name + " fork from " + source + " with widget creations rewritten to proxies.");
            task.getMainClass().set("com.zapthemes.tools.Fork");
            task.classpath(tools);
            task.getInputs().files(project.fileTree(source)).withPropertyName("upstream");
            task.getInputs().files(lists).withPropertyName("proxyLists");
            task.getInputs().files(project.files(allow)).withPropertyName("allowList");
            task.getInputs().files(project.fileTree(patches)).withPropertyName("patches");
            task.getOutputs().dir(out).withPropertyName("sources");
            task.getOutputs().file(report).withPropertyName("report");
            task.args("--source-root", source.getAbsolutePath());
            for (File list : lists) {
                task.args("--proxies", list.getAbsolutePath());
            }
            task.args("--out", out.getAbsolutePath(), "--report", report.getAbsolutePath(), "--allow", allow.getAbsolutePath(),
                    "--patches", patches.getAbsolutePath());
        });

        TaskProvider<Sync> resources = project.getTasks().register("fork" + taskName + "Resources", Sync.class, task -> {
            task.setGroup("build");
            task.setDescription("Copies the " + name + " fork's resources.");
            task.from(source, spec -> spec.exclude("**/*.java"));
            for (Object dir : resourceDirs) {
                File resource = project.file(dir);
                if (resource.isFile()) {
                    task.from(resource);
                } else {
                    task.from(resource, spec -> spec.into(resource.getName()));
                }
            }
            task.into(resourcesOut);
        });

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME, main -> {
            main.getJava().srcDir(sources.map(t -> out));
            main.getResources().srcDir(resources.map(t -> resourcesOut));
        });
    }
}
