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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

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

    companion object {
        const val CRASH_REPORTS_DIR = "crash-reports"
    }
}
