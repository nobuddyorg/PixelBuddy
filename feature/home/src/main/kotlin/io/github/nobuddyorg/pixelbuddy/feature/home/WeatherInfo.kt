package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelText
import io.github.nobuddyorg.pixelbuddy.core.weather.Freshness
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherCondition
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherReport
import io.github.nobuddyorg.pixelbuddy.core.weather.displayDegrees
import java.time.Instant
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Weather in the info line: a pixel glyph and the temperature. Dimmed once the report is 3 hours old and gone
 * after 12 ([Freshness]). [onClickLabel] says what a tap does (refresh or open an app).
 */
@Composable
internal fun WeatherInfo(
    report: WeatherReport,
    fahrenheit: Boolean,
    now: Instant,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val freshness = Freshness.of(report, now)
    if (freshness == Freshness.Hidden) return
    val degrees = displayDegrees(report.temperatureCelsius, fahrenheit)
    val description =
        stringResource(
            if (fahrenheit) R.string.home_weather_fahrenheit else R.string.home_weather_celsius,
            stringResource(report.condition.label),
            degrees,
        )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                // A 48 dp touch target, though the text is small.
                .heightIn(min = 48.dp)
                .alpha(if (freshness == Freshness.Dimmed) 0.5f else 1f)
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                    onClick(label = onClickLabel) {
                        onClick()
                        true
                    }
                },
    ) {
        WeatherGlyph(report.condition)
        BasicText("$degrees°", style = PixelText.small.copy(color = PixelBuddyColors.Text))
    }
}

/** A 7 × 7 pixel glyph, every pixel the same whole number of screen pixels. */
@Composable
internal fun WeatherGlyph(
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
    color: Color = PixelBuddyColors.Text,
) {
    val rows = WeatherGlyphs.of(condition)
    val cell = with(LocalDensity.current) { max(1, 2.dp.toPx().roundToInt()) }
    val side = with(LocalDensity.current) { (cell * GLYPH_SIZE).toDp() }
    Canvas(modifier.size(side)) {
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == '#') {
                    drawRect(
                        color,
                        Offset((x * cell).toFloat(), (y * cell).toFloat()),
                        Size(cell.toFloat(), cell.toFloat()),
                    )
                }
            }
        }
    }
}

private const val GLYPH_SIZE = 7

internal val WeatherCondition.label: Int
    get() =
        when (this) {
            WeatherCondition.ClearDay -> R.string.weather_clear
            WeatherCondition.ClearNight -> R.string.weather_clear_night
            WeatherCondition.PartlyCloudy -> R.string.weather_partly_cloudy
            WeatherCondition.Cloudy -> R.string.weather_cloudy
            WeatherCondition.Fog -> R.string.weather_fog
            WeatherCondition.Rain -> R.string.weather_rain
            WeatherCondition.Snow -> R.string.weather_snow
            WeatherCondition.Thunder -> R.string.weather_thunder
        }

/** Pixel art for each condition, 7 × 7. */
internal object WeatherGlyphs {
    fun of(condition: WeatherCondition): List<String> =
        when (condition) {
            WeatherCondition.ClearDay -> SUN
            WeatherCondition.ClearNight -> MOON
            WeatherCondition.PartlyCloudy -> PARTLY_CLOUDY
            WeatherCondition.Cloudy -> CLOUD
            WeatherCondition.Fog -> FOG
            WeatherCondition.Rain -> RAIN
            WeatherCondition.Snow -> SNOW
            WeatherCondition.Thunder -> THUNDER
        }

    private val SUN = listOf("#..#..#", ".#...#.", "..###..", "#.###.#", "..###..", ".#...#.", "#..#..#")
    private val MOON = listOf("..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###..")
    private val PARTLY_CLOUDY = listOf("#.#....", ".#.##..", "#.#####", ".######", "#######", ".......", ".......")
    private val CLOUD = listOf(".......", ".##.##.", "#######", "#######", ".#####.", ".......", ".......")
    private val FOG = listOf(".......", "#######", ".......", ".######", ".......", "######.", ".......")
    private val RAIN = listOf(".##.##.", "#######", ".#####.", ".......", ".#.#.#.", "#.#.#..", ".......")
    private val SNOW = listOf("#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#")
    private val THUNDER = listOf(".##.##.", "#######", ".#####.", "...#...", "..##...", ".##....", ".#.....")
}
