import com.android.build.api.dsl.Lint

/** Lint is strict: every warning fails the build. */
internal fun Lint.configureStrictLint() {
    abortOnError = true
    warningsAsErrors = true
    checkReleaseBuilds = false
    // All user-visible text goes through string resources (#19).
    error += setOf("HardcodedText", "SetTextI18n", "MissingTranslation", "ExtraTranslation")
    // Dependency updates come from Dependabot; lint must not fail when a new version is published.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
}
