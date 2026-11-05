plugins {
    id("zapthemes.publishing")
    id("zapthemes.java-conventions")
    id("zapthemes.swt-platform")
}

description = "Bundled JSON themes, and the zapthemes.theme system property that picks one."

dependencies {
    api(project(":zapthemes-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
