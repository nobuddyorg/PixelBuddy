package io.github.nobuddyorg.pixelbuddy.core.designsystem

import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TypographyTest {
    @Test
    fun `text sizes land on the font's 11 px grid`() {
        listOf(1f, 2.625f, 3f, 4f).forEach { density ->
            listOf(1f, 1.3f, 2f).forEach { fontScale ->
                listOf(8f, 14f, 20f, 26f).forEach { sp ->
                    val px = snapToPixelGrid(sp, density, fontScale) * density * fontScale
                    assertEquals(0, px.roundToInt() % PIXEL_FONT_GRID_PX, "sp=$sp density=$density scale=$fontScale")
                }
            }
        }
    }

    @Test
    fun `snapping stays close to the requested size and follows the font scale`() {
        val normal = snapToPixelGrid(20f, 2.625f, 1f)
        val large = snapToPixelGrid(20f, 2.625f, 1.3f)

        assertEquals(20f, normal * 1f, absoluteTolerance = 2.1f)
        assertTrue(large * 1.3f > normal, "large font scale renders bigger")
        assertEquals(11f / 2.625f, snapToPixelGrid(0.1f, 2.625f, 1f), absoluteTolerance = 0.001f)
    }

    @Test
    fun `pixel font covers German and the symbols the UI uses`() {
        val typeface = ResourcesCompat.getFont(ApplicationProvider.getApplicationContext(), R.font.departure_mono)
        val paint = Paint().apply { this.typeface = typeface }

        "AaZzÄäÖöÜüß„“0123456789%°:►".forEach { char ->
            assertTrue(paint.hasGlyph(char.toString()), "missing glyph '$char'")
        }
    }
}
