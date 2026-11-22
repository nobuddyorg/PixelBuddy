package io.github.nobuddyorg.pixelbuddy.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/** Departure Mono (SIL OFL 1.1, see assets/licenses/departure_mono_OFL.txt). */
val PixelFont: FontFamily = FontFamily(Font(R.font.departure_mono))

/** Departure Mono is pixel-perfect at multiples of this size in physical pixels. */
const val PIXEL_FONT_GRID_PX = 11

/**
 * Pure helper behind [pixelTextSize]: the multiple of [PIXEL_FONT_GRID_PX] closest to [sp] at the given density
 * and font scale, as an sp value. The user's font scale is respected, then snapped to the font's pixel grid.
 */
fun snapToPixelGrid(
    sp: Float,
    density: Float,
    fontScale: Float,
): Float {
    val targetPx = sp * density * fontScale
    val multiple = max(1, (targetPx / PIXEL_FONT_GRID_PX).roundToInt())
    return multiple * PIXEL_FONT_GRID_PX / (density * fontScale)
}

/** A text size near [sp] that renders Departure Mono crisply on this screen. */
@Composable
@ReadOnlyComposable
fun pixelTextSize(sp: Float): TextUnit {
    val density = LocalDensity.current
    return snapToPixelGrid(sp, density.density, density.fontScale).sp
}

/** Text styles in the pixel font, scaled by [PixelBuddyStyle.textScale]. */
object PixelText {
    @Composable
    @ReadOnlyComposable
    fun style(
        sp: Float,
        color: Color = PixelBuddyColors.Text,
    ): TextStyle =
        TextStyle(
            fontFamily = PixelFont,
            fontSize = pixelTextSize(sp * PixelBuddyTheme.style.textScale),
            color = color,
        )

    /** App names in lists. */
    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = style(20f)

    /** Secondary lines, info line. */
    val small: TextStyle
        @Composable @ReadOnlyComposable
        get() = style(14f, PixelBuddyColors.TextMuted)

    val title: TextStyle
        @Composable @ReadOnlyComposable
        get() = style(26f)
}
