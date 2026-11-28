package io.github.nobuddyorg.pixelbuddy.feature.home

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PixelDissolveTest {
    private val rows = 40
    private val columns = 20

    private fun covered(progress: Float) =
        (0 until rows).sumOf { row -> (0 until columns).count { PixelDissolve.covers(it, row, rows, progress) } }

    @Test
    fun `nothing at the start, everything at the end`() {
        assertEquals(0, covered(0f))
        assertEquals(rows * columns, covered(1f))
        assertEquals(0, PixelDissolve.touchedRows(rows, 0f))
        assertEquals(rows, PixelDissolve.solidRows(rows, 1f))
    }

    @Test
    fun `the blocks only ever grow, from the bottom`() {
        var before = 0
        for (step in 1..50) {
            val now = covered(step / 50f)
            assertTrue(now >= before, "step $step")
            before = now
        }
        assertTrue(PixelDissolve.covers(0, 0, rows, 0.3f))
        assertFalse(PixelDissolve.covers(0, rows - 1, rows, 0.3f))
    }

    @Test
    fun `each column is covered from the bottom without gaps`() {
        for (step in 1 until 20) {
            for (column in 0 until columns) {
                val top = (0 until rows).count { PixelDissolve.covers(column, it, rows, step / 20f) }
                (0 until top).forEach { assertTrue(PixelDissolve.covers(column, it, rows, step / 20f)) }
            }
        }
    }

    @Test
    fun `the edge is ragged, and solid and touched rows bound it`() {
        val progress = 0.5f
        val solid = PixelDissolve.solidRows(rows, progress)
        val touched = PixelDissolve.touchedRows(rows, progress)
        assertTrue(solid < touched)
        for (row in 0 until rows) {
            val count = (0 until columns).count { PixelDissolve.covers(it, row, rows, progress) }
            when {
                row < solid -> assertEquals(columns, count, "row $row is solid")
                row >= touched -> assertEquals(0, count, "row $row is untouched")
            }
        }
        // Somewhere on the edge a row is only partly covered: a skyline, not a straight line.
        assertTrue(
            (solid until touched).any { row ->
                (0 until columns).count { PixelDissolve.covers(it, row, rows, progress) } in
                    1 until columns
            },
        )
    }
}
