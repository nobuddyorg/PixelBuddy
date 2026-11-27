package io.github.nobuddyorg.pixelbuddy

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.AppNotification
import io.github.nobuddyorg.pixelbuddy.core.data.notifications.AppNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * The opt-in notification access (#24, #25). Runs only while the user has granted it and a feature that needs it
 * is on; otherwise it lets go of the connection (requestUnbind). Notifications are held in memory only, as long as
 * they are shown: nothing is stored, logged or put into crash reports.
 */
class NotificationService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Content intents by key, to open a notification; in memory only. */
    private var intents: Map<String, Pair<PendingIntent, Boolean>> = emptyMap()

    override fun onListenerConnected() {
        super.onListenerConnected()
        current = WeakReference(this)
        connectedState.value = true
        refresh()
        // Not needed any more once no feature uses it: let go, so nothing keeps listening.
        scope.launch {
            appContainer.settings.notifications.first { !it.needsAccess }
            release()
            requestUnbind()
        }
    }

    override fun onListenerDisconnected() {
        release()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
    ) = refresh(rankingMap)

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
    ) = refresh(rankingMap)

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = refresh(rankingMap)

    private fun refresh(rankingMap: RankingMap? = null) {
        if (current?.get() !== this) return
        val notifications =
            try {
                activeNotifications.orEmpty().toList()
            } catch (_: SecurityException) {
                // The access was just taken away.
                emptyList()
            }
        val ranking = rankingMap ?: runCatching { currentRanking }.getOrNull()
        val shown = notifications.filter { it.showsBadge(ranking) }
        intents =
            shown
                .mapNotNull { sbn ->
                    sbn.notification.contentIntent?.let {
                        sbn.key to (it to (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0))
                    }
                }.toMap()
        activeState.value = shown.map { it.toAppNotification() }.sortedByDescending { it.postedAt }
    }

    private fun StatusBarNotification.showsBadge(rankingMap: RankingMap?): Boolean {
        if (isOngoing || notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val ranking = Ranking()
        return rankingMap == null || !rankingMap.getRanking(key, ranking) || ranking.canShowBadge()
    }

    private fun StatusBarNotification.toAppNotification(): AppNotification {
        val extras = notification.extras
        return AppNotification(
            key = key,
            packageName = packageName,
            user = user,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            postedAt = postTime,
            clearable = isClearable,
        )
    }

    private fun open(key: String): Boolean {
        val (intent, autoCancel) = intents[key] ?: return false
        try {
            intent.send(this, 0, null, null, null, null, launchOptions())
        } catch (_: PendingIntent.CanceledException) {
            return false
        }
        // Like the shade: a notification meant to go away when opened goes away.
        if (autoCancel) cancelNotification(key)
        return true
    }

    private fun release() {
        scope.cancel()
        if (current?.get() === this) {
            current = null
            intents = emptyMap()
            connectedState.value = false
            activeState.value = emptyList()
        }
    }

    internal companion object : AppNotifications {
        // Only touched on the main thread: the system calls the listener there (API 24+), and home acts from taps.
        private var current: WeakReference<NotificationService>? = null
        private val connectedState = MutableStateFlow(false)
        private val activeState = MutableStateFlow(emptyList<AppNotification>())

        override val connected: StateFlow<Boolean> = connectedState.asStateFlow()
        override val active: StateFlow<List<AppNotification>> = activeState.asStateFlow()

        override fun open(key: String): Boolean = current?.get()?.open(key) == true

        override fun dismiss(key: String) {
            current?.get()?.cancelNotification(key)
        }

        /** This app's listener (the debug build has another package). Set once at start, see [attach]. */
        private var component: ComponentName? = null

        /** Called by the application at start. */
        fun attach(context: Context) {
            component = ComponentName(context, NotificationService::class.java)
        }

        override fun settingsIntent(): Intent {
            val listener = component
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && listener != null) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener.flattenToString())
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            }
        }

        /** A feature was turned on while the access is granted but the listener let go of it: connect again. */
        fun reconnect() {
            val listener = component ?: return
            if (!connectedState.value) requestRebind(listener)
        }

        /** Home is visible when the user taps, so the app may open its screen from there. */
        private fun launchOptions() =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA -> {
                    ActivityOptions
                        .makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE,
                        ).toBundle()
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    @Suppress("DEPRECATION") // The only mode before API 36; replaced by ALLOW_IF_VISIBLE above.
                    ActivityOptions
                        .makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(
                            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                        ).toBundle()
                }

                else -> {
                    null
                }
            }
    }
}
