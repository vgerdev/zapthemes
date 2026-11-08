plugins {
    id("zapthemes.publishing")
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
}

description = "JFace support for the ZapThemes ribbon: actions as commands, contribution items, a ribbon ApplicationWindow."

dependencies {
    api(project(":zapthemes-jface"))
    api(project(":zapthemes-widgets"))

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
