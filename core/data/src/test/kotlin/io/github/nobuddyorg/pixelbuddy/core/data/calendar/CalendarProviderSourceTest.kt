package io.github.nobuddyorg.pixelbuddy.core.data.calendar

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CalendarProviderSourceTest {
    /** A calendar provider with fixed rows; remembers the last instances query. */
    class FakeCalendar : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            val cursor = MatrixCursor(projection)
            if (uri.pathSegments.firstOrNull() == "instances") {
                lastSelection = selection
                instances.forEach { cursor.addRow(it) }
            } else {
                calendars.forEach { cursor.addRow(it) }
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0

        companion object {
            var instances = listOf<Array<Any>>()
            var calendars = listOf<Array<Any>>()
            var lastSelection: String? = null
        }
    }

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val berlin = ZoneId.of("Europe/Berlin")
    private val source = CalendarProviderSource(app, zone = { berlin }, dispatcher = Dispatchers.Unconfined)

    @Before
    fun provider() {
        Robolectric.setupContentProvider(FakeCalendar::class.java, CalendarContract.AUTHORITY)
        FakeCalendar.instances = emptyList()
        FakeCalendar.calendars = emptyList()
    }

    private fun grant() = shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR)

    @Test
    fun `without the permission nothing is read`() =
        runTest {
            FakeCalendar.calendars = listOf(arrayOf(1L, "Work", "me@example.org", 0))

            assertEquals(false, source.hasAccess())
            assertEquals(emptyList(), source.calendars())
            assertEquals(emptyList(), source.events(Instant.now()))
        }

    @Test
    fun `calendars are listed by name`() =
        runTest {
            grant()
            FakeCalendar.calendars =
                listOf(arrayOf(2L, "work", "me@example.org", 1), arrayOf(1L, "Family", "me@example.org", 2))

            assertEquals(listOf("Family", "work"), source.calendars().map { it.name })
        }

    @Test
    fun `events are read, all-day ones moved to the local date, cancelled and declined ones left out`() =
        runTest {
            grant()
            FakeCalendar.instances =
                listOf(
                    arrayOf(7L, 1L, " Dentist ", 1_795_000_000_000L, 1_795_003_600_000L, 0),
                    // 2026-11-27, stored at UTC midnight.
                    arrayOf(8L, 1L, "Birthday", 1_795_737_600_000L, 1_795_824_000_000L, 1),
                )

            val events = source.events(Instant.parse("2026-11-27T08:00:00Z"))

            assertEquals("Dentist", events[0].title)
            assertEquals(Instant.ofEpochMilli(1_795_000_000_000L), events[0].begin)
            assertEquals(Instant.parse("2026-11-26T23:00:00Z"), events[1].begin)
            assertTrue(events[1].allDay)
            val selection = FakeCalendar.lastSelection.orEmpty()
            assertTrue("visible = 1" in selection && "eventStatus" in selection && "selfAttendeeStatus" in selection)
        }

    @Test
    fun `changes come from the calendar provider`() =
        runTest {
            source.changes().test {
                app.contentResolver.notifyChange(CalendarContract.Events.CONTENT_URI, null)
                ShadowLooper.idleMainLooper()
                assertEquals(Unit, awaitItem())
            }
        }

    @Test
    fun `events open in the calendar app at their time`() {
        val event =
            CalendarEvent(7, 1, "Dentist", Instant.ofEpochMilli(1000), Instant.ofEpochMilli(2000), allDay = false)

        val intent = source.openIntent(event)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("content://com.android.calendar/events/7", intent.dataString)
        assertEquals(1000L, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0))
        assertEquals(2000L, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0))
    }
}
