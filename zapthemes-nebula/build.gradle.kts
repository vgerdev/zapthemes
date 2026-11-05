plugins {
    id("zapthemes.swt-proxies")
    id("zapthemes.raw-widget-audit")
}

description = "Theme-aware proxies, stylers and renderers for the Nebula released widgets."

dependencies {
    api(project(":zapthemes-nebula-fork"))
    api(project(":zapthemes-swt"))

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
