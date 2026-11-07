package com.zapthemes.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.external.javadoc.StandardJavadocDocletOptions;
import org.gradle.plugins.signing.SigningExtension;

/**
 * Publishes a module to Maven Central: sources and Javadoc jars, the POM metadata Central requires,
 * and PGP signatures. Modules are staged in the root {@code build/staging-deploy} repository, which
 * {@link CentralBundlePlugin} zips into an upload bundle.
 *
 * <p>Signing uses an ASCII-armored key from the {@code signingKey} and {@code signingPassword}
 * properties (e.g. {@code ORG_GRADLE_PROJECT_signingKey} in CI), or the {@code signing.keyId}
 * keyring properties. Without either, publications are left unsigned (fine for
 * {@code publishToMavenLocal}); the Central bundle then refuses to build.
 */
public class PublishingPlugin implements Plugin<Project> {

    public static final String STAGING_REPOSITORY = "staging";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaConventionsPlugin.class);
        project.getPluginManager().apply("maven-publish");
        project.getPluginManager().apply("signing");

        project.getExtensions().getByType(JavaPluginExtension.class).withJavadocJar();
        project.getTasks().withType(Javadoc.class).configureEach(javadoc -> {
            StandardJavadocDocletOptions options = (StandardJavadocDocletOptions) javadoc.getOptions();
            options.setEncoding("UTF-8");
            options.addBooleanOption("html5", true);
            // No options.links for the JDK: javadoc links the platform docs from its built-in element list,
            // so the build never downloads Oracle's package-list (which now redirects and fails the build).
            options.quiet();
            if (project.getPluginManager().hasPlugin("zapthemes.fork")) {
                // Forked upstream code keeps upstream's Javadoc, warts and all.
                options.addBooleanOption("Xdoclint:none", true);
                options.addStringOption("Xmaxwarns", "1");
                options.tags("generated:a:Generated", "model:a:Model", "ordered:a:Ordered", "noreference:a:No reference",
                        "noimplement:a:No implement", "noextend:a:No extend", "noinstantiate:a:No instantiate");
                javadoc.setFailOnError(false);
            } else {
                // Our API documents what matters; missing comments on overrides and getters are fine.
                options.addBooleanOption("Xdoclint:all,-missing", true);
            }
        });

        PublishingExtension publishing = project.getExtensions().getByType(PublishingExtension.class);
        MavenPublication publication = publishing.getPublications().create("maven", MavenPublication.class, maven -> {
            maven.from(project.getComponents().getByName("java"));
            maven.versionMapping(mapping -> mapping.allVariants(variant -> variant.fromResolutionResult()));
            maven.pom(pom -> {
                pom.getName().set(project.getName());
                pom.getDescription().set(project.provider(project::getDescription));
                pom.getUrl().set(property(project, "pom.url"));
                pom.licenses(licenses -> licenses.license(license -> {
                    license.getName().set("Eclipse Public License 2.0");
                    license.getUrl().set("https://www.eclipse.org/legal/epl-2.0/");
                }));
                pom.developers(developers -> developers.developer(developer -> {
                    developer.getId().set(property(project, "pom.developerId"));
                    developer.getName().set(property(project, "pom.developerName"));
                }));
                pom.scm(scm -> {
                    scm.getUrl().set(property(project, "pom.url"));
                    scm.getConnection().set("scm:git:" + property(project, "pom.url") + ".git");
                    scm.getDeveloperConnection().set("scm:git:" + property(project, "pom.url") + ".git");
                });
            });
        });

        publishing.repositories(repositories -> repositories.maven(repository -> {
            repository.setName(STAGING_REPOSITORY);
            repository.setUrl(project.getRootProject().getLayout().getBuildDirectory().dir(CentralBundlePlugin.STAGING_DIR));
        }));
        project.getTasks().withType(PublishToMavenRepository.class).configureEach(task ->
                task.mustRunAfter(":" + CentralBundlePlugin.CLEAN_STAGING));

        SigningExtension signing = project.getExtensions().getByType(SigningExtension.class);
        Object key = project.findProperty("signingKey");
        if (key != null) {
            Object password = project.findProperty("signingPassword");
            signing.useInMemoryPgpKeys(key.toString(), password != null ? password.toString() : "");
            signing.sign(publication);
        } else if (project.findProperty("signing.keyId") != null) {
            signing.sign(publication);
        }
    }

    private static String property(Project project, String name) {
        Object value = project.findProperty(name);
        if (value == null) {
            throw new org.gradle.api.GradleException("Property " + name + " is not set (gradle.properties)");
        }
        return value.toString();
    }
}
