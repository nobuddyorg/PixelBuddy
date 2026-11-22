package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.settings.TemperatureUnit
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val S24 = "w411dp-h891dp-420dpi"

private fun launcherApp(
    label: String,
    work: Boolean = false,
): LauncherApp {
    val pkg = "pkg." + label.lowercase()
    return LauncherApp(
        key = AppKey(pkg, "$pkg.Main", if (work) 10 else 0),
        label = label,
        component = ComponentName(pkg, "$pkg.Main"),
        user = Process.myUserHandle(),
        isMainProfile = !work,
    )
}

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

    private val hidden = MutableStateFlow(emptyList<LauncherApp>())
    private val shown = mutableListOf<String>()

    private val weather = MutableStateFlow(WeatherSettings())
    private val searched = mutableListOf<String>()
    private var searchFailure: IOException? = null

    private fun showRoute() =
        compose.setContent {
            SettingsRoute(
                store = store,
                appVersion = "1.2.3",
                hiddenApps = hidden,
                onShowApp = { shown += it.label },
                weatherSettings = weather,
                onUpdateWeather = { change -> weather.value = change(weather.value) },
                searchPlaces = { name ->
                    searched += name
                    searchFailure?.let { throw it }
                    listOf(berlin, WeatherPlace("Berlin", "New Hampshire", "USA", 44.47, -71.19))
                },
                apps = flowOf(listOf(launcherApp("Clock"), launcherApp("Teams", work = true))),
                ioDispatcher = Dispatchers.Unconfined,
            )
        }

    private val berlin = WeatherPlace("Berlin", "Land Berlin", "Deutschland", 52.52, 13.41)

    @Test
    fun `weather is off by default and switching it on asks for a place`() {
        showRoute()
        compose.onNodeWithText("Off: no network access at all").assertIsDisplayed()

        compose.onNodeWithText("Weather on home").performClick()

        assertTrue(weather.value.enabled)
        compose.onNodeWithText("City or postcode").assertIsDisplayed()
        compose.onNodeWithText("Only what you type is sent, to Open-Meteo.").assertIsDisplayed()
    }

    @Test
    fun `a place is found by name and picked`() {
        showRoute()
        compose.onNodeWithText("Weather on home").performClick()

        compose.onNode(hasSetTextAction()).performTextInput("Ber")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("New Hampshire, USA").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Land Berlin, Deutschland").performClick()

        assertEquals(listOf("Ber"), searched)
        assertEquals(WeatherSettings(enabled = true, place = berlin), weather.value)
        compose.onNodeWithText("Berlin, Land Berlin, Deutschland").assertIsDisplayed()
    }

    @Test
    fun `a failed search says so and one letter searches nothing`() {
        searchFailure = IOException("offline")
        weather.value = WeatherSettings(enabled = true)
        showRoute()
        compose.onNodeWithText("Weather location").performClick()

        compose.onNode(hasSetTextAction()).performTextInput("B")
        compose.waitForIdle()
        assertEquals(emptyList(), searched)
        compose.onNode(hasSetTextAction()).performTextInput("e")
        val failed = "Couldn't search. Check the connection."
        compose.waitUntil(3_000) { compose.onAllNodesWithText(failed).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `approximate location is used once the permission is there`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        weather.value = WeatherSettings(enabled = true, place = berlin)
        showRoute()

        compose.onNodeWithText("Weather location").performClick()
        compose.onNodeWithText("Use approximate location", substring = true).performClick()

        assertTrue(weather.value.approximateLocation)
        compose.onNodeWithText("Approximate location (rounded to about 10 km)").assertIsDisplayed()
    }

    @Test
    fun `the unit cycles through automatic, Celsius and Fahrenheit`() {
        weather.value = WeatherSettings(enabled = true, place = berlin)
        showRoute()

        compose.onNodeWithText("Automatic", substring = true).performClick()
        assertEquals(TemperatureUnit.Celsius, weather.value.unit)
        compose.onNodeWithText("°C").performClick()
        assertEquals(TemperatureUnit.Fahrenheit, weather.value.unit)
        compose.onNodeWithText("°F").performClick()
        assertEquals(TemperatureUnit.System, weather.value.unit)
    }

    @Test
    fun `a tap on the weather can open an app or refresh`() {
        weather.value = WeatherSettings(enabled = true, place = berlin)
        showRoute()

        compose.onNodeWithText("Tap on the weather").performClick()
        compose.onNodeWithText("Teams (work)").assertIsDisplayed()
        compose.onNodeWithText("Clock").performClick()
        assertEquals(launcherApp("Clock").key, weather.value.tapApp)
        compose.onNodeWithText("Clock").assertIsDisplayed()

        compose.onNodeWithText("Tap on the weather").performClick()
        compose.onNodeWithText("Refresh the weather").performClick()
        assertEquals(null, weather.value.tapApp)
    }

    @Test
    fun `the weather data attribution links to Open-Meteo`() {
        showRoute()

        compose.onNodeWithText("Weather data by Open-Meteo.com").performScrollTo().performClick()

        val intent = shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://open-meteo.com/", intent.dataString)
    }

    @Test
    fun `hidden apps are listed and can be shown again`() {
        hidden.value = listOf(launcherApp("Bloat"), launcherApp("Teams", work = true))
        showRoute()

        compose.onNodeWithText("Teams (work)").assertIsDisplayed()
        compose.onNodeWithText("Bloat").performClick()

        assertEquals(listOf("Bloat"), shown)
    }

    @Test
    fun `no hidden apps explains how to hide one`() {
        showRoute()

        compose.onNodeWithText("No hidden apps. Long-press an app on home to hide it.").assertIsDisplayed()
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
                hiddenApps = listOf(launcherApp("Bloat")),
                onShowApp = {},
                weather = WeatherOptions(WeatherSettings(enabled = true, place = berlin)),
                onWeather = {},
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
