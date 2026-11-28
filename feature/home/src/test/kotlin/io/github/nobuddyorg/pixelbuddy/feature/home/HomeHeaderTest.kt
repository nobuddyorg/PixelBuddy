package io.github.nobuddyorg.pixelbuddy.feature.home

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.assertEquals

private const val S24 = "w411dp-h891dp-420dpi"
private val FRIDAY_EVENING = LocalDateTime.of(2026, 11, 6, 21, 45)
private val DATE_EN = HeaderFormat.date(FRIDAY_EVENING.toLocalDate(), Locale.US)

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = S24)
class HomeHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun show(
        battery: BatteryState? = BatteryState(58, charging = false),
        is24Hour: Boolean = true,
        onClockClick: () -> Unit = {},
        onDateClick: () -> Unit = {},
    ) = compose.setContent {
        Box(Modifier.background(PixelBuddyColors.Navy).padding(24.dp)) {
            HomeHeader(FRIDAY_EVENING, is24Hour, battery, onClockClick, onDateClick)
        }
    }

    @Test
    fun `shows clock, date and battery`() {
        show()

        compose.onNodeWithContentDescription("21:45").assertIsDisplayed()
        compose.onNodeWithText(DATE_EN).assertIsDisplayed()
        compose.onNodeWithContentDescription("Battery 58%").assertIsDisplayed()
    }

    @Test
    fun `clock opens alarms and date opens calendar`() {
        var clock = 0
        var date = 0
        show(onClockClick = { clock++ }, onDateClick = { date++ })

        compose.onNodeWithContentDescription("21:45").performClick()
        compose.onNodeWithText(DATE_EN).performClick()

        assertEquals(1, clock)
        assertEquals(1, date)
    }

    @Test
    fun `alarm and calendar intents`() {
        app.openAlarms()
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, shadowOf(app).nextStartedActivity.action)

        app.openCalendar()
        val calendar = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, calendar.action)
        assertEquals("content://com.android.calendar/time", calendar.data.toString().substringBeforeLast('/'))
    }

    @Test
    fun `a clock app that guards its alarms page opens itself instead`() {
        // Like Samsung's clock: the alarms page needs a permission PixelBuddy doesn't have.
        val clock = "com.example.clock"
        val packages = shadowOf(app.packageManager)
        val alarms = ComponentName(clock, "$clock.Alarms")
        val main = ComponentName(clock, "$clock.Main")
        packages.addActivityIfNotPresent(alarms)
        packages.addIntentFilterForActivity(
            alarms,
            IntentFilter(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            },
        )
        packages.addActivityIfNotPresent(main)
        packages.addIntentFilterForActivity(
            main,
            IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            },
        )
        val guarded =
            object : ContextWrapper(app) {
                override fun startActivity(intent: Intent) {
                    if (intent.action == AlarmClock.ACTION_SHOW_ALARMS) throw SecurityException("needs SET_ALARM")
                    super.startActivity(intent)
                }
            }

        guarded.openAlarms()

        val started = shadowOf(app).nextStartedActivity
        assertEquals(clock, started.component?.packageName ?: started.`package`)
        assertEquals(Intent.ACTION_MAIN, started.action)
    }

    @Test
    fun `battery comes from the sticky broadcast`() {
        @Suppress("DEPRECATION")
        app.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 42)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
                .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_AC),
        )
        compose.setContent {
            val battery by rememberBatteryState()
            HomeHeader(FRIDAY_EVENING, true, battery, {}, {})
        }

        compose.onNodeWithContentDescription("Battery 42%, charging").assertIsDisplayed()
    }

    @Test
    fun `plugging in and out updates the battery while visible`() {
        fun battery(
            level: Int,
            plugged: Int,
        ) = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, level)
            .putExtra(BatteryManager.EXTRA_SCALE, 100)
            .putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
        compose.setContent {
            val state by rememberBatteryState()
            HomeHeader(FRIDAY_EVENING, true, state, {}, {})
        }

        app.sendBroadcast(battery(70, plugged = 0))
        compose.onNodeWithContentDescription("Battery 70%").assertIsDisplayed()

        app.sendBroadcast(battery(70, plugged = BatteryManager.BATTERY_PLUGGED_USB))
        compose.onNodeWithContentDescription("Battery 70%, charging").assertIsDisplayed()

        app.sendBroadcast(battery(71, plugged = 0))
        compose.onNodeWithContentDescription("Battery 71%").assertIsDisplayed()
    }

    @Test
    fun `time tick updates the clock`() {
        val clock = MutableClock(Instant.parse("2026-11-06T21:45:00Z"))
        compose.setContent {
            val now by rememberCurrentTime(clock)
            HomeHeader(now, true, null, {}, {})
        }
        compose.onNodeWithContentDescription("21:45").assertIsDisplayed()

        clock.instant = Instant.parse("2026-11-06T21:46:00Z")
        app.sendBroadcast(Intent(Intent.ACTION_TIME_TICK))

        compose.onNodeWithContentDescription("21:46").assertIsDisplayed()
    }

    private class MutableClock(
        var instant: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = instant
    }

    @Test
    fun `screenshot normal`() {
        show()
        compose.onRoot().captureRoboImage("HomeHeader_normal.png")
    }

    @Test
    fun `screenshot charging`() {
        show(battery = BatteryState(40, charging = true))
        compose.onNodeWithContentDescription("Battery 40%, charging").assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeHeader_charging.png")
    }

    @Test
    fun `screenshot full`() {
        show(battery = BatteryState(100, charging = false, full = true))
        compose.onNodeWithContentDescription("Battery full").assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeHeader_full.png")
    }

    @Test
    fun `screenshot low battery`() {
        show(battery = BatteryState(9, charging = false))
        compose.onRoot().captureRoboImage("HomeHeader_low.png")
    }

    @Test
    fun `screenshot 12 hour clock`() {
        show(is24Hour = false)
        compose.onNodeWithContentDescription("9:45").assertIsDisplayed()
        compose.onNodeWithText("PM").assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeHeader_12h.png")
    }

    @Test
    @Config(fontScale = 1.3f)
    fun `screenshot large font`() {
        show()
        compose.onRoot().captureRoboImage("HomeHeader_large_font.png")
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun `screenshot system light mode`() {
        show()
        compose.onRoot().captureRoboImage("HomeHeader_light.png")
    }

    @Test
    @Config(qualifiers = "+night")
    fun `screenshot system dark mode`() {
        show()
        compose.onRoot().captureRoboImage("HomeHeader_dark.png")
    }

    @Test
    @Config(qualifiers = "+de")
    fun `screenshot German`() {
        show()
        compose.onNodeWithText("Fr., 06. Nov.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Akku " + HeaderFormat.percent(58, Locale.GERMANY)).assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeHeader_de.png")
    }
}
