package com.zapthemes.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Generates theme-aware proxy subclasses for the widget classes listed in
 * {@code src/proxies/proxies.txt} and adds them to the main source set.
 *
 * <p>The generator ({@code com.zapthemes.tools.ProxyGenerator}) lives in {@code :zapthemes-tools}
 * and reads the widget classes reflectively from the SWT / Nebula classpath, so the proxies always
 * mirror the exact constructors of the pinned upstream release. A proxy written by hand in
 * {@code src/main/java} (same class name) replaces the generated one.
 */
public class SwtProxiesPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SwtPlatformPlugin.class);

        Configuration generator = project.getConfigurations().create("proxyGenerator", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
        });
        project.getDependencies().add(generator.getName(),
                project.getDependencies().project(java.util.Map.of("path", ":zapthemes-tools")));

        RegularFile proxyList = project.getLayout().getProjectDirectory().file("src/proxies/proxies.txt");
        Provider<Directory> outputDir = project.getLayout().getBuildDirectory().dir("generated/sources/proxies/java");

        TaskProvider<JavaExec> generate = project.getTasks().register("generateProxies", JavaExec.class, task -> {
            task.setGroup("build");
            task.setDescription("Generates theme-aware proxy subclasses for SWT/Nebula widgets.");
            task.getMainClass().set("com.zapthemes.tools.ProxyGenerator");
            // The module's compile classpath holds the widget libraries to proxy (e.g. the Nebula fork).
            task.classpath(generator, project.getConfigurations().getByName("compileClasspath"));
            task.getInputs().file(proxyList).withPropertyName("proxyList");
            task.getOutputs().dir(outputDir).withPropertyName("outputDir");
            // The generator empties the output directory itself before writing.
            java.io.File handWritten = project.file("src/main/java");
            task.getInputs().files(project.fileTree(handWritten)).withPropertyName("handWritten");
            task.args("--list", proxyList.getAsFile().getAbsolutePath(),
                    "--out", outputDir.get().getAsFile().getAbsolutePath(),
                    "--skip-existing", handWritten.getAbsolutePath());
        });

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME, main ->
                main.getJava().srcDir(generate.map(t -> outputDir)));
    }
}
