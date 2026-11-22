package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.nobuddyorg.pixelbuddy.core.designsystem.BatteryState
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.weather.Coordinates
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherCondition
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-420dpi")
class WeatherInfoTest {
    @get:Rule
    val compose = createComposeRule()

    private val fetched = Instant.parse("2026-11-06T20:30:00Z")
    private val rainyNight = WeatherReport(4.4, 61, isDay = false, Coordinates(52.5, 13.4), fetched)

    private fun show(
        report: WeatherReport = rainyNight,
        fahrenheit: Boolean = false,
        age: Duration = Duration.ofMinutes(10),
        onClick: () -> Unit = {},
    ) = compose.setContent {
        Box(Modifier.background(PixelBuddyColors.Navy).padding(24.dp)) {
            HomeHeader(LocalDateTime.of(2026, 11, 6, 21, 45), true, BatteryState(58, charging = false), {}, {}) {
                WeatherInfo(report, fahrenheit, report.fetchedAt + age, "Refresh weather", onClick)
            }
        }
    }

    @Test
    fun `shows the glyph and temperature with a spoken description`() {
        show()

        compose.onNodeWithContentDescription("Rain, 4 °C").assertIsDisplayed()
    }

    @Test
    fun `Fahrenheit is converted`() {
        show(fahrenheit = true)

        compose.onNodeWithContentDescription("Rain, 40 °F").assertIsDisplayed()
    }

    @Test
    fun `old reports are hidden`() {
        show(age = Duration.ofHours(12))

        compose.onNodeWithContentDescription("Rain, 4 °C").assertDoesNotExist()
    }

    @Test
    fun `tapping runs the tap action`() {
        var taps = 0
        show(onClick = { taps++ })

        compose.onNodeWithContentDescription("Rain, 4 °C").performClick()

        assertEquals(1, taps)
        val label =
            compose
                .onNodeWithContentDescription("Rain, 4 °C")
                .fetchSemanticsNode()
                .config[SemanticsActions.OnClick]
                .label
        assertEquals("Refresh weather", label)
    }

    @Test
    fun `every glyph is 7 by 7`() {
        WeatherCondition.entries.forEach { condition ->
            val rows = WeatherGlyphs.of(condition)
            assertEquals(7, rows.size, condition.name)
            assertTrue(rows.all { it.length == 7 && it.all { c -> c == '#' || c == '.' } }, condition.name)
        }
        assertEquals(
            WeatherCondition.entries.size,
            WeatherCondition.entries
                .map { WeatherGlyphs.of(it) }
                .distinct()
                .size,
        )
    }

    @Test
    fun `screenshot header with weather`() {
        show()

        compose.onRoot().captureRoboImage("HomeHeader_weather.png")
    }

    @Test
    fun `screenshot dimmed weather`() {
        show(report = rainyNight.copy(weatherCode = 0, isDay = false), age = Duration.ofHours(4))

        compose.onNodeWithContentDescription("Clear night, 4 °C").assertIsDisplayed()
        compose.onRoot().captureRoboImage("HomeHeader_weather_dimmed.png")
    }

    @Test
    fun `screenshot all glyphs`() {
        compose.setContent {
            Row(
                Modifier.background(PixelBuddyColors.Navy).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WeatherCondition.entries.forEach { WeatherGlyph(it) }
            }
        }

        compose.onRoot().captureRoboImage("WeatherGlyphs.png")
    }
}
