package io.github.nobuddyorg.pixelbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SetupStep
import io.github.nobuddyorg.pixelbuddy.feature.settings.SettingsRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    /** The last settings change, so work that reads the settings can wait for it. */
    private var lastUpdate: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        val home = container.appRepository.home
        val launchableApps = home.map { apps -> apps.apps.map { it.app } }
        val favoriteApps = home.map { it.favorites }
        setContent {
            // The accent and sky apply here too, as soon as they change.
            StoredAppearanceTheme(container.settings, container.weatherRepository.report) {
                SettingsRoute(
                    store = container.crashReports,
                    appVersion = container.deviceInfo.appVersionName,
                    settings = container.settings.settings,
                    onUpdate = { change ->
                        val previous = lastUpdate
                        lastUpdate =
                            lifecycleScope.launch {
                                previous?.join()
                                container.settings.update(change)
                            }
                    },
                    favoriteApps = favoriteApps,
                    hiddenApps = container.appRepository.hiddenApps,
                    apps = launchableApps,
                    searchPlaces = container::searchPlaces,
                    icons = container.iconRepository,
                    customIcons = container.customIcons,
                    onIconsChanged = {
                        val stored = lastUpdate
                        lifecycleScope.launch {
                            // Once stored, so the background renders use the new icons.
                            stored?.join()
                            container.refreshIcons(resources.configuration.densityDpi)
                        }
                    },
                    editIconOf = intent.getStringExtra(EXTRA_EDIT_ICON),
                    onDone = ::finish,
                    screenLock = LockService,
                    appNotifications = NotificationService,
                    calendarSource = container.calendar,
                    onRunSetup = {
                        lifecycleScope.launch {
                            // Stored first, then home shows the setup.
                            container.settings.update { it.copy(setup = SetupStep.HomeApp) }
                            finish()
                        }
                    },
                )
            }
        }
    }

    companion object {
        /** Key id of an app whose icon editor opens right away (from home's long-press menu). */
        const val EXTRA_EDIT_ICON = "io.github.nobuddyorg.pixelbuddy.EDIT_ICON"
    }
}
