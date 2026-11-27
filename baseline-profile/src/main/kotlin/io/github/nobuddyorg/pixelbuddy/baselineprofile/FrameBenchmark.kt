package io.github.nobuddyorg.pixelbuddy.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing of what people do on home (#55): scrolling all apps, dragging the alphabet rail, typing a search and
 * opening an app's menu. CI reports the frame times and checks them against .github/frame-budget.json; on a phone
 * the same tests give the real numbers (see CONTRIBUTING).
 */
@RunWith(AndroidJUnit4::class)
class FrameBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Measures [action] on home, after [prepare] (not measured). */
    private fun measure(
        prepare: MacrobenchmarkScope.() -> Unit = {},
        action: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = ITERATIONS,
        setupBlock = {
            pressHome()
            startHome()
            prepare()
        },
        measureBlock = action,
    )

    @Test
    fun scrollAllApps() =
        measure(prepare = { openSearch() }) {
            val list = device.findObject(By.scrollable(true))
            list.fling(Direction.DOWN)
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }

    @Test
    fun dragRail() =
        measure {
            val rail = device.findObject(By.desc("Alphabet")).visibleBounds
            device.drag(rail.centerX(), rail.top + EDGE_PX, rail.centerX(), rail.bottom - EDGE_PX, DRAG_STEPS)
            device.waitForIdle()
        }

    @Test
    fun typeSearch() =
        measure(prepare = { openSearch() }) {
            val field = device.findObject(By.focused(true))
            listOf("s", "se", "set", "sett").forEach {
                field.text = it
                device.waitForIdle()
            }
        }

    @Test
    fun openAppMenu() =
        measure(prepare = {
            openSearch()
            device.findObject(By.focused(true)).text = "Sett"
            device.wait(Until.hasObject(By.desc("Settings")), TIMEOUT_MS)
        }) {
            device.findObject(By.desc("Settings")).longClick()
            device.wait(Until.hasObject(By.textContains("App info")), TIMEOUT_MS)
            device.pressBack()
            device.waitForIdle()
        }

    private companion object {
        const val ITERATIONS = 5
        const val TIMEOUT_MS = 5_000L

        /** Stays inside the rail, away from the system's gesture areas at the screen edges. */
        const val EDGE_PX = 8

        /** About 5 ms per step: a 0.5 s drag over all letters. */
        const val DRAG_STEPS = 100
    }
}
