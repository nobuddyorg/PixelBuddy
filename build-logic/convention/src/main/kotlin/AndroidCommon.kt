import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Configuration shared by the application and all Android library modules. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk {
            version =
                release(libs.intVersion("sdk-compile")) {
                    minorApiLevel = libs.intVersion("sdk-compile-minor")
                }
        }
        defaultConfig.apply {
            minSdk {
                version = release(libs.intVersion("sdk-min"))
            }
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions.apply {
            sourceCompatibility = javaTarget
            targetCompatibility = javaTarget
        }
        testOptions.apply {
            unitTests.isIncludeAndroidResources = true
        }
        lint.configureStrictLint(rootProject.file("lint.xml"))
    }
    configureKotlinCompilation()
    configureKover(sourceVariant = "debug")

    dependencies {
        add("testImplementation", libs.findLibrary("junit4").get())
        add("testImplementation", libs.findLibrary("kotlin-test").get())
    }
}
