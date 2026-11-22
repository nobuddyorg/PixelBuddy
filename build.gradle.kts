plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.pitest) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
}

val ktlintVersion: String = libs.versions.ktlint.get()

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
    }
    format("misc") {
        target("**/*.md", "**/*.xml", "**/*.toml", "**/*.yml", ".gitignore", ".editorconfig")
        targetExclude("**/build/**", ".gradle/**", ".kotlin/**", "gradle/verification-metadata.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Merged unit-test coverage of all modules: ./gradlew :koverXmlReportUnit :koverVerifyUnit
dependencies {
    kover(projects.app)
    kover(projects.feature.home)
    kover(projects.feature.settings)
    kover(projects.core.data)
    kover(projects.core.weather)
    kover(projects.core.designSystem)
    kover(projects.core.pixelEngine)
}

kover {
    currentProject {
        // The root project has no code of its own; it only merges the modules' "unit" variants.
        createVariant("unit") {}
    }
    reports {
        filters {
            excludes {
                classes("*.BuildConfig", "*.R", "*.R$*", "*.ComposableSingletons*")
            }
        }
        variant("unit") {
            verify {
                rule("Line coverage of all modules") {
                    minBound(80)
                }
            }
        }
    }
}
