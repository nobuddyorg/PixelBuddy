package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContoursTest {
    private fun labels(
        size: Int,
        at: (x: Int, y: Int) -> Int,
    ) = IntArray(size * size) { at(it % size, it / size) }

    @Test
    fun `a filled square is one closed outer contour along the pixel edges`() {
        val contours = Contours.trace(labels(6) { x, y -> if (x in 1..3 && y in 2..4) 1 else 0 }, 6)
        val c = contours.single()
        assertTrue(c.closed && c.outer)
        assertEquals(12, c.size)
        assertEquals(12.0, c.length)
        assertEquals(1.0, (0 until c.size).minOf { c.x(it) })
        assertEquals(4.0, (0 until c.size).maxOf { c.x(it) })
        assertEquals(2.0, (0 until c.size).minOf { c.y(it) })
    }

    @Test
    fun `a split square gives open chains between its junctions`() {
        // left half 1, right half 2: three borders (0-1, 0-2, 1-2) meeting in two junctions
        val contours =
            Contours.trace(
                labels(6) { x, y ->
                    if (y !in 1..4 || x !in 1..4) {
                        0
                    } else if (x < 3) {
                        1
                    } else {
                        2
                    }
                },
                6,
            )
        assertEquals(3, contours.size)
        assertTrue(contours.none { it.closed })
        val middle = contours.single { !it.outer }
        assertEquals(4.0, middle.length)
        assertEquals(setOf(1.0, 5.0), setOf(middle.y(0), middle.y(middle.size - 1)))
        // every chain starts and ends on a junction
        contours.forEach { c -> assertEquals(setOf(1.0, 5.0), setOf(c.y(0), c.y(c.size - 1))) }
    }

    @Test
    fun `a region touching the image border is closed by the padding`() {
        val c = Contours.trace(labels(4) { _, _ -> 1 }, 4).single()
        assertTrue(c.closed)
        assertEquals(16.0, c.length)
    }

    @Test
    fun `resampling spaces points evenly`() {
        val c = Contours.trace(labels(10) { x, y -> if (x in 2..7 && y in 2..7) 1 else 0 }, 10).single()
        val r = Contours.resample(c, 2.0)
        assertEquals(12, r.size)
        for (i in 0 until r.size) {
            val j = (i + 1) % r.size
            assertTrue(abs(hypot(r.x(j) - r.x(i), r.y(j) - r.y(i)) - 2.0) < 1e-9)
        }
        val open = OutlineContour(doubleArrayOf(0.0, 3.0), doubleArrayOf(0.0, 4.0), 0, 1, closed = false)
        val line = Contours.resample(open)
        assertEquals(5, line.size)
        assertEquals(3.0, line.x(4))
        val dot = OutlineContour(doubleArrayOf(2.0, 2.0), doubleArrayOf(1.0, 1.0), 0, 1, closed = false)
        assertEquals(1, Contours.resample(dot).size)
    }

    @Test
    fun `smoothing rounds corners and keeps open ends pinned`() {
        val square = Contours.trace(labels(20) { x, y -> if (x in 4..15 && y in 4..15) 1 else 0 }, 20).single()
        val smooth = Contours.smooth(square, 2.0)
        assertTrue(smooth.closed)
        // the corner (4, 4) is cut
        assertTrue((0 until smooth.size).minOf { hypot(smooth.x(it) - 4, smooth.y(it) - 4) } > 0.5)
        val zigzag =
            OutlineContour(doubleArrayOf(0.0, 1.0, 2.0, 3.0, 4.0), doubleArrayOf(0.0, 1.0, 0.0, 1.0, 0.0), 1, 2, false)
        val pinned = Contours.smooth(zigzag, 1.0)
        assertEquals(0.0, pinned.x(0))
        assertEquals(4.0, pinned.x(pinned.size - 1))
        assertEquals(0.0, pinned.y(pinned.size - 1))
        assertEquals(Contours.resample(zigzag).size, Contours.smooth(zigzag, 0.0).size)
    }

    @Test
    fun `cleanup drops short contours and near duplicates`() {
        val p = OutlineParams(workSize = 100)

        fun line(
            y: Double,
            length: Double,
            a: Int = 1,
        ) = OutlineContour(
            DoubleArray(length.toInt() + 1) { it.toDouble() },
            DoubleArray(length.toInt() + 1) { y },
            a,
            a + 1,
            false,
        )
        val long = line(10.0, 60.0)
        val duplicate = line(12.0, 50.0, a = 2)
        val separate = line(40.0, 50.0, a = 3)
        val short = line(70.0, 5.0, a = 4)
        val shortOuter = line(80.0, 5.0, a = 0)
        val result = Contours.cleanup(listOf(short, duplicate, separate, long, shortOuter), 100, p)
        assertEquals(listOf(long, separate, shortOuter), result)
    }

    @Test
    fun `relabel compacts merged regions and keeps the background`() {
        val merged = Regions.relabel(intArrayOf(0, 1, 2, 3, 4), mapOf(2 to 1, 4 to 2))
        assertContentEquals(intArrayOf(0, 1, 1, 2, 1), merged)
    }

    @Test
    fun `region pairs are ordered`() {
        val key = Regions.pair(7, 3)
        assertEquals(3, Regions.first(key))
        assertEquals(7, Regions.second(key))
        assertEquals(1.0, Regions.strongShare(doubleArrayOf(10.0, 0.0, 2.0)))
        assertEquals(0.25, Regions.strongShare(doubleArrayOf(10.0, 1.0, 4.0)))
    }
}
