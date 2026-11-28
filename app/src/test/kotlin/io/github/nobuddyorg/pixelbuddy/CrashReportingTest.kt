package io.github.nobuddyorg.pixelbuddy

import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashHandler
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CrashReportingTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app: PixelBuddyApp = ApplicationProvider.getApplicationContext()

    @Test
    fun `application installs the crash handler`() {
        assertIs<CrashHandler>(Thread.getDefaultUncaughtExceptionHandler())
    }

    @Test
    fun `uncaught crash is written to app-private storage`() {
        // A handler without a previous one, so the test runner doesn't see the simulated crash.
        CrashHandler(app.container.crashReports, app.container.deviceInfo, previous = null)
            .uncaughtException(Thread.currentThread(), IllegalStateException("forced crash"))

        val report =
            app.container.crashReports
                .list()
                .single()
        assertContains(report.summary, "forced crash")
        assertTrue(
            app.filesDir
                .resolve(AppContainer.CRASH_REPORTS_DIR)
                .resolve(report.id)
                .isFile,
        )
    }

    @Test
    fun `a new install shows the setup and skipping it shows home`() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("► Skip setup").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("► Skip setup").performClick()

        val home = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Home screen")
        compose.waitUntil(5_000) { compose.onAllNodes(home).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `long press on home opens the settings`() {
        compose.waitUntil(5_000) { compose.onAllNodesWithText("► Skip setup").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("► Skip setup").performClick()
        // Home shows once the setup is stored as done.
        val home = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Home screen")
        compose.waitUntil(5_000) { compose.onAllNodes(home).fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().performTouchInput { longClick() }

        val started = generateSequence { shadowOf(app).nextStartedActivity }.toList()
        assertTrue(started.any { it.component == ComponentName(app, SettingsActivity::class.java) })
    }

    @Test
    fun `settings activity is private to the app`() {
        val info = app.packageManager.getActivityInfo(ComponentName(app, SettingsActivity::class.java), 0)

        assertTrue(!info.exported)
        assertTrue(Intent(app, SettingsActivity::class.java).resolveActivity(app.packageManager) != null)
    }
}
