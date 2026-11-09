plugins {
    id("zapthemes.publishing")
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
}

description = "Canvas-drawn replacements for native controls that ignore theme colors."

dependencies {
    api(project(":zapthemes-core"))
    // Popups (Shell), the combo editor (Text), the customization dialog and the title bar (search
    // field, slot, menus, pop-up) use the themed proxies.
    api(project(":zapthemes-swt"))

    testImplementation(project(":zapthemes-themes"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
