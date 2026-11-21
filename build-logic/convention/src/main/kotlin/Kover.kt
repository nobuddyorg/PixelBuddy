import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Name of the Kover report variant that holds the unit-test coverage of every module. */
internal const val KOVER_VARIANT = "unit"

/**
 * Applies Kover and maps this module's unit-test variant (Android `debug`, or `jvm`) to the
 * shared [KOVER_VARIANT], so the root project can merge all modules into one report.
 */
internal fun Project.configureKover(sourceVariant: String) {
    pluginManager.apply("org.jetbrains.kotlinx.kover")
    extensions.configure<KoverProjectExtension> {
        currentProject {
            createVariant(KOVER_VARIANT) {
                add(sourceVariant)
            }
        }
    }
}
