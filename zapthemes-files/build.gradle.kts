plugins {
    id("zapthemes.publishing")
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
}

description = "An Explorer-style file browser over local, rooted and virtual filesystems."

dependencies {
    api(project(":zapthemes-core"))
    api(project(":zapthemes-swt"))
    api(project(":zapthemes-widgets"))

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
