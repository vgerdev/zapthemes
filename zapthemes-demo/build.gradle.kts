plugins {
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
    application
}

description = "Demo application."

dependencies {
    implementation(project(":zapthemes-swt"))
    implementation(project(":zapthemes-jface"))
    implementation(project(":zapthemes-jface-notifications"))
    implementation(project(":zapthemes-jface-text"))
    implementation(project(":zapthemes-nattable"))
    implementation(project(":zapthemes-visualization"))
    implementation(project(":zapthemes-widgets"))
    implementation(project(":zapthemes-nebula"))
    implementation(project(":zapthemes-nebula-incubation"))
    implementation(project(":zapthemes-themes"))
}

// NatTable's POM asks for the official JFace; the fork provides the same capability.
configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("org.eclipse.platform:org.eclipse.jface") {
        select(candidates.first { (it.id as? ProjectComponentIdentifier)?.projectPath == ":zapthemes-jface" })
    }
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
val smokeText = tasks.register<JavaExec>("smokeTestText") {
    group = "verification"
    description = "Smoke-tests the JFace Text source viewer demo and its popups."
    mainClass = "com.zapthemes.demo.TextDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
val smokeNatTable = tasks.register<JavaExec>("smokeTestNatTable") {
    group = "verification"
    description = "Smoke-tests the NatTable demo."
    mainClass = "com.zapthemes.demo.NatTableDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
val smokeVisualization = tasks.register<JavaExec>("smokeTestVisualization") {
    group = "verification"
    description = "Smoke-tests the Nebula Visualization demo."
    mainClass = "com.zapthemes.demo.VisualizationDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs every demo smoke test."
    dependsOn(smokeStandalone, smokeJFace, smokeNebula, smokeText, smokeNatTable, smokeVisualization)
}

// The JFace Text source viewer demo.
tasks.register<JavaExec>("runText") {
    group = "application"
    description = "Runs the JFace Text source viewer demo."
    mainClass = "com.zapthemes.demo.TextDemo"
    classpath = sourceSets.main.get().runtimeClasspath
}

// The Nebula Visualization demo.
tasks.register<JavaExec>("runVisualization") {
    group = "application"
    description = "Runs the Nebula Visualization demo."
    mainClass = "com.zapthemes.demo.VisualizationDemo"
    classpath = sourceSets.main.get().runtimeClasspath
}

// The NatTable demo.
tasks.register<JavaExec>("runNatTable") {
    group = "application"
    description = "Runs the NatTable demo."
    mainClass = "com.zapthemes.demo.NatTableDemo"
    classpath = sourceSets.main.get().runtimeClasspath
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
    "Text" to "com.zapthemes.demo.TextDemo",
    "NatTable" to "com.zapthemes.demo.NatTableDemo",
    "Visualization" to "com.zapthemes.demo.VisualizationDemo",
).map { (name, main) ->
    tasks.register<JavaExec>("screenshots$name") {
        group = "documentation"
        description = "Saves screenshots of the $name demo in every theme."
        mainClass = main
        classpath = sourceSets.main.get().runtimeClasspath
        args("--smoke", "--screenshots", screenshotDir.get().asFile.absolutePath)
    }
}
// macOS only: the JFace demo's window, dialogs and hovers as the screen shows them (screencapture),
// since Control.print leaves Cocoa dialogs blank. Elsewhere it only runs the smoke check.
tasks.register<JavaExec>("screenCapturesJFace") {
    group = "documentation"
    description = "Saves macOS screen captures of the JFace demo's window, dialogs and hovers in every theme."
    mainClass = "com.zapthemes.demo.JFaceDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke", "--screen-captures", screenshotDir.get().dir("screen").asFile.absolutePath)
}
tasks.register("screenshots") {
    group = "documentation"
    description = "Saves screenshots of every demo in every theme."
    dependsOn(screenshotTasks)
}
