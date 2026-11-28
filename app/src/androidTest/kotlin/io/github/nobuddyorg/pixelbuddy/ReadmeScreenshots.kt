package io.github.nobuddyorg.pixelbuddy

import android.content.pm.LauncherApps
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyleSetting
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SetupStep
import io.github.nobuddyorg.pixelbuddy.core.data.settings.TemperatureUnit
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import io.github.nobuddyorg.pixelbuddy.core.weather.Coordinates
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherCache
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherReport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The README screenshots (#71): PixelBuddy on an emulator with real installed apps, so every icon is a real app icon
 * drawn by PixelBuddy. Only runs when asked (`-Pandroid.testInstrumentationRunnerArguments.readmeScreenshots=true`,
 * see .github/workflows/readme-screenshots.yml); the PNGs land in [OUTPUT_DIR] on the device.
 *
 * Time, battery, weather and the status bar are fixed, so the pictures only change when PixelBuddy or the system
 * image's apps do. The app icons are only used on the device; the repository only gets the screenshots.
 */
@RunWith(AndroidJUnit4::class)
class ReadmeScreenshots {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val pkg = context.packageName

    @Before
    fun prepare() {
        assumeTrue(
            "README screenshots only on request",
            InstrumentationRegistry.getArguments().getString(ARGUMENT) == "true",
        )
        shell("rm -rf $OUTPUT_DIR")
        shell("mkdir -p $OUTPUT_DIR")
        fixDevice()
        runBlocking {
            context.appContainer.settings.update {
                it.copy(
                    setup = SetupStep.Done,
                    favorites = favoriteApps(),
                    iconStyle = IconStyleSetting(),
                    weather = WeatherSettings(enabled = true, unit = TemperatureUnit.Celsius, place = BERLIN),
                )
            }
        }
        // A fresh report, so home shows it without going online.
        WeatherCache(File(context.noBackupFilesDir, "weather.json")).write(
            WeatherReport(
                temperatureCelsius = 4.0,
                weatherCode = CLEAR_SKY,
                isDay = false,
                coordinates = Coordinates(BERLIN.latitude, BERLIN.longitude).rounded(),
                fetchedAt = Instant.now(),
            ),
        )
        shell("cmd role add-role-holder --user 0 android.app.role.HOME $pkg")
        device.pressHome()
        assertTrue(device.wait(Until.hasObject(By.pkg(pkg).depth(0)), TIMEOUT_MS), "PixelBuddy isn't home")
    }

    @After
    fun restore() {
        if (InstrumentationRegistry.getArguments().getString(ARGUMENT) != "true") return
        demo("exit")
        shell("dumpsys battery reset")
        shell("wm density reset")
        shell("wm size reset")
        shell("settings put global auto_time 1")
        shell("settings put global auto_time_zone 1")
    }

    @Test
    fun captureTheReadmeScreens() {
        assertNotNull(device.wait(Until.findObject(By.desc(RAIL)), TIMEOUT_MS), "home didn't show")
        snap("home")

        holdOnRail(letter = 'C') { snap("rail") }
        device.pressHome()

        device.findObject(By.textContains("SEARCH")).click()
        val field = device.wait(Until.findObject(By.focused(true)), TIMEOUT_MS)
        assertNotNull(field, "the search field isn't focused")
        field.text = "ca"
        snap("search")
        device.pressBack()
        device.pressBack()
        device.pressHome()

        runBlocking {
            context.appContainer.settings.update { it.copy(iconStyle = IconStyleSetting(IconPreset.NeonLines.name)) }
        }
        snap("home-neon")

        longPressEmptySpace()
        assertNotNull(device.wait(Until.findObject(By.text("App icons")), TIMEOUT_MS), "settings didn't open")
        device.findObject(By.text("App icons")).click()
        assertNotNull(device.wait(Until.findObject(By.textContains("Apply")), TIMEOUT_MS), "no icon editor")
        snap("icon-editor")
        device.pressBack()
        device.pressBack()
    }

    /** A screen like a Galaxy S24, Fri 6 Nov 21:45 in Berlin, 58 % battery and a tidy status bar. */
    private fun fixDevice() {
        shell("wm size 1080x2340")
        shell("wm density 450")
        shell("settings put global auto_time 0")
        shell("settings put global auto_time_zone 0")
        shell("settings put system time_12_24 24")
        shell("cmd alarm set-timezone ${ZONE.id}")
        shell("cmd alarm set-time ${TIME.atZone(ZONE).toInstant().toEpochMilli()}")
        val clock = shell("date +%H:%M").trim()
        if (clock != "21:45") Log.w(TAG, "The time couldn't be set, the screenshots show $clock")
        shell("dumpsys battery unplug")
        shell("dumpsys battery set level $BATTERY")
        shell("settings put global sysui_demo_allowed 1")
        demo("enter")
        demo("clock", "hhmm" to "2145")
        demo("battery", "level" to "$BATTERY", "plugged" to "false")
        demo("network", "wifi" to "show", "level" to "4")
        demo("network", "mobile" to "hide")
        demo("notifications", "visible" to "false")
        demo("status", "volume" to "hide", "bluetooth" to "hide", "alarm" to "hide")
    }

    /** Well-known apps of the system image first, then others, so home shows icons people recognize. */
    private fun favoriteApps(): List<AppKey> {
        val user = Process.myUserHandle()
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        val activities =
            context
                .getSystemService(LauncherApps::class.java)
                .getActivityList(null, user)
                .filter { it.componentName.packageName != pkg }
        Log.i(TAG, "Apps: " + activities.joinToString { "${it.label} (${it.componentName.packageName})" })
        val ranked =
            activities.sortedWith(
                compareBy(
                    { PREFERRED.indexOf(it.componentName.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                    { it.label.toString() },
                ),
            )
        return ranked.take(FAVORITES).map {
            AppKey(it.componentName.packageName, it.componentName.className, serial)
        }
    }

    /** Touches the rail at ★, drags to [letter] and holds there while [whileHeld] runs, like a finger would. */
    private fun holdOnRail(
        letter: Char,
        whileHeld: () -> Unit,
    ) {
        val rail = device.findObject(By.desc(RAIL)).visibleBounds
        // ★, A–Z, #: every entry has the same height.
        val cell = rail.height() / RAIL_ENTRIES.toFloat()
        val x = rail.exactCenterX()
        val startY = rail.top + cell / 2
        val endY = rail.top + (letter - 'A' + 1.5f) * cell
        val downTime = SystemClock.uptimeMillis()
        inject(downTime, MotionEvent.ACTION_DOWN, x, startY)
        for (step in 1..DRAG_STEPS) {
            inject(downTime, MotionEvent.ACTION_MOVE, x, startY + (endY - startY) * step / DRAG_STEPS)
            SystemClock.sleep(DRAG_STEP_MS)
        }
        try {
            whileHeld()
        } finally {
            inject(downTime, MotionEvent.ACTION_UP, x, endY)
        }
    }

    private fun inject(
        downTime: Long,
        action: Int,
        x: Float,
        y: Float,
    ) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        instrumentation.uiAutomation.injectInputEvent(event, true)
        event.recycle()
    }

    private fun longPressEmptySpace() {
        val x = device.displayWidth / 2
        val y = device.displayHeight * 4 / 5
        device.swipe(x, y, x, y, LONG_PRESS_STEPS)
    }

    /** Lets icons render and animations end, then saves the screen as [name].png. */
    private fun snap(name: String) {
        device.waitForIdle()
        SystemClock.sleep(SETTLE_MS)
        shell("screencap -p $OUTPUT_DIR/$name.png")
    }

    private fun demo(
        command: String,
        vararg extras: Pair<String, String>,
    ) {
        val args = extras.joinToString(" ") { (key, value) -> "-e $key $value" }
        shell("am broadcast -a com.android.systemui.demo -e command $command $args")
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private companion object {
        const val TAG = "ReadmeScreenshots"
        const val ARGUMENT = "readmeScreenshots"
        const val OUTPUT_DIR = "/data/local/tmp/readme-screenshots"
        const val RAIL = "Alphabet"
        const val RAIL_ENTRIES = 28
        const val FAVORITES = 7
        const val BATTERY = 58
        const val CLEAR_SKY = 0
        const val TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 3_000L
        const val DRAG_STEPS = 10
        const val DRAG_STEP_MS = 30L

        /** Swipe steps of about 5 ms each, standing still: a long press. */
        const val LONG_PRESS_STEPS = 200

        val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        val TIME: LocalDateTime = LocalDateTime.of(2026, 11, 6, 21, 45)
        val BERLIN = WeatherPlace("Berlin", country = "Germany", latitude = 52.52, longitude = 13.41)

        val PREFERRED =
            listOf(
                "com.android.chrome",
                "com.google.android.apps.maps",
                "com.google.android.gm",
                "com.google.android.youtube",
                "com.google.android.apps.photos",
                "com.google.android.dialer",
                "com.google.android.apps.messaging",
                "com.google.android.calendar",
                "com.google.android.deskclock",
            )
    }
}
