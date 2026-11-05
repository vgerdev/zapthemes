plugins {
    id("zapthemes.publishing")
    id("zapthemes.fork")
    id("zapthemes.raw-widget-audit")
}

description = "JFace notifications 0.8.100 fork: org.eclipse.jface.notifications on the forked JFace, " +
    "with its popups built from ZapThemes proxies."

val notificationsVersion = "0.8.100"
val notificationsBundle = rootProject.layout.projectDirectory.dir("third_party/jface-upstream/bundles/org.eclipse.jface.notifications")

zapthemesFork {
    upstream(
        "notifications",
        notificationsBundle.dir("src"),
        notificationsBundle.dir("icons"),
        notificationsBundle.file("plugin.properties"),
        notificationsBundle.file("about.html"),
    )
}

dependencies {
    api(project(":zapthemes-jface"))
    // As in upstream's manifest: Platform.getProduct() and the auto-close Job.
    api(libs.core.runtime)

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Drop-in replacement for org.eclipse.platform:org.eclipse.jface.notifications.
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.capability("${project.group}:${project.name}:${project.version}")
        outgoing.capability("org.eclipse.platform:org.eclipse.jface.notifications:$notificationsVersion")
    }
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}
