package io.github.nobuddyorg.pixelbuddy.core.designsystem

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DitherTest {
    @Test
    fun `bayer matrix 2x2 and 4x4 match the classic tables`() {
        assertContentEquals(
            arrayOf(intArrayOf(0, 2), intArrayOf(3, 1)).map {
                it.toList()
            },
            Dither.bayerMatrix(2).map { it.toList() },
        )
        assertEquals(
            listOf(
                listOf(0, 8, 2, 10),
                listOf(12, 4, 14, 6),
                listOf(3, 11, 1, 9),
                listOf(15, 7, 13, 5),
            ),
            Dither.bayerMatrix(4).map { it.toList() },
        )
    }

    @Test
    fun `bayer matrix contains every threshold once`() {
        listOf(1, 2, 4, 8).forEach { n ->
            assertEquals((0 until n * n).toList(), Dither.bayerMatrix(n).flatMap { it.toList() }.sorted())
        }
        assertFailsWith<IllegalArgumentException> { Dither.bayerMatrix(3) }
    }

    @Test
    fun `gradient starts and ends with the first and last colour`() {
        val stops = intArrayOf(0xFF141833.toInt(), 0xFFFF8A3D.toInt())

        val image = Dither.verticalGradient(width = 8, height = 64, stops = stops)

        assertTrue(image.take(8).all { it == stops[0] })
        assertTrue(image.takeLast(8).all { it == stops[1] })
    }

    @Test
    fun `gradient only uses colours of its bands and is opaque`() {
        val stops = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val bands = 5

        val image = Dither.verticalGradient(16, 100, stops, bands = bands)

        val palette = (0 until bands).map { Dither.interpolate(stops, it / (bands - 1f)) }.toSet()
        assertTrue(image.all { it in palette })
        assertTrue(image.all { it ushr 24 == 0xFF })
        assertEquals(bands, image.toSet().size)
    }

    @Test
    fun `rows between two bands mix both colours`() {
        val stops = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())

        // Height 3 with 2 bands: the middle row is exactly halfway.
        val middle = Dither.verticalGradient(4, 3, stops, bands = 2).drop(4).take(4)

        assertEquals(setOf(stops[0], stops[1]), middle.toSet())
    }

    @Test
    fun `band index is pushed by the threshold and clamped`() {
        assertEquals(1, Dither.bandIndex(1.4f, 0f, 5))
        assertEquals(2, Dither.bandIndex(1.4f, 0.2f, 5))
        assertEquals(0, Dither.bandIndex(0f, -0.5f, 5))
        assertEquals(4, Dither.bandIndex(4f, 0.49f, 5))
    }

    @Test
    fun `invalid input is rejected`() {
        assertFailsWith<IllegalArgumentException> { Dither.verticalGradient(0, 1, intArrayOf(0, 0)) }
        assertFailsWith<IllegalArgumentException> { Dither.verticalGradient(1, 1, intArrayOf(0)) }
    }
}
