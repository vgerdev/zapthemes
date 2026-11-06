plugins {
    id("zapthemes.publishing")
    id("zapthemes.fork")
}

description = "Nebula Visualization (XYGraph, gauges, meters, tanks, ...) forked like zapthemes-nebula-fork, " +
    "with a theme bridge for its draw2d figures."

val visualization = rootProject.layout.projectDirectory.dir("third_party/nebula-upstream/widgets/visualization")

zapthemesFork {
    // The figures and their dialogs paint with display.getSystemColor(...): route those through the theme.
    themeSystemColors()
    upstream("xygraph", visualization.dir("org.eclipse.nebula.visualization.xygraph/src"))
    upstream("widgets", visualization.dir("org.eclipse.nebula.visualization.widgets/src"))
}

dependencies {
    // As in upstream's manifests: draw2d (from GEF Classic, see zapthemes-draw2d), JFace, core.runtime.
    api(project(":zapthemes-jface"))
    api(project(":zapthemes-draw2d"))
    api(libs.core.runtime)

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}
