package io.github.nobuddyorg.pixelbuddy.core.designsystem

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BatteryStateTest {
    @Test
    fun `segments round up so any charge shows`() {
        val segments = listOf(0, 1, 25, 26, 50, 58, 75, 76, 100).map { BatteryState(it, charging = false).segments }

        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 4, 4), segments)
    }

    @Test
    fun `low below 15 percent unless charging`() {
        assertTrue(BatteryState(14, charging = false).isLow)
        assertFalse(BatteryState(15, charging = false).isLow)
        assertFalse(BatteryState(5, charging = true).isLow)
    }

    @Test
    fun `level outside 0 to 100 is rejected`() {
        assertFailsWith<IllegalArgumentException> { BatteryState(101, charging = false) }
        assertFailsWith<IllegalArgumentException> { BatteryState(-1, charging = false) }
    }
}
