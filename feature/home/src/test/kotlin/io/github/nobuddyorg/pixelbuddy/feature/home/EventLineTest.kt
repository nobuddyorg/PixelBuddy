package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarEvent
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class EventLineTest {
    @get:Rule
    val compose = createComposeRule()

    private val berlin = ZoneId.of("Europe/Berlin")

    // Friday 27 Nov 2026, 11:00 in Berlin.
    private val now = Instant.parse("2026-11-27T10:00:00Z")

    private fun event(
        begin: String,
        end: String,
        allDay: Boolean = false,
        title: String = "Dentist",
    ) = CalendarEvent(7, 1, title, Instant.parse(begin), Instant.parse(end), allDay)

    @Test
    fun `later today shows the start time`() {
        assertEquals(
            EventTime.At("15:00", tomorrow = false),
            EventTime.of(event("2026-11-27T14:00:00Z", "2026-11-27T15:00:00Z"), now, berlin, is24Hour = true),
        )
        assertEquals(
            EventTime.At("3:00", tomorrow = false),
            EventTime.of(event("2026-11-27T14:00:00Z", "2026-11-27T15:00:00Z"), now, berlin, is24Hour = false),
        )
    }

    @Test
    fun `after midnight it is tomorrow, a running event shows its end`() {
        assertEquals(
            EventTime.At("09:00", tomorrow = true),
            EventTime.of(event("2026-11-28T08:00:00Z", "2026-11-28T09:00:00Z"), now, berlin, is24Hour = true),
        )
        assertEquals(
            EventTime.Until("11:30"),
            EventTime.of(event("2026-11-27T09:45:00Z", "2026-11-27T10:30:00Z"), now, berlin, is24Hour = true),
        )
    }

    @Test
    fun `all-day events say today or tomorrow`() {
        assertEquals(
            EventTime.AllDay(tomorrow = false),
            EventTime.of(event("2026-11-26T23:00:00Z", "2026-11-27T23:00:00Z", allDay = true), now, berlin, true),
        )
        assertEquals(
            EventTime.AllDay(tomorrow = true),
            EventTime.of(event("2026-11-27T23:00:00Z", "2026-11-28T23:00:00Z", allDay = true), now, berlin, true),
        )
    }

    private fun show(
        time: EventTime,
        title: String = "Dentist",
        onClick: () -> Unit = {},
    ) = compose.setContent {
        Column(Modifier.background(PixelBuddyColors.Navy).padding(32.dp)) {
            HomeHeader(LocalDateTime.of(2026, 11, 27, 11, 0), true, BatteryState(58, charging = false), {}, {})
            EventLine(event("2026-11-27T14:00:00Z", "2026-11-27T15:00:00Z", title = title), time, onClick)
        }
    }

    @Test
    fun `the line reads like the header and opens the event`() {
        var opened = 0
        show(EventTime.At("15:00", tomorrow = true), onClick = { opened++ })

        compose.onNodeWithText("► Tomorrow 15:00 Dentist").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `running, all-day and untitled events read clearly`() {
        show(EventTime.Until("11:30"), title = "")
        compose.onNodeWithText("► Until 11:30 (No title)").assertIsDisplayed()
    }

    @Test
    fun `the event line is accessible`() {
        show(EventTime.AllDay(tomorrow = false))

        compose.onNodeWithText("► Today: Dentist").assertIsDisplayed()
        compose.assertAccessible(48 * compose.density.density)
    }

    @Test
    fun `screenshot next event`() {
        show(EventTime.At("15:00", tomorrow = false))

        compose.onRoot().captureRoboImage("HomeHeader_event.png")
    }
}
