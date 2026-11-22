package io.github.nobuddyorg.pixelbuddy.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The release app the benchmarks run against (no `.debug` suffix). */
internal const val PACKAGE = "io.github.nobuddyorg.pixelbuddy"

private const val TIMEOUT_MS = 10_000L

/** Home as the launcher starts it: an explicit intent, since a launcher has no launcher icon of its own. */
internal fun homeIntent(): Intent =
    Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME)
        .setComponent(ComponentName(PACKAGE, "$PACKAGE.MainActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Starts home and waits until it is drawn; skips the first-start setup, so home itself is measured. */
internal fun MacrobenchmarkScope.startHome() {
    startActivityAndWait(homeIntent())
    skipSetup()
}

/** The first-start setup shows on a fresh install; skipping it is stored, so it shows only once. */
internal fun MacrobenchmarkScope.skipSetup() {
    device.wait(Until.findObject(By.textContains("Skip setup")), 2_000L)?.click()
    device.wait(Until.hasObject(By.textContains("SEARCH")), TIMEOUT_MS)
}

/** What people do on home: the list of all apps, the rail, the search. */
internal fun MacrobenchmarkScope.useHome() {
    device.findObject(By.textContains("SEARCH"))?.click()
    device.waitForIdle()
    device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
    device.pressBack()
    device.findObject(By.desc("Alphabet"))?.let { rail ->
        rail.drag(rail.visibleCenter)
    }
    device.pressBack()
}
