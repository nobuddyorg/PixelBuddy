package io.github.nobuddyorg.pixelbuddy.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cold start of home with the baseline profile, as after installing a release. CI checks it against a budget. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = {
                // Once, so the measured starts show home and not the first-start setup.
                pressHome()
                startHome()
                pressHome()
            },
        ) {
            startActivityAndWait(homeIntent())
        }

    private companion object {
        const val ITERATIONS = 10
    }
}
