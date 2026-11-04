plugins {
    id("zapthemes.publishing")
    id("zapthemes.fork")
}

description = "Nebula incubation widgets, forked at build time like zapthemes-nebula-fork."

val nebula = rootProject.layout.projectDirectory.dir("third_party/nebula-upstream/widgets")

// Data binding is left out for now (revisited for all controls later); so are the Eclipse
// ui.forms toolkits, which would pull in the workbench.
val dataBinding = listOf("**/*ObservableValue.java")
val forms = listOf("**/forms/*.java")

zapthemesFork {
    themeSystemColors()
    upstream("collapsiblebuttons", nebula.dir("collapsiblebuttons/org.eclipse.nebula.widgets.collapsiblebuttons/src"))
    upstream("compositetable", nebula.dir("compositetable/org.eclipse.nebula.widgets.compositetable/src"))
    upstream("datechooser", nebula.dir("datechooser/org.eclipse.nebula.widgets.datechooser/src"), dataBinding)
    upstream("formattedtext", nebula.dir("formattedtext/org.eclipse.nebula.widgets.formattedtext/src"), dataBinding)
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
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}
