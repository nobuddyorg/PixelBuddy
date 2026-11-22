package io.github.nobuddyorg.pixelbuddy

import android.content.Context
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherAppsSource
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.crash.DeviceInfo
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconCache
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.icons.LauncherIconLoader
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.weather.OpenMeteoClient
import io.github.nobuddyorg.pixelbuddy.core.weather.PlatformLocationSource
import io.github.nobuddyorg.pixelbuddy.core.weather.UrlConnectionHttpGet
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherCache
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** Manual dependency wiring (no DI framework, see docs/PLAN.md). One instance per process. */
class AppContainer(
    context: Context,
) {
    val deviceInfo: DeviceInfo = DeviceInfo.from(context)

    /** Excluded from backups, see res/xml/backup_rules.xml and data_extraction_rules.xml. */
    val crashReports: CrashReportStore = CrashReportStore(File(context.filesDir, CRASH_REPORTS_DIR))

    /** Favorites, hidden apps and all other settings in one file; included in backups (#8). */
    val settings: SettingsRepository =
        SettingsRepository.create(
            File(context.filesDir, SettingsRepository.FILE_PATH),
            Dispatchers.IO + SupervisorJob(),
        )

    /** Pixelated icons, cached in memory and in the cache directory (#7). */
    val iconRepository: IconRepository =
        IconRepository(LauncherIconLoader(context), IconCache(File(context.cacheDir, "icons")))

    /** Every app list reload also re-checks icons, so updates and icon themes show up. */
    val appRepository: AppRepository =
        AppRepository(LauncherAppsSource(context), settings, onReload = iconRepository::recheck)

    /** Weather (#11): the only network access, and only while weather is on. Cached outside backups. */
    val weatherRepository: WeatherRepository =
        WeatherRepository(
            settings = settings.weather,
            client = OpenMeteoClient(UrlConnectionHttpGet()),
            cache = WeatherCache(File(context.noBackupFilesDir, "weather.json")),
            location = PlatformLocationSource(context),
        )

    /** Work that should finish even if the screen that started it closes; lives as long as the process. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * After an icon style change: renders all apps in the styles now in use, in the background, and removes cached
     * icons of other styles. Home renders the icons on screen itself, first.
     */
    fun refreshIcons(densityDpi: Int) {
        appScope.launch {
            val current = settings.settings.first()
            val styles = setOf(current.iconStyle.style) + current.iconOverrides.map { it.style.style }
            iconRepository.prewarm(
                appRepository.home
                    .first()
                    .apps
                    .map { it.app },
                styles,
                densityDpi,
            )
        }
    }

    /** Places by name, for the weather (network: only while the user searches). */
    suspend fun searchPlaces(name: String): List<WeatherPlace> =
        weatherRepository.searchPlaces(name, Locale.getDefault().language).map {
            WeatherPlace(it.name, it.region, it.country, it.coordinates.latitude, it.coordinates.longitude)
        }

    companion object {
        const val CRASH_REPORTS_DIR = "crash-reports"
    }
}
