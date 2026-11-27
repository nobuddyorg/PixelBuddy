package io.github.nobuddyorg.pixelbuddy.core.data.notifications

import android.content.Intent
import android.os.UserHandle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import kotlinx.coroutines.flow.StateFlow

/**
 * One notification an app shows (#24), as far as home needs it. Held in memory only: never stored, logged or put
 * into crash reports.
 */
data class AppNotification(
    /** The system's key, to open or dismiss it. */
    val key: String,
    val packageName: String,
    val user: UserHandle,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** The user may dismiss it (not ongoing). */
    val clearable: Boolean,
)

/** Notifications of the apps on home, through the opt-in notification access (#24). */
interface AppNotifications {
    /** Whether the notification access is granted and connected. */
    val connected: StateFlow<Boolean>

    /** Every notification that would show a badge, newest first. Empty while not [connected]. */
    val active: StateFlow<List<AppNotification>>

    /** Opens the notification like the shade does; false if it can't (gone, no action). */
    fun open(key: String): Boolean

    fun dismiss(key: String)

    /** Opens the system screen where the notification access is granted. */
    fun settingsIntent(): Intent
}

/** The notifications of [app]'s package and profile, newest first. */
fun List<AppNotification>.of(app: LauncherApp): List<AppNotification> =
    filter { it.packageName == app.component.packageName && it.user == app.user }
