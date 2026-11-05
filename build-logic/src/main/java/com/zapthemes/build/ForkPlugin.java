package com.zapthemes.build;

import java.util.Map;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/**
 * Builds a fork of upstream sources at build time: see {@link ForkExtension#upstream}. The pristine
 * sources come from {@code third_party/} (fetched by {@code scripts/fetch-upstream.sh}); the fork is
 * never edited by hand, so bumping upstream is a ref change.
 */
public class ForkPlugin implements Plugin<Project> {

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

        ForkExtension extension = project.getExtensions().create("zapthemesFork", ForkExtension.class, project, tools);
        extension.proxies(project.getRootProject().file("zapthemes-swt/src/proxies/proxies.txt"));
    }
}
