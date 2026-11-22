package io.github.nobuddyorg.pixelbuddy.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PixelBuddyStyle(
    val accent: Color = PixelBuddyColors.DefaultAccent,
    /** Faint CRT scanlines over the background; off by default. */
    val scanlines: Boolean = false,
    /** Sky gradient, top to bottom. */
    val sky: List<Color> = PixelBuddyColors.Sky,
    /** Dithered bands (pixel look) or a smooth gradient. */
    val dithering: Boolean = true,
    /** Factor for [PixelText] sizes, on top of the system font size. */
    val textScale: Float = 1f,
)

private val LocalPixelBuddyStyle = staticCompositionLocalOf { PixelBuddyStyle() }

/** Provides the user's style (accent colour, sky, scanlines, text size) to all PixelBuddy components. */
@Composable
fun PixelBuddyTheme(
    style: PixelBuddyStyle = PixelBuddyStyle(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalPixelBuddyStyle provides style, content = content)
}

object PixelBuddyTheme {
    val style: PixelBuddyStyle
        @Composable @ReadOnlyComposable
        get() = LocalPixelBuddyStyle.current
}
