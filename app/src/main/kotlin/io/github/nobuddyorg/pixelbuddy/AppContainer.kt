package io.github.nobuddyorg.pixelbuddy

import android.content.Context
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppRepository
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherAppsSource
import io.github.nobuddyorg.pixelbuddy.core.data.crash.CrashReportStore
import io.github.nobuddyorg.pixelbuddy.core.data.crash.DeviceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Manual dependency wiring (no DI framework, see docs/PLAN.md). One instance per process. */
class AppContainer(
    context: Context,
) {
    val deviceInfo: DeviceInfo = DeviceInfo.from(context)

    /** Excluded from backups, see res/xml/backup_rules.xml and data_extraction_rules.xml. */
    val crashReports: CrashReportStore = CrashReportStore(File(context.filesDir, CRASH_REPORTS_DIR))

    /** Hidden apps; kept in memory until settings storage arrives (#8). */
    val hiddenApps: MutableStateFlow<Set<AppKey>> = MutableStateFlow(emptySet())

    val appRepository: AppRepository = AppRepository(LauncherAppsSource(context), hiddenApps)

    companion object {
        const val CRASH_REPORTS_DIR = "crash-reports"
    }
}
