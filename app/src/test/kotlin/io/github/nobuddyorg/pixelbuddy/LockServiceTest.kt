package io.github.nobuddyorg.pixelbuddy

import android.accessibilityservice.AccessibilityService
import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Looper
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.data.settings.GestureAction
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Gestures
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LockServiceTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val controller = Robolectric.buildService(LockService::class.java)

    @After
    fun tearDown() {
        controller.destroy()
    }

    private fun useGestures(gestures: Gestures) =
        runBlocking { app.appContainer.settings.update { it.copy(gestures = gestures) } }

    /** Runs main-thread work until [done], while the settings are read on other threads. */
    private fun idleUntil(done: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (!done() && System.currentTimeMillis() < until) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun `the manifest declares a protected accessibility service with its configuration`() {
        val info =
            app.packageManager.getServiceInfo(
                ComponentName(app, LockService::class.java),
                PackageManager.GET_META_DATA,
            )

        assertEquals("android.permission.BIND_ACCESSIBILITY_SERVICE", info.permission)
        assertTrue(info.metaData.containsKey("android.accessibilityservice"))
    }

    @Test
    fun `while connected and a gesture locks, locking performs the lock action`() {
        useGestures(Gestures(doubleTap = GestureAction.Lock))
        assertFalse(LockService.lock())

        val service = controller.create().get()
        service.onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(LockService.available.value)
        assertTrue(LockService.lock())
        assertEquals(listOf(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN), shadowOf(service).globalActionsPerformed)
    }

    @Test
    fun `once no gesture locks, the service turns itself off`() {
        useGestures(Gestures(doubleTap = GestureAction.Lock))
        controller.create().get().onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(LockService.available.value)

        useGestures(Gestures())
        idleUntil { !LockService.available.value }

        assertFalse(LockService.available.value)
        assertFalse(LockService.lock())
    }

    @Test
    fun `unbinding ends locking`() {
        useGestures(Gestures(swipeDown = GestureAction.Lock))
        controller.create().get().onServiceConnected()
        shadowOf(Looper.getMainLooper()).idle()

        controller.unbind()

        assertFalse(LockService.available.value)
    }

    @Test
    fun `the settings intent opens the accessibility settings`() {
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, LockService.settingsIntent().action)
    }
}
