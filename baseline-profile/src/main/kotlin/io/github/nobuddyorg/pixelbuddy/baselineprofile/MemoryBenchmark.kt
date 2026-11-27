package io.github.nobuddyorg.pixelbuddy.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Memory of home (#56): the most heap and anonymous RSS while it starts and every app's icon is shown in the list.
 * CI reports it and checks it against .github/memory-budget.json.
 */
@RunWith(AndroidJUnit4::class)
class MemoryBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun homeMemory() =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics =
                listOf(
                    MemoryUsageMetric(
                        MemoryUsageMetric.Mode.Max,
                        listOf(MemoryUsageMetric.SubMetric.HeapSize, MemoryUsageMetric.SubMetric.RssAnon),
                    ),
                ),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
            setupBlock = { pressHome() },
        ) {
            startHome()
            openSearch()
            // Down to the last app and back: every icon is loaded once.
            val list = device.findObject(By.scrollable(true))
            repeat(FLINGS) { list.fling(Direction.DOWN) }
            repeat(FLINGS) { list.fling(Direction.UP) }
            device.waitForIdle()
        }

    private companion object {
        const val ITERATIONS = 3
        const val FLINGS = 3
    }
}
