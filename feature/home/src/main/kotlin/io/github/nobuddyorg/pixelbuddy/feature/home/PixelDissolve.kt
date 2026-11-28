package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * The search's backdrop rising in blocks from the bottom: each column a little ahead or behind the next (in Bayer
 * order, so neighbours differ), a ragged skyline. At [progress] 0 nothing is covered, at 1 everything. Pure, so the
 * shape is tested without drawing.
 */
internal object PixelDissolve {
    /** How much of the height the ragged edge spans. */
    const val EDGE = 0.06f

    /** The 1-D Bayer order (bit reversal): no two neighbours are close in height. */
    private val order = intArrayOf(0, 4, 2, 6, 1, 5, 3, 7)

    /** Whether the block in [column] and [row] (0 at the bottom) of [rows] is covered at [progress]. */
    fun covers(
        column: Int,
        row: Int,
        rows: Int,
        progress: Float,
    ): Boolean = progress * (1 + EDGE) > height(row, rows) + EDGE * threshold(column)

    /** Rows from the bottom that are fully covered whatever the column; above them is the edge. */
    fun solidRows(
        rows: Int,
        progress: Float,
    ): Int = (0 until rows).firstOrNull { height(it, rows) + EDGE > progress * (1 + EDGE) } ?: rows

    /** Rows from the bottom that are at least partly covered. */
    fun touchedRows(
        rows: Int,
        progress: Float,
    ): Int = (0 until rows).firstOrNull { height(it, rows) >= progress * (1 + EDGE) } ?: rows

    private fun height(
        row: Int,
        rows: Int,
    ) = row.toFloat() / rows

    private fun threshold(column: Int) = (order[column % order.size] + 0.5f) / order.size
}

/**
 * Draws [PixelDissolve] at [progress] behind the content in [color], each column's top block lit in [edge]. Full rows
 * are one rectangle each, so a frame draws a few hundred rectangles at most.
 */
internal fun Modifier.pixelDissolve(
    progress: () -> Float,
    color: Color,
    edge: Color,
    block: Dp = BlockSize,
): Modifier =
    drawBehind {
        val p = progress().coerceIn(0f, 1f)
        if (p <= 0f) return@drawBehind
        if (p >= 1f) {
            drawRect(color)
            return@drawBehind
        }
        val side = block.toPx()
        val columns = ceil(size.width / side).toInt()
        val rows = ceil(size.height / side).toInt()
        val solid = PixelDissolve.solidRows(rows, p)
        if (solid > 0) {
            val top = size.height - solid * side
            drawRect(color, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
        }
        val cell = Size(side, side)
        for (row in solid until PixelDissolve.touchedRows(rows, p)) {
            val y = size.height - (row + 1) * side
            for (column in 0 until columns) {
                if (!PixelDissolve.covers(column, row, rows, p)) continue
                val lit = !PixelDissolve.covers(column, row + 1, rows, p)
                drawRect(if (lit) edge else color, topLeft = Offset(column * side, y), size = cell)
            }
        }
    }

private val BlockSize = 12.dp
