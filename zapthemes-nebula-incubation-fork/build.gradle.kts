plugins {
    id("zapthemes.publishing")
    id("zapthemes.fork")
}

description = "Nebula incubation widgets, forked at build time like zapthemes-nebula-fork."

val nebula = rootProject.layout.projectDirectory.dir("third_party/nebula-upstream/widgets")

// The Eclipse ui.forms toolkits are left out: they would pull in the workbench.
val forms = listOf("**/forms/*.java")

zapthemesFork {
    themeSystemColors()
    upstream("collapsiblebuttons", nebula.dir("collapsiblebuttons/org.eclipse.nebula.widgets.collapsiblebuttons/src"))
    upstream("compositetable", nebula.dir("compositetable/org.eclipse.nebula.widgets.compositetable/src"))
    upstream("datechooser", nebula.dir("datechooser/org.eclipse.nebula.widgets.datechooser/src"))
    upstream("formattedtext", nebula.dir("formattedtext/org.eclipse.nebula.widgets.formattedtext/src"))
    upstream("radiogroup", nebula.dir("radiogroup/org.eclipse.nebula.widgets.radiogroup/src"), forms)
    upstream("picture", nebula.dir("picture/org.eclipse.nebula.widgets.picture/src"), forms)
    upstream("pagination", nebula.dir("pagination/org.eclipse.nebula.widgets.pagination/src"), forms)
    upstream("timeline", nebula.dir("timeline/org.eclipse.nebula.widgets.timeline/src"))
    // Timeline's EMF model (generated code, a second source folder upstream).
    upstream("timelinemodel", nebula.dir("timeline/org.eclipse.nebula.widgets.timeline/src-gen"))
}

dependencies {
    api(project(":zapthemes-jface"))
    api(libs.equinox.common)
    // Timeline: draw2d from the GEF Classic update site (scripts/fetch-upstream.sh), EMF from Maven Central.
    api(project(":zapthemes-draw2d"))
    api(libs.emf.ecore)
    implementation(libs.core.jobs)
    // DateChooser's and FormattedText's *ObservableValue classes: optional, as in upstream's OSGi
    // manifest. An application that binds them adds jface.databinding itself.
    compileOnly(libs.jface.databinding)
}

// jface.databinding's POM asks for the official JFace; the fork provides the same capability.
configurations.all {
    resolutionStrategy.capabilitiesResolution.withCapability("org.eclipse.platform:org.eclipse.jface") {
        select(candidates.first { (it.id as? ProjectComponentIdentifier)?.projectPath == ":zapthemes-jface" })
    }
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}
