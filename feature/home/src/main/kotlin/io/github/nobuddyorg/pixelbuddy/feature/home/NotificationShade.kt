package io.github.nobuddyorg.pixelbuddy.feature.home

import android.annotation.SuppressLint
import android.content.Context

/**
 * Pulls down the notification shade like the system launcher does. There is no public API for it: this calls the
 * non-public `StatusBarManager.expandNotificationsPanel()` (needs `EXPAND_STATUS_BAR`). Returns false where that
 * isn't possible, so home can fall back to search.
 */
internal fun Context.expandNotificationShade(): Boolean {
    // "statusbar" is the hidden Context.STATUS_BAR_SERVICE constant.
    @SuppressLint("WrongConstant")
    val service = getSystemService("statusbar")
    return expandNotificationShade(service)
}

internal fun expandNotificationShade(service: Any?): Boolean {
    if (service == null) return false
    return try {
        service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
        true
    } catch (_: ReflectiveOperationException) {
        // Missing, blocked by the hidden-API policy, or failed inside.
        false
    } catch (_: SecurityException) {
        false
    }
}
