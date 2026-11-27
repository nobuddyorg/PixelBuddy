package io.github.nobuddyorg.pixelbuddy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.data.sky.GeoPoint
import io.github.nobuddyorg.pixelbuddy.core.data.sky.SkyLocation
import io.github.nobuddyorg.pixelbuddy.core.data.sky.SkyPhase
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyStyle
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** The design-system style for the user's [Appearance] settings; [phase] replaces the sky while it follows the sun. */
internal fun Appearance.toStyle(phase: SkyPhase? = null): PixelBuddyStyle =
    PixelBuddyStyle(
        accent = Color(accent.argb),
        scanlines = scanlines,
        sky = (phase?.stops ?: sky.stops.toList()).map { Color(it) },
        dithering = dithering,
        textScale = textSize.scale,
    )

/**
 * [PixelBuddyTheme] with the stored appearance, so every change applies at once. Until the settings are read only
 * the plain background shows, so the default look never flashes up. A sky that follows the sun (#28) takes its
 * place from the weather (its [weatherReport] for an approximate location).
 */
@Composable
internal fun StoredAppearanceTheme(
    settings: SettingsRepository,
    weatherReport: StateFlow<WeatherReport?> = MutableStateFlow(null),
    content: @Composable (Appearance) -> Unit,
) {
    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = null)
    val current = appearance
    if (current == null) {
        Box(Modifier.fillMaxSize().background(PixelBuddyColors.Navy))
        return
    }
    val phase =
        if (current.skyFollowsSun) {
            val weather by settings.weather.collectAsStateWithLifecycle(initialValue = WeatherSettings())
            val report by weatherReport.collectAsStateWithLifecycle()
            rememberSkyPhase(weatherPlace(weather, report))
        } else {
            null
        }
    PixelBuddyTheme(current.toStyle(phase)) { content(current) }
}

/** The weather's place for the sun: the chosen city, or where the last approximate-location weather was for. */
internal fun weatherPlace(
    weather: WeatherSettings,
    report: WeatherReport?,
): GeoPoint? =
    when {
        weather.approximateLocation -> report?.coordinates?.let { GeoPoint(it.latitude, it.longitude) }
        else -> weather.place?.let { GeoPoint(it.latitude, it.longitude) }
    }

/**
 * The sky's phase now, updated at each phase change (a few times a day) while the screen is visible: no
 * background work, no animation.
 */
@Composable
private fun rememberSkyPhase(weatherPlace: GeoPoint?): SkyPhase {
    val zone = ZoneId.systemDefault()
    val place = remember(weatherPlace, zone) { SkyLocation.resolve(weatherPlace, zone, Instant.now()) }
    var phase by remember(place) { mutableStateOf(SkyPhase.at(Instant.now(), place)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(place, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = Instant.now()
                phase = SkyPhase.at(now, place)
                // At most an hour, so a changed clock or time zone is caught too.
                val next = SkyPhase.nextChange(now, place) ?: now.plus(MAX_WAIT)
                delay(Duration.between(now, next).coerceIn(MIN_WAIT, MAX_WAIT).toMillis())
            }
        }
    }
    return phase
}

private val MIN_WAIT = Duration.ofSeconds(1)
private val MAX_WAIT = Duration.ofHours(1)
