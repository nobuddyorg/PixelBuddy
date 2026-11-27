package io.github.nobuddyorg.pixelbuddy

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import io.github.nobuddyorg.pixelbuddy.core.data.lock.ScreenLock
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
 * The opt-in lock service (#16): the only way for an app to lock the screen without Device Admin, which would turn
 * off biometric unlock. It runs only while the user has turned it on in the accessibility settings, subscribes to
 * no events, can't read window content (res/xml/lock_service.xml) and does nothing but
 * [GLOBAL_ACTION_LOCK_SCREEN] when home asks. Once no gesture locks any more, it turns itself off.
 */
class LockService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    public override fun onServiceConnected() {
        super.onServiceConnected()
        current = WeakReference(this)
        connected.value = true
        scope.launch {
            appContainer.settings.gestures.first { !it.usesLock }
            release()
            disableSelf()
        }
    }

    // The configuration subscribes to no events.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        scope.cancel()
        if (current?.get() === this) {
            current = null
            connected.value = false
        }
    }

    internal companion object : ScreenLock {
        // Only touched on the main thread: the system calls the service there, and home locks from a gesture.
        private var current: WeakReference<LockService>? = null
        private val connected = MutableStateFlow(false)

        override val available: StateFlow<Boolean> = connected.asStateFlow()

        override fun lock(): Boolean = current?.get()?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) == true

        // The page for one service isn't public API; the list is, and the service is one tap from there.
        override fun settingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}
