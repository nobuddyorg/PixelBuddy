package io.github.nobuddyorg.pixelbuddy.core.data.sky

import io.github.nobuddyorg.pixelbuddy.core.data.settings.Sky
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkyCycleTest {
    private val berlin = GeoPoint(52.52, 13.405)

    @Test
    fun `phases follow the sun's elevation, rising and setting`() {
        assertEquals(SkyPhase.Night, SkyPhase.of(SunPosition(-20.0, morning = true)))
        assertEquals(SkyPhase.EarlyDawn, SkyPhase.of(SunPosition(-7.0, morning = true)))
        assertEquals(SkyPhase.Dawn, SkyPhase.of(SunPosition(0.0, morning = true)))
        assertEquals(SkyPhase.Morning, SkyPhase.of(SunPosition(7.0, morning = true)))
        assertEquals(SkyPhase.Day, SkyPhase.of(SunPosition(40.0, morning = true)))
        assertEquals(SkyPhase.Day, SkyPhase.of(SunPosition(40.0, morning = false)))
        assertEquals(SkyPhase.Evening, SkyPhase.of(SunPosition(7.0, morning = false)))
        assertEquals(SkyPhase.Dusk, SkyPhase.of(SunPosition(0.0, morning = false)))
        assertEquals(SkyPhase.LateDusk, SkyPhase.of(SunPosition(-7.0, morning = false)))
        assertEquals(SkyPhase.Night, SkyPhase.of(SunPosition(-20.0, morning = false)))
    }

    @Test
    fun `a summer day in Berlin goes through every phase in order, a few changes a day`() {
        var time = Instant.parse("2026-06-21T00:00:00Z")
        val phases = mutableListOf(SkyPhase.at(time, berlin))
        repeat(10) {
            time = SkyPhase.nextChange(time, berlin) ?: return@repeat
            phases += SkyPhase.at(time, berlin)
        }

        // Starts after midnight in the late dusk/night; a full cycle has eight changes.
        val dayCycle =
            listOf(
                SkyPhase.EarlyDawn,
                SkyPhase.Dawn,
                SkyPhase.Morning,
                SkyPhase.Day,
                SkyPhase.Evening,
                SkyPhase.Dusk,
                SkyPhase.LateDusk,
            )
        assertTrue(phases.windowed(dayCycle.size).any { it == dayCycle }, "phases: $phases")
    }

    @Test
    fun `with four changes a day the blends join their twilight`() {
        var time = Instant.parse("2026-06-21T00:00:00Z")
        val phases = mutableListOf(SkyPhase.at(time, berlin, fourSteps = true))
        repeat(6) {
            time = SkyPhase.nextChange(time, berlin, fourSteps = true) ?: return@repeat
            phases += SkyPhase.at(time, berlin, fourSteps = true)
        }

        // Only the four main looks, each change to a different one.
        assertTrue(phases.all { it in listOf(SkyPhase.Night, SkyPhase.Dawn, SkyPhase.Day, SkyPhase.Dusk) }, "$phases")
        assertTrue(phases.zipWithNext().all { (a, b) -> a != b }, "$phases")
        assertTrue(phases.windowed(3).any { it == listOf(SkyPhase.Dawn, SkyPhase.Day, SkyPhase.Dusk) }, "$phases")
        assertEquals(
            listOf(SkyPhase.Night, SkyPhase.Dawn, SkyPhase.Day, SkyPhase.Dusk),
            SkyPhase.day(fourSteps = true),
        )
        assertEquals(SkyPhase.entries, SkyPhase.day(fourSteps = false))
    }

    @Test
    fun `dark nights are almost black, and so are the blends into them`() {
        fun brightness(stops: List<Int>) = stops.sumOf { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) }

        assertTrue(brightness(SkyPhase.Night.stops(darkNight = true)) < brightness(SkyPhase.Night.stops) / 2)
        assertTrue(SkyPhase.Night.stops(darkNight = true).first() == 0xFF000000.toInt())
        assertTrue(brightness(SkyPhase.LateDusk.stops(darkNight = true)) < brightness(SkyPhase.LateDusk.stops))
        assertTrue(brightness(SkyPhase.EarlyDawn.stops(darkNight = true)) < brightness(SkyPhase.EarlyDawn.stops))
        // Day, dawn and dusk don't change.
        listOf(SkyPhase.Dawn, SkyPhase.Day, SkyPhase.Dusk).forEach {
            assertEquals(it.stops, it.stops(darkNight = true), "$it")
        }
    }

    @Test
    fun `the next change is the first minute of the new phase`() {
        val start = Instant.parse("2026-12-21T05:00:00Z")
        val before = SkyPhase.at(start, berlin)

        val change = assertNotNull(SkyPhase.nextChange(start, berlin))

        assertEquals(before, SkyPhase.at(change.minusSeconds(60), berlin))
        assertTrue(SkyPhase.at(change, berlin) != before)
        assertTrue(Duration.between(start, change) < Duration.ofHours(3))
    }

    @Test
    fun `in the polar night the sky stays night`() {
        val longyearbyen = GeoPoint(78.22, 15.65)
        val noon = Instant.parse("2026-12-21T11:00:00Z")

        assertEquals(SkyPhase.Night, SkyPhase.at(noon, longyearbyen))
        assertNull(SkyPhase.nextChange(noon, longyearbyen))
    }

    @Test
    fun `the looks come from the skies, with half-and-half blends between them`() {
        assertEquals(Sky.Night.stops.toList(), SkyPhase.Night.stops)
        assertEquals(Sky.Dawn.stops.toList(), SkyPhase.Dawn.stops)
        assertEquals(Sky.Dusk.stops.toList(), SkyPhase.Dusk.stops)
        // Night top 0x0B0E24 and dawn top 0x1E2A5A: each channel halfway.
        assertEquals(0xFF151C3F.toInt(), SkyPhase.EarlyDawn.stops.first())
        SkyPhase.entries.forEach { assertEquals(4, it.stops.size, "$it") }
    }

    @Test
    fun `the weather place wins, otherwise the time zone gives a rough place`() {
        val now = Instant.parse("2026-06-21T12:00:00Z")
        assertEquals(berlin, SkyLocation.resolve(berlin, ZoneId.of("Asia/Tokyo"), now))

        val tokyo = SkyLocation.resolve(null, ZoneId.of("Asia/Tokyo"), now)
        assertEquals(135.0, tokyo.longitude)
        assertTrue(tokyo.latitude > 0)

        // Standard time, not summer time: Berlin's zone is UTC+1.
        assertEquals(15.0, SkyLocation.resolve(null, ZoneId.of("Europe/Berlin"), now).longitude)
        assertTrue(SkyLocation.resolve(null, ZoneId.of("Australia/Sydney"), now).latitude < 0)
    }

    @Test
    fun `the day sky is no brighter at the bottom than the dusk's orange, so light text stays readable`() {
        fun luminance(argb: Int): Double {
            fun linear(channel: Int): Double {
                val c = channel / 255.0
                return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) +
                0.0722 * linear(argb and 0xFF)
        }
        SkyPhase.entries.forEach {
            assertTrue(luminance(it.stops.last()) <= luminance(Sky.Dusk.stops.last()), "$it")
            // The clock sits on the top colour: as dark as the fixed skies' tops.
            assertTrue(luminance(it.stops.first()) <= luminance(Sky.Dawn.stops.first()), "$it")
        }
    }
}
