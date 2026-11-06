plugins {
    id("zapthemes.publishing")
    id("zapthemes.fork")
}

description = "org.eclipse.text 3.14.800 built from its unchanged sources, for the JFace Text fork."

// Not a widget library: it is rebuilt only because it shares packages (org.eclipse.jface.text,
// ...source) with org.eclipse.jface.text, and the published jar is signed. The JVM refuses to load
// the unsigned forked JFace Text classes into a package whose other classes are signed.
val textVersion = "3.14.800"
val textBundle = rootProject.layout.projectDirectory.dir("third_party/jface-upstream/bundles/org.eclipse.text")

zapthemesFork {
    upstream("text", textBundle.dir("src"), textBundle.file("plugin.properties"), textBundle.file("about.html"))
    upstream("projection", textBundle.dir("projection"))
}

dependencies {
    // As in upstream's POM.
    api(libs.core.commands)
    api(libs.equinox.common)
    api(libs.equinox.preferences)
    api(libs.core.runtime)
}

// Drop-in replacement for org.eclipse.platform:org.eclipse.text.
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.capability("${project.group}:${project.name}:${project.version}")
        outgoing.capability("org.eclipse.platform:org.eclipse.text:$textVersion")
    }
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}
