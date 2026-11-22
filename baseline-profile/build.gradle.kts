import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

// Startup benchmark and baseline profile for :app (#15). Runs on a device or emulator (API 33+ without root):
//   ./gradlew :app:generateBaselineProfile                            writes app/src/release/generated/baselineProfiles
//   ./gradlew :baseline-profile:connectedBenchmarkReleaseAndroidTest  measures cold startup
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "io.github.nobuddyorg.pixelbuddy.baselineprofile"
    compileSdk {
        version =
            release(
                libs.versions.sdk.compile
                    .asProvider()
                    .get()
                    .toInt(),
            ) {
                minorApiLevel =
                    libs.versions.sdk.compile.minor
                        .get()
                        .toInt()
            }
    }

    defaultConfig {
        minSdk {
            version =
                release(
                    libs.versions.sdk.min
                        .get()
                        .toInt(),
                )
        }
        targetSdk {
            version =
                release(
                    libs.versions.sdk.target
                        .get()
                        .toInt(),
                )
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

baselineProfile {
    // The emulator in CI, or a phone connected over adb.
    useConnectedDevices = true
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions { allWarningsAsErrors.set(true) }
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
