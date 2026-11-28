package io.github.nobuddyorg.pixelbuddy

import androidx.compose.ui.graphics.Color
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Accent
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SizeStep
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Sky
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.data.sky.GeoPoint
import io.github.nobuddyorg.pixelbuddy.core.data.sky.SkyPhase
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyStyle
import io.github.nobuddyorg.pixelbuddy.core.weather.Coordinates
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherReport
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppearanceStyleTest {
    @Test
    fun `the defaults are the design system's defaults`() {
        assertEquals(PixelBuddyStyle(), Appearance().toStyle())
    }

    @Test
    fun `every appearance setting reaches the style`() {
        val style =
            Appearance(
                accent = Accent.Green,
                scanlines = true,
                sky = Sky.Night,
                dithering = false,
                textSize = SizeStep.Large,
            ).toStyle()

        assertEquals(Color(0xFF7CE38B), style.accent)
        assertEquals(true, style.scanlines)
        assertEquals(Color(0xFF0B0E24), style.sky.first())
        assertEquals(false, style.dithering)
        assertEquals(1.2f, style.textScale)
    }

    @Test
    fun `a sky that follows the sun takes the phase's colours`() {
        val style = Appearance(sky = Sky.Forest, skyFollowsSun = true).toStyle(SkyPhase.Day)

        assertEquals(SkyPhase.Day.stops.map { Color(it) }, style.sky)
    }

    @Test
    fun `dark nights darken the night sky that follows the sun`() {
        val dark = Appearance(skyFollowsSun = true, darkNights = true).toStyle(SkyPhase.Night)
        assertEquals(SkyPhase.Night.stops(darkNight = true).map { Color(it) }, dark.sky)
        val day = Appearance(skyFollowsSun = true, darkNights = true).toStyle(SkyPhase.Day)
        assertEquals(SkyPhase.Day.stops.map { Color(it) }, day.sky)
    }

    @Test
    fun `the sun follows the weather's city, or its last approximate location`() {
        val berlin = WeatherPlace("Berlin", latitude = 52.52, longitude = 13.41)
        val report = WeatherReport(10.0, 0, true, Coordinates(48.1, 11.6), Instant.EPOCH)

        assertEquals(GeoPoint(52.52, 13.41), weatherPlace(WeatherSettings(place = berlin), report))
        assertEquals(
            GeoPoint(48.1, 11.6),
            weatherPlace(WeatherSettings(place = berlin, approximateLocation = true), report),
        )
        assertNull(weatherPlace(WeatherSettings(approximateLocation = true), null))
        assertNull(weatherPlace(WeatherSettings(), report))
    }
}
