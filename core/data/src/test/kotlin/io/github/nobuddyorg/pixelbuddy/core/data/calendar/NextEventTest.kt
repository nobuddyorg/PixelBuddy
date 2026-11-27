package io.github.nobuddyorg.pixelbuddy.core.data.calendar

import io.github.nobuddyorg.pixelbuddy.core.data.settings.CalendarSettings
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextEventTest {
    private val now = Instant.parse("2026-11-27T10:00:00Z")

    private fun event(
        title: String,
        begin: String,
        end: String,
        calendar: Long = 1,
        allDay: Boolean = false,
    ) = CalendarEvent(title.hashCode().toLong(), calendar, title, Instant.parse(begin), Instant.parse(end), allDay)

    private val dentist = event("Dentist", "2026-11-27T14:00:00Z", "2026-11-27T15:00:00Z")
    private val lunch = event("Lunch", "2026-11-27T12:00:00Z", "2026-11-27T13:00:00Z", calendar = 2)
    private val birthday = event("Birthday", "2026-11-27T00:00:00Z", "2026-11-28T00:00:00Z", allDay = true)

    private fun next(
        vararg events: CalendarEvent,
        settings: CalendarSettings = CalendarSettings(enabled = true),
    ) = nextEvent(events.toList(), now, settings)

    @Test
    fun `the soonest upcoming event wins`() {
        assertEquals(lunch, next(dentist, lunch))
    }

    @Test
    fun `a running event counts until it ends, an ended one not`() {
        val running = event("Standup", "2026-11-27T09:45:00Z", "2026-11-27T10:15:00Z")
        val ended = event("Breakfast", "2026-11-27T08:00:00Z", "2026-11-27T09:00:00Z")

        assertEquals(running, next(ended, running, lunch))
    }

    @Test
    fun `only the next 24 hours`() {
        val later = event("Flight", "2026-11-28T11:00:00Z", "2026-11-28T13:00:00Z")

        assertNull(next(later))
    }

    @Test
    fun `all-day events only when wanted`() {
        assertEquals(lunch, next(birthday, lunch))
        assertEquals(birthday, next(birthday, lunch, settings = CalendarSettings(enabled = true, allDay = true)))
    }

    @Test
    fun `only the chosen calendars`() {
        assertEquals(dentist, next(dentist, lunch, settings = CalendarSettings(enabled = true, calendars = setOf(1))))
        assertNull(next(dentist, lunch, settings = CalendarSettings(enabled = true, calendars = emptySet())))
    }
}
