package io.github.nobuddyorg.pixelbuddy.core.designsystem

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The retro sky: a dithered vertical gradient rendered once per size at low resolution ([pixelSize] per pixel)
 * and scaled up without filtering. Optional faint scanlines.
 */
@Composable
fun DitheredSky(
    modifier: Modifier = Modifier,
    colors: List<Color> = PixelBuddyColors.Sky,
    pixelSize: Dp = 1.5.dp,
    scanlines: Boolean = PixelBuddyTheme.style.scanlines,
) {
    Spacer(
        modifier =
            modifier.drawWithCache {
                val block = max(1, pixelSize.toPx().roundToInt())
                val w = max(1, ceil(size.width / block).toInt())
                val h = max(1, ceil(size.height / block).toInt())
                val image = skyBitmap(w, h, colors)
                val target = IntSize(w * block, h * block)
                onDrawBehind {
                    drawImage(image, dstOffset = IntOffset.Zero, dstSize = target, filterQuality = FilterQuality.None)
                    if (scanlines) {
                        var y = 0f
                        while (y < size.height) {
                            drawRect(SCANLINE, topLeft = Offset(0f, y), size = Size(size.width, block.toFloat()))
                            y += block * 2f
                        }
                    }
                }
            },
    )
}

private val SCANLINE = Color.Black.copy(alpha = 0.08f)

private fun skyBitmap(
    width: Int,
    height: Int,
    colors: List<Color>,
): ImageBitmap {
    val pixels = Dither.verticalGradient(width, height, colors.map { it.toArgb() }.toIntArray())
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
