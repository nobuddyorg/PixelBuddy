package io.github.nobuddyorg.pixelbuddy.core.data.lock

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow

/**
 * Locks the screen (#16). Android only lets an accessibility service do that without Device Admin (which would
 * turn off biometric unlock), so locking works while the user has turned PixelBuddy's lock service on.
 */
interface ScreenLock {
    /** Whether the lock service is on, so [lock] works. */
    val available: StateFlow<Boolean>

    /** Locks the screen; false if the service is off or the system refused. */
    fun lock(): Boolean

    /** Opens the system screen where the service is turned on. */
    fun settingsIntent(): Intent
}
