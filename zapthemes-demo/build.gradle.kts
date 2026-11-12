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
    implementation(project(":zapthemes-jface-ribbon"))
    implementation(project(":zapthemes-jface-text"))
    implementation(project(":zapthemes-nattable"))
    implementation(project(":zapthemes-visualization"))
    implementation(project(":zapthemes-widgets"))
    implementation(project(":zapthemes-nebula"))
    implementation(project(":zapthemes-nebula-incubation"))
    implementation(project(":zapthemes-themes"))
    implementation(project(":zapthemes-editor"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
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

// Opens both demos, switches themes, checks every control and exits. Used in CI. Each records the
// theme keys its stylers read (KeyRecorder), which checkCatalog compares with the property catalog.
val recordedKeys = layout.buildDirectory.dir("recorded-keys")
val clearRecordedKeys = tasks.register<Delete>("clearRecordedKeys") {
    delete(recordedKeys)
}
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
val smokeTitleBar = tasks.register<JavaExec>("smokeTestTitleBar") {
    group = "verification"
    description = "Smoke-tests the Office-style title bar demo."
    mainClass = "com.zapthemes.demo.TitleBarDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
val smokeRibbon = tasks.register<JavaExec>("smokeTestRibbon") {
    group = "verification"
    description = "Smoke-tests the ribbon demo: reduction, KeyTips, backstage, contextual tabs, collapse."
    mainClass = "com.zapthemes.demo.RibbonDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
val smokePreview = tasks.register<JavaExec>("smokeTestPreview") {
    group = "verification"
    description = "Smoke-tests preview themes: one pane themed apart from the window, and edited in place."
    mainClass = "com.zapthemes.demo.PreviewDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
val smokeThemeWizard = tasks.register<JavaExec>("smokeTestThemeWizard") {
    group = "verification"
    description = "Smoke-tests the theme wizard: each page themed apart from its preview, and edits reaching the preview."
    mainClass = "com.zapthemes.demo.ThemeWizardDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
val smokeTasks = listOf(smokeStandalone, smokeJFace, smokeNebula, smokeText, smokeNatTable, smokeVisualization, smokeRibbon, smokeTitleBar,
    smokePreview, smokeThemeWizard)
smokeTasks.forEach { task ->
    task.configure {
        dependsOn(clearRecordedKeys)
        systemProperty("zapthemes.recordKeys", recordedKeys.get().asFile.absolutePath)
    }
}
// Every key the smoke tests read must be described by the PropertyCatalog.
val checkCatalog = tasks.register<JavaExec>("checkCatalog") {
    group = "verification"
    description = "Checks that the property catalog describes every theme key the demo smoke tests read."
    mainClass = "com.zapthemes.demo.CatalogCheck"
    classpath = sourceSets.main.get().runtimeClasspath
    args(recordedKeys.get().asFile.absolutePath)
    mustRunAfter(smokeTasks)
}
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs every demo smoke test, then checks the property catalog against the keys they read."
    dependsOn(smokeTasks, checkCatalog)
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

// The ribbon demo.
tasks.register<JavaExec>("runRibbon") {
    group = "application"
    description = "Runs the ribbon demo."
    mainClass = "com.zapthemes.demo.RibbonDemo"
    classpath = sourceSets.main.get().runtimeClasspath
}

// The Office-style title bar demo (Word-like and Excel-like windows).
tasks.register<JavaExec>("runTitleBar") {
    group = "application"
    description = "Runs the Office-style title bar demo."
    mainClass = "com.zapthemes.demo.TitleBarDemo"
    classpath = sourceSets.main.get().runtimeClasspath
}

// Preview themes: the same controls in the window's theme and in a previewed one.
tasks.register<JavaExec>("runPreview") {
    group = "application"
    description = "Runs the preview themes demo."
    mainClass = "com.zapthemes.demo.PreviewDemo"
    classpath = sourceSets.main.get().runtimeClasspath
}

// The theme wizard.
tasks.register<JavaExec>("runThemeWizard") {
    group = "application"
    description = "Runs the theme wizard."
    mainClass = "com.zapthemes.demo.ThemeWizardDemo"
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
    "Ribbon" to "com.zapthemes.demo.RibbonDemo",
    "TitleBar" to "com.zapthemes.demo.TitleBarDemo",
    "Preview" to "com.zapthemes.demo.PreviewDemo",
    "ThemeWizard" to "com.zapthemes.demo.ThemeWizardDemo",
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
// The title bar demo's windows as the screen shows them, native frame included (needs a desktop).
tasks.register<JavaExec>("screenCapturesTitleBar") {
    group = "documentation"
    description = "Saves screen captures of the title bar demo's windows, frame included, in every theme."
    mainClass = "com.zapthemes.demo.TitleBarDemo"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke", "--screen-captures", screenshotDir.get().dir("screen").asFile.absolutePath)
}
tasks.register("screenshots") {
    group = "documentation"
    description = "Saves screenshots of every demo in every theme."
    dependsOn(screenshotTasks)
}
