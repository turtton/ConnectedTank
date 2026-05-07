import java.util.concurrent.TimeUnit
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("dev.kikugie.stonecutter")
    alias(libs.plugins.fabric.loom)
    id("maven-publish")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.mod.publish.plugin)
}

val mcVersion = stonecutter.current.version

version = providers.environmentVariable("MOD_VERSION").orElse("dev").get() + "+fabric-$mcVersion"
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    maven { url = uri("https://maven.isxander.dev/releases") }
    maven { url = uri("https://maven.terraformersmc.com/releases") }

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

loom {
    splitEnvironmentSourceSets()

    mods {
        register("connectedtank") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
}

fabricApi {
    configureDataGeneration {
        client = true
    }
    @Suppress("UnstableApiUsage")
    configureTests {
        createSourceSet = true
        modId = "connectedtank-test"
        enableGameTests = true
        enableClientGameTests = false
        eula = true
    }
}

sourceSets.named("gametest") {
    kotlin.srcDir("src/gametest/kotlin")
}

val clientGametestSourceSet = sourceSets.create("clientGametest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
    compileClasspath += sourceSets.getByName("client").output
    runtimeClasspath += sourceSets.getByName("client").output
    kotlin.srcDir("src/clientGametest/kotlin")
}

configurations.named(clientGametestSourceSet.compileClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().compileClasspathConfigurationName])
    extendsFrom(configurations[sourceSets.getByName("client").compileClasspathConfigurationName])
}
configurations.named(clientGametestSourceSet.runtimeClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().runtimeClasspathConfigurationName])
    extendsFrom(configurations[sourceSets.getByName("client").runtimeClasspathConfigurationName])
}

loom {
    mods {
        register("connectedtank-client-test") {
            sourceSet(clientGametestSourceSet)
        }
    }

    runs {
        register("clientGameTest") {
            inherit(runs.getByName("client"))
            source(clientGametestSourceSet)
            property("fabric.client.gametest")
            property(
                "fabric.client.gametest.testModResourcesPath",
                file("src/clientGametest/resources").absolutePath,
            )
            runDir("build/run/clientGameTest")
        }
    }
}

dependencies {
    val fabricApiVersion = "0.148.0+26.1.2"
    val yaclVersion = "3.9.3+26.1-fabric"
    val modmenuVersion = "18.0.0-alpha.8"
    val jeiVersion = "29.5.0.28"
    val jadeVersion = "26.1.0+fabric"

    minecraft("com.mojang:minecraft:$mcVersion")
    implementation(libs.fabric.loader)

    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation(libs.fabric.language.kotlin)

    compileOnly("dev.isxander:yet-another-config-lib:$yaclVersion")
    runtimeOnly("dev.isxander:yet-another-config-lib:$yaclVersion")
    compileOnly("com.terraformersmc:modmenu:$modmenuVersion")
    runtimeOnly("com.terraformersmc:modmenu:$modmenuVersion")

    runtimeOnly("maven.modrinth:jei:$jeiVersion")
    compileOnly("maven.modrinth:jade:$jadeVersion")
    runtimeOnly("maven.modrinth:jade:$jadeVersion")
}

tasks {
    processResources {
        inputs.property("version", project.version)
        inputs.property("minecraft_version", mcVersion)
        inputs.property("java_version", 25)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to inputs.properties["version"], "minecraft_version" to inputs.properties["minecraft_version"], "java_version" to inputs.properties["java_version"]))
        }
    }
    jar {
        inputs.property("archivesName", base.archivesName)

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }
    withType<JavaCompile>().configureEach {
        options.release.set(25)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("25")
    }
    jvmToolchain(25)
}

java {
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

publishMods {
    file.set(tasks.jar.flatMap { it.archiveFile })
    additionalFiles.from(tasks.named("sourcesJar").map { (it as Jar).archiveFile })
    changelog.set(providers.environmentVariable("CHANGELOG").orElse(""))
    type.set(STABLE)
    modLoaders.add("fabric")

    modrinth {
        projectId.set(providers.environmentVariable("MODRINTH_ID"))
        accessToken.set(providers.environmentVariable("MODRINTH_TOKEN"))
        minecraftVersions.add(mcVersion)
        requires("fabric-api")
        requires("fabric-language-kotlin")
    }
    curseforge {
        projectId.set(providers.environmentVariable("CURSEFORGE_ID"))
        accessToken.set(providers.environmentVariable("CURSEFORGE_TOKEN"))
        minecraftVersions.add(mcVersion)
        requires("fabric-api")
        requires("fabric-language-kotlin")
    }
}

// configure the maven publication
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

// Auto-start Xvfb for headless client test execution (Wayland / headless environments)
val xvfbState = objects.property<Process>()
val xvfbShutdownHook = objects.property<Thread>()

fun needsXvfb(): Boolean {
    val display = System.getenv("DISPLAY")
    if (display.isNullOrBlank()) return true
    if (display.contains(":") && !display.startsWith(":")) return false
    val displayNum = display.removePrefix(":").takeWhile { it.isDigit() }
    val socket = File("/tmp/.X11-unix/X$displayNum")
    return !socket.exists()
}

fun findXvfb(): String? {
    val candidates = listOf("Xvfb", "/usr/bin/Xvfb")
    return candidates.firstOrNull { name ->
        runCatching {
            ProcessBuilder("which", name)
                .redirectErrorStream(true)
                .start()
                .waitFor() == 0
        }.getOrDefault(false)
    }
}

fun startXvfb(xvfb: String): Pair<Process, String> {
    for (displayNum in 99..199) {
        val display = ":$displayNum"
        if (File("/tmp/.X11-unix/X$displayNum").exists()) continue

        val process = ProcessBuilder(xvfb, display, "-screen", "0", "1280x1024x24", "-nolisten", "tcp")
            .redirectErrorStream(true)
            .start()

        val socketFile = File("/tmp/.X11-unix/X$displayNum")
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) break
            if (socketFile.exists()) return process to display
            Thread.sleep(100)
        }

        if (process.isAlive) process.destroyForcibly()
    }
    error("Failed to start Xvfb: no available display number in :99..:199")
}

val cleanupXvfbTask = tasks.register("cleanupXvfb") {
    notCompatibleWithConfigurationCache("Manages Xvfb process lifecycle at execution time")
    doLast {
        xvfbState.orNull?.let { process ->
            if (process.isAlive) {
                logger.lifecycle("Stopping Xvfb (pid: ${process.pid()})")
                process.destroy()
                process.waitFor(5, TimeUnit.SECONDS)
                if (process.isAlive) process.destroyForcibly()
            }
        }
        xvfbShutdownHook.orNull?.let { hook ->
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
    }
}

tasks.named<JavaExec>("runClientGameTest") {
    notCompatibleWithConfigurationCache("Manages Xvfb process lifecycle at execution time")
    finalizedBy(cleanupXvfbTask)

    doFirst {
        if (!needsXvfb()) return@doFirst

        val xvfb = findXvfb() ?: error(
            "No usable DISPLAY found and Xvfb is not installed. " +
                "Install Xvfb or run with a display server (e.g., xvfb-run ./gradlew runClientGameTest)",
        )

        val (process, display) = startXvfb(xvfb)
        xvfbState.set(process)
        val shutdownHook = Thread { if (process.isAlive) process.destroyForcibly() }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        xvfbShutdownHook.set(shutdownHook)

        logger.lifecycle("Started Xvfb on display $display (pid: ${process.pid()})")
        environment("DISPLAY", display)
    }
}
