pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            setUrl("https://maven.fabricmc.net/")
        }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.kikugie.stonecutter") version "0.9.3"
}

stonecutter {
    create(rootProject) {
        versions("1.21.8", "1.21.11")
        vcsVersion = "1.21.8"
    }
}
