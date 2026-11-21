import com.android.build.api.dsl.Lint
import java.io.File

/** Lint is strict: every warning fails the build. [config] is the shared `lint.xml` in the root project. */
internal fun Lint.configureStrictLint(config: File) {
    lintConfig = config
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = false
    // All user-visible text goes through string resources (#19).
    error += setOf("HardcodedText", "SetTextI18n", "MissingTranslation", "ExtraTranslation")
    // Dependency updates come from Dependabot; lint must not fail when a new version is published.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
}
