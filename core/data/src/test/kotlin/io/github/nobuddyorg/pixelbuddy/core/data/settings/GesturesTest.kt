package io.github.nobuddyorg.pixelbuddy.core.data.settings

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GesturesTest {
    @Test
    fun `nothing locks by default and the double tap is off`() {
        assertFalse(Gestures().usesLock)
        assertEquals(GestureAction.Nothing, Gestures().doubleTap)
    }

    @Test
    fun `any gesture set to lock uses the lock service`() {
        assertTrue(Gestures(doubleTap = GestureAction.Lock).usesLock)
        assertTrue(Gestures(swipeUp = GestureAction.Lock).usesLock)
        assertTrue(Gestures(swipeDown = GestureAction.Lock).usesLock)
        assertTrue(Gestures(swipeUp = GestureAction.Settings, longPress = GestureAction.Lock).usesLock)
    }

    @Test
    fun `a long press forced to open the settings doesn't lock`() {
        // No swipe opens the settings, so the long press does, whatever is stored.
        assertFalse(Gestures(longPress = GestureAction.Lock).usesLock)
    }
}
