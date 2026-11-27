package io.github.nobuddyorg.pixelbuddy

import android.content.Context
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherAppsSource
import io.github.nobuddyorg.pixelbuddy.core.data.calendar.CalendarProviderSource
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.crash.DeviceInfo
import io.github.nobuddyorg.pixelbuddy.core.data.icons.CustomIconStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconCache
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.icons.LauncherIconLoader
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.widgets.HomeWidgets
import io.github.nobuddyorg.pixelbuddy.core.weather.OpenMeteoClient
import io.github.nobuddyorg.pixelbuddy.core.weather.PlatformLocationSource
import io.github.nobuddyorg.pixelbuddy.core.weather.UrlConnectionHttpGet
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherCache
import io.github.nobuddyorg.pixelbuddy.core.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** Pictures imported as app icons (#14); included in backups with the settings that use them. */
    val customIcons: CustomIconStore = CustomIconStore(File(context.filesDir, CUSTOM_ICONS_DIR))

    /** Pixelated icons, cached in memory and in the cache directory (#7). */
    val iconRepository: IconRepository =
        IconRepository(
            loader = LauncherIconLoader(context),
            cache = IconCache(File(context.cacheDir, "icons")),
            images = customIcons,
        )

    /** Every app list reload also re-checks icons, so updates and icon themes show up, and drops removed apps' icons. */
    val appRepository: AppRepository =
        AppRepository(
            LauncherAppsSource(context),
            settings,
            onReload = iconRepository::recheck,
            onLoaded = iconRepository::retainApps,
        )

    /** Weather (#11): the only network access, and only while weather is on. Cached outside backups. */
    val weatherRepository: WeatherRepository =
        WeatherRepository(
            settings = settings.weather,
            client = OpenMeteoClient(UrlConnectionHttpGet()),
            cache = WeatherCache(File(context.noBackupFilesDir, "weather.json")),
            location = PlatformLocationSource(context),
        )

    /** The widget slot on home (#26). */
    val widgets: HomeWidgets = HomeWidgets(context)

    /** The phone's calendar for the next event (#27); only with the calendar permission. */
    val calendar: CalendarProviderSource = CalendarProviderSource(context)

    /** The playing media for the controls on home (#25); only with the notification access. */
    val mediaPlayback: MediaSessions = MediaSessions(context)

    /** Work that should finish even if the screen that started it closes; lives as long as the process. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * After an icon style change: renders all apps in the styles now in use, in the background, and removes cached
     * icons of other styles. Home renders the icons on screen itself, first.
     */
    fun refreshIcons(densityDpi: Int) {
        appScope.launch {
            customIcons.retain(settings.settings.first().iconImages)
            val apps =
                appRepository.home
                    .first()
                    .apps
                    .map { it.app }
            iconRepository.prewarm(apps, settings.iconStyles.first(), densityDpi)
        }
    }

    init {
        // Turning on a feature that needs the notification access connects the listener again (#24); it lets go by
        // itself when none does. Cheap: it only reacts to settings changes.
        appScope.launch {
            settings.notifications
                .map { it.needsAccess }
                .distinctUntilChanged()
                .collect { needed -> if (needed) withContext(Dispatchers.Main) { NotificationService.reconnect() } }
        }
        // Pictures of overrides that are gone (reset, app uninstalled, older import) are deleted on start.
        appScope.launch { customIcons.retain(settings.settings.first().iconImages) }
    }

    /** Places by name, for the weather (network: only while the user searches). */
    suspend fun searchPlaces(name: String): List<WeatherPlace> =
        weatherRepository.searchPlaces(name, Locale.getDefault().language).map {
            WeatherPlace(it.name, it.region, it.country, it.coordinates.latitude, it.coordinates.longitude)
        }

    companion object {
        const val CRASH_REPORTS_DIR = "crash-reports"
        const val CUSTOM_ICONS_DIR = "icon-images"
    }
}
