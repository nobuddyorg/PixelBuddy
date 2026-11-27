package io.github.nobuddyorg.pixelbuddy.feature.home

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarEvent
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarSource
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.nextEvent
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.lock.ScreenLock
import io.github.nobuddyorg.pixelbuddy.core.data.media.MediaPlayback
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.AppNotification
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.AppNotifications
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.CalendarSettings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.ClockFormat
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Gestures
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyles
import io.github.nobuddyorg.pixelbuddy.core.data.settings.NotificationSettings
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherRepository
import io.github.nobuddyorg.pixelbuddy.core.weather.isFahrenheit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
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
    /** Notifications for the dots (#24), only read while they are turned on. */
    appNotifications: AppNotifications? = null,
    /** The playing media (#25), only read while the controls are turned on. */
    mediaPlayback: MediaPlayback? = null,
    /** The phone's calendar for the next event (#27), only read while it is turned on. */
    calendarSource: CalendarSource? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val home by repository.home.collectAsStateWithLifecycle(initialValue = HomeApps())
    val iconStyles by settings.iconStyles.collectAsStateWithLifecycle(initialValue = IconStyles())
    val gestures by settings.gestures.collectAsStateWithLifecycle(initialValue = Gestures())
    val notificationSettings by settings.notifications.collectAsStateWithLifecycle(
        initialValue = NotificationSettings(),
    )
    val noNotifications = remember { MutableStateFlow(emptyList<AppNotification>()) }
    val notificationSource = appNotifications?.active?.takeIf { notificationSettings.dots } ?: noNotifications
    val notifications by notificationSource.collectAsStateWithLifecycle()
    val mediaSource =
        remember(mediaPlayback, notificationSettings.media) {
            mediaPlayback?.takeIf { notificationSettings.media }?.nowPlaying() ?: flowOf(null)
        }
    val nowPlaying by mediaSource.collectAsStateWithLifecycle(initialValue = null)
    val calendarSettings by settings.calendar.collectAsStateWithLifecycle(initialValue = CalendarSettings())
    val events by rememberCalendarEvents(calendarSource?.takeIf { calendarSettings.enabled })
    val artStyle = iconStyles.default
    val art by produceState<ImageBitmap?>(null, nowPlaying?.title, nowPlaying?.artist, artStyle) {
        val picture = nowPlaying?.art
        value = picture?.let { icons.pixelate(it, artStyle).asImageBitmap() }
    }

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

                is AppAction.OpenShortcut -> {
                    val shortcut = action.shortcut
                    if (repository.launch(shortcut) is LaunchResult.Failed) {
                        val message = resources.getString(R.string.home_launch_failed, shortcut.label)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
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
                below = { now, is24Hour ->
                    val instant = now.atZone(ZoneId.systemDefault()).toInstant()
                    if (calendarSource != null) {
                        nextEvent(events, instant, calendarSettings)?.let { event ->
                            EventLine(
                                event = event,
                                time = EventTime.of(event, instant, ZoneId.systemDefault(), is24Hour),
                                onClick = { context.startFirstAvailable(calendarSource.openIntent(event)) },
                            )
                        }
                    }
                    nowPlaying?.let { playing ->
                        MediaRow(playing, art, onAction = { action -> mediaPlayback?.control(action) })
                    }
                },
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
        notifications = notifications,
        onNotification = { action ->
            when (action) {
                is NotificationAction.Open -> {
                    if (appNotifications?.open(action.key) != true) {
                        Toast.makeText(context, R.string.home_notification_gone, Toast.LENGTH_SHORT).show()
                    }
                }

                is NotificationAction.Dismiss -> {
                    appNotifications?.dismiss(action.key)
                }
            }
        },
        shortcuts = repository::shortcuts,
        shortcutIcon = { shortcut -> ShortcutIcon(shortcut, icons, iconStyles.lookFor(shortcut.app.key).style) },
    )
}

/** Reads time and battery itself, so a new minute only recomposes the header, not the app list. */
@Composable
private fun LiveHomeHeader(
    appearance: Appearance,
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
    /** Under the info line: the next event (#27) and the media controls (#25). */
    below: @Composable (now: LocalDateTime, is24Hour: Boolean) -> Unit = { _, _ -> },
    weather: @Composable (now: Instant) -> Unit,
) {
    val now by rememberCurrentTime()
    val system24Hour by rememberIs24Hour()
    val battery by rememberBatteryState()
    val is24Hour =
        when (appearance.clockFormat) {
            ClockFormat.System -> system24Hour
            ClockFormat.TwelveHour -> false
            ClockFormat.TwentyFourHour -> true
        }
    Column {
        HomeHeader(
            now = now,
            is24Hour = is24Hour,
            battery = battery,
            onClockClick = onClockClick,
            onDateClick = onDateClick,
            clockHeight = appearance.clockSize.clockHeightDp.dp,
            clockShadow = appearance.clockShadow,
        ) {
            weather(now.atZone(ZoneId.systemDefault()).toInstant())
        }
        below(now, is24Hour)
    }
}

/**
 * The calendar's events from now on, while home is visible: read when it shows, when the calendar changes and every
 * half hour (so the 24 h window moves along). Empty without a [source].
 */
@Composable
private fun rememberCalendarEvents(source: CalendarSource?): State<List<CalendarEvent>> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(emptyList(), source, lifecycle) {
        if (source == null) {
            value = emptyList()
            return@produceState
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            merge(source.changes(), every(CALENDAR_REFRESH)).collect { value = source.events(Instant.now()) }
        }
    }
}

/** Emits now and then every [period]. */
private fun every(period: Duration) =
    flow {
        while (true) {
            emit(Unit)
            delay(period.toMillis())
        }
    }

private val CALENDAR_REFRESH = Duration.ofMinutes(30)

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
