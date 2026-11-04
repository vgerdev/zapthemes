plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        register("javaConventions") {
            id = "zapthemes.java-conventions"
            implementationClass = "com.zapthemes.build.JavaConventionsPlugin"
        }
        register("swtPlatform") {
            id = "zapthemes.swt-platform"
            implementationClass = "com.zapthemes.build.SwtPlatformPlugin"
        }
        register("swtProxies") {
            id = "zapthemes.swt-proxies"
            implementationClass = "com.zapthemes.build.SwtProxiesPlugin"
        }
    }
}
