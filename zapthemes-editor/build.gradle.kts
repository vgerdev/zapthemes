plugins {
    id("zapthemes.publishing")
    id("zapthemes.java-conventions")
    id("zapthemes.swt-platform")
}

description = "The theme editor: its palette model and generator (com.zapthemes.editor.palette), headless."

dependencies {
    api(project(":zapthemes-core"))

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
