plugins {
    id("zapthemes.swt-platform")
    id("zapthemes.publishing")
}

description = "GEF Classic draw2d 3.23.100, republished unchanged for Nebula Timeline: GEF Classic is not on Maven Central."

// Downloaded and checksum-verified by scripts/fetch-upstream.sh from the GEF Classic 3.29.0 update site.
val draw2dJar = rootProject.layout.projectDirectory.file("third_party/gef/org.eclipse.draw2d.jar")

// Unpacked as a classes directory, so projects in this build compile against it like any module.
// The Eclipse signature would not match our manifest, so it and the OSGi manifest are dropped.
val unpack = tasks.register<Sync>("unpackDraw2d") {
    from(zipTree(draw2dJar)) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    }
    into(layout.buildDirectory.dir("draw2d"))
}
(sourceSets.main.get().output.classesDirs as ConfigurableFileCollection).from(unpack)
