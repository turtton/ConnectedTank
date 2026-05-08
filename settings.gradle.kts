pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            setUrl("https://maven.fabricmc.net/")
        }
        maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
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
        version("1.21.8-fabric", "1.21.8").buildscript("build.fabric.gradle.kts")
        version("1.21.11-fabric", "1.21.11").buildscript("build.fabric.gradle.kts")
        version("1.21.11-neoforge", "1.21.11").buildscript("build.neoforge.gradle.kts")
        version("26.1-fabric", "26.1").buildscript("build.fabric.unobfuscated.gradle.kts")
        vcsVersion = "1.21.8-fabric"
    }
}
