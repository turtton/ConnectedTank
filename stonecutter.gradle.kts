plugins {
    id("dev.kikugie.stonecutter")
    alias(libs.plugins.spotless)
}

stonecutter active "1.21.8"

repositories {
    mavenCentral()
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint().editorConfigOverride(
            mapOf(
                "ktlint_standard_import-ordering" to "disabled",
                "ktlint_standard_comment-spacing" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
    java {
        target("src/**/*.java")
        palantirJavaFormat("2.90.0")
    }
}
