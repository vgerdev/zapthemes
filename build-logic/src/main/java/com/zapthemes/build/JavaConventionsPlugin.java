package com.zapthemes.build;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

/**
 * Shared Java settings: JDK 25 toolchain, UTF-8, JUnit Platform, the JVM flags SWT needs, and an
 * {@code Automatic-Module-Name} for every jar.
 */
public class JavaConventionsPlugin implements Plugin<Project> {

    public static final int JAVA_VERSION = 25;

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java-library");

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.getToolchain().getLanguageVersion().set(JavaLanguageVersion.of(JAVA_VERSION));
        java.withSourcesJar();

        project.getTasks().withType(JavaCompile.class).configureEach(compile -> {
            compile.getOptions().setEncoding("UTF-8");
            compile.getOptions().getRelease().set(JAVA_VERSION);
            compile.getOptions().getCompilerArgs().add("-Xlint:all,-serial,-processing");
        });

        String moduleName = "com." + project.getName().replace('-', '.');
        project.getTasks().withType(Jar.class).configureEach(jar ->
                jar.getManifest().getAttributes().put("Automatic-Module-Name", moduleName));

        List<String> swtJvmArgs = swtJvmArgs();
        project.getTasks().withType(Test.class).configureEach(test -> {
            test.useJUnitPlatform();
            test.jvmArgs(swtJvmArgs);
        });

        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        project.getTasks().withType(JavaExec.class).configureEach(exec -> {
            exec.getJavaLauncher().convention(toolchains.launcherFor(java.getToolchain()));
            exec.jvmArgs(swtJvmArgs);
        });
    }

    /** SWT is a JNI library: allow native access on JDK 24+, and run on thread 0 on macOS. */
    static List<String> swtJvmArgs() {
        List<String> args = new ArrayList<>();
        args.add("--enable-native-access=ALL-UNNAMED");
        if (SwtPlatform.detect().os().equals("macosx")) {
            args.add("-XstartOnFirstThread");
        }
        return args;
    }
}
