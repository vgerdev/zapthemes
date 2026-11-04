package com.zapthemes.build;

import java.io.File;
import java.util.Map;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Adds the SWT fragment for the target platform as an {@code api} dependency.
 *
 * <p>The SWT POM selects its fragment with Maven OS-activated profiles, which Gradle ignores, so the
 * placeholder module {@code org.eclipse.platform:org.eclipse.swt} (pulled in transitively by JFace,
 * Nebula, ...) is excluded everywhere and the real fragment is added instead.
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
        project.getDependencies().add("api", swt);
    }

    static SwtPlatform platform(Project project) {
        Object requested = project.findProperty("swt.platform");
        return requested != null ? SwtPlatform.parse(requested.toString()) : SwtPlatform.detect();
    }
}
