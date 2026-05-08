import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("dev.kikugie.stonecutter")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.neoforge.moddev)
    id("maven-publish")
    alias(libs.plugins.mod.publish.plugin)
}

val mcVersion = stonecutter.current.version

version = providers.environmentVariable("MOD_VERSION").orElse("0.0.0").get() + "+neoforge-$mcVersion"
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    maven("https://thedarkcolour.github.io/KotlinForForge/") { name = "KotlinForForge" }
    maven { url = uri("https://maven.shedaniel.me") }
    maven { url = uri("https://maven.isxander.dev/releases") }

    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}

val clientSourceSet = sourceSets.create("client") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations.named(clientSourceSet.compileClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().compileClasspathConfigurationName])
}
configurations.named(clientSourceSet.runtimeClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().runtimeClasspathConfigurationName])
}

val gametestSourceSet = sourceSets.create("gametest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
    kotlin.srcDir("src/gametest/kotlin")
    resources.srcDir("src/gametest/resources")
}

configurations.named(gametestSourceSet.compileClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().compileClasspathConfigurationName])
}
configurations.named(gametestSourceSet.runtimeClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().runtimeClasspathConfigurationName])
}

val neoForgeVersion: String = libs.versions.neoforge.mc.get()

neoForge {
    version = neoForgeVersion

    addModdingDependenciesTo(clientSourceSet)
    addModdingDependenciesTo(gametestSourceSet)

    mods {
        register("connectedtank") {
            sourceSet(sourceSets.main.get())
            sourceSet(clientSourceSet)
            sourceSet(gametestSourceSet)
        }
    }

    runs {
        create("client") {
            client()
            sourceSet = clientSourceSet
        }
        create("server") {
            server()
        }
        create("gameTestServer") {
            type = "gameTestServer"
            sourceSet = gametestSourceSet
        }
    }
}

dependencies {
    val yaclVersion = "3.8.2+1.21.11-neoforge"
    val jadeVersion = "21.1.7+neoforge"
    val reiVersion = "21.11.814"

    implementation("thedarkcolour:kotlinforforge-neoforge:${libs.versions.kff.get()}")

    compileOnly("dev.isxander:yet-another-config-lib:$yaclVersion")
    runtimeOnly("dev.isxander:yet-another-config-lib:$yaclVersion")
    compileOnly("maven.modrinth:jade:$jadeVersion")
    runtimeOnly("maven.modrinth:jade:$jadeVersion")
    runtimeOnly("me.shedaniel:RoughlyEnoughItems-neoforge:$reiVersion")
}

tasks {
    processResources {
        val modVersion = project.version.toString().substringBefore("+")
        inputs.property("version", modVersion)
        inputs.property("minecraft_version", mcVersion)
        inputs.property("neoforge_version", neoForgeVersion)
        inputs.property("java_version", 21)

        filesMatching("META-INF/neoforge.mods.toml") {
            expand(
                mapOf(
                    "version" to inputs.properties["version"],
                    "minecraft_version" to inputs.properties["minecraft_version"],
                    "neoforge_version" to inputs.properties["neoforge_version"],
                    "java_version" to inputs.properties["java_version"],
                ),
            )
        }
    }
    jar {
        inputs.property("archivesName", base.archivesName)

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }
    withType<JavaCompile>().configureEach {
        options.release.set(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
    jvmToolchain(21)
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

publishMods {
    file.set(tasks.jar.flatMap { it.archiveFile })
    additionalFiles.from(tasks.named("sourcesJar").map { (it as Jar).archiveFile })
    changelog.set(providers.environmentVariable("CHANGELOG").orElse(""))
    type.set(STABLE)
    modLoaders.add("neoforge")

    modrinth {
        projectId.set(providers.environmentVariable("MODRINTH_ID"))
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
        minecraftVersions.add(mcVersion)
        requires("kotlin-for-forge")
    }
    curseforge {
        projectId.set(providers.environmentVariable("CURSEFORGE_ID"))
        accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
        minecraftVersions.add(mcVersion)
        requires("kotlin-for-forge")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    repositories {
    }
}
