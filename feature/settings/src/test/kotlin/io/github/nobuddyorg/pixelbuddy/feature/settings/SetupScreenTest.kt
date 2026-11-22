package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SetupStep
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class SetupScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val settings = MutableStateFlow(Settings())

    private val apps = listOf("Maps", "Signal", "Bahn", "Teams").map { launcherApp(it, work = it == "Teams") }

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

    private class FakeHomeApp(
        var isHome: Boolean,
    ) : HomeAppAccess {
        val roleDialog = Intent("test.REQUEST_HOME_ROLE")

        override fun isDefault() = isHome

        override fun requestIntents() = listOf(roleDialog)
    }

    private val berlin = WeatherPlace("Berlin", "Land Berlin", "Deutschland", 52.52, 13.41)

    /** Changing it starts the setup afresh, like a new process: only the stored settings are left. */
    private var process by mutableIntStateOf(0)

    private fun show(homeApp: HomeAppAccess = FakeHomeApp(isHome = false)) =
        compose.setContent {
            key(process) {
                SetupRoute(
                    settings = settings,
                    onUpdate = { change -> settings.value = change(settings.value) },
                    homeApp = homeApp,
                    apps = flowOf(apps),
                    searchPlaces = { listOf(berlin) },
                )
            }
        }

    /** System Back, once the screen shows the current step. */
    private fun back() {
        compose.waitForIdle()
        Espresso.pressBack()
        compose.waitForIdle()
    }

    private val step get() = settings.value.setup

    @Test
    fun `a new install starts with the home app`() {
        show()

        compose.onNodeWithText("SETUP · 1 OF 3").assertIsDisplayed()
        compose.onNodeWithText("▶ Make PixelBuddy the home app").assertIsDisplayed()
    }

    @Test
    fun `the home app step is skipped when PixelBuddy already is the home app`() {
        show(FakeHomeApp(isHome = true))

        compose.onNodeWithText("SETUP · 1 OF 2").assertIsDisplayed()
        compose.onNodeWithText("Favorites").assertIsDisplayed()
        assertEquals(SetupStep.Favorites, step)
        // There is no step to go back to.
        back()
        assertEquals(SetupStep.Favorites, step)
    }

    @Test
    fun `the home app is requested and the setup goes on once PixelBuddy is it`() {
        val homeApp = FakeHomeApp(isHome = false)
        show(homeApp)

        compose.onNodeWithText("▶ Make PixelBuddy the home app").performClick()
        val activity = shadowOf(compose.activity)
        val request = activity.nextStartedActivityForResult
        assertEquals(homeApp.roleDialog, request.intent)

        homeApp.isHome = true
        activity.receiveResult(request.intent, Activity.RESULT_OK, null)
        compose.waitForIdle()

        assertEquals(SetupStep.Favorites, step)
    }

    @Test
    fun `declining the home app explains how to change it later`() {
        show()

        compose.onNodeWithText("▶ Make PixelBuddy the home app").performClick()
        val activity = shadowOf(compose.activity)
        val request = activity.nextStartedActivityForResult
        activity.receiveResult(request.intent, Activity.RESULT_CANCELED, null)

        compose.onNodeWithText("You can change that later", substring = true).assertIsDisplayed()
        assertEquals(SetupStep.HomeApp, step)
        compose.onNodeWithText("▶ Not now").performClick()
        assertEquals(SetupStep.Favorites, step)
    }

    @Test
    fun `favorites are picked in order and can be unpicked`() {
        settings.value = Settings(setup = SetupStep.Favorites)
        show()

        compose.onNodeWithText("Signal").performClick()
        compose.onNodeWithText("Maps").performClick()
        compose.onNodeWithText("Teams (work)").performClick()
        assertEquals(listOf("Signal", "Maps", "Teams"), settings.value.favorites.map { key -> label(key) })
        compose.onNodeWithText("[2]").assertIsDisplayed()
        compose.onNodeWithText("Picked: 3").assertIsDisplayed()

        compose.onNodeWithText("Signal").performClick()
        assertEquals(listOf("Maps", "Teams"), settings.value.favorites.map { key -> label(key) })
        compose.onNodeWithText("Signal").assertIsOff()
        compose.onNodeWithText("Maps").assertIsOn()

        compose.onNodeWithText("▶ Next").performClick()
        assertEquals(SetupStep.Weather, step)
    }

    private fun label(key: AppKey) = apps.single { it.key == key }.label

    @Test
    fun `Back goes to the step before`() {
        settings.value = Settings(setup = SetupStep.Weather)
        show()

        back()
        assertEquals(SetupStep.Favorites, step)
        back()
        assertEquals(SetupStep.HomeApp, step)
    }

    @Test
    fun `no weather finishes the setup with weather off`() {
        settings.value = Settings(setup = SetupStep.Weather)
        show()

        compose.onNodeWithText("No weather").performClick()

        assertEquals(SetupStep.Done, step)
        assertFalse(settings.value.weather.enabled)
    }

    @Test
    fun `a place finishes the setup with weather on`() {
        settings.value = Settings(setup = SetupStep.Weather)
        show()

        compose.onNodeWithText("Choose a place").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Berlin")
        compose.waitUntil(
            3_000,
        ) { compose.onAllNodesWithText("Land Berlin, Deutschland").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Land Berlin, Deutschland").performClick()

        assertEquals(SetupStep.Done, step)
        assertEquals(WeatherSettings(enabled = true, place = berlin), settings.value.weather)
    }

    @Test
    fun `Back from the place search returns to the weather step`() {
        settings.value = Settings(setup = SetupStep.Weather)
        show()

        compose.onNodeWithText("Choose a place").performClick()
        back()

        compose.onNodeWithText("No weather").assertIsDisplayed()
        assertEquals(SetupStep.Weather, step)
    }

    @Test
    fun `approximate location finishes the setup once allowed`() {
        shadowOf(compose.activity.application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        settings.value = Settings(setup = SetupStep.Weather)
        show()

        compose.onNodeWithText("Use approximate location").performClick()

        assertEquals(SetupStep.Done, step)
        assertTrue(settings.value.weather.enabled && settings.value.weather.approximateLocation)
    }

    @Test
    fun `skipping ends the setup from any step`() {
        show()

        compose.onNodeWithText("▶ Skip setup").performClick()

        assertEquals(SetupStep.Done, step)
        assertEquals(Settings(setup = SetupStep.Done), settings.value)
    }

    @Test
    fun `the setup goes on where it was after the process was killed`() {
        settings.value = Settings(setup = SetupStep.Favorites)
        show()
        compose.onNodeWithText("Bahn").performClick()
        compose.onNodeWithText("▶ Next").performClick()

        process++

        compose.onNodeWithText("SETUP · 3 OF 3").assertIsDisplayed()
        compose.onNodeWithText("No weather").assertIsDisplayed()
        back()
        compose.onNodeWithText("Bahn").assertIsOn()
    }

    @Test
    fun `screenshot home app`() {
        show()
        compose.onRoot().captureRoboImage("Setup_home_app.png")
    }

    @Test
    fun `screenshot favorites`() {
        settings.value = Settings(setup = SetupStep.Favorites, favorites = listOf(apps[1].key, apps[0].key))
        show()
        compose.onRoot().captureRoboImage("Setup_favorites.png")
    }

    @Test
    fun `screenshot weather`() {
        settings.value = Settings(setup = SetupStep.Weather)
        show()
        compose.onRoot().captureRoboImage("Setup_weather.png")
    }

    @Test
    @Config(qualifiers = "+de")
    fun `screenshot German`() {
        settings.value = Settings(setup = SetupStep.Favorites, favorites = listOf(apps[2].key))
        show()
        compose.onNodeWithText("EINRICHTUNG · 2 VON 3").assertIsDisplayed()
        compose.onRoot().captureRoboImage("Setup_de.png")
    }
}
