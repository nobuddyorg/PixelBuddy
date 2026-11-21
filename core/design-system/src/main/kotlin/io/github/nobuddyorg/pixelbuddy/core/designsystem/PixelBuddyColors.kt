package io.github.nobuddyorg.pixelbuddy.core.designsystem

import androidx.compose.ui.graphics.Color

/** Colour tokens of the retro look: a navy → purple → orange sky with light text. */
object PixelBuddyColors {
    val Navy: Color = Color(0xFF141833)
    val Purple: Color = Color(0xFF3D2A6B)
    val Orange: Color = Color(0xFFFF8A3D)

    /** Lower end of the sky gradient, behind the bottom of the app list. */
    val Dusk: Color = Color(0xFFB4506A)

    val Text: Color = Color(0xFFF2EEFF)
    val TextMuted: Color = Color(0xFFB4ADCF)

    /** 1 px drop shadow under pixel text. */
    val Shadow: Color = Color(0xFF07081A)

    val HpFull: Color = Color(0xFF7CE38B)
    val HpLow: Color = Color(0xFFFF4D5E)

    /** Default accent; configurable in the settings (#12). */
    val DefaultAccent: Color = Orange

    /** Sky gradient from top to bottom. */
    val Sky: List<Color> = listOf(Navy, Purple, Dusk, Orange)
}
