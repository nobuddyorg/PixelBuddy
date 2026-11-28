package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.settings.TemperatureUnit
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import kotlinx.coroutines.delay
import java.io.IOException

/** The weather settings as shown: [tapAppLabel] names the app a tap opens; [systemFahrenheit] is the region's unit. */
data class WeatherOptions(
    val settings: WeatherSettings = WeatherSettings(),
    val tapAppLabel: String? = null,
    val systemFahrenheit: Boolean = false,
)

/** Something the user changed in the weather section. */
sealed interface WeatherAction {
    data object Toggle : WeatherAction

    data object ChoosePlace : WeatherAction

    data object CycleUnit : WeatherAction

    data object ChooseTapApp : WeatherAction

    /** Opens Open-Meteo's site (data attribution). */
    data object OpenAttribution : WeatherAction
}

/** The weather settings page. */
@Composable
internal fun WeatherSection(
    weather: WeatherOptions,
    onAction: (WeatherAction) -> Unit,
) {
    val settings = weather.settings
    ListRow(
        title = stringResource(R.string.weather_show),
        subtitle = stringResource(if (settings.enabled) R.string.weather_on else R.string.weather_off),
        onClick = { onAction(WeatherAction.Toggle) },
    )
    if (!settings.enabled) return
    ListRow(
        title = stringResource(R.string.weather_location),
        subtitle =
            when {
                settings.approximateLocation -> stringResource(R.string.weather_location_approximate)
                settings.place != null -> settings.place!!.display()
                else -> stringResource(R.string.weather_location_none)
            },
        onClick = { onAction(WeatherAction.ChoosePlace) },
    )
    val systemUnit = if (weather.systemFahrenheit) "°F" else "°C"
    ListRow(
        title = stringResource(R.string.weather_unit),
        subtitle =
            when (settings.unit) {
                TemperatureUnit.System -> stringResource(R.string.weather_unit_system, systemUnit)
                TemperatureUnit.Celsius -> "°C"
                TemperatureUnit.Fahrenheit -> "°F"
            },
        onClick = { onAction(WeatherAction.CycleUnit) },
    )
    ListRow(
        title = stringResource(R.string.weather_tap),
        subtitle = weather.tapAppLabel ?: stringResource(R.string.weather_tap_refresh),
        onClick = { onAction(WeatherAction.ChooseTapApp) },
    )
}

/** The data attribution Open-Meteo's licence (CC BY 4.0) asks for. */
@Composable
internal fun WeatherAttribution(onClick: () -> Unit) {
    ListRow(
        title = stringResource(R.string.weather_attribution),
        subtitle = stringResource(R.string.weather_attribution_licence),
        onClick = onClick,
    )
}

private fun WeatherPlace.display(): String = listOfNotNull(name, region, country).distinct().joinToString(", ")

/**
 * Choosing where the weather is for: a place by name (searched with [search] once the user stops typing) or the
 * phone's approximate location ([onApproximate] asks for the permission).
 */
@Composable
internal fun WeatherPlaceContent(
    search: suspend (String) -> List<WeatherPlace>,
    onPick: (WeatherPlace) -> Unit,
    onApproximate: () -> Unit,
    onBack: () -> Unit,
    message: String? = null,
) {
    BackHandler(onBack = onBack)
    val query = rememberTextFieldState()
    var results by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    val text = query.text.toString().trim()
    LaunchedEffect(text) {
        if (text.length < 2) {
            results = SearchState.Idle
            return@LaunchedEffect
        }
        // Search once typing pauses: one request per name, not per keystroke.
        delay(SEARCH_DELAY_MILLIS)
        results = SearchState.Searching
        results =
            try {
                SearchState.Found(search(text))
            } catch (_: IOException) {
                SearchState.Failed
            }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val hint = stringResource(R.string.weather_search_hint)

    Column {
        Title(stringResource(R.string.weather_location))
        TextButton(stringResource(R.string.action_back), onBack, color = TextColor, pointer = "◄")
        BasicTextField(
            state = query,
            textStyle = BodyStyle,
            cursorBrush = SolidColor(PixelBuddyColors.Orange),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .semantics { contentDescription = hint },
            decorator = { field ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier =
                        Modifier
                            .border(3.dp, TextColor)
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp),
                ) {
                    if (query.text.isEmpty()) BasicText(hint, style = MutedStyle)
                    field()
                }
            },
        )
        TextButton(stringResource(R.string.weather_location_use_approximate), onApproximate)
        message?.let { BasicText(it, style = MutedStyle) }
        when (val state = results) {
            SearchState.Idle -> {
                BasicText(stringResource(R.string.weather_search_privacy), style = MutedStyle)
            }

            SearchState.Searching -> {
                BasicText(stringResource(R.string.weather_searching), style = MutedStyle)
            }

            SearchState.Failed -> {
                BasicText(stringResource(R.string.weather_search_failed), style = MutedStyle)
            }

            is SearchState.Found -> {
                if (state.places.isEmpty()) BasicText(stringResource(R.string.weather_search_empty), style = MutedStyle)
                LazyColumn {
                    items(state.places) { place ->
                        ListRow(
                            title = place.name,
                            subtitle = listOfNotNull(place.region, place.country).joinToString(", "),
                            onClick = { onPick(place) },
                        )
                    }
                }
            }
        }
    }
}

private sealed interface SearchState {
    data object Idle : SearchState

    data object Searching : SearchState

    data object Failed : SearchState

    data class Found(
        val places: List<WeatherPlace>,
    ) : SearchState
}

private const val SEARCH_DELAY_MILLIS = 400L

/** Choosing what a tap on the weather does: refresh it, or open one of [apps]. */
@Composable
internal fun WeatherTapAppContent(
    apps: List<LauncherApp>,
    onPick: (LauncherApp?) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column {
        Title(stringResource(R.string.weather_tap))
        TextButton(stringResource(R.string.action_back), onBack, color = TextColor, pointer = "◄")
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                ListRow(
                    title = stringResource(R.string.weather_tap_refresh),
                    subtitle = stringResource(R.string.weather_tap_default),
                    onClick = { onPick(null) },
                )
            }
            items(apps, key = { it.key.id }) { app ->
                ListRow(
                    title = if (app.isMainProfile) app.label else stringResource(R.string.settings_work_app, app.label),
                    subtitle = "",
                    onClick = { onPick(app) },
                )
            }
        }
    }
}
