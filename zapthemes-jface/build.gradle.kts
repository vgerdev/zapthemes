plugins {
    id("zapthemes.fork")
    id("zapthemes.raw-widget-audit")
}

description = "JFace 3.40.0 fork: a drop-in for org.eclipse.jface whose widgets are ZapThemes proxies."

val jfaceVersion = "3.40.0"
val jfaceBundle = rootProject.layout.projectDirectory.dir("third_party/jface-upstream/bundles/org.eclipse.jface")

zapthemesFork {
    upstream(
        "jface",
        jfaceBundle.dir("src"),
        jfaceBundle.dir("icons"),
        jfaceBundle.file("plugin.properties"),
        jfaceBundle.file("about.html"),
    )
}

dependencies {
    api(project(":zapthemes-swt"))
    api(libs.core.commands)
    api(libs.equinox.common)
    implementation(libs.equinox.bidi)
    // Only for FrameworkUtil.getBundle(...), which returns null outside OSGi.
    implementation(libs.eclipse.osgi)
    compileOnly(libs.pde.annotations)
    // JFace 3.40 requires the eclipse.swt "image.format=svg" capability: its dialog icons are SVG.
    runtimeOnly(libs.swt.svg)

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Drop-in replacement: declare the official JFace capability, so a build that also pulls in
// org.eclipse.platform:org.eclipse.jface fails with a capability conflict instead of mixing classes.
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.capability("${project.group}:${project.name}:${project.version}")
        outgoing.capability("org.eclipse.platform:org.eclipse.jface:$jfaceVersion")
    }
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}
