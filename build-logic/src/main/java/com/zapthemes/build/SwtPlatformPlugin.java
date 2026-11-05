package com.zapthemes.build;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

/**
 * Puts the SWT fragment for the build machine's platform on every classpath, and publishes a
 * dependency on the platform-neutral {@code org.eclipse.platform:org.eclipse.swt} instead.
 *
 * <p>The SWT POM selects its fragment with Maven OS-activated profiles, which Gradle ignores, so the
 * placeholder module {@code org.eclipse.platform:org.eclipse.swt} (pulled in transitively by JFace,
 * Nebula, ...) is excluded everywhere and the real fragment is added instead. The fragment sits in
 * the unpublished {@code swt} configuration: published metadata names the placeholder, as JFace's
 * own does, so Maven consumers get their platform's fragment and a jar built on Windows does not
 * pull win32 natives into a Linux app.
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code swt.platform} — target platform, e.g. {@code gtk.linux.aarch64} (default: detected)</li>
 *   <li>{@code zapthemes.swtVersion} — SWT version for Maven Central fragments</li>
 *   <li>{@code swt.localJar} — SWT jar for platforms with no published fragment (FreeBSD);
 *       defaults to the FreeBSD ports location</li>
 * </ul>
 */
public class SwtPlatformPlugin implements Plugin<Project> {

    static final String FREEBSD_PORTS_JAR = "/usr/local/share/java/classes/swt.jar";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaConventionsPlugin.class);

        SwtPlatform platform = platform(project);
        project.getExtensions().getExtraProperties().set("swtPlatform", platform.id());

        project.getConfigurations().configureEach(configuration ->
                configuration.exclude(Map.of("group", "org.eclipse.platform", "module", "org.eclipse.swt")));

        Object swt;
        if (platform.publishedOnMavenCentral()) {
            Object version = project.findProperty("zapthemes.swtVersion");
            if (version == null) {
                throw new GradleException("Property zapthemes.swtVersion is not set");
            }
            swt = "org.eclipse.platform:" + platform.artifactId() + ":" + version;
        } else {
            Object localJar = project.findProperty("swt.localJar");
            File jar = new File(localJar != null ? localJar.toString() : FREEBSD_PORTS_JAR);
            if (!jar.isFile()) {
                throw new GradleException("No SWT fragment is published for " + platform.id()
                        + ". Install the SWT port (x11-toolkits/swt) or pass -Pswt.localJar=<path to swt.jar>."
                        + " Not found: " + jar);
            }
            swt = project.files(jar);
        }
        Configuration fragment = project.getConfigurations().create("swt", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(false);
        });
        project.getDependencies().add(fragment.getName(), swt);
        for (String classpath : List.of("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")) {
            project.getConfigurations().getByName(classpath).extendsFrom(fragment);
        }

        Object version = project.findProperty("zapthemes.swtVersion");
        if (version != null) {
            Configuration published = project.getConfigurations().create("swtPublished", c -> {
                c.setCanBeConsumed(false);
                c.setCanBeResolved(false);
            });
            project.getDependencies().add(published.getName(), "org.eclipse.platform:org.eclipse.swt:" + version);
            project.getConfigurations().getByName("apiElements").extendsFrom(published);
            project.getConfigurations().getByName("runtimeElements").extendsFrom(published);
        }
    }

    static SwtPlatform platform(Project project) {
        Object requested = project.findProperty("swt.platform");
        return requested != null ? SwtPlatform.parse(requested.toString()) : SwtPlatform.detect();
    }
}
