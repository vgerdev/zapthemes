plugins {
    id("zapthemes.publishing")
    id("zapthemes.swt-proxies")
    id("zapthemes.raw-widget-audit")
}

description = "Theme-aware proxies and stylers for the Nebula incubation widgets."

dependencies {
    api(project(":zapthemes-nebula-incubation-fork"))
    api(project(":zapthemes-swt"))

    testImplementation(project(":zapthemes-themes"))
    testImplementation(libs.jface.databinding)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// jface.databinding's POM asks for the official JFace; the fork provides the same capability.
configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("org.eclipse.platform:org.eclipse.jface") {
        select(candidates.first { (it.id as? ProjectComponentIdentifier)?.projectPath == ":zapthemes-jface" })
    }
}
