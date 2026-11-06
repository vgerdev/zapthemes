// ZapNotes: an editor-style JFace application built the way an outside user would build it, on the
// published com.zapthemes artifacts (docs/theme-authoring.md, "Add the library"). Before the first
// release they come from the staged repository: run `./gradlew publishToStaging` in the repository
// root, then `../../gradlew run` here (or `./gradlew -p apps/zapnotes run` from the root).
plugins {
    application
}

repositories {
    maven { url = uri("../../build/staging-deploy") }
    mavenCentral()
}

val zapthemes = providers.gradleProperty("zapthemesVersion").orElse("1.0.0-SNAPSHOT").get()
val os = System.getProperty("os.name").lowercase()
val arch = if (System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }) "aarch64" else "x86_64"
val swtFragment = when {
    os.contains("win") -> "win32.win32"
    os.contains("mac") -> "cocoa.macosx"
    else -> "gtk.linux"
} + "." + arch
val jvmFlags = listOf("--enable-native-access=ALL-UNNAMED") + if (os.contains("mac")) listOf("-XstartOnFirstThread") else listOf()

dependencies {
    implementation("com.zapthemes:zapthemes-jface:$zapthemes")
    implementation("com.zapthemes:zapthemes-jface-notifications:$zapthemes")
    // Source viewer for the note body (brings com.zapthemes:zapthemes-text).
    implementation("com.zapthemes:zapthemes-jface-text:$zapthemes")
    implementation("com.zapthemes:zapthemes-themes:$zapthemes")
    // The official data binding, on the forked JFace.
    implementation("org.eclipse.platform:org.eclipse.jface.databinding:1.16.0")
    implementation("org.eclipse.platform:org.eclipse.swt.$swtFragment:3.135.0")
}
// The POMs name the platform-neutral SWT placeholder; Gradle needs the fragment instead.
configurations.all {
    exclude(group = "org.eclipse.platform", module = "org.eclipse.swt")
    // jface.databinding asks for the official JFace; the fork provides the same capability.
    resolutionStrategy.capabilitiesResolution.withCapability("org.eclipse.platform:org.eclipse.jface") {
        select(candidates.first { (it.id as? ModuleComponentIdentifier)?.module == "zapthemes-jface" })
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

application {
    mainClass = "zapnotes.ZapNotes"
    applicationDefaultJvmArgs = jvmFlags
}

tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Opens two windows, every view and dialog in every bundled theme, and checks each control."
    mainClass = "zapnotes.ZapNotes"
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs(jvmFlags)
    args("--smoke")
}
tasks.register<JavaExec>("screenshots") {
    group = "documentation"
    description = "The smoke test, saving the windows and dialogs as PNG under build/screenshots."
    mainClass = "zapnotes.ZapNotes"
    classpath = sourceSets.main.get().runtimeClasspath
    jvmArgs(jvmFlags)
    args("--smoke", "--screenshots", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
}
