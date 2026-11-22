package io.github.nobuddyorg.pixelbuddy.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Big clock drawn from [PixelGlyphs] as whole physical pixels, with a 1-glyph-pixel drop shadow.
 * [height] is a maximum: the glyph pixel size is rounded down so every pixel has the same size. A null [shadow]
 * leaves the shadow out (its space stays, so the clock doesn't move).
 */
@Composable
fun PixelClock(
    text: String,
    modifier: Modifier = Modifier,
    height: Dp = 72.dp,
    color: Color = PixelBuddyColors.Text,
    shadow: Color? = PixelBuddyColors.Shadow,
    contentDescription: String = text,
) {
    val layout = remember(text) { PixelGlyphs.layout(text) }
    val density = LocalDensity.current
    // Glyph rows plus one row for the shadow.
    val cellPx = max(1, (with(density) { height.toPx() } / (layout.height + 1)).toInt())
    val widthPx = (layout.width + 1) * cellPx
    val heightPx = (layout.height + 1) * cellPx
    Canvas(
        modifier =
            modifier
                .size(with(density) { widthPx.toDp() }, with(density) { heightPx.toDp() })
                .semantics { this.contentDescription = contentDescription },
    ) {
        val cell = cellPx.toFloat()
        val pixel = Size(cell, cell)
        if (shadow != null) {
            layout.pixels.forEach { (x, y) -> drawRect(shadow, Offset((x + 1) * cell, (y + 1) * cell), pixel) }
        }
        layout.pixels.forEach { (x, y) -> drawRect(color, Offset(x * cell, y * cell), pixel) }
    }
}
