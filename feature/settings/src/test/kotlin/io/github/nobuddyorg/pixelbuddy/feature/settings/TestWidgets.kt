package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.os.Process
import org.robolectric.Shadows.shadowOf

/** Installs [info] as a widget provider of this profile; binding it then reports [info] for its id, as on a phone. */
internal fun AppWidgetManager.install(info: AppWidgetProviderInfo) {
    shadowOf(this).addInstalledProvider(info)
    shadowOf(this).addInstalledProvidersForProfile(Process.myUserHandle(), info)
}
