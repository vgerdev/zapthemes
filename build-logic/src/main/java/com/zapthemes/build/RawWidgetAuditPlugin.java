package com.zapthemes.build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * Adds {@code auditRawWidgets} to {@code check}: fails if the project's main or test sources create
 * an SWT widget with {@code new} instead of through its {@code com.zapthemes} proxy. A line marked
 * {@code // zapthemes:allow-raw} is exempt.
 */
public class RawWidgetAuditPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(SwtPlatformPlugin.class);

        Configuration tools = project.getConfigurations().maybeCreate("zapthemesTools");
        // The tools load SWT classes; the fragment is not published, so it is not transitive.
        tools.extendsFrom(project.getConfigurations().getByName("swt"));
        tools.setCanBeConsumed(false);
        tools.setCanBeResolved(true);
        project.getDependencies().add(tools.getName(),
                project.getDependencies().project(Map.of("path", ":zapthemes-tools")));

        List<File> roots = new ArrayList<>();
        for (String name : List.of(SourceSet.MAIN_SOURCE_SET_NAME, SourceSet.TEST_SOURCE_SET_NAME)) {
            roots.add(project.file("src/" + name + "/java"));
        }

        TaskProvider<JavaExec> audit = project.getTasks().register("auditRawWidgets", JavaExec.class, task -> {
            task.setGroup("verification");
            task.setDescription("Fails on SWT widgets created without their ZapThemes proxy.");
            task.getMainClass().set("com.zapthemes.tools.Audit");
            task.classpath(tools);
            task.args("--list");
            for (File root : roots) {
                task.getInputs().files(project.fileTree(root)).withPropertyName("sources-" + root.getParentFile().getName());
                task.args("--source-root", root.getAbsolutePath());
            }
        });
        project.getTasks().named("check", check -> check.dependsOn(audit));
    }
}
