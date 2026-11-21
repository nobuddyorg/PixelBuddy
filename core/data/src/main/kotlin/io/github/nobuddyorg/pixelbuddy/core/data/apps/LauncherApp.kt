package io.github.nobuddyorg.pixelbuddy.core.data.apps

import android.content.ComponentName
import android.os.UserHandle

/**
 * Stable identity of a launchable activity. [userSerial] is the user's serial number, which, unlike the user id,
 * survives reboots, so keys can be stored (favorites, hidden apps, overrides).
 */
data class AppKey(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
) {
    /** The key as one string, e.g. for list keys that must fit into a Bundle. */
    val id: String get() = "$packageName/$activityName/$userSerial"
}

/** A launchable activity of a visible profile (main or work profile). */
data class LauncherApp(
    val key: AppKey,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    /** False for apps in a work profile. */
    val isMainProfile: Boolean,
)

/** A [LauncherApp] with its alphabet-rail section: `A`–`Z` or [AppSorter.OTHER_SECTION]. */
data class AppListEntry(
    val app: LauncherApp,
    val section: String,
)

sealed interface LaunchResult {
    data object Launched : LaunchResult

    /** The app was removed, disabled or its profile is unavailable. */
    data class Failed(
        val reason: Throwable,
    ) : LaunchResult
}
