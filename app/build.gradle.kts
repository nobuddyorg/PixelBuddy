plugins {
    alias(libs.plugins.pixelbuddy.android.application)
    alias(libs.plugins.pixelbuddy.android.compose)
}

/**
 * Release version: `-Ppixelbuddy.versionName=X.Y.Z` (set by the release workflow from the tag `vX.Y.Z`).
 * versionCode = X·1,000,000 + Y·1,000 + Z, so every release updates over the previous one.
 * Release APKs are built unsigned and signed afterwards with apksigner, so Gradle never sees the keystore
 * (see docs/RELEASING.md).
 */
val releaseVersion: String = providers.gradleProperty("pixelbuddy.versionName").getOrElse("0.0.1")

fun versionCodeOf(version: String): Int {
    val match =
        Regex("""(\d{1,4})\.(\d{1,3})\.(\d{1,3})""").matchEntire(version)
            ?: error("pixelbuddy.versionName must look like X.Y.Z, was '$version'")
    val (major, minor, patch) = match.destructured.toList().map(String::toInt)
    require(major <= 2099) { "major version too large for a versionCode: $major" }
    return major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy"

    defaultConfig {
        applicationId = "io.github.nobuddyorg.pixelbuddy"
        versionCode = versionCodeOf(releaseVersion)
        versionName = releaseVersion
    }

    androidResources {
        // Per-app language support: generates locales_config.xml from the values-* folders (#19).
        generateLocaleConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.feature.home)
    implementation(projects.feature.settings)
    implementation(projects.core.data)
    implementation(projects.core.weather)
    implementation(projects.core.designSystem)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.androidx.test.core)
}
