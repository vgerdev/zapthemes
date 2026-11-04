plugins {
    id("zapthemes.swt-platform")
}

description = "Build tools: proxy generator, JFace/Nebula codemod and the raw-widget audit."

dependencies {
    implementation(libs.javaparser.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val jfaceUpstream = rootProject.layout.projectDirectory.dir("third_party/jface-upstream/bundles/org.eclipse.jface/src")

tasks.test {
    systemProperty("zapthemes.jfaceSrc", jfaceUpstream.asFile.absolutePath)
}

// Spike: run the codemod over three JFace files and write the result for review.
tasks.register<JavaExec>("codemodSpike") {
    group = "verification"
    description = "Rewrites ButtonFactory/Window/ToolTip from upstream JFace into build/codemod-spike."
    mainClass = "com.zapthemes.tools.Codemod"
    classpath = sourceSets.main.get().runtimeClasspath
    val out = layout.buildDirectory.dir("codemod-spike").get().asFile
    args(
        "--source-root", jfaceUpstream.asFile.absolutePath,
        "--proxies", rootProject.file("zapthemes-swt/src/proxies/proxies.txt").absolutePath,
        "--out", out.absolutePath,
        "org/eclipse/jface/widgets/ButtonFactory.java",
        "org/eclipse/jface/window/Window.java",
        "org/eclipse/jface/window/ToolTip.java",
    )
}

// Census of every raw widget creation in upstream JFace (sizes Phase 2). Fails while any remain.
tasks.register<JavaExec>("auditJfaceUpstream") {
    group = "verification"
    description = "Counts raw SWT widget creations in upstream JFace, per widget class."
    mainClass = "com.zapthemes.tools.Audit"
    classpath = sourceSets.main.get().runtimeClasspath
    args("--source-root", jfaceUpstream.asFile.absolutePath)
    isIgnoreExitValue = true
}
