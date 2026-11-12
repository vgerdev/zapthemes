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

    testImplementation(project(":zapthemes-themes"))
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
