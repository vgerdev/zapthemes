plugins {
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
}

description = "Small, self-contained examples of using ZapThemes (docs/theme-authoring.md)."

dependencies {
    implementation(project(":zapthemes-swt"))
    implementation(project(":zapthemes-jface"))
    implementation(project(":zapthemes-themes"))
}

// run<Name> opens a sample; smokeTest runs each with --smoke (build, check, exit), as CI does.
val samples = mapOf(
    "Hello" to "HelloZapThemes",
    "CustomTheme" to "CustomThemeSample",
    "CustomWidget" to "CustomWidgetSample",
    "JFace" to "JFaceSample",
)
val smokeTests = samples.map { (name, main) ->
    tasks.register<JavaExec>("run$name") {
        group = "application"
        description = "Runs the $main sample."
        mainClass = "com.zapthemes.samples.$main"
        classpath = sourceSets.main.get().runtimeClasspath
    }
    tasks.register<JavaExec>("smokeTest$name") {
        group = "verification"
        description = "Smoke-tests the $main sample."
        mainClass = "com.zapthemes.samples.$main"
        classpath = sourceSets.main.get().runtimeClasspath
        args("--smoke")
    }
}
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs every sample's smoke test."
    dependsOn(smokeTests)
}
