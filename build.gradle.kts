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

version = providers.environmentVariable("MOD_VERSION").orElse("dev").get() + "+mc$mcVersion"
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    maven { url = uri("https://maven.shedaniel.me") }
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

    createRemapConfigurations(clientGametestSourceSet)

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
    val yarnMappings = when (mcVersion) {
        "1.21.8" -> "1.21.8+build.1"
        "1.21.11" -> "1.21.11+build.5"
        else -> error("Unsupported MC version: $mcVersion")
    }
    val fabricApiVersion = when (mcVersion) {
        "1.21.8" -> "0.132.0+1.21.8"
        "1.21.11" -> "0.141.3+1.21.11"
        else -> error("Unsupported MC version: $mcVersion")
    }
    val yaclVersion = when (mcVersion) {
        "1.21.8" -> "3.7.1+1.21.6-fabric"
        "1.21.11" -> "3.8.2+1.21.11-fabric"
        else -> error("Unsupported MC version: $mcVersion")
    }
    val modmenuVersion = when (mcVersion) {
        "1.21.8" -> "15.0.1"
        "1.21.11" -> "17.0.0"
        else -> error("Unsupported MC version: $mcVersion")
    }
    val reiVersion = when (mcVersion) {
        "1.21.8" -> "20.0.811"
        "1.21.11" -> "21.11.814"
        else -> error("Unsupported MC version: $mcVersion")
    }
    val jadeVersion = when (mcVersion) {
        "1.21.8" -> "19.3.2+fabric"
        "1.21.11" -> "21.1.6+fabric"
        else -> error("Unsupported MC version: $mcVersion")
    }

    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation(libs.fabric.loader)

    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation(libs.fabric.language.kotlin)

    "productionRuntimeMods"("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    "productionRuntimeMods"(libs.fabric.language.kotlin)

    modCompileOnly("dev.isxander:yet-another-config-lib:$yaclVersion")
    modRuntimeOnly("dev.isxander:yet-another-config-lib:$yaclVersion")
    modCompileOnly("com.terraformersmc:modmenu:$modmenuVersion")
    modRuntimeOnly("com.terraformersmc:modmenu:$modmenuVersion")

    modRuntimeOnly("me.shedaniel:RoughlyEnoughItems-fabric:$reiVersion")
    modCompileOnly("maven.modrinth:jade:$jadeVersion")
    modRuntimeOnly("maven.modrinth:jade:$jadeVersion")
}

tasks {
    processResources {
        inputs.property("version", project.version)
        inputs.property("minecraft_version", mcVersion)

        filesMatching("fabric.mod.json") {
            expand(mapOf("version" to inputs.properties["version"], "minecraft_version" to inputs.properties["minecraft_version"]))
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
    val clientGametestJar = register<Jar>("clientGametestJar") {
        from(clientGametestSourceSet.output)
        archiveClassifier.set("client-gametest")
    }
    val remapClientGametestJar = register<net.fabricmc.loom.task.RemapJarTask>("remapClientGametestJar") {
        inputFile.set(clientGametestJar.flatMap { it.archiveFile })
        sourceNamespace.set("named")
        targetNamespace.set("intermediary")
        archiveClassifier.set("client-gametest-remapped")
        classpath.from(clientGametestSourceSet.compileClasspath)
        addNestedDependencies.set(false)
    }
    @Suppress("UnstableApiUsage")
    register<net.fabricmc.loom.task.prod.ClientProductionRunTask>("runProductionClientGameTest") {
        jvmArgs.add("-Dfabric.client.gametest")
        jvmArgs.add(
            "-Dfabric.client.gametest.testModResourcesPath=${file("src/clientGametest/resources").absolutePath}",
        )
        mods.from(remapClientGametestJar)
        runDir.set(project.layout.projectDirectory.dir("build/run/clientGameTest"))
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
    jvmToolchain(21)
}

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

publishMods {
    file.set(tasks.remapJar.flatMap { it.archiveFile })
    additionalFiles.from(tasks.remapSourcesJar.flatMap { it.archiveFile })
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

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}

// Auto-start Xvfb for headless client test execution (Wayland / headless environments)
// Shared state for Xvfb process between run task and cleanup task
val xvfbState = objects.property<Process>()
val xvfbShutdownHook = objects.property<Thread>()

fun needsXvfb(): Boolean {
    val display = System.getenv("DISPLAY")
    if (display.isNullOrBlank()) return true
    // For remote displays (e.g., SSH X11 forwarding like localhost:10.0), trust the env
    if (display.contains(":") && !display.startsWith(":")) return false
    // For local displays, check if the X11 socket file exists (simple heuristic; does not probe connection)
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
    // Try multiple display numbers to handle concurrent usage
    for (displayNum in 99..199) {
        val display = ":$displayNum"
        if (File("/tmp/.X11-unix/X$displayNum").exists()) continue

        val process = ProcessBuilder(xvfb, display, "-screen", "0", "1280x1024x24", "-nolisten", "tcp")
            .redirectErrorStream(true)
            .start()

        // Poll for X11 socket to appear (readiness check)
        val socketFile = File("/tmp/.X11-unix/X$displayNum")
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive) break
            if (socketFile.exists()) return process to display
            Thread.sleep(100)
        }

        // This display didn't work, clean up and try next
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
        // Remove shutdown hook after process cleanup (keeps hook as safety net until stop completes)
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
        // Last-resort cleanup for JVM crash (daemon shutdown)
        val shutdownHook = Thread { if (process.isAlive) process.destroyForcibly() }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        xvfbShutdownHook.set(shutdownHook)

        logger.lifecycle("Started Xvfb on display $display (pid: ${process.pid()})")
        environment("DISPLAY", display)
    }
}
