plugins {
    id("zapthemes.swt-platform")
    id("zapthemes.raw-widget-audit")
    application
}

description = "Demo application."

dependencies {
    implementation(project(":zapthemes-swt"))
    implementation(project(":zapthemes-jface"))
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
tasks.register("smokeTest") {
    group = "verification"
    description = "Runs every demo smoke test."
    dependsOn(smokeStandalone, smokeJFace)
}
