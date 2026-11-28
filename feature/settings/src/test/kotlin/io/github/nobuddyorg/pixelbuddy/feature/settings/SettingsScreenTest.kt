package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.Manifest
import android.app.Application
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarEvent
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarInfo
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarSource
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReport
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.RenderTimings
import io.github.nobuddyorg.pixelbuddy.core.data.lock.ScreenLock
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.AppNotification
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.AppNotifications
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Accent
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.CalendarSettings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.ClockFormat
import io.github.nobuddyorg.pixelbuddy.core.data.settings.ClockPosition
import io.github.nobuddyorg.pixelbuddy.core.data.settings.GestureAction
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Gestures
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconSize
import io.github.nobuddyorg.pixelbuddy.core.data.settings.NotificationSettings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.RailSize
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SizeStep
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Sky
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SkySteps
import io.github.nobuddyorg.pixelbuddy.core.data.settings.TemperatureUnit
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WallpaperSync
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WidgetSettings
import io.github.nobuddyorg.pixelbuddy.core.data.widgets.HomeWidgets
import io.github.nobuddyorg.pixelbuddy.core.data.widgets.WidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private val store by lazy { CrashReportStore(folder.root) }

    private fun reportText(message: String) =
        "PixelBuddy crash report\nTime: x\n\njava.lang.IllegalStateException: $message\n\tat a.b.C.d(C.kt:1)\n"

    private val settings = MutableStateFlow(Settings())

    /** Apps the favorites can refer to; the favorites follow the settings like on the phone. */
    private var knownApps = emptyList<LauncherApp>()
    private val favorites = settings.map { s -> s.favorites.mapNotNull { key -> knownApps.find { it.key == key } } }
    private val hidden = MutableStateFlow(emptyList<LauncherApp>())
    private val searched = mutableListOf<String>()
    private var searchFailure: IOException? = null

    private val berlin = WeatherPlace("Berlin", "Land Berlin", "Deutschland", 52.52, 13.41)

    private fun weather(change: (WeatherSettings) -> WeatherSettings) {
        settings.value = settings.value.copy(weather = change(settings.value.weather))
    }

    private fun showRoute(
        screenLock: ScreenLock? = null,
        appNotifications: AppNotifications? = null,
        calendarSource: CalendarSource? = null,
        homeWidgets: HomeWidgets? = null,
    ) = compose.setContent {
        SettingsRoute(
            store = store,
            appVersion = "1.2.3",
            settings = settings,
            onUpdate = { change -> settings.value = change(settings.value) },
            favoriteApps = favorites,
            hiddenApps = hidden,
            apps = flowOf(listOf(launcherApp("Clock"), launcherApp("Teams", work = true))),
            searchPlaces = { name ->
                searched += name
                searchFailure?.let { throw it }
                listOf(berlin, WeatherPlace("Berlin", "New Hampshire", "USA", 44.47, -71.19))
            },
            ioDispatcher = Dispatchers.Unconfined,
            screenLock = screenLock,
            appNotifications = appNotifications,
            calendarSource = calendarSource,
            homeWidgets = homeWidgets,
        )
    }

    private class FakeCalendar : CalendarSource {
        override fun hasAccess() = true

        override suspend fun calendars() =
            listOf(CalendarInfo(1, "Family", "me@example.org", 0), CalendarInfo(2, "Work", "me@example.org", 0))

        override suspend fun events(from: Instant) = emptyList<CalendarEvent>()

        override fun changes() = emptyFlow<Unit>()

        override fun openIntent(event: CalendarEvent) = Intent()
    }

    private class FakeNotifications(
        granted: Boolean,
    ) : AppNotifications {
        override val connected = MutableStateFlow(granted)
        override val active = MutableStateFlow(emptyList<AppNotification>())

        override fun open(key: String) = false

        override fun dismiss(key: String) = Unit

        override fun settingsIntent() = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    private class FakeScreenLock(
        on: Boolean,
    ) : ScreenLock {
        override val available = MutableStateFlow(on)

        override fun lock() = available.value

        override fun settingsIntent() = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    private fun open(category: String) {
        compose.onNodeWithText(category).performScrollTo().performClick()
    }

    // Main page and navigation

    @Test
    fun `the main page lists every category and Back returns to it`() {
        showRoute()

        listOf(
            "Appearance & themes",
            "Clock & typography",
            "App icons",
            "Favorites & hidden apps",
            "Gestures",
            "Wallpaper & background",
            "Weather",
            "About",
        ).forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("Version 1.2.3 · Licences · Crash reports").assertIsDisplayed()

        open("Gestures")
        compose.onNodeWithText("Swipe up").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Appearance & themes").assertIsDisplayed()

        open("About")
        open("Licences")
        compose.onNodeWithText("◄ Back").performClick()
        compose.onNodeWithText("Source code").assertIsDisplayed()
    }

    @Test
    fun `the setup can be run again`() {
        var runs = 0
        compose.setContent {
            SettingsRoute(
                store = store,
                appVersion = "1.2.3",
                settings = settings,
                onUpdate = {},
                onRunSetup = { runs++ },
                ioDispatcher = Dispatchers.Unconfined,
            )
        }

        compose.onNodeWithText("Run setup again").performScrollTo().performClick()

        assertEquals(1, runs)
    }

    // Appearance

    @Test
    fun `accent and scanlines apply at once`() {
        showRoute()
        open("Appearance & themes")

        compose.onNodeWithContentDescription("Pink").performClick()
        assertEquals(Accent.Pink, settings.value.appearance.accent)
        compose.onNodeWithContentDescription("Pink").assertIsSelected()

        compose.onNodeWithText("Scanlines").performClick()
        assertTrue(settings.value.appearance.scanlines)
        compose.onNodeWithText("[ON]").assertIsDisplayed()
    }

    @Test
    fun `clock format, shadow and sizes`() {
        showRoute()
        open("Clock & typography")

        compose.onNodeWithText("Like the phone", substring = true).performClick()
        assertEquals(ClockFormat.TwelveHour, settings.value.appearance.clockFormat)
        compose.onNodeWithContentDescription("9:09").assertIsDisplayed()
        compose.onNodeWithText("12 h").performClick()
        assertEquals(ClockFormat.TwentyFourHour, settings.value.appearance.clockFormat)
        compose.onNodeWithContentDescription("21:09").assertIsDisplayed()

        compose.onNodeWithText("Clock shadow").performClick()
        assertFalse(settings.value.appearance.clockShadow)

        compose.onNodeWithText("Centre").performScrollTo().performClick()
        assertEquals(ClockPosition.Center, settings.value.appearance.clockPosition)

        compose.onNodeWithText("Clock size").performClick()
        assertEquals(SizeStep.Large, settings.value.appearance.clockSize)
        compose.onNodeWithText("Text size on home").performScrollTo().performClick()
        compose.onNodeWithText("Text size on home").performClick()
        // Medium → Large → Extra small: the steps go round.
        assertEquals(SizeStep.ExtraSmall, settings.value.appearance.textSize)

        compose.onNodeWithText("Icon size on home").performScrollTo().performClick()
        compose.onNodeWithText("Icon size on home").performClick()
        assertEquals(IconSize.Small, settings.value.appearance.iconSize)

        compose.onNodeWithText("Alphabet letters on the side").performScrollTo().performClick()
        assertEquals(RailSize.Large, settings.value.appearance.railSize)
    }

    // Favorites and hidden apps

    @Test
    fun `favorites can be reordered and removed`() {
        knownApps = listOf(launcherApp("Maps"), launcherApp("Signal"), launcherApp("Bahn"))
        settings.value = Settings(favorites = knownApps.map { it.key })
        showRoute()
        open("Favorites & hidden apps")

        compose.onNodeWithContentDescription("Move Bahn up").performClick()
        assertEquals(listOf("Maps", "Bahn", "Signal"), settings.value.favorites.map { it.packageName.label() })
        compose.onNodeWithContentDescription("Move Maps down").performClick()
        assertEquals(listOf("Bahn", "Maps", "Signal"), settings.value.favorites.map { it.packageName.label() })
        compose.onNodeWithContentDescription("Remove Signal from favorites").performClick()
        assertEquals(listOf("Bahn", "Maps"), settings.value.favorites.map { it.packageName.label() })
    }

    private fun String.label() = removePrefix("pkg.").replaceFirstChar { it.uppercase() }

    @Test
    fun `the first favorite can't move up, the last can't move down`() {
        knownApps = listOf(launcherApp("Maps"), launcherApp("Signal"))
        settings.value = Settings(favorites = knownApps.map { it.key })
        showRoute()
        open("Favorites & hidden apps")

        compose.onNodeWithContentDescription("Move Maps up").assertDoesNotExist()
        compose.onNodeWithContentDescription("Move Signal down").assertDoesNotExist()
    }

    @Test
    fun `hidden apps are listed and can be shown again`() {
        val bloat = launcherApp("Bloat")
        val teams = launcherApp("Teams", work = true)
        settings.value = Settings(hidden = setOf(bloat.key, teams.key))
        hidden.value = listOf(bloat, teams)
        showRoute()
        compose.onNodeWithText("Favorites: 0 · Hidden: 2").assertIsDisplayed()
        open("Favorites & hidden apps")

        compose.onNodeWithText("Teams (work)").assertIsDisplayed()
        compose.onNodeWithText("Bloat").performClick()

        assertEquals(setOf(teams.key), settings.value.hidden)
    }

    @Test
    fun `no favorites and no hidden apps explain how to add them`() {
        showRoute()
        open("Favorites & hidden apps")

        compose.onNodeWithText("No favorites. Long-press an app on home to add one.").assertIsDisplayed()
        compose.onNodeWithText("No hidden apps. Long-press an app on home to hide it.").assertIsDisplayed()
    }

    // Gestures

    @Test
    fun `gestures cycle through their actions`() {
        showRoute()
        open("Gestures")

        compose.onNodeWithText("Swipe up").performClick()
        assertEquals(GestureAction.Notifications, settings.value.gestures.swipeUp)
        compose.onNodeWithText("Swipe down").performClick()
        assertEquals(GestureAction.Settings, settings.value.gestures.swipeDown)
        // A swipe opens the settings now, so the long press is free.
        compose.onNodeWithText("Long press on empty space").performClick()
        assertEquals(GestureAction.Nothing, settings.value.gestures.longPress)
    }

    @Test
    fun `web search in the search can be switched off`() {
        showRoute()
        open("Gestures")
        assertTrue(settings.value.gestures.webSearch)

        compose.onNodeWithText("Web search").performScrollTo().performClick()

        assertFalse(settings.value.gestures.webSearch)
    }

    @Test
    fun `with locking available the gestures can lock, and the page explains the lock service`() {
        val lock = FakeScreenLock(on = false)
        showRoute(lock)
        open("Gestures")
        compose.onNodeWithText("Lock service".uppercase()).assertDoesNotExist()

        repeat(4) { compose.onNodeWithText("Double tap on empty space").performClick() }
        assertEquals(GestureAction.Lock, settings.value.gestures.doubleTap)
        compose.onNodeWithText("Off: turn it on so the gesture can lock the screen.").assertIsDisplayed()
        compose.onNodeWithText("► Turn on in accessibility settings").performScrollTo().performClick()
        assertEquals(
            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity.action,
        )

        lock.available.value = true
        compose.onNodeWithText("On: the gesture locks the screen.").assertIsDisplayed()
        compose.onNodeWithText("► Turn on in accessibility settings").assertDoesNotExist()

        // Past "Lock" the cycle starts over, and nothing locks any more.
        compose.onNodeWithText("Double tap on empty space").performClick()
        assertEquals(GestureAction.Nothing, settings.value.gestures.doubleTap)
        compose.onNodeWithText("Lock service".uppercase()).assertDoesNotExist()
    }

    // Widget slot (#26)

    private val widgetManager = AppWidgetManager.getInstance(ApplicationProvider.getApplicationContext())

    private fun widgetInfo(
        label: String,
        configure: ComponentName? = null,
    ) = AppWidgetProviderInfo().apply {
        provider = ComponentName("org.example.clock", "org.example.clock.Widget")
        this.configure = configure
        minHeight = 200
        ReflectionHelpers.setField(
            this,
            "providerInfo",
            ActivityInfo().apply {
                packageName = "org.example.clock"
                name = "org.example.clock.Widget"
                nonLocalizedLabel = label
                applicationInfo =
                    ApplicationInfo().apply {
                        packageName = "org.example.clock"
                        uid = Process.myUid()
                    }
            },
        )
    }

    @Test
    fun `a widget is picked, bound and placed in the slot`() {
        widgetManager.install(widgetInfo("World clock"))
        shadowOf(widgetManager).setAllowedToBindAppWidgets(true)
        showRoute(homeWidgets = HomeWidgets(ApplicationProvider.getApplicationContext()))
        open("Widget")

        compose.onNodeWithText("► Add a widget").performClick()
        compose.onNodeWithText("World clock").performClick()

        assertNotNull(settings.value.widget.id)
        compose.onNodeWithText("Below the favorites").performClick()
        assertTrue(settings.value.widget.below)
        compose.onNodeWithText("► Remove the widget").performClick()
        assertNull(settings.value.widget.id)
    }

    @Test
    fun `a widget whose app is gone can be removed`() {
        settings.value = Settings(widget = WidgetSettings(id = 42))
        showRoute(homeWidgets = HomeWidgets(ApplicationProvider.getApplicationContext()))
        open("Widget")

        compose.onNodeWithText("The widget's app is gone. It shows again when the app is back.").assertIsDisplayed()
        compose.onNodeWithText("► Remove the widget").performClick()

        assertNull(settings.value.widget.id)
    }

    @Test
    fun `screenshot widget picker`() {
        showPage(
            SettingsPage.WidgetPicker,
            sampleState.copy(
                widget =
                    WidgetUi(
                        providers =
                            listOf(
                                WidgetProvider(
                                    ComponentName("a", "a.W"),
                                    Process.myUserHandle(),
                                    "World clock",
                                    "Clock",
                                ),
                                WidgetProvider(ComponentName("b", "b.W"), Process.myUserHandle(), "Note", "Notes"),
                            ),
                    ),
            ),
        )
        compose.onRoot().captureRoboImage("SettingsScreen_widget_picker.png")
    }

    // Next calendar event (#27)

    @Test
    fun `the next event asks for the calendar and then offers all-day events and calendars`() {
        shadowOf(
            ApplicationProvider.getApplicationContext<Application>(),
        ).grantPermissions(Manifest.permission.READ_CALENDAR)
        showRoute(calendarSource = FakeCalendar())
        open("Next event")

        compose.onNodeWithText("Next event under the date").performClick()
        assertTrue(settings.value.calendar.enabled)

        compose.onNodeWithText("All-day events").performClick()
        assertTrue(settings.value.calendar.allDay)
        compose.onNodeWithText("Work").performScrollTo().performClick()
        assertEquals(setOf(1L), settings.value.calendar.calendars)
        // Both chosen again is "all", which takes in calendars added later.
        compose.onNodeWithText("Work").performClick()
        assertNull(settings.value.calendar.calendars)

        compose.onNodeWithText("Next event under the date").performScrollTo().performClick()
        assertFalse(settings.value.calendar.enabled)
    }

    @Test
    fun `without a calendar the next event isn't offered`() {
        showRoute()

        compose.onNodeWithText("Next event").assertDoesNotExist()
    }

    @Test
    fun `screenshot calendar`() {
        showPage(
            SettingsPage.Calendar,
            sampleState.copy(
                settings = sampleState.settings.copy(calendar = CalendarSettings(enabled = true, calendars = setOf(1))),
                calendars =
                    listOf(
                        CalendarInfo(1, "Family", "me@example.org", 0),
                        CalendarInfo(2, "Work", "me@work.example", 0),
                    ),
            ),
        )
        compose.onRoot().captureRoboImage("SettingsScreen_calendar.png")
    }

    // Notifications (#24)

    @Test
    fun `notification dots are opt-in and explain the access they need`() {
        val access = FakeNotifications(granted = false)
        showRoute(appNotifications = access)
        open("Notifications & media")
        compose.onNodeWithText("Notification access".uppercase()).assertDoesNotExist()

        compose.onNodeWithText("Notification dots").performClick()

        assertTrue(settings.value.notifications.dots)
        compose.onNodeWithText("Not granted: Android asks once you allow it.").assertIsDisplayed()
        compose.onNodeWithText("► Allow notification access").performScrollTo().performClick()
        assertEquals(
            android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity.action,
        )
        access.connected.value = true
        compose.onNodeWithText("Granted").assertIsDisplayed()
        compose.onNodeWithText("► Allow notification access").assertDoesNotExist()
    }

    @Test
    fun `media controls are opt-in too and need the same access`() {
        showRoute(appNotifications = FakeNotifications(granted = true))
        open("Notifications & media")

        compose.onNodeWithText("Media controls").performClick()

        assertTrue(settings.value.notifications.media)
        assertFalse(settings.value.notifications.dots)
        compose.onNodeWithText("Granted").assertIsDisplayed()
    }

    @Test
    fun `without the notification access the page isn't offered`() {
        showRoute()

        compose.onNodeWithText("Notifications & media").assertDoesNotExist()
    }

    @Test
    fun `screenshot notifications`() {
        showPage(
            SettingsPage.Notifications,
            sampleState.copy(
                settings = sampleState.settings.copy(notifications = NotificationSettings(dots = true)),
                notificationAccessOn = false,
            ),
        )
        compose.onRoot().captureRoboImage("SettingsScreen_notifications.png")
    }

    @Test
    fun `without locking no gesture can be set to lock`() {
        settings.value = Settings(gestures = Gestures(doubleTap = GestureAction.Settings))
        showRoute()
        open("Gestures")

        compose.onNodeWithText("Double tap on empty space").performClick()

        assertEquals(GestureAction.Nothing, settings.value.gestures.doubleTap)
    }

    @Test
    fun `without a swipe to the settings the long press keeps opening them`() {
        settings.value = Settings(gestures = Gestures(longPress = GestureAction.Search))
        showRoute()
        open("Gestures")

        compose.onNodeWithText("Settings · Opens the settings while no swipe does").assertIsDisplayed()
        compose.onNodeWithText("Long press on empty space").performClick()
        assertEquals(GestureAction.Search, settings.value.gestures.longPress)
    }

    // Wallpaper and background

    @Test
    fun `sky and dithering apply at once`() {
        showRoute()
        open("Wallpaper & background")

        compose.onNodeWithContentDescription("Forest").performClick()
        assertEquals(Sky.Forest, settings.value.appearance.sky)
        compose.onNodeWithText("Dithering").performClick()
        assertFalse(settings.value.appearance.dithering)
    }

    @Test
    fun `the sky can follow the sun instead of a fixed gradient`() {
        showRoute()
        open("Wallpaper & background")
        compose
            .onNodeWithText(
                "From your time zone. Pick a weather place for exact times.",
            ).assertIsDisplayed()

        compose.onNodeWithText("Follows the sun").performClick()

        assertTrue(settings.value.appearance.skyFollowsSun)
        compose.onNodeWithText("Night → dawn → day → dusk", substring = true).assertIsDisplayed()
        // The fixed skies aren't offered while it follows the sun.
        compose.onNodeWithContentDescription("Forest").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithText("Follows the sun").assertIsDisplayed()
    }

    @Test
    fun `with a weather place the sun follows that place`() {
        settings.value = Settings(weather = WeatherSettings(place = berlin))
        showRoute()
        open("Wallpaper & background")

        compose
            .onNodeWithText("Sunrise and sunset at your weather place")
            .assertIsDisplayed()
    }

    @Test
    fun `the sky can become the lock screen wallpaper only`() {
        showRoute()
        open("Wallpaper & background")

        compose.onNodeWithText("► Lock screen").performScrollTo().performClick()

        compose.onNodeWithText("Wallpaper set").assertIsDisplayed()
        val wallpapers = shadowOf(WallpaperManager.getInstance(app))
        assertNotNull(wallpapers.getBitmap(WallpaperManager.FLAG_LOCK))
        assertNull(wallpapers.getBitmap(WallpaperManager.FLAG_SYSTEM))
    }

    @Test
    fun `a sky that follows the sun can change four times a day and have dark nights`() {
        settings.value = settings.value.copy(appearance = settings.value.appearance.copy(skyFollowsSun = true))
        showRoute()
        open("Wallpaper & background")

        compose.onNodeWithText("Changes per day").performScrollTo()
        compose.onNodeWithText("4").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Dark nights").performScrollTo().performClick()

        assertEquals(SkySteps.Four, settings.value.appearance.skySteps)
        assertTrue(settings.value.appearance.darkNights)
    }

    @Test
    fun `the wallpaper can be kept in sync with the sky`() {
        showRoute()
        open("Wallpaper & background")
        assertEquals(WallpaperSync.Off, settings.value.appearance.wallpaperSync)

        compose.onNodeWithText("Keep in sync").performScrollTo()
        compose.onAllNodesWithText("Both")[0].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(WallpaperSync.Both, settings.value.appearance.wallpaperSync)
    }

    @Test
    fun `the sky can become the wallpaper of both screens`() {
        showRoute()
        open("Wallpaper & background")

        compose.onNodeWithText("► Both").performScrollTo().performClick()

        val wallpapers = shadowOf(WallpaperManager.getInstance(app))
        val lock = assertNotNull(wallpapers.getBitmap(WallpaperManager.FLAG_LOCK))
        assertNotNull(wallpapers.getBitmap(WallpaperManager.FLAG_SYSTEM))
        // The exact screen size. (Robolectric ignores the size hint to Android, so that is checked on the phone.)
        val screen = app.resources.displayMetrics
        assertEquals(screen.widthPixels to screen.heightPixels, lock.width to lock.height)
    }

    @Test
    fun `a phone that doesn't allow wallpapers says so`() {
        shadowOf(WallpaperManager.getInstance(app)).setIsSetWallpaperAllowed(false)
        showRoute()
        open("Wallpaper & background")

        compose.onNodeWithText("► Home screen").performScrollTo().performClick()

        compose.onNodeWithText("This phone doesn't allow changing the wallpaper").assertIsDisplayed()
    }

    // Weather

    @Test
    fun `weather is off by default and switching it on asks for a place`() {
        showRoute()
        compose.onNodeWithText("Off: no network access at all").assertIsDisplayed()
        open("Weather")

        compose.onNodeWithText("Weather on home").performClick()

        assertTrue(settings.value.weather.enabled)
        compose.onNodeWithText("City or postcode").assertIsDisplayed()
        compose.onNodeWithText("Only what you type is sent, to Open-Meteo.").assertIsDisplayed()
    }

    @Test
    fun `a place is found by name and picked`() {
        showRoute()
        open("Weather")
        compose.onNodeWithText("Weather on home").performClick()

        compose.onNode(hasSetTextAction()).performTextInput("Ber")
        compose.waitUntil(3_000) { compose.onAllNodesWithText("New Hampshire, USA").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Land Berlin, Deutschland").performClick()

        assertEquals(listOf("Ber"), searched)
        assertEquals(WeatherSettings(enabled = true, place = berlin), settings.value.weather)
        compose.onNodeWithText("Berlin, Land Berlin, Deutschland").assertIsDisplayed()
    }

    @Test
    fun `a failed search says so and one letter searches nothing`() {
        searchFailure = IOException("offline")
        weather { WeatherSettings(enabled = true) }
        showRoute()
        open("Weather")
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
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        weather { WeatherSettings(enabled = true, place = berlin) }
        showRoute()
        open("Weather")

        compose.onNodeWithText("Weather location").performClick()
        compose.onNodeWithText("Use approximate location", substring = true).performClick()

        assertTrue(settings.value.weather.approximateLocation)
        compose.onNodeWithText("Approximate location (rounded to about 10 km)").assertIsDisplayed()
    }

    @Test
    fun `the unit cycles through automatic, Celsius and Fahrenheit`() {
        weather { WeatherSettings(enabled = true, place = berlin) }
        showRoute()
        open("Weather")

        compose.onNodeWithText("Automatic", substring = true).performClick()
        assertEquals(TemperatureUnit.Celsius, settings.value.weather.unit)
        compose.onNodeWithText("°C").performClick()
        assertEquals(TemperatureUnit.Fahrenheit, settings.value.weather.unit)
        compose.onNodeWithText("°F").performClick()
        assertEquals(TemperatureUnit.System, settings.value.weather.unit)
    }

    @Test
    fun `a tap on the weather can open an app or refresh`() {
        weather { WeatherSettings(enabled = true, place = berlin) }
        showRoute()
        open("Weather")

        compose.onNodeWithText("Tap on the weather").performClick()
        compose.onNodeWithText("Teams (work)").assertIsDisplayed()
        compose.onNodeWithText("Clock").performClick()
        assertEquals(launcherApp("Clock").key, settings.value.weather.tapApp)
        compose.onNodeWithText("Clock").assertIsDisplayed()

        compose.onNodeWithText("Tap on the weather").performClick()
        compose.onNodeWithText("Refresh the weather").performClick()
        assertEquals(null, settings.value.weather.tapApp)
    }

    // About

    @Test
    fun `the weather data attribution links to Open-Meteo`() {
        showRoute()
        open("About")

        compose.onNodeWithText("Weather data by Open-Meteo.com").performClick()

        val intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://open-meteo.com/", intent.dataString)
    }

    @Test
    fun `the source code link opens the repository`() {
        showRoute()
        open("About")

        compose.onNodeWithText("Source code").performClick()

        assertEquals("https://github.com/nobuddyorg/PixelBuddy", shadowOf(app).nextStartedActivity.dataString)
    }

    @Test
    fun `the licences include the full font licence`() {
        showRoute()
        open("About")
        open("Licences")

        compose.onNodeWithText("SIL Open Font License 1.1").assertIsDisplayed()
        compose.onNodeWithText("Apache License 2.0").assertIsDisplayed()
        compose.onNodeWithText("SIL OPEN FONT LICENSE Version 1.1", substring = true).performScrollTo()
    }

    @Test
    fun `about shows how long icons took to render on this phone`() {
        showPage(
            SettingsPage.About,
            sampleState.copy(
                neonTimings = RenderTimings.Summary(count = 87, medianMs = 14, p90Ms = 22),
                pixelTimings = RenderTimings.Summary(count = 3, medianMs = 1, p90Ms = 2),
            ),
        )
        compose.onNodeWithText("Icon rendering on this phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Neon lines: 14 ms median, 90 % within 22 ms (87 icons)").assertExists()
        compose.onNodeWithText("Pixels: 1 ms median, 90 % within 2 ms (3 icons)").assertExists()
    }

    // Crash reports

    private fun openCrashReports() {
        open("About")
        open("Crash reports")
    }

    @Test
    fun `empty state explains that nothing crashed`() {
        showRoute()
        open("About")

        compose.onNodeWithText("Version 1.2.3").assertIsDisplayed()
        compose.onNodeWithText("No crash reports. Nothing has crashed so far.").assertIsDisplayed()
    }

    @Test
    fun `opening a report shows its text and delete removes it`() {
        store.save(Instant.ofEpochMilli(1_000), reportText("first"))
        store.save(Instant.ofEpochMilli(2_000), reportText("second"))
        showRoute()
        compose.onNodeWithText("About").performScrollTo().performClick()
        compose.onNodeWithText("Saved: 2").assertIsDisplayed()
        openCrashReports()

        compose.onNodeWithText("java.lang.IllegalStateException: second").performClick()
        compose.onNodeWithText("Crash report").assertIsDisplayed()
        compose.onNodeWithText(reportText("second").trimEnd(), substring = true).assertIsDisplayed()

        compose.onNodeWithText("► Delete").performClick()

        compose.onNodeWithText("java.lang.IllegalStateException: first").assertIsDisplayed()
        assertEquals(listOf("java.lang.IllegalStateException: first"), store.list().map { it.summary })
    }

    @Test
    fun `back from a report returns to the list`() {
        store.save(Instant.ofEpochMilli(1_000), reportText("first"))
        showRoute()
        openCrashReports()

        compose.onNodeWithText("java.lang.IllegalStateException: first").performClick()
        compose.onNodeWithText("Crash report").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Reports stay on this phone", substring = true).assertIsDisplayed()

        compose.onNodeWithText("java.lang.IllegalStateException: first").performClick()
        compose.onNodeWithText("◄ Back").performClick()
        compose.onNodeWithText("java.lang.IllegalStateException: first").assertIsDisplayed()
    }

    @Test
    fun `delete all removes every report`() {
        repeat(3) { store.save(Instant.ofEpochMilli(it * 1_000L), reportText("crash $it")) }
        showRoute()
        openCrashReports()

        compose.onNodeWithText("► Delete all").performClick()

        compose.onNodeWithText("No crash reports. Nothing has crashed so far.").assertIsDisplayed()
        assertEquals(emptyList(), store.list())
    }

    @Test
    fun `back with an open report closes it`() {
        val actions = mutableListOf<SettingsAction>()
        compose.setContent {
            SettingsScreen(
                SettingsPage.CrashReports,
                sampleState.copy(openReport = OpenCrashReport("crash-1.txt", "boom")),
                onAction = { actions += it },
            )
        }
        Espresso.pressBack()
        assertEquals(listOf<SettingsAction>(SettingsAction.CloseReport), actions)
    }

    // Screenshots

    private val sampleState =
        SettingsState(
            appVersion = "1.2.3",
            settings =
                Settings(
                    appearance = Appearance(accent = Accent.Cyan, scanlines = true, sky = Sky.Dawn),
                    weather = WeatherSettings(enabled = true, place = berlin),
                ),
            favorites = listOf(launcherApp("Maps"), launcherApp("Signal"), launcherApp("Teams", work = true)),
            hiddenApps = listOf(launcherApp("Bloat")),
            reports =
                listOf(
                    CrashReport(
                        "crash-2.txt",
                        Instant.parse("2026-11-21T20:15:30Z"),
                        "java.lang.IllegalStateException: boom",
                    ),
                    CrashReport("crash-1.txt", Instant.parse("2026-11-20T08:01:02Z"), "java.lang.NullPointerException"),
                ),
        )

    private fun showPage(
        page: SettingsPage,
        state: SettingsState = sampleState,
    ) = compose.setContent { SettingsScreen(page, state, onAction = {}, zone = ZoneOffset.UTC) }

    // Accessibility (#15): every page's controls have a name for TalkBack and a 48 dp touch target.

    @Test
    fun `every settings page is accessible`() {
        val pages =
            SettingsPage.entries - listOf(SettingsPage.Icons, SettingsPage.AppIconEditor) -
                listOf(SettingsPage.WeatherPlace, SettingsPage.WeatherTap)
        var page by mutableStateOf(SettingsPage.Main)
        compose.setContent { SettingsScreen(page, sampleState.copy(apps = sampleState.favorites), onAction = {}) }
        pages.forEach {
            page = it
            compose.waitForIdle()
            compose.assertAccessible(48 * compose.density.density)
        }
    }

    // German (#19): every page, to review the texts and catch layouts that break with longer words.
    @Test
    @Config(qualifiers = "+de")
    fun `screenshot every settings page in German`() {
        val pages =
            SettingsPage.entries - listOf(SettingsPage.Icons, SettingsPage.AppIconEditor) -
                listOf(SettingsPage.WeatherPlace, SettingsPage.WeatherTap)
        var page by mutableStateOf(SettingsPage.Main)
        val state =
            sampleState.copy(
                apps = sampleState.favorites,
                neonTimings = RenderTimings.Summary(count = 87, medianMs = 14, p90Ms = 22),
                pixelTimings = RenderTimings.Summary(count = 1, medianMs = 1, p90Ms = 1),
            )
        compose.setContent { SettingsScreen(page, state, onAction = {}, zone = ZoneOffset.UTC) }
        pages.forEach {
            page = it
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("Settings_de_${it.name}.png")
        }
    }

    @Test
    fun `the weather place search is accessible`() {
        weather { WeatherSettings(enabled = true) }
        showRoute()
        open("Weather")
        compose.onNodeWithText("Weather location").performClick()

        compose.assertAccessible(48 * compose.density.density)
    }

    @Test
    @Config(fontScale = 2f)
    fun `the settings are accessible at the largest font size`() {
        showPage(SettingsPage.Main)
        compose.assertAccessible(48 * compose.density.density)
        compose.onRoot().captureRoboImage("SettingsScreen_font_2x.png")
    }

    @Test
    fun `screenshot English`() {
        showPage(SettingsPage.Main)
        compose.onRoot().captureRoboImage("SettingsScreen_en.png")
    }

    @Test
    @Config(qualifiers = "+de")
    fun `screenshot German`() {
        showPage(SettingsPage.Main)
        compose.onNodeWithText("Aussehen & Themen").assertIsDisplayed()
        compose.onRoot().captureRoboImage("SettingsScreen_de.png")
    }

    @Test
    fun `screenshot appearance`() {
        showPage(SettingsPage.Appearance)
        compose.onRoot().captureRoboImage("SettingsScreen_appearance.png")
    }

    @Test
    fun `screenshot clock`() {
        showPage(SettingsPage.Clock)
        compose.onRoot().captureRoboImage("SettingsScreen_clock.png")
    }

    @Test
    fun `screenshot favorites and hidden apps`() {
        showPage(SettingsPage.Apps)
        compose.onRoot().captureRoboImage("SettingsScreen_apps.png")
    }

    @Test
    fun `screenshot gestures`() {
        showPage(SettingsPage.Gestures)
        compose.onRoot().captureRoboImage("SettingsScreen_gestures.png")
    }

    @Test
    fun `screenshot gestures with the lock service off`() {
        showPage(
            SettingsPage.Gestures,
            sampleState.copy(
                settings = sampleState.settings.copy(gestures = Gestures(doubleTap = GestureAction.Lock)),
                lockServiceOn = false,
            ),
        )
        compose.onRoot().captureRoboImage("SettingsScreen_gestures_lock.png")
    }

    @Test
    fun `screenshot sky following the sun, every phase`() {
        showPage(
            SettingsPage.Wallpaper,
            sampleState.copy(
                settings =
                    sampleState.settings.copy(
                        appearance = sampleState.settings.appearance.copy(skyFollowsSun = true),
                    ),
            ),
        )
        compose.onRoot().captureRoboImage("SettingsScreen_wallpaper_sun.png")
    }

    @Test
    fun `screenshot wallpaper`() {
        showPage(SettingsPage.Wallpaper, sampleState.copy(wallpaper = WallpaperStatus.Done))
        compose.onRoot().captureRoboImage("SettingsScreen_wallpaper.png")
    }

    @Test
    fun `screenshot crash reports`() {
        showPage(SettingsPage.CrashReports)
        compose.onRoot().captureRoboImage("SettingsScreen_crash_reports.png")
    }
}
