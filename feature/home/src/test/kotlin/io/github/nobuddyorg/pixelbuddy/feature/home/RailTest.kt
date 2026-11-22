package io.github.nobuddyorg.pixelbuddy.feature.home

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RailTest {
    private val entries = Rail.entries(setOf("A", "B", "M", "S", "#"))

    @Test
    fun `the rail has favorites, A to Z and other`() {
        assertEquals(28, Rail.KEYS.size)
        assertEquals(listOf("★", "A", "B"), Rail.KEYS.take(3))
        assertEquals(listOf("Z", "#"), Rail.KEYS.takeLast(2))
    }

    @Test
    fun `only letters with apps are active, favorites always`() {
        assertEquals(listOf("★", "A", "B", "M", "S", "#"), entries.filter { it.active }.map { it.key })
    }

    @Test
    fun `positions map to equal cells and are clamped`() {
        // 28 cells of 10 px.
        assertEquals(0, Rail.indexAt(0f, 280f, 28))
        assertEquals(0, Rail.indexAt(9.9f, 280f, 28))
        assertEquals(1, Rail.indexAt(10f, 280f, 28))
        assertEquals(19, Rail.indexAt(195f, 280f, 28))
        assertEquals(27, Rail.indexAt(279f, 280f, 28))
        assertEquals(0, Rail.indexAt(-50f, 280f, 28))
        assertEquals(27, Rail.indexAt(5000f, 280f, 28))
        assertEquals(0, Rail.indexAt(10f, 0f, 28))
        assertEquals(0, Rail.indexAt(10f, 280f, 0))
    }

    @Test
    fun `inactive letters are skipped to the nearest active one`() {
        fun key(letter: String) = entries[Rail.nearestActive(Rail.KEYS.indexOf(letter), entries)!!].key

        assertEquals("S", key("S"))
        assertEquals("S", key("R"))
        assertEquals("S", key("T"))
        assertEquals("M", key("N"))
        // P is 3 from M and 3 from S: the one above wins.
        assertEquals("M", key("P"))
        assertEquals("#", key("Y"))
        assertEquals("B", key("C"))
    }

    @Test
    fun `without any active entry nothing is selected`() {
        val none = Rail.KEYS.map { RailEntry(it, active = false) }

        assertNull(Rail.nearestActive(5, none))
        assertNull(Rail.nearestActive(0, emptyList()))
    }

    @Test
    fun `the wave is stepped and flat with reduced motion`() {
        assertEquals(2f, Rail.waveScale(0f))
        assertEquals(2f, Rail.waveScale(-0.9f))
        assertEquals(1.5f, Rail.waveScale(1.2f))
        assertEquals(1.25f, Rail.waveScale(-2.5f))
        assertEquals(1f, Rail.waveScale(3f))
        assertTrue((-10..10).all { Rail.waveScale(it / 2f, reducedMotion = true) == 1f })
    }
}
