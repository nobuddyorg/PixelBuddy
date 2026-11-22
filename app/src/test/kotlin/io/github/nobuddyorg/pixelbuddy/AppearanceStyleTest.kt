package io.github.nobuddyorg.pixelbuddy

import androidx.compose.ui.graphics.Color
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Accent
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SizeStep
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Sky
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyStyle
import org.junit.Test
import kotlin.test.assertEquals

class AppearanceStyleTest {
    @Test
    fun `the defaults are the design system's defaults`() {
        assertEquals(PixelBuddyStyle(), Appearance().toStyle())
    }

    @Test
    fun `every appearance setting reaches the style`() {
        val style =
            Appearance(
                accent = Accent.Green,
                scanlines = true,
                sky = Sky.Night,
                dithering = false,
                textSize = SizeStep.Large,
            ).toStyle()

        assertEquals(Color(0xFF7CE38B), style.accent)
        assertEquals(true, style.scanlines)
        assertEquals(Color(0xFF0B0E24), style.sky.first())
        assertEquals(false, style.dithering)
        assertEquals(1.2f, style.textScale)
    }
}
