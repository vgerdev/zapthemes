plugins {
    id("zapthemes.publishing")
    id("zapthemes.swt-platform")
}

description = "Theme model, JSON (GSON) loading and the ThemeManager."

dependencies {
    api(libs.gson)

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
