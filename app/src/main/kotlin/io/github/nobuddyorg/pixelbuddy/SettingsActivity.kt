package io.github.nobuddyorg.pixelbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.feature.settings.SettingsRoute
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = appContainer
        val home = container.appRepository.home
        val launchableApps = home.map { apps -> apps.apps.map { it.app } }
        val favoriteApps = home.map { it.favorites }
        setContent {
            // The accent and sky apply here too, as soon as they change.
            StoredAppearanceTheme(container.settings) {
                SettingsRoute(
                    store = container.crashReports,
                    appVersion = container.deviceInfo.appVersionName,
                    settings = container.settings.settings,
                    onUpdate = { change -> lifecycleScope.launch { container.settings.update(change) } },
                    favoriteApps = favoriteApps,
                    hiddenApps = container.appRepository.hiddenApps,
                    apps = launchableApps,
                    searchPlaces = { name ->
                        container.weatherRepository.searchPlaces(name, Locale.getDefault().language).map {
                            WeatherPlace(
                                it.name,
                                it.region,
                                it.country,
                                it.coordinates.latitude,
                                it.coordinates.longitude,
                            )
                        }
                    },
                )
            }
        }
    }
}
