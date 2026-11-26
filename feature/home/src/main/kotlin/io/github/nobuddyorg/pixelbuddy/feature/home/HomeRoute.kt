package io.github.nobuddyorg.pixelbuddy.feature.home

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.HomeApps
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LaunchResult
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.lock.ScreenLock
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.ClockFormat
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Gestures
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyles
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherRepository
import io.github.nobuddyorg.pixelbuddy.core.weather.isFahrenheit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Home with state. Apps, settings, time and battery are only observed while home is visible (no background
 * work). Menu actions change the [settings] or open system screens.
 */
@Composable
fun HomeRoute(
    repository: AppRepository,
    settings: SettingsRepository,
    icons: IconRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Changes whenever Home is pressed on home: back to the favorites. */
    resetKey: Int = 0,
    /** Weather for the info line; null leaves it out. */
    weather: WeatherRepository? = null,
    /** Clock settings; colours and text size come from the theme. */
    appearance: Appearance = Appearance(),
    /** Opens the editor for an app's own icon (#14). */
    onEditIcon: (LauncherApp) -> Unit = {},
    /** Locks the screen for the lock gesture (#16); without it, the gesture only explains. */
    screenLock: ScreenLock? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val home by repository.home.collectAsStateWithLifecycle(initialValue = HomeApps())
    val iconStyles by settings.iconStyles.collectAsStateWithLifecycle(initialValue = IconStyles())
    val gestures by settings.gestures.collectAsStateWithLifecycle(initialValue = Gestures())

    // Null until the stored settings are read, so no request goes out for the defaults first.
    val storedWeather by settings.weather.collectAsStateWithLifecycle(initialValue = null)
    val weatherSettings = storedWeather ?: WeatherSettings()
    val reports = remember(weather) { weather?.report ?: MutableStateFlow(null) }
    val report by reports.collectAsStateWithLifecycle()
    if (weather != null && storedWeather != null) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        // Whenever home becomes visible, or weather is switched on or moved; the repository decides if it's due.
        LaunchedEffect(
            weather,
            lifecycle,
            weatherSettings.enabled,
            weatherSettings.place,
            weatherSettings.approximateLocation,
        ) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { weather.refreshIfStale() }
        }
    }
    val tapApp = weatherSettings.tapApp?.let { key -> home.apps.firstOrNull { it.app.key == key }?.app }

    fun toast(
        message: Int,
        app: LauncherApp,
    ) = Toast.makeText(context, resources.getString(message, app.label), Toast.LENGTH_SHORT).show()

    HomeScreen(
        home = home,
        onAppClick = { app, from ->
            // The app opens out of the tapped icon.
            val animation = from?.let { launchAnimation(view, it) }
            if (repository.launch(app, animation?.sourceBounds, animation?.options) is LaunchResult.Failed) {
                toast(R.string.home_launch_failed, app)
            }
        },
        onAppAction = { action ->
            val app = action.app
            when (action) {
                is AppAction.AddFavorite -> {
                    scope.launch { settings.addFavorite(app.key) }
                }

                is AppAction.RemoveFavorite -> {
                    scope.launch { settings.removeFavorite(app.key) }
                }

                is AppAction.MoveFavorite -> {
                    scope.launch { settings.moveFavorite(app.key, action.offset) }
                }

                is AppAction.EditIcon -> {
                    onEditIcon(app)
                }

                is AppAction.Hide -> {
                    scope.launch { settings.hide(app.key) }
                    toast(R.string.home_app_hidden, app)
                }

                is AppAction.AppInfo -> {
                    if (repository.openDetails(app) is LaunchResult.Failed) toast(R.string.home_action_failed, app)
                }

                is AppAction.Uninstall -> {
                    if (repository.uninstall(app) is LaunchResult.Failed) toast(R.string.home_action_failed, app)
                }
            }
        },
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        resetKey = resetKey,
        gestures = gestures,
        onOpenNotifications = context::expandNotificationShade,
        onLock = {
            if (screenLock?.lock() != true) {
                Toast.makeText(context, R.string.home_lock_needs_service, Toast.LENGTH_LONG).show()
            }
        },
        header = {
            LiveHomeHeader(
                appearance = appearance,
                onClockClick = context::openAlarms,
                onDateClick = context::openCalendar,
            ) { now ->
                val current = report
                if (weather != null && weatherSettings.enabled && current != null) {
                    WeatherInfo(
                        report = current,
                        fahrenheit = weatherSettings.unit.isFahrenheit(LocalConfiguration.current.locales[0]),
                        now = now,
                        onClickLabel =
                            if (tapApp == null) {
                                stringResource(R.string.home_weather_refresh)
                            } else {
                                stringResource(R.string.home_weather_open_app, tapApp.label)
                            },
                        onClick = {
                            if (tapApp == null) {
                                scope.launch { weather.refreshIfStale(force = true) }
                            } else if (repository.launch(tapApp) is LaunchResult.Failed) {
                                toast(R.string.home_launch_failed, tapApp)
                            }
                        },
                    )
                }
            }
        },
        icon = { app -> AppIcon(app, icons, iconStyles.lookFor(app.key)) },
    )
}

/** Reads time and battery itself, so a new minute only recomposes the header, not the app list. */
@Composable
private fun LiveHomeHeader(
    appearance: Appearance,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    weather: @Composable (now: Instant) -> Unit,
) {
    val now by rememberCurrentTime()
    val system24Hour by rememberIs24Hour()
    val battery by rememberBatteryState()
    HomeHeader(
        now = now,
        is24Hour =
            when (appearance.clockFormat) {
                ClockFormat.System -> system24Hour
                ClockFormat.TwelveHour -> false
                ClockFormat.TwentyFourHour -> true
            },
        battery = battery,
        onClockClick = onClockClick,
        onDateClick = onDateClick,
        clockHeight = appearance.clockSize.clockHeightDp.dp,
        clockShadow = appearance.clockShadow,
    ) {
        weather(now.atZone(ZoneId.systemDefault()).toInstant())
    }
}

internal fun Context.openAlarms() {
    startFirstAvailable(Intent(AlarmClock.ACTION_SHOW_ALARMS))
}

internal fun Context.openCalendar() {
    val today = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
    ContentUris.appendId(today, System.currentTimeMillis())
    startFirstAvailable(
        Intent(Intent.ACTION_VIEW, today.build()),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR),
    )
}

private fun Context.startFirstAvailable(vararg intents: Intent) {
    for (intent in intents) {
        try {
            // Other apps open in their own task, like any launcher does.
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one; with no clock or calendar app, nothing happens.
        }
    }
}
