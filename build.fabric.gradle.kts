import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("dev.kikugie.stonecutter")
    alias(libs.plugins.fabric.loom.remap)
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

val clienttestSourceSet = sourceSets.create("clienttest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
    compileClasspath += sourceSets.getByName("client").output
    runtimeClasspath += sourceSets.getByName("client").output
    kotlin.srcDir("src/clienttest/kotlin")
}

configurations.named(clienttestSourceSet.compileClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().compileClasspathConfigurationName])
    extendsFrom(configurations[sourceSets.getByName("client").compileClasspathConfigurationName])
}
configurations.named(clienttestSourceSet.runtimeClasspathConfigurationName) {
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

loom {
    mods {
        register("connectedtank-clienttest") {
            sourceSet(clienttestSourceSet)
        }
    }

    createRemapConfigurations(clienttestSourceSet)

    runs {
        register("clientTest") {
            inherit(runs.getByName("client"))
            source(clienttestSourceSet)
            property("connectedtank.clienttest")
            runDir("build/run/clientTest")
        }
    }
}

dependencies {
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
    mappings(loom.officialMojangMappings())
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

    "clienttestImplementation"(libs.kotlinx.coroutines.core)
}

tasks {
    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        inputs.property("version", project.version)
        inputs.property("minecraft_version", mcVersion)
        inputs.property("java_version", 21)

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
        options.release.set(21)
    }
    withType<Jar>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
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

/**
 * Compile a stub libpulse-simple.so that returns dummy handles to prevent
 * flite (TTS) from crashing with SIGABRT when PulseAudio daemon is unavailable.
 * flite calls pa_simple_write() without null-checking the handle from pa_simple_new().
 */
fun ensurePulseStub(): File? {
    val stubDir = layout.buildDirectory.dir("pulse-stub").get().asFile
    val stubLib = File(stubDir, "libpulse-simple-stub.so")
    if (stubLib.exists()) return stubLib

    // Check if gcc is available
    val hasGcc = runCatching {
        ProcessBuilder("which", "gcc").redirectErrorStream(true).start().waitFor() == 0
    }.getOrDefault(false)
    if (!hasGcc) return null

    stubDir.mkdirs()
    val stubSrc = File(stubDir, "pulse_stub.c")
    stubSrc.writeText(
        """
        #include <stddef.h>
        void *pa_simple_new(const void *s, const char *n, int d,
                            const char *dev, const char *sn,
                            const void *ss, const void *map, int *e) {
            static char dummy; return &dummy;
        }
        int pa_simple_write(void *p, const void *data, size_t bytes, int *e) { return 0; }
        int pa_simple_drain(void *p, int *e) { return 0; }
        void pa_simple_free(void *p) {}
        int pa_simple_read(void *p, void *data, size_t bytes, int *e) { return 0; }
        size_t pa_simple_get_latency(void *p, int *e) { return 0; }
        int pa_simple_flush(void *p, int *e) { return 0; }
        """.trimIndent(),
    )
    val result = ProcessBuilder("gcc", "-shared", "-fPIC", "-o", stubLib.absolutePath, stubSrc.absolutePath)
        .redirectErrorStream(true)
        .start()
    if (result.waitFor(10, TimeUnit.SECONDS) && result.exitValue() == 0) {
        return stubLib
    }
    return null
}

fun startXvfb(xvfb: String): Pair<Process, String> {
    for (displayNum in 99..199) {
        val display = ":$displayNum"
        if (File("/tmp/.X11-unix/X$displayNum").exists()) continue

        // Use file lock to prevent race conditions when multiple Gradle subprojects start Xvfb in parallel
        val lockFile = File("/tmp/.xvfb-gradle-lock-$displayNum")
        val raf = try {
            RandomAccessFile(lockFile, "rw")
        } catch (_: Exception) {
            continue
        }
        val lock = try {
            raf.channel.tryLock()
        } catch (_: Exception) {
            raf.close()
            continue
        }
        if (lock == null) {
            raf.close()
            continue
        }

        try {
            // Double-check socket after acquiring lock
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
        } finally {
            lock.release()
            raf.close()
        }
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
        environment("PULSE_SERVER", "/dev/null")
        environment("ALSOFT_DRIVERS", "null")
        ensurePulseStub()?.let { stub ->
            environment("LD_PRELOAD", stub.absolutePath)
            logger.lifecycle("Using PulseAudio stub: ${stub.absolutePath}")
        }
    }
}

tasks.named<JavaExec>("runClientTest") {
    notCompatibleWithConfigurationCache("Manages Xvfb process lifecycle at execution time")
    finalizedBy(cleanupXvfbTask)

    doFirst {
        if (!needsXvfb()) return@doFirst

        val xvfb = findXvfb() ?: error(
            "No usable DISPLAY found and Xvfb is not installed. " +
                "Install Xvfb or run with a display server (e.g., xvfb-run ./gradlew runClientTest)",
        )

        val (process, display) = startXvfb(xvfb)
        xvfbState.set(process)
        val shutdownHook = Thread { if (process.isAlive) process.destroyForcibly() }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        xvfbShutdownHook.set(shutdownHook)

        logger.lifecycle("Started Xvfb on display $display (pid: ${process.pid()})")
        environment("DISPLAY", display)
        environment("PULSE_SERVER", "/dev/null")
        environment("ALSOFT_DRIVERS", "null")
        ensurePulseStub()?.let { stub ->
            environment("LD_PRELOAD", stub.absolutePath)
            logger.lifecycle("Using PulseAudio stub: ${stub.absolutePath}")
        }
    }
}
