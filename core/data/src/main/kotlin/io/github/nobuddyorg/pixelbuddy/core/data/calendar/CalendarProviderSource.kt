package io.github.nobuddyorg.pixelbuddy.core.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** [CalendarSource] on Android's calendar provider. Reads only; nothing is stored. */
class CalendarProviderSource(
    private val context: Context,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CalendarSource {
    private val resolver = context.contentResolver

    override fun hasAccess(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun calendars(): List<CalendarInfo> =
        withContext(dispatcher) {
            if (!hasAccess()) return@withContext emptyList()
            val calendars = mutableListOf<CalendarInfo>()
            resolver
                .query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Calendars._ID,
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME,
                        CalendarContract.Calendars.CALENDAR_COLOR,
                    ),
                    "${CalendarContract.Calendars.VISIBLE} = 1",
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        calendars +=
                            CalendarInfo(
                                id = cursor.getLong(0),
                                name = cursor.getString(1).orEmpty(),
                                account = cursor.getString(2).orEmpty(),
                                color = cursor.getInt(3),
                            )
                    }
                }
            calendars.sortedWith(compareBy({ it.name.lowercase() }, { it.account }))
        }

    override suspend fun events(from: Instant): List<CalendarEvent> =
        withContext(dispatcher) {
            if (!hasAccess()) return@withContext emptyList()
            // A day before too: all-day events are stored at UTC midnight, up to a day off the local one.
            val uri =
                CalendarContract.Instances.CONTENT_URI
                    .buildUpon()
                    .also { ContentUris.appendId(it, from.minus(Duration.ofDays(1)).toEpochMilli()) }
                    .also {
                        ContentUris.appendId(
                            it,
                            from.plus(CalendarSource.LOOK_AHEAD).plusSeconds(DAY).toEpochMilli(),
                        )
                    }.build()
            val events = mutableListOf<CalendarEvent>()
            resolver
                .query(
                    uri,
                    arrayOf(
                        CalendarContract.Instances.EVENT_ID,
                        CalendarContract.Instances.CALENDAR_ID,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.ALL_DAY,
                    ),
                    // Shown calendars only; no cancelled events and none the user declined.
                    "${CalendarContract.Instances.VISIBLE} = 1 AND " +
                        "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED} AND " +
                        "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != " +
                        "${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}",
                    null,
                    "${CalendarContract.Instances.BEGIN} ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val allDay = cursor.getInt(5) != 0
                        events +=
                            CalendarEvent(
                                eventId = cursor.getLong(0),
                                calendarId = cursor.getLong(1),
                                title = cursor.getString(2).orEmpty().trim(),
                                begin = localInstant(cursor.getLong(3), allDay),
                                end = localInstant(cursor.getLong(4), allDay),
                                allDay = allDay,
                            )
                    }
                }
            events
        }

    /** All-day events are stored at UTC midnight of their date; they belong to the local date. */
    private fun localInstant(
        millis: Long,
        allDay: Boolean,
    ): Instant {
        val instant = Instant.ofEpochMilli(millis)
        if (!allDay) return instant
        return instant
            .atOffset(ZoneOffset.UTC)
            .toLocalDate()
            .atStartOfDay(zone())
            .toInstant()
    }

    override fun changes(): Flow<Unit> =
        callbackFlow {
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        trySend(Unit)
                    }
                }
            try {
                resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            } catch (_: SecurityException) {
                // No calendar provider access on this phone: no updates.
            }
            awaitClose { resolver.unregisterContentObserver(observer) }
        }.conflate()

    override fun openIntent(event: CalendarEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin.toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end.toEpochMilli())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val DAY = 86_400L
    }
}
