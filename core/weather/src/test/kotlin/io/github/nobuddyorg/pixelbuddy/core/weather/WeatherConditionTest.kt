package io.github.nobuddyorg.pixelbuddy.core.weather

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WeatherConditionTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `WMO codes map to glyphs`() {
        val expected =
            mapOf(
                0 to WeatherCondition.ClearDay,
                1 to WeatherCondition.ClearDay,
                2 to WeatherCondition.PartlyCloudy,
                3 to WeatherCondition.Cloudy,
                45 to WeatherCondition.Fog,
                48 to WeatherCondition.Fog,
                51 to WeatherCondition.Rain,
                57 to WeatherCondition.Rain,
                61 to WeatherCondition.Rain,
                67 to WeatherCondition.Rain,
                80 to WeatherCondition.Rain,
                82 to WeatherCondition.Rain,
                71 to WeatherCondition.Snow,
                77 to WeatherCondition.Snow,
                85 to WeatherCondition.Snow,
                86 to WeatherCondition.Snow,
                95 to WeatherCondition.Thunder,
                96 to WeatherCondition.Thunder,
                99 to WeatherCondition.Thunder,
                // Unknown codes look cloudy rather than wrong.
                4 to WeatherCondition.Cloudy,
                -1 to WeatherCondition.Cloudy,
                100 to WeatherCondition.Cloudy,
            )
        expected.forEach { (code, condition) ->
            assertEquals(condition, WeatherCondition.fromWmo(code, isDay = true), "code $code")
        }
    }

    @Test
    fun `clear skies at night show the moon`() {
        assertEquals(WeatherCondition.ClearNight, WeatherCondition.fromWmo(0, isDay = false))
        assertEquals(WeatherCondition.ClearNight, WeatherCondition.fromWmo(1, isDay = false))
        assertEquals(WeatherCondition.Rain, WeatherCondition.fromWmo(61, isDay = false))
    }

    @Test
    fun `the cache survives a round trip and ignores broken files`() {
        val file = File(folder.root, "sub/weather.json")
        val cache = WeatherCache(file)
        val report = WeatherReport(-3.5, 71, false, Coordinates(52.5, 13.4), Instant.ofEpochMilli(1_795_000_000_000))

        assertNull(cache.read())
        cache.write(report)
        assertEquals(report, cache.read())
        file.writeText("{broken")
        assertNull(cache.read())
        cache.clear()
        assertNull(cache.read())
    }
}
