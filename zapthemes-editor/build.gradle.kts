plugins {
    id("zapthemes.publishing")
    id("zapthemes.java-conventions")
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
}

description = "The theme editor: a JFace ThemeWizard (com.zapthemes.editor) on a headless palette model and generator (com.zapthemes.editor.palette)."

dependencies {
    api(project(":zapthemes-core"))
    // The wizard: a JFace Wizard on ZapThemes proxies, with a preview pane of sample controls.
    api(project(":zapthemes-jface"))
    implementation(project(":zapthemes-nebula"))
    implementation(project(":zapthemes-visualization"))
    // The bundled themes: the ones to duplicate and build on, and the standalone app's own.
    implementation(project(":zapthemes-themes"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// editor.libraryVersion in the themes the wizard writes.
val versionResource = tasks.register("versionResource") {
    val out = layout.buildDirectory.dir("generated/version")
    val libraryVersion = project.version.toString()
    inputs.property("version", libraryVersion)
    outputs.dir(out)
    doLast {
        val file = out.get().file("com/zapthemes/editor/version.txt").asFile
        file.parentFile.mkdirs()
        file.writeText(libraryVersion)
    }
}
sourceSets.main { resources.srcDir(versionResource) }

// The standalone app (com.zapthemes.editor.app.ThemeEditor): run it, smoke-test it, or save each
// page in each bundled theme as PNG under build/screenshots.
val editorMain = "com.zapthemes.editor.app.ThemeEditor"
tasks.register<JavaExec>("run") {
    group = "application"
    description = "Runs the theme editor. -Pfile=<theme.json> edits a theme file, -Ptheme=<id> picks the editor's own theme."
    mainClass = editorMain
    classpath = sourceSets.main.get().runtimeClasspath
    // A relative file is the repository root's, where ./gradlew runs.
    providers.gradleProperty("file").orNull?.let { args(rootProject.file(it).absolutePath) }
    providers.gradleProperty("theme").orNull?.let { args("--theme", it) }
}
tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Smoke-tests the theme editor: every page in every bundled window theme, edits reaching the preview, undo."
    mainClass = editorMain
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
tasks.register<JavaExec>("screenshots") {
    group = "documentation"
    description = "Saves screenshots of every page of the theme editor in every bundled theme."
    mainClass = editorMain
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke", "--screenshots", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    // Control.print on GTK can capture another window on top: never beside the demos' screenshots.
    mustRunAfter(":zapthemes-demo:screenshots")
}
