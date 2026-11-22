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
        val launchableApps = container.appRepository.home.map { home -> home.apps.map { it.app } }
        setContent {
            SettingsRoute(
                store = container.crashReports,
                appVersion = container.deviceInfo.appVersionName,
                hiddenApps = container.appRepository.hiddenApps,
                onShowApp = { app -> lifecycleScope.launch { container.settings.unhide(app.key) } },
                weatherSettings = container.settings.weather,
                onUpdateWeather = { change -> lifecycleScope.launch { container.settings.updateWeather(change) } },
                searchPlaces = { name ->
                    container.weatherRepository.searchPlaces(name, Locale.getDefault().language).map {
                        WeatherPlace(it.name, it.region, it.country, it.coordinates.latitude, it.coordinates.longitude)
                    }
                },
                apps = launchableApps,
            )
        }
    }
}
