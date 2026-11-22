package io.github.nobuddyorg.pixelbuddy.feature.home

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.HomeApps
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LaunchResult
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyles
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsRepository
import kotlinx.coroutines.launch

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
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val home by repository.home.collectAsStateWithLifecycle(initialValue = HomeApps())
    val iconStyles by settings.iconStyles.collectAsStateWithLifecycle(initialValue = IconStyles())

    fun toast(
        message: Int,
        app: LauncherApp,
    ) = Toast.makeText(context, resources.getString(message, app.label), Toast.LENGTH_SHORT).show()

    HomeScreen(
        home = home,
        onAppClick = { app ->
            if (repository.launch(app) is LaunchResult.Failed) toast(R.string.home_launch_failed, app)
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
        header = { LiveHomeHeader(onClockClick = context::openAlarms, onDateClick = context::openCalendar) },
        icon = { app -> AppIcon(app, icons, iconStyles.styleFor(app.key)) },
    )
}

/** Reads time and battery itself, so a new minute only recomposes the header, not the app list. */
@Composable
private fun LiveHomeHeader(
    onClockClick: () -> Unit,
    onDateClick: () -> Unit,
) {
    val now by rememberCurrentTime()
    val is24Hour by rememberIs24Hour()
    val battery by rememberBatteryState()
    HomeHeader(now, is24Hour, battery, onClockClick, onDateClick)
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
