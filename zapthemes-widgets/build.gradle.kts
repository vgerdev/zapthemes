plugins {
    id("zapthemes.publishing")
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
}

description = "Canvas-drawn replacements for native controls that ignore theme colors."

dependencies {
    api(project(":zapthemes-core"))

    testImplementation(project(":zapthemes-swt"))
    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
