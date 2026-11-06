plugins {
    id("zapthemes.publishing")
    id("zapthemes.fork")
    id("zapthemes.raw-widget-audit")
}

description = "JFace Text 3.31.100 fork: org.eclipse.jface.text (source viewers, content assist, hovers, " +
    "rulers) on the forked JFace, with its widgets built from ZapThemes proxies."

val jfaceTextVersion = "3.31.100"
val textBundle = rootProject.layout.projectDirectory.dir("third_party/jface-upstream/bundles/org.eclipse.jface.text")

zapthemesFork {
    // Rulers and hovers paint with system colors: map them through the theme's systemColors.
    themeSystemColors()
    // Upstream builds one jar from two source folders.
    upstream("text", textBundle.dir("src"), textBundle.file("plugin.properties"), textBundle.file("about.html"))
    upstream("projection", textBundle.dir("projection"))
}

dependencies {
    api(project(":zapthemes-jface"))
    // As in upstream's POM. org.eclipse.text (documents, no widgets) is built from source: see there.
    api(project(":zapthemes-text"))
    api(libs.core.runtime)

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Drop-in replacement for org.eclipse.platform:org.eclipse.jface.text.
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.capability("${project.group}:${project.name}:${project.version}")
        outgoing.capability("org.eclipse.platform:org.eclipse.jface.text:$jfaceTextVersion")
    }
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}
