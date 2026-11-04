plugins {
    id("zapthemes.publishing")
    id("zapthemes.swt-proxies")
    id("zapthemes.raw-widget-audit")
}

description = "Generated theme-aware proxy subclasses of the SWT widgets."

dependencies {
    api(project(":zapthemes-core"))

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
