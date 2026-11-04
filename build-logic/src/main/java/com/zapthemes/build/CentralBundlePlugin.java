package com.zapthemes.build;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Zip;

/**
 * Root-project tasks for a Maven Central release through the Central Portal:
 * <ul>
 *   <li>{@code publishToStaging}: publishes every module with {@link PublishingPlugin} into
 *       {@code build/staging-deploy}, emptied first;</li>
 *   <li>{@code centralBundle}: zips it into {@code build/central/<name>-<version>-bundle.zip}, after
 *       checking the version is a release and every artifact is signed;</li>
 *   <li>{@code uploadToCentral}: uploads the bundle as a user-managed deployment (see
 *       {@link CentralUploadTask}).</li>
 * </ul>
 */
public class CentralBundlePlugin implements Plugin<Project> {

    static final String STAGING_DIR = "staging-deploy";
    static final String CLEAN_STAGING = "cleanStaging";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("base");
        Provider<Directory> staging = project.getLayout().getBuildDirectory().dir(STAGING_DIR);

        var clean = project.getTasks().register(CLEAN_STAGING, Delete.class, task -> task.delete(staging));

        var publish = project.getTasks().register("publishToStaging", task -> {
            task.setGroup("publishing");
            task.setDescription("Publishes every module into build/" + STAGING_DIR + " for a Central bundle.");
            task.dependsOn(clean);
            task.dependsOn(project.provider(() -> project.getSubprojects().stream()
                    .filter(p -> p.getPluginManager().hasPlugin("zapthemes.publishing"))
                    .map(p -> p.getPath() + ":publishAllPublicationsTo" + capitalize(PublishingPlugin.STAGING_REPOSITORY) + "Repository")
                    .toList()));
        });

        String version = project.getVersion().toString();
        var bundle = project.getTasks().register("centralBundle", Zip.class, zip -> {
            zip.setGroup("publishing");
            zip.setDescription("Zips the staged modules into a Maven Central Portal upload bundle.");
            zip.dependsOn(publish);
            zip.from(staging, spec -> spec.exclude("**/maven-metadata*.xml*"));
            zip.getArchiveFileName().set(project.getName() + "-" + version + "-bundle.zip");
            zip.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("central"));
            zip.doFirst(task -> checkBundle(version, staging.get().getAsFile()));
        });

        project.getTasks().register("uploadToCentral", CentralUploadTask.class, upload -> {
            upload.setGroup("publishing");
            upload.setDescription("Uploads the Central bundle as a deployment to publish from central.sonatype.com.");
            upload.getBundle().set(bundle.flatMap(Zip::getArchiveFile));
            upload.getDeploymentName().set(project.getName() + " " + version);
            upload.getUsername().set(project.getProviders().environmentVariable("CENTRAL_TOKEN_USERNAME"));
            upload.getPassword().set(project.getProviders().environmentVariable("CENTRAL_TOKEN_PASSWORD"));
        });
    }

    /** Central rejects snapshots and unsigned artifacts; fail here rather than after the upload. */
    private static void checkBundle(String version, File staging) {
        if (version.endsWith("-SNAPSHOT")) {
            throw new GradleException("Version " + version + " is a snapshot; Central bundles need a release version (-Pversion=...).");
        }
        List<Path> unsigned;
        try (Stream<Path> files = Files.walk(staging.toPath())) {
            unsigned = files.filter(Files::isRegularFile)
                    .filter(f -> f.toString().endsWith(".jar") || f.toString().endsWith(".pom") || f.toString().endsWith(".module"))
                    .filter(f -> !Files.exists(f.resolveSibling(f.getFileName() + ".asc")))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!unsigned.isEmpty()) {
            throw new GradleException(unsigned.size() + " artifacts are not signed (set signingKey/signingPassword), e.g. " + unsigned.getFirst());
        }
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
