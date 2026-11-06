plugins {
    id("zapthemes.publishing")
    id("zapthemes.swt-platform")
}

description = "NatTable bridge: the current ZapThemes theme as a NatTable ThemeConfiguration, switched live."

dependencies {
    api(project(":zapthemes-jface"))
    // NatTable paints its cells itself: it is used as published, not forked.
    api(libs.nattable.core)

    testImplementation(project(":zapthemes-themes"))
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
