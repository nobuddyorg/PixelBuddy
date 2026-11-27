package io.github.nobuddyorg.pixelbuddy.core.data.sky

import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SunPositionTest {
    private val berlin = GeoPoint(52.52, 13.405)
    private val sydney = GeoPoint(-33.87, 151.21)

    /** The minute in [from]..[to] the sun crosses the horizon (-0.833° with refraction), rising or setting. */
    private fun crossing(
        place: GeoPoint,
        from: String,
        to: String,
    ): Instant {
        var time = Instant.parse(from)
        val end = Instant.parse(to)
        val startsUp = SunPosition.at(time, place).elevation > HORIZON
        while (time < end) {
            val next = time.plusSeconds(60)
            if ((SunPosition.at(next, place).elevation > HORIZON) != startsUp) return next
            time = next
        }
        error("No sunrise or sunset between $from and $to")
    }

    private fun assertNear(
        expected: String,
        actual: Instant,
    ) {
        val minutes = Duration.between(Instant.parse(expected), actual).toMinutes()
        assertTrue(abs(minutes) <= 3, "expected $expected, got $actual")
    }

    // Reference times from published sunrise/sunset tables (timeanddate.com, NOAA solar calculator).

    @Test
    fun `Berlin at midsummer rises at 04 43 and sets at 21 33 summer time`() {
        assertNear("2026-06-21T02:43:00Z", crossing(berlin, "2026-06-21T00:00:00Z", "2026-06-21T06:00:00Z"))
        assertNear("2026-06-21T19:33:00Z", crossing(berlin, "2026-06-21T17:00:00Z", "2026-06-21T22:00:00Z"))
    }

    @Test
    fun `Berlin at midwinter rises at 08 15 and sets at 15 54`() {
        assertNear("2026-12-21T07:15:00Z", crossing(berlin, "2026-12-21T05:00:00Z", "2026-12-21T09:00:00Z"))
        assertNear("2026-12-21T14:54:00Z", crossing(berlin, "2026-12-21T13:00:00Z", "2026-12-21T17:00:00Z"))
    }

    @Test
    fun `in Sydney December is summer`() {
        // 05:41 and 20:05 local daylight time (UTC+11).
        assertNear("2026-12-20T18:41:00Z", crossing(sydney, "2026-12-20T16:00:00Z", "2026-12-20T21:00:00Z"))
        assertNear("2026-12-21T09:05:00Z", crossing(sydney, "2026-12-21T07:00:00Z", "2026-12-21T11:00:00Z"))
    }

    @Test
    fun `at midsummer noon the sun stands 90 minus latitude plus the tilt high`() {
        // Solar noon in Berlin is about 13:07 summer time.
        val noon = SunPosition.at(Instant.parse("2026-06-21T11:07:00Z"), berlin)

        assertEquals(90 - 52.52 + 23.44, noon.elevation, 0.3)
    }

    @Test
    fun `the sun rises before noon and sets after`() {
        assertTrue(SunPosition.at(Instant.parse("2026-06-21T06:00:00Z"), berlin).morning)
        assertFalse(SunPosition.at(Instant.parse("2026-06-21T16:00:00Z"), berlin).morning)
    }

    private companion object {
        const val HORIZON = -0.833
    }
}
