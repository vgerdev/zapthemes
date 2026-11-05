plugins {
    id("zapthemes.swt-proxies")
    id("zapthemes.raw-widget-audit")
}

description = "Theme-aware proxies and stylers for the Nebula incubation widgets."

dependencies {
    api(project(":zapthemes-nebula-incubation-fork"))
    api(project(":zapthemes-swt"))

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
