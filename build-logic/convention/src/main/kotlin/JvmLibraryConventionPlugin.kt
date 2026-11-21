import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin/JVM module without any Android dependency. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")

            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = javaTarget
                targetCompatibility = javaTarget
            }
            extensions.configure<KotlinJvmProjectExtension> {
                explicitApi()
                compilerOptions {
                    jvmTarget.set(JvmTarget.fromTarget(javaTarget.toString()))
                }
            }
            configureKotlinCompilation()

            tasks.withType<Test>().configureEach {
                useJUnitPlatform()
            }

            dependencies {
                val junitBom = platform(libs.findLibrary("junit-bom").get())
                add("testImplementation", junitBom)
                add("testImplementation", libs.findLibrary("junit-jupiter").get())
                add("testImplementation", libs.findLibrary("kotlin-test").get())
                add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
            }
        }
    }
}
