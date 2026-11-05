plugins {
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
    application
}

description = "Demo application."

dependencies {
    implementation(project(":zapthemes-swt"))
    implementation(project(":zapthemes-jface"))
    implementation(project(":zapthemes-widgets"))
    implementation(project(":zapthemes-nebula"))
    implementation(project(":zapthemes-nebula-incubation"))
    implementation(project(":zapthemes-themes"))
}

application {
    mainClass = "com.zapthemes.demo.JFaceDemo"
}

// The plain Display/Shell demo (no JFace).
tasks.register<JavaExec>("runStandalone") {
    group = "application"
    description = "Runs the standalone SWT demo."
    mainClass = "com.zapthemes.demo.StandaloneDemo"
    classpath = sourceSets.main.get().runtimeClasspath
}

// Opens both demos, switches themes, checks every control and exits. Used in CI.
val smokeStandalone = tasks.register<JavaExec>("smokeTestStandalone") {
    group = "verification"
    description = "Smoke-tests the standalone SWT demo."
    mainClass = "com.zapthemes.demo.StandaloneDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
val smokeJFace = tasks.register<JavaExec>("smokeTestJFace") {
    group = "verification"
    description = "Smoke-tests the JFace ApplicationWindow demo and its dialogs."
    mainClass = "com.zapthemes.demo.JFaceDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
val smokeNebula = tasks.register<JavaExec>("smokeTestNebula") {
    group = "verification"
    description = "Smoke-tests the Nebula demo."
    mainClass = "com.zapthemes.demo.NebulaDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs every demo smoke test."
    dependsOn(smokeStandalone, smokeJFace, smokeNebula)
}

// The Nebula widgets demo.
tasks.register<JavaExec>("runNebula") {
    group = "application"
    description = "Runs the Nebula widgets demo."
    mainClass = "com.zapthemes.demo.NebulaDemo"
    classpath = sourceSets.main.get().runtimeClasspath
}

// Saves every demo in every bundled theme as PNG under build/screenshots. CI uploads them so the
// GTK and Cocoa rendering can be looked at, not just tested.
val screenshotDir = layout.buildDirectory.dir("screenshots")
val screenshotTasks = mapOf(
    "Standalone" to "com.zapthemes.demo.StandaloneDemo",
    "JFace" to "com.zapthemes.demo.JFaceDemo",
    "Nebula" to "com.zapthemes.demo.NebulaDemo",
).map { (name, main) ->
    tasks.register<JavaExec>("screenshots$name") {
        group = "documentation"
        description = "Saves screenshots of the $name demo in every theme."
        mainClass = main
        classpath = sourceSets.main.get().runtimeClasspath
        args("--smoke", "--screenshots", screenshotDir.get().asFile.absolutePath)
    }
}
tasks.register("screenshots") {
    group = "documentation"
    description = "Saves screenshots of every demo in every theme."
    dependsOn(screenshotTasks)
}
