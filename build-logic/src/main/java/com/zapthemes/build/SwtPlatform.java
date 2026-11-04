package com.zapthemes.build;

import java.util.Locale;

/**
 * An SWT platform triple ({@code ws.os.arch}), e.g. {@code win32.win32.x86_64} or
 * {@code gtk.freebsd.aarch64}.
 */
public record SwtPlatform(String ws, String os, String arch) {

    public static SwtPlatform parse(String id) {
        String[] parts = id.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("SWT platform must be ws.os.arch, got: " + id);
        }
        return new SwtPlatform(parts[0], parts[1], parts[2]);
    }

    /** Detects the platform of the JVM running Gradle. */
    public static SwtPlatform detect() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        String arch = switch (osArch) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "ppc64le" -> "ppc64le";
            case "riscv64" -> "riscv64";
            default -> throw new IllegalStateException("Unsupported architecture for SWT: " + osArch);
        };
        if (osName.startsWith("windows")) {
            return new SwtPlatform("win32", "win32", arch);
        }
        if (osName.startsWith("mac")) {
            return new SwtPlatform("cocoa", "macosx", arch);
        }
        if (osName.startsWith("linux")) {
            return new SwtPlatform("gtk", "linux", arch);
        }
        if (osName.startsWith("freebsd")) {
            return new SwtPlatform("gtk", "freebsd", arch);
        }
        throw new IllegalStateException("Unsupported OS for SWT: " + osName);
    }

    /**
     * Eclipse publishes no FreeBSD fragment (neither on Maven Central nor on download.eclipse.org),
     * so FreeBSD builds use a locally installed SWT jar, e.g. from the {@code x11-toolkits/swt} port.
     */
    public boolean publishedOnMavenCentral() {
        return !os.equals("freebsd");
    }

    public String id() {
        return ws + "." + os + "." + arch;
    }

    public String artifactId() {
        return "org.eclipse.swt." + id();
    }
}
