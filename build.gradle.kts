import java.util.concurrent.TimeUnit
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.fabric.loom)
    id("maven-publish")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.spotless)
}

version = libs.versions.mod.version.get()
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
    // To change the versions see the gradle/libs.versions.toml file
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn.mappings) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    // Fabric API. This is technically optional, but you probably want it anyway.
    modImplementation(libs.fabric.api)
    modImplementation(libs.fabric.language.kotlin)

    "productionRuntimeMods"(libs.fabric.api)
    "productionRuntimeMods"(libs.fabric.language.kotlin)

    modCompileOnly(libs.yacl)
    modRuntimeOnly(libs.yacl)
    modCompileOnly(libs.modmenu)
    modRuntimeOnly(libs.modmenu)

    modRuntimeOnly(libs.rei)
    modCompileOnly(libs.jade)
    modRuntimeOnly(libs.jade)
}

tasks {
    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand("version" to inputs.properties["version"])
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
    @Suppress("UnstableApiUsage")
    named<UpdateDaemonJvm>("updateDaemonJvm") {
        languageVersion = JavaLanguageVersion.of(21)
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

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
    java {
        palantirJavaFormat()
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
