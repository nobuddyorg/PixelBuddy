package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Settings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SetupStep
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Whether PixelBuddy is the home app, and the screens to make it one. Implemented by the app. */
interface HomeAppAccess {
    fun isDefault(): Boolean

    /** Screens that let the user pick the home app, best first: the system's role dialog, then settings. */
    fun requestIntents(): List<Intent>
}

/**
 * The first-start setup (#20): home app, favorites, weather. Every choice is stored at once, and so is the step,
 * so the setup goes on where it was after the process was killed. "Skip setup" or the last step set
 * [SetupStep.Done]; the setup can be run again from the settings. A step for the home app shows only if
 * PixelBuddy isn't the home app yet.
 */
@Composable
fun SetupRoute(
    settings: Flow<Settings>,
    onUpdate: ((Settings) -> Settings) -> Unit,
    homeApp: HomeAppAccess,
    modifier: Modifier = Modifier,
    apps: Flow<List<LauncherApp>> = flowOf(emptyList()),
    searchPlaces: suspend (String) -> List<WeatherPlace> = { emptyList() },
) {
    val stored by settings.collectAsStateWithLifecycle(initialValue = null)
    val allApps by apps.collectAsStateWithLifecycle(initialValue = emptyList())
    var isDefault by remember { mutableStateOf(homeApp.isDefault()) }
    // Decided once: whoever starts as the home app never sees that step.
    val withHomeStep = remember { !isDefault }
    var homeNotYet by rememberSaveable { mutableStateOf(false) }
    var choosingPlace by rememberSaveable { mutableStateOf(false) }
    var locationDenied by rememberSaveable { mutableStateOf(false) }

    val goTo: (SetupStep) -> Unit = { step -> onUpdate { it.copy(setup = step) } }
    LifecycleResumeEffect(homeApp) {
        isDefault = homeApp.isDefault()
        onPauseOrDispose {}
    }
    val homeRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isDefault = homeApp.isDefault()
            if (isDefault) goTo(SetupStep.Favorites) else homeNotYet = true
        }
    val useApproximate =
        rememberApproximateLocation(
            onGranted = {
                onUpdate {
                    it.copy(
                        weather = it.weather.copy(enabled = true, approximateLocation = true),
                        setup = SetupStep.Done,
                    )
                }
            },
            onDenied = { locationDenied = true },
        )

    val current = stored
    if (current == null || (current.setup == SetupStep.HomeApp && !withHomeStep)) {
        // Still reading, or about to skip the home-app step.
        if (current != null) LaunchedEffect(Unit) { goTo(SetupStep.Favorites) }
        SettingsFrame(modifier) {}
        return
    }
    val step = current.setup

    if (step == SetupStep.Weather && choosingPlace) {
        SettingsFrame(modifier) {
            WeatherPlaceContent(
                search = searchPlaces,
                onPick = { place ->
                    onUpdate {
                        it.copy(
                            weather = it.weather.copy(enabled = true, place = place, approximateLocation = false),
                            setup = SetupStep.Done,
                        )
                    }
                },
                onApproximate = useApproximate,
                onBack = { choosingPlace = false },
                message = if (locationDenied) stringResource(R.string.weather_location_denied) else null,
            )
        }
        return
    }

    val first = if (withHomeStep) SetupStep.HomeApp else SetupStep.Favorites
    SetupScreen(
        step = step,
        steps = SetupStep.entries.filter { it != SetupStep.Done && (withHomeStep || it != SetupStep.HomeApp) },
        isDefaultHome = isDefault,
        homeNotYet = homeNotYet,
        apps = allApps,
        favorites = current.favorites,
        locationDenied = locationDenied,
        onAction = { action ->
            when (action) {
                SetupAction.Skip -> {
                    goTo(SetupStep.Done)
                }

                SetupAction.Next -> {
                    goTo(step.next)
                }

                SetupAction.Back -> {
                    if (step != first) goTo(step.previous)
                }

                SetupAction.MakeHomeApp -> {
                    val opened =
                        homeApp.requestIntents().any { intent ->
                            try {
                                homeRequest.launch(intent)
                                true
                            } catch (_: ActivityNotFoundException) {
                                false
                            }
                        }
                    if (!opened) homeNotYet = true
                }

                is SetupAction.ToggleFavorite -> {
                    onUpdate {
                        if (action.app in it.favorites) it.withoutFavorite(action.app) else it.withFavorite(action.app)
                    }
                }

                SetupAction.NoWeather -> {
                    onUpdate { it.copy(weather = it.weather.copy(enabled = false), setup = SetupStep.Done) }
                }

                SetupAction.ChoosePlace -> {
                    choosingPlace = true
                }

                SetupAction.ApproximateLocation -> {
                    useApproximate()
                }
            }
        },
        modifier = modifier,
    )
}

/** Something the user did in the setup. */
sealed interface SetupAction {
    data object Skip : SetupAction

    data object Next : SetupAction

    data object Back : SetupAction

    data object MakeHomeApp : SetupAction

    data class ToggleFavorite(
        val app: AppKey,
    ) : SetupAction

    data object NoWeather : SetupAction

    data object ChoosePlace : SetupAction

    data object ApproximateLocation : SetupAction
}

/** One step of the setup ([step] of [steps]); stateless. */
@Composable
fun SetupScreen(
    step: SetupStep,
    steps: List<SetupStep>,
    isDefaultHome: Boolean,
    homeNotYet: Boolean,
    apps: List<LauncherApp>,
    favorites: List<AppKey>,
    locationDenied: Boolean,
    onAction: (SetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = step != steps.first()) { onAction(SetupAction.Back) }
    SettingsFrame(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionHeader(
                stringResource(R.string.setup_progress, steps.indexOf(step) + 1, steps.size),
                modifier = Modifier.weight(1f),
            )
            TextButton(stringResource(R.string.setup_skip), { onAction(SetupAction.Skip) }, color = TextColor)
        }
        when (step) {
            SetupStep.HomeApp -> HomeAppStep(isDefaultHome, homeNotYet, onAction)
            SetupStep.Favorites -> FavoritesStep(apps, favorites, onAction, Modifier.weight(1f))
            SetupStep.Weather -> WeatherStep(locationDenied, onAction)
            SetupStep.Done -> Unit
        }
    }
}

@Composable
private fun HomeAppStep(
    isDefaultHome: Boolean,
    notYet: Boolean,
    onAction: (SetupAction) -> Unit,
) {
    Column {
        Title(stringResource(R.string.setup_home_title))
        Spacer(Modifier.height(16.dp))
        BasicText(stringResource(R.string.setup_home_text), style = BodyStyle)
        Spacer(Modifier.height(16.dp))
        if (isDefaultHome) {
            BasicText(stringResource(R.string.setup_home_done), style = BodyStyle.copy(color = accent()))
            TextButton(stringResource(R.string.setup_next), { onAction(SetupAction.Next) })
        } else {
            TextButton(stringResource(R.string.setup_home_button), { onAction(SetupAction.MakeHomeApp) })
            if (notYet) BasicText(stringResource(R.string.setup_home_not_yet), style = MutedStyle)
            TextButton(stringResource(R.string.setup_home_later), { onAction(SetupAction.Next) }, color = TextColor)
        }
    }
}

@Composable
private fun FavoritesStep(
    apps: List<LauncherApp>,
    favorites: List<AppKey>,
    onAction: (SetupAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Title(stringResource(R.string.setup_favorites_title))
        Spacer(Modifier.height(8.dp))
        BasicText(stringResource(R.string.setup_favorites_text), style = MutedStyle)
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(apps, key = { it.key.id }) { app ->
                val position = favorites.indexOf(app.key)
                FavoriteChoice(app, position, onToggle = { onAction(SetupAction.ToggleFavorite(app.key)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                stringResource(R.string.setup_favorites_count, favorites.size),
                style = MutedStyle,
                modifier = Modifier.weight(1f),
            )
            TextButton(stringResource(R.string.setup_next), { onAction(SetupAction.Next) })
        }
    }
}

/** An app to pick; [position] is its place among the favorites, -1 if it isn't one. */
@Composable
private fun FavoriteChoice(
    app: LauncherApp,
    position: Int,
    onToggle: () -> Unit,
) {
    val picked = position >= 0
    val state = if (picked) stringResource(R.string.setup_favorite_position, position + 1) else ""
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = picked, role = Role.Checkbox, onValueChange = { onToggle() })
                .semantics { if (picked) stateDescription = state },
    ) {
        BasicText(
            if (picked) "[${position + 1}]" else "[ ]",
            style = BodyStyle.copy(color = if (picked) accent() else MutedTextColor),
        )
        Spacer(Modifier.width(12.dp))
        BasicText(
            if (app.isMainProfile) app.label else stringResource(R.string.settings_work_app, app.label),
            style = BodyStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeatherStep(
    locationDenied: Boolean,
    onAction: (SetupAction) -> Unit,
) {
    Column {
        Title(stringResource(R.string.weather_title))
        Spacer(Modifier.height(16.dp))
        BasicText(stringResource(R.string.setup_weather_text), style = BodyStyle)
        Spacer(Modifier.height(8.dp))
        ListRow(
            title = stringResource(R.string.setup_weather_off),
            subtitle = stringResource(R.string.weather_off),
            onClick = { onAction(SetupAction.NoWeather) },
        )
        ListRow(
            title = stringResource(R.string.setup_weather_place),
            subtitle = stringResource(R.string.weather_search_hint),
            onClick = { onAction(SetupAction.ChoosePlace) },
        )
        ListRow(
            title = stringResource(R.string.weather_location_use_approximate),
            subtitle = stringResource(R.string.weather_location_approximate),
            onClick = { onAction(SetupAction.ApproximateLocation) },
        )
        if (locationDenied) BasicText(stringResource(R.string.weather_location_denied), style = MutedStyle)
    }
}

@Composable
private fun accent() = PixelBuddyTheme.style.accent
