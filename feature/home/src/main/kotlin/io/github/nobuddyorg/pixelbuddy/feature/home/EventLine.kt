package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarEvent
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText
import java.time.Instant
import java.time.ZoneId

/** When an event is, as home says it (#27). */
internal sealed interface EventTime {
    /** All day, today or tomorrow. */
    data class AllDay(
        val tomorrow: Boolean,
    ) : EventTime

    /** Already running: until [end]. */
    data class Until(
        val end: String,
    ) : EventTime

    /** Starts at [begin], today or tomorrow. */
    data class At(
        val begin: String,
        val tomorrow: Boolean,
    ) : EventTime

    companion object {
        fun of(
            event: CalendarEvent,
            now: Instant,
            zone: ZoneId,
            is24Hour: Boolean,
        ): EventTime {
            val today = now.atZone(zone).toLocalDate()
            val begin = event.begin.atZone(zone)
            val tomorrow = begin.toLocalDate().isAfter(today)
            return when {
                event.allDay -> AllDay(tomorrow)
                event.begin <= now -> Until(HeaderFormat.clock(event.end.atZone(zone).toLocalTime(), is24Hour))
                else -> At(HeaderFormat.clock(begin.toLocalTime(), is24Hour), tomorrow)
            }
        }
    }
}

/** The next calendar event under the date, e.g. "► 14:00 Dentist"; tapping it opens the event. */
@Composable
internal fun EventLine(
    event: CalendarEvent,
    time: EventTime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = event.title.ifBlank { stringResource(R.string.home_event_untitled) }
    val text =
        when (time) {
            is EventTime.AllDay -> {
                stringResource(
                    if (time.tomorrow) R.string.home_event_all_day_tomorrow else R.string.home_event_all_day,
                    title,
                )
            }

            is EventTime.Until -> {
                stringResource(R.string.home_event_until, time.end, title)
            }

            is EventTime.At -> {
                stringResource(
                    if (time.tomorrow) R.string.home_event_tomorrow else R.string.home_event_today,
                    time.begin,
                    title,
                )
            }
        }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.home_event_open),
                    onClick = onClick,
                ),
    ) {
        BasicText(
            "► $text",
            style = PixelText.small.copy(color = PixelBuddyColors.Text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
