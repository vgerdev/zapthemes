plugins {
    id("zapthemes.fork")
}

description = "Eclipse's SWT ControlExample and JFace snippets, unchanged but for the codemod, on ZapThemes. Not published."

val swtExamples = rootProject.layout.projectDirectory.dir("third_party/swt-upstream/examples/org.eclipse.swt.examples/src")
val jfaceSnippets = rootProject.layout.projectDirectory.dir("third_party/jface-upstream/examples/org.eclipse.jface.snippets/Eclipse JFace Snippets")

// Only the ControlExample package of the SWT examples.
val otherSwtExamples = swtExamples.dir("org/eclipse/swt/examples").asFile.listFiles { f -> f.isDirectory && f.name != "controlexample" }
    .orEmpty().map { "org/eclipse/swt/examples/${it.name}/**" }

zapthemesFork {
    // The examples paint with display.getSystemColor(...): route those through the theme.
    themeSystemColors()
    upstream("controlexample", swtExamples, otherSwtExamples)
    // Left out: snippets for bundles ZapThemes does not ship yet (JFace notifications, data binding).
    upstream("jfacesnippets", jfaceSnippets, listOf(
        "org/eclipse/jface/snippets/notifications/**",
        "org/eclipse/jface/snippets/dialogs/Snippet081NotificationPopup.java",
        "org/eclipse/jface/snippets/viewers/Snippet064TreeViewerReplacingElements.java",
        "org/eclipse/jface/snippets/viewers/Snippet065TableViewerReplacingElements.java",
    ))
}

dependencies {
    implementation(project(":zapthemes-jface"))
    implementation(project(":zapthemes-themes"))
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}

// -Ptheme=zap-dark (or auto, the default, or a theme file) picks the starting theme.
val startTheme = providers.gradleProperty("theme").orElse("auto")
fun JavaExec.example(main: String) {
    mainClass = main
    classpath = sourceSets.main.get().runtimeClasspath
    systemProperty("zapthemes.theme", startTheme.get())
}

tasks.register<JavaExec>("runControlExample") {
    group = "application"
    description = "Runs Eclipse's SWT ControlExample, unchanged but for the codemod, on ZapThemes."
    example("org.eclipse.swt.examples.controlexample.ControlExample")
}
tasks.register<JavaExec>("runSnippets") {
    group = "application"
    description = "Runs the JFace snippets browser; each snippet starts in its own JVM in the chosen theme."
    example("com.zapthemes.examples.SnippetsApp")
}
tasks.register<JavaExec>("runExamples") {
    group = "application"
    description = "Runs ControlExample and the JFace snippets together in one window."
    example("com.zapthemes.examples.ExamplesApp")
}

val smokeExamples = tasks.register<JavaExec>("smokeTestExamples") {
    group = "verification"
    description = "Checks every ControlExample tab in every bundled theme, and that the snippets are listed."
    example("com.zapthemes.examples.ExamplesApp")
    args("--smoke")
}
val smokeSnippets = tasks.register<JavaExec>("smokeTestSnippets") {
    group = "verification"
    description = "Opens the JFace snippets browser and checks it lists the snippets."
    example("com.zapthemes.examples.SnippetsApp")
    args("--smoke")
}
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs every examples smoke test."
    dependsOn(smokeExamples, smokeSnippets)
}
tasks.register<JavaExec>("screenshots") {
    group = "documentation"
    description = "Saves every ControlExample tab and the snippets browser in every theme under build/screenshots."
    example("com.zapthemes.examples.ExamplesApp")
    args("--smoke", "--screenshots", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}
tasks.register<JavaExec>("screenshotsSnippets") {
    group = "verification"
    description = "Runs every JFace snippet (-Psnippet=<name part> for some) in its own JVM in -Ptheme (default zap-dark): checks it is tagged and closes, saves a PNG."
    mainClass = "com.zapthemes.examples.SnippetShots"
    classpath = sourceSets.main.get().runtimeClasspath
    args(layout.buildDirectory.dir("screenshots/snippets").get().asFile.absolutePath, providers.gradleProperty("theme").orElse("zap-dark").get())
    providers.gradleProperty("snippet").orNull?.let { args(it) }
}
