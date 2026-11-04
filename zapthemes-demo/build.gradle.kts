plugins {
    id("zapthemes.swt-platform")
    application
}

description = "Demo application."

dependencies {
    implementation(project(":zapthemes-swt"))
    implementation(project(":zapthemes-themes"))
}

application {
    mainClass = "com.zapthemes.demo.SpikeDemo"
}

// Opens the demo, switches themes, checks every control and exits. Used in CI.
tasks.register<JavaExec>("smokeTest") {
    group = "verification"
    description = "Runs the demo headless-ish: builds the UI, switches theme, verifies, exits."
    mainClass = application.mainClass
    classpath = sourceSets.main.get().runtimeClasspath
    args("--smoke")
}
