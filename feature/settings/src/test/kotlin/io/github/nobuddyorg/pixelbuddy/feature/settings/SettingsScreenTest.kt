package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

private const val S24 = "w411dp-h891dp-420dpi"

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = S24)
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { CrashReportStore(folder.root) }

    private fun reportText(message: String) =
        "PixelBuddy crash report\nTime: x\n\njava.lang.IllegalStateException: $message\n\tat a.b.C.d(C.kt:1)\n"

    private fun showRoute() =
        compose.setContent {
            SettingsRoute(store = store, appVersion = "1.2.3", ioDispatcher = Dispatchers.Unconfined)
        }

    @Test
    fun `empty state explains that nothing crashed`() {
        showRoute()

        compose.onNodeWithText("Version 1.2.3").assertIsDisplayed()
        compose.onNodeWithText("No crash reports. Nothing has crashed so far.").assertIsDisplayed()
    }

    @Test
    fun `opening a report shows its text and delete removes it`() {
        store.save(Instant.ofEpochMilli(1_000), reportText("first"))
        store.save(Instant.ofEpochMilli(2_000), reportText("second"))
        showRoute()

        compose.onNodeWithText("java.lang.IllegalStateException: second").performClick()
        compose.onNodeWithText("Crash report").assertIsDisplayed()
        compose.onNodeWithText(reportText("second").trimEnd(), substring = true).assertIsDisplayed()

        compose.onNodeWithText("▶ Delete").performClick()

        compose.onNodeWithText("java.lang.IllegalStateException: first").assertIsDisplayed()
        assertEquals(listOf("java.lang.IllegalStateException: first"), store.list().map { it.summary })
    }

    @Test
    fun `back from a report returns to the list`() {
        store.save(Instant.ofEpochMilli(1_000), reportText("first"))
        showRoute()

        compose.onNodeWithText("java.lang.IllegalStateException: first").performClick()
        compose.onNodeWithText("▶ Back").performClick()

        compose.onNodeWithText("Crash reports".uppercase()).assertIsDisplayed()
    }

    @Test
    fun `delete all removes every report`() {
        repeat(3) { store.save(Instant.ofEpochMilli(it * 1_000L), reportText("crash $it")) }
        showRoute()

        compose.onNodeWithText("▶ Delete all").performClick()

        compose.onNodeWithText("No crash reports. Nothing has crashed so far.").assertIsDisplayed()
        assertEquals(emptyList(), store.list())
    }

    private val sampleReports =
        listOf(
            CrashReport("crash-2.txt", Instant.parse("2026-11-21T20:15:30Z"), "java.lang.IllegalStateException: boom"),
            CrashReport("crash-1.txt", Instant.parse("2026-11-20T08:01:02Z"), "java.lang.NullPointerException"),
        )

    private fun showList() =
        compose.setContent {
            SettingsScreen(
                appVersion = "1.2.3",
                reports = sampleReports,
                openReport = null,
                onOpenReport = {},
                onCloseReport = {},
                onShareReport = {},
                onDeleteReport = {},
                onDeleteAllReports = {},
                zone = ZoneOffset.UTC,
            )
        }

    @Test
    fun `screenshot English`() {
        showList()
        compose.onRoot().captureRoboImage("SettingsScreen_en.png")
    }

    @Test
    @Config(qualifiers = "+de")
    fun `screenshot German`() {
        showList()
        compose.onNodeWithText("Absturzberichte".uppercase()).assertIsDisplayed()
        compose.onRoot().captureRoboImage("SettingsScreen_de.png")
    }
}
