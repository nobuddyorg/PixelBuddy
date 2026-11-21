package io.github.nobuddyorg.pixelbuddy.feature.home

import android.content.Intent
import android.os.BatteryManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class HeaderFormatTest {
    @Test
    fun `clock follows the 12 or 24 hour setting`() {
        assertEquals("09:05", HeaderFormat.clock(LocalTime.of(9, 5), is24Hour = true))
        assertEquals("21:05", HeaderFormat.clock(LocalTime.of(21, 5), is24Hour = true))
        assertEquals("9:05", HeaderFormat.clock(LocalTime.of(21, 5), is24Hour = false))
        assertEquals("12:00", HeaderFormat.clock(LocalTime.of(0, 0), is24Hour = false))
        assertEquals("PM", HeaderFormat.amPm(LocalTime.of(21, 5), Locale.US))
    }

    @Test
    fun `date is short weekday, day and month in the locale's order`() {
        val friday = LocalDate.of(2026, 11, 6)

        assertEquals("Fri 06 Nov", HeaderFormat.date(friday, Locale.UK))
        assertEquals("Fri, Nov 06", HeaderFormat.date(friday, Locale.US))
        assertEquals("Fr., 06. Nov.", HeaderFormat.date(friday, Locale.GERMANY))
    }

    @Test
    fun `percent uses the locale's format`() {
        assertEquals("58%", HeaderFormat.percent(58, Locale.US))
        assertEquals("58 %", HeaderFormat.percent(58, Locale.GERMANY).replace(' ', ' '))
    }

    private fun battery(
        level: Int,
        scale: Int = 100,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
        plugged: Int = 0,
    ) = Intent(Intent.ACTION_BATTERY_CHANGED)
        .putExtra(BatteryManager.EXTRA_LEVEL, level)
        .putExtra(BatteryManager.EXTRA_SCALE, scale)
        .putExtra(BatteryManager.EXTRA_STATUS, status)
        .putExtra(BatteryManager.EXTRA_PLUGGED, plugged)

    @Test
    fun `battery broadcast maps to level and charging state`() {
        assertEquals(BatteryState(58, charging = false), HeaderFormat.battery(battery(58)))
        assertEquals(
            BatteryState(40, charging = true),
            HeaderFormat.battery(
                battery(
                    40,
                    status = BatteryManager.BATTERY_STATUS_CHARGING,
                    plugged = BatteryManager.BATTERY_PLUGGED_USB,
                ),
            ),
        )
        assertEquals(BatteryState(50, charging = false), HeaderFormat.battery(battery(level = 1, scale = 2)))
    }

    @Test
    fun `plugged in at 100 percent counts as full`() {
        val full =
            HeaderFormat.battery(
                battery(100, status = BatteryManager.BATTERY_STATUS_FULL, plugged = BatteryManager.BATTERY_PLUGGED_AC),
            )

        assertEquals(BatteryState(100, charging = false, full = true), full)
    }

    @Test
    fun `low battery is flagged`() {
        val state = HeaderFormat.battery(battery(9))!!

        assertEquals(true, state.isLow)
    }

    @Test
    fun `broadcast without level is ignored`() {
        assertNull(HeaderFormat.battery(null))
        assertNull(HeaderFormat.battery(Intent(Intent.ACTION_BATTERY_CHANGED)))
    }
}
