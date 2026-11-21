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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LaunchResult
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconStyle

/** Home with state. Apps, time and battery are only observed while home is visible (no background work). */
@Composable
fun HomeRoute(
    repository: AppRepository,
    icons: IconRepository,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    iconStyle: IconStyle = IconPreset.Default.style,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val apps by repository.apps.collectAsStateWithLifecycle(initialValue = emptyList())
    HomeScreen(
        apps = apps,
        onAppClick = { app ->
            if (repository.launch(app) is LaunchResult.Failed) {
                Toast
                    .makeText(
                        context,
                        resources.getString(R.string.home_launch_failed, app.label),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        },
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        header = { LiveHomeHeader(onClockClick = context::openAlarms, onDateClick = context::openCalendar) },
        icon = { app -> AppIcon(app, icons, iconStyle) },
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
