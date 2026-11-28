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
 * and scaled up without filtering. Optional faint scanlines. [SkyImage] renders the same sky as a full-size
 * image (the wallpaper).
 */
@Composable
fun DitheredSky(
    modifier: Modifier = Modifier,
    colors: List<Color> = PixelBuddyTheme.style.sky,
    pixelSize: Dp = SkyImage.PixelSize,
    scanlines: Boolean = PixelBuddyTheme.style.scanlines,
    dithering: Boolean = PixelBuddyTheme.style.dithering,
) {
    Spacer(
        modifier =
            modifier.drawWithCache {
                val block = SkyImage.blockPx(pixelSize.toPx())
                val w = max(1, ceil(size.width / block).toInt())
                val h = max(1, ceil(size.height / block).toInt())
                val image = skyBitmap(w, h, colors, dithering)
                val target = IntSize(w * block, h * block)
                onDrawBehind {
                    drawImage(image, dstOffset = IntOffset.Zero, dstSize = target, filterQuality = FilterQuality.None)
                    if (scanlines) {
                        var y = 0f
                        while (y < size.height) {
                            drawRect(
                                SkyImage.Scanline,
                                topLeft = Offset(0f, y),
                                size = Size(size.width, block.toFloat()),
                            )
                            y += block * 2f
                        }
                    }
                }
            },
    )
}

private fun skyBitmap(
    width: Int,
    height: Int,
    colors: List<Color>,
    dithering: Boolean,
): ImageBitmap {
    val pixels = Dither.verticalGradient(width, height, colors.map { it.toArgb() }.toIntArray(), dithered = dithering)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The sky as one full-size image, pixel for pixel what [DitheredSky] draws on a screen of that size. */
object SkyImage {
    /** Size of one sky pixel. */
    val PixelSize: Dp = 1.5.dp

    /**
     * Scanline colour, drawn over every other row of sky pixels. Subtle but visible on a phone: at 8 % they could
     * not be seen on the S24's dark sky.
     */
    val Scanline: Color = Color.Black.copy(alpha = 0.24f)

    /** One sky pixel in screen pixels, at least 1. */
    fun blockPx(pixelSizePx: Float): Int = max(1, pixelSizePx.roundToInt())

    /** Renders [width] × [height] screen pixels, e.g. a wallpaper at the exact screen size. */
    fun render(
        width: Int,
        height: Int,
        density: Float,
        colors: List<Color>,
        dithering: Boolean,
        scanlines: Boolean,
    ): Bitmap {
        val pixels =
            Dither.skyPixels(
                width = width,
                height = height,
                block = blockPx(PixelSize.value * density),
                stops = colors.map { it.toArgb() }.toIntArray(),
                dithered = dithering,
                scanline = if (scanlines) Scanline.toArgb() else null,
            )
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
