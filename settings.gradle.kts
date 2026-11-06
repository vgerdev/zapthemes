pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "zapthemes"

include(
    "zapthemes-core",
    "zapthemes-swt",
    "zapthemes-widgets",
    "zapthemes-jface",
    "zapthemes-jface-notifications",
    "zapthemes-jface-text",
    "zapthemes-text",
    "zapthemes-nebula-fork",
    "zapthemes-nebula",
    "zapthemes-draw2d",
    "zapthemes-nebula-incubation-fork",
    "zapthemes-nebula-incubation",
    "zapthemes-themes",
    "zapthemes-tools",
    "zapthemes-demo",
    "zapthemes-samples",
    "zapthemes-examples",
)
