package io.github.nobuddyorg.pixelbuddy.core.data.calendar

import android.content.Intent
import io.github.nobuddyorg.pixelbuddy.core.data.settings.CalendarSettings
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/** One occurrence of a calendar event (#27). Read from the phone's calendar, kept in memory only. */
data class CalendarEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val begin: Instant,
    val end: Instant,
    val allDay: Boolean,
)

/** A calendar on the phone, to choose which ones home shows. */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
)

/** The phone's calendars, through the opt-in calendar permission (#27). */
interface CalendarSource {
    /** Whether the calendar permission is granted. */
    fun hasAccess(): Boolean

    /** The calendars Android shows, by name. */
    suspend fun calendars(): List<CalendarInfo>

    /** Events overlapping [from] until [from] + [LOOK_AHEAD]; empty without access. */
    suspend fun events(from: Instant): List<CalendarEvent>

    /** Emits whenever the calendar may have changed; listens only while collected. */
    fun changes(): Flow<Unit>

    /** Opens [event] in the calendar app. */
    fun openIntent(event: CalendarEvent): Intent

    companion object {
        val LOOK_AHEAD: Duration = Duration.ofHours(24)
    }
}

/**
 * The event home shows at [now]: the next one that hasn't ended, from the chosen calendars, within the next 24 hours;
 * all-day events only if [settings] say so. A running event counts until it ends.
 */
fun nextEvent(
    events: List<CalendarEvent>,
    now: Instant,
    settings: CalendarSettings,
): CalendarEvent? =
    events
        .asSequence()
        .filter { settings.calendars == null || it.calendarId in settings.calendars }
        .filter { settings.allDay || !it.allDay }
        .filter { it.end > now && it.begin < now.plus(CalendarSource.LOOK_AHEAD) }
        .sortedWith(compareBy({ it.begin }, { !it.allDay }, { it.title }))
        .firstOrNull()
