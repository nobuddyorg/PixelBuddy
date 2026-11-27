package io.github.nobuddyorg.pixelbuddy

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.data.settings.NotificationSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNotificationListenerService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NotificationServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val controller = Robolectric.buildService(NotificationService::class.java)

    @Before
    fun dotsOn() {
        useNotifications(NotificationSettings(dots = true))
    }

    @After
    fun tearDown() {
        controller.destroy()
        ShadowNotificationListenerService.reset()
    }

    private fun useNotifications(notifications: NotificationSettings) {
        runBlocking { app.appContainer.settings.update { it.copy(notifications = notifications) } }
    }

    private fun notification(
        title: String,
        text: String = "",
        configure: Notification.Builder.() -> Unit = {},
    ): Notification =
        Notification
            .Builder(app, "chat")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .apply(configure)
            .build()

    private fun connect(): NotificationService =
        controller.create().get().also {
            it.onListenerConnected()
            shadowOf(Looper.getMainLooper()).idle()
        }

    /** Runs main-thread work until [done], while the settings are read on other threads. */
    private fun idleUntil(done: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!done() && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun `the manifest declares a protected notification listener`() {
        val info = app.packageManager.getServiceInfo(ComponentName(app, NotificationService::class.java), 0)

        assertEquals("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", info.permission)
    }

    @Test
    fun `connected, it lists notifications with a badge, newest first`() {
        val service = controller.create().get()
        val shadow = shadowOf(service)
        shadow.addActiveNotification("org.example.chat", 1, notification("Ada", "Hi!") { setWhen(1) })
        shadow.addActiveNotification("org.example.music", 2, notification("Playing") { setOngoing(true) })
        shadow.addActiveNotification(
            "org.example.chat",
            3,
            notification("Group") { setGroupSummary(true).setGroup("g") },
        )

        service.onListenerConnected()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(NotificationService.connected.value)
        val active = NotificationService.active.value
        assertEquals(listOf("Ada"), active.map { it.title })
        assertEquals("Hi!", active.single().text)
        assertEquals("org.example.chat", active.single().packageName)
        assertTrue(active.single().clearable)
    }

    @Test
    fun `opening sends the notification's intent and removes it if it cancels itself`() {
        val service = controller.create().get()
        val intent =
            PendingIntent.getActivity(
                app,
                0,
                Intent(Intent.ACTION_VIEW).setPackage("org.example.chat"),
                PendingIntent.FLAG_IMMUTABLE,
            )
        val key =
            shadowOf(service).addActiveNotification(
                "org.example.chat",
                1,
                notification("Ada") {
                    setContentIntent(intent)
                    setAutoCancel(true)
                },
            )
        service.onListenerConnected()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(NotificationService.open(key))

        assertEquals(Intent.ACTION_VIEW, shadowOf(app).nextStartedActivity.action)
        assertTrue(service.activeNotifications.isEmpty())
    }

    @Test
    fun `a notification without an action can't be opened, but dismissed`() {
        val service = controller.create().get()
        val key = shadowOf(service).addActiveNotification("org.example.chat", 1, notification("Ada"))
        service.onListenerConnected()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(NotificationService.open(key))
        NotificationService.dismiss(key)

        assertTrue(service.activeNotifications.isEmpty())
    }

    @Test
    fun `when no feature needs it any more, it lets go of the access`() {
        val service = connect()
        assertTrue(NotificationService.connected.value)

        useNotifications(NotificationSettings())
        idleUntil { !NotificationService.connected.value }

        assertFalse(NotificationService.connected.value)
        assertEquals(1, shadowOf(service).unbindRequestCount)
        assertTrue(NotificationService.active.value.isEmpty())
    }

    @Test
    fun `turning a feature on again connects again`() {
        NotificationService.reconnect()

        assertEquals(1, ShadowNotificationListenerService.getRebindRequestCount())
    }

    @Test
    fun `the settings intent opens this app's notification access page`() {
        val intent = NotificationService.settingsIntent()

        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, intent.action)
        assertEquals(
            ComponentName(app, NotificationService::class.java).flattenToString(),
            intent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME),
        )
    }

    @Test
    fun `disconnecting clears everything`() {
        val service = controller.create().get()
        shadowOf(service).addActiveNotification("org.example.chat", 1, notification("Ada"))
        service.onListenerConnected()
        shadowOf(Looper.getMainLooper()).idle()

        service.onListenerDisconnected()

        assertFalse(NotificationService.connected.value)
        assertTrue(NotificationService.active.value.isEmpty())
    }
}
