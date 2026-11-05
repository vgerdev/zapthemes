plugins {
    id("zapthemes.fork")
}

description = "Nebula released widgets, forked at build time: drop-in org.eclipse.nebula.* whose SWT widgets are ZapThemes proxies."

val nebula = rootProject.layout.projectDirectory.dir("third_party/nebula-upstream/widgets")

// One upstream per Nebula bundle: name -> bundle directory under widgets/.
val bundles = mapOf(
    "badgedlabel" to "badgedlabel/org.eclipse.nebula.widgets.badgedlabel",
    "carousel" to "carousel/org.eclipse.nebula.widgets.carousel",
    "cdatetime" to "cdatetime/org.eclipse.nebula.widgets.cdatetime",
    "cwt" to "cwt/org.eclipse.nebula.cwt",
    "chips" to "chips/org.eclipse.nebula.widgets.chips",
    "ctreecombo" to "ctreecombo/org.eclipse.nebula.widgets.ctreecombo",
    "gallery" to "gallery/org.eclipse.nebula.widgets.gallery",
    "grid" to "grid/org.eclipse.nebula.widgets.grid",
    "opalcommons" to "opal/commons/org.eclipse.nebula.widgets.opal.commons",
    "opalbreadcrumb" to "opal/breadcrumb/org.eclipse.nebula.widgets.opal.breadcrumb",
    "opalcheckboxgroup" to "opal/checkboxgroup/org.eclipse.nebula.widgets.opal.checkboxgroup",
    "opalduallist" to "opal/duallist/org.eclipse.nebula.widgets.opal.duallist",
    "opalheader" to "opal/header/org.eclipse.nebula.widgets.opal.header",
    "opalrangeslider" to "opal/rangeslider/org.eclipse.nebula.widgets.opal.rangeslider",
    "opalstarrating" to "opal/starrating/org.eclipse.nebula.widgets.opal.starrating",
    "opaltitledseparator" to "opal/titledseparator/org.eclipse.nebula.widgets.opal.titledseparator",
    "opalroundedtoolbar" to "opal/roundedtoolbar/org.eclipse.nebula.widgets.opal.roundedtoolbar",
    "opalswitchbutton" to "opal/switchbutton/org.eclipse.nebula.widgets.opal.switchbutton",
    "opaltextassist" to "opal/textassist/org.eclipse.nebula.widgets.opal.textassist",
    "pgroup" to "pgroup/org.eclipse.nebula.widgets.pgroup",
    "progresscircle" to "progresscircle/org.eclipse.nebula.widgets.progresscircle",
    "segmentedbar" to "segmentedbar/org.eclipse.nebula.widgets.segmentedbar",
    "tablecombo" to "tablecombo/org.eclipse.nebula.widgets.tablecombo",
    "tiles" to "tiles/org.eclipse.nebula.widgets.tiles",
    "pshelf" to "pshelf/org.eclipse.nebula.widgets.pshelf",
    "passwordrevealer" to "passwordrevealer/org.eclipse.nebula.widgets.passwordrevealer",
    "calendarcombo" to "calendarcombo/org.eclipse.nebula.widgets.calendarcombo",
    "splitbutton" to "splitbutton/org.eclipse.nebula.widgets.splitbutton",
)

zapthemesFork {
    // Nebula paints with display.getSystemColor(...): route those through the theme.
    themeSystemColors()
    bundles.forEach { (name, path) -> upstream(name, nebula.dir("$path/src")) }
}

dependencies {
    api(project(":zapthemes-jface"))
    api(libs.equinox.common)
}

// Upstream code: its warnings are upstream's to fix.
tasks.compileJava {
    options.compilerArgs.removeIf { it.startsWith("-Xlint") }
    options.compilerArgs.addAll(listOf("-Xlint:none", "-nowarn"))
}
