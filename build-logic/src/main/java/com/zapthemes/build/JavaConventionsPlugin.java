package com.zapthemes.build;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestExceptionFormat;
import org.gradle.api.tasks.testing.logging.TestLogEvent;
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
            // SWT tests can block on a native event loop; fail the stuck test with its stack trace.
            test.systemProperty("junit.jupiter.execution.timeout.default", "2 m");
            test.getTimeout().set(Duration.ofMinutes(10));
            // Full stack traces in the console, so CI logs show where a test failed.
            test.testLogging(logging -> {
                logging.events(TestLogEvent.FAILED, TestLogEvent.SKIPPED);
                logging.setExceptionFormat(TestExceptionFormat.FULL);
            });
        });

        JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);
        project.getTasks().withType(JavaExec.class).configureEach(exec -> {
            exec.getJavaLauncher().convention(toolchains.launcherFor(java.getToolchain()));
            exec.jvmArgs(swtJvmArgs);
            // Smoke tests and screenshots can hang on a native event loop; stop them. The run tasks
            // are someone using the app, so they run until its windows close.
            if (!isInteractive(exec.getName())) {
                exec.getTimeout().convention(Duration.ofMinutes(10));
            }
        });
    }

    /** {@code run}, {@code runTitleBar}, {@code runJFace}…: tasks a person runs and uses. */
    static boolean isInteractive(String taskName) {
        return taskName.equals("run")
                || taskName.startsWith("run") && taskName.length() > 3 && Character.isUpperCase(taskName.charAt(3));
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
