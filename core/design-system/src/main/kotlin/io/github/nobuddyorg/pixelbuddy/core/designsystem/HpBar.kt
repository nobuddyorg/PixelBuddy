package io.github.nobuddyorg.pixelbuddy.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max

/** Battery as a game HP bar. */
@Immutable
data class BatteryState(
    /** 0..100 */
    val level: Int,
    val charging: Boolean,
    /** Plugged in and fully charged. */
    val full: Boolean = false,
) {
    init {
        require(level in 0..100) { "level must be 0..100, was $level" }
    }

    /** Filled segments out of [SEGMENTS]; any charge above 0 shows at least one. */
    val segments: Int get() = if (level == 0) 0 else ceil(level * SEGMENTS / 100.0).toInt().coerceIn(1, SEGMENTS)

    /** Below 15 % and not charging. */
    val isLow: Boolean get() = level < LOW_LEVEL && !charging

    companion object {
        const val SEGMENTS = 4
        const val LOW_LEVEL = 15
    }
}

/** HP bar for [battery]: frame, four segments, a lightning bolt while charging, green when full, red when low. */
@Composable
fun HpBar(
    battery: BatteryState,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    color: Color = PixelBuddyColors.Text,
    accent: Color = PixelBuddyTheme.style.accent,
) {
    val density = LocalDensity.current
    val cellPx = max(1, (with(density) { height.toPx() } / ROWS).toInt())
    val columns = (if (battery.charging || battery.full) BOLT_COLUMNS + 1 else 0) + BAR_COLUMNS
    val segmentColor =
        when {
            battery.full -> PixelBuddyColors.HpFull
            battery.isLow -> PixelBuddyColors.HpLow
            else -> color
        }
    Canvas(
        modifier.size(with(density) { (columns * cellPx).toDp() }, with(density) { (ROWS * cellPx).toDp() }),
    ) {
        val cell = cellPx.toFloat()
        var x0 = 0
        if (battery.charging || battery.full) {
            BOLT.forEachIndexed { y, row ->
                row.forEachIndexed { x, c -> if (c == '#') pixel(x, y, cell, accent) }
            }
            x0 = BOLT_COLUMNS + 1
        }
        val frame = if (battery.isLow) PixelBuddyColors.HpLow else color
        // Frame: 1 pixel border around 4 segments of 3 pixels with 1 pixel gaps, plus a 1 pixel cap.
        for (x in 0 until BAR_COLUMNS - 1) {
            pixel(x0 + x, 0, cell, frame)
            pixel(x0 + x, ROWS - 1, cell, frame)
        }
        for (y in 0 until ROWS) {
            pixel(x0, y, cell, frame)
            pixel(x0 + BAR_COLUMNS - 2, y, cell, frame)
        }
        for (y in 2 until ROWS - 2) pixel(x0 + BAR_COLUMNS - 1, y, cell, frame)
        repeat(battery.segments) { s ->
            val sx = x0 + 2 + s * (SEGMENT_WIDTH + 1)
            for (x in sx until sx + SEGMENT_WIDTH) for (y in 2 until ROWS - 2) pixel(x, y, cell, segmentColor)
        }
    }
}

private fun DrawScope.pixel(
    x: Int,
    y: Int,
    cell: Float,
    color: Color,
) = drawRect(color, Offset(x * cell, y * cell), Size(cell, cell))

private const val ROWS = 7
private const val SEGMENT_WIDTH = 3

/** Border + gap + 4 × (3 + 1 gap) + border + cap. */
private const val BAR_COLUMNS = 1 + 1 + BatteryState.SEGMENTS * (SEGMENT_WIDTH + 1) + 1 + 1
private const val BOLT_COLUMNS = 4
private val BOLT = listOf("..#.", ".##.", "####", ".##.", ".#..", "#...", "....")
