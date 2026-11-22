package io.github.nobuddyorg.pixelbuddy.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.test.assertTrue

/** WCAG contrast of the colours text is drawn in (#15): 4.5:1 for normal text. */
class ContrastTest {
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private fun assertReadable(
        text: Color,
        background: Color,
        name: String,
    ) {
        val ratio = contrast(text, background)
        assertTrue(ratio >= 4.5f, "$name: contrast $ratio is below 4.5")
    }

    @Test
    fun `text on the dark background is readable`() {
        assertReadable(PixelBuddyColors.Text, PixelBuddyColors.Navy, "text")
        assertReadable(PixelBuddyColors.TextMuted, PixelBuddyColors.Navy, "muted text")
        assertReadable(PixelBuddyColors.HpLow, PixelBuddyColors.Navy, "low battery")
        assertReadable(PixelBuddyColors.HpFull, PixelBuddyColors.Navy, "full battery")
    }

    @Test
    fun `accent colours are readable on the dark background`() {
        listOf(
            PixelBuddyColors.Orange,
            Color(0xFFFF6FAE),
            Color(0xFFFFD23F),
            Color(0xFF7CE38B),
            Color(0xFF4FD6E8),
            Color(0xFFB69CFF),
        ).forEach { assertReadable(it, PixelBuddyColors.Navy, "accent $it") }
    }

    @Test
    fun `the bright end of the sky needs the text shadow`() {
        // Light text alone is too faint on the orange at the bottom of the default sky ...
        assertTrue(contrast(PixelBuddyColors.Text, PixelBuddyColors.Orange) < 4.5f)
        // ... so every letter has a dark drop shadow, which it stands out against.
        assertReadable(PixelBuddyColors.Text, PixelBuddyColors.Shadow, "text on its shadow")
        assertReadable(PixelBuddyColors.TextMuted, PixelBuddyColors.Shadow, "muted text on its shadow")
    }
}
