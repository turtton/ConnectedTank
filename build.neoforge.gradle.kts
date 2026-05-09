import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
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

sourceSets.main.get().resources.srcDir("src/main/generated")

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

val clienttestSourceSet = sourceSets.create("clienttest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
    compileClasspath += clientSourceSet.output
    runtimeClasspath += clientSourceSet.output
    kotlin.srcDir("src/clienttest/kotlin")
}

configurations.named(clienttestSourceSet.compileClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().compileClasspathConfigurationName])
    extendsFrom(configurations[clientSourceSet.compileClasspathConfigurationName])
}
configurations.named(clienttestSourceSet.runtimeClasspathConfigurationName) {
    extendsFrom(configurations[sourceSets.main.get().runtimeClasspathConfigurationName])
    extendsFrom(configurations[clientSourceSet.runtimeClasspathConfigurationName])
}

val neoForgeVersion: String = libs.versions.neoforge.mc.get()

neoForge {
    version = neoForgeVersion

    addModdingDependenciesTo(clientSourceSet)
    addModdingDependenciesTo(gametestSourceSet)
    addModdingDependenciesTo(clienttestSourceSet)

    mods {
        register("connectedtank") {
            sourceSet(sourceSets.main.get())
            sourceSet(clientSourceSet)
            sourceSet(gametestSourceSet)
            sourceSet(clienttestSourceSet)
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
        create("clientTest") {
            client()
            sourceSet = clienttestSourceSet
            systemProperties.put("connectedtank.clienttest", "true")
            systemProperties.put("mixin.configs", "connectedtank.clienttest.mixins.json")
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

    "clienttestImplementation"(libs.kotlinx.coroutines.core)
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
