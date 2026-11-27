package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconShape
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconSourceRendererTest {
    private val red = Color.RED
    private val blue = Color.BLUE

    /** Red background, blue square in the middle third of the 108 dp canvas. */
    private fun adaptive(monochrome: Boolean = false) =
        if (monochrome) {
            AdaptiveIconDrawable(
                ColorDrawable(red),
                InsetDrawable(ColorDrawable(blue), 1f / 3),
                InsetDrawable(
                    ColorDrawable(Color.BLACK),
                    1f / 3,
                ),
            )
        } else {
            AdaptiveIconDrawable(ColorDrawable(red), InsetDrawable(ColorDrawable(blue), 1f / 3))
        }

    @Test
    fun `adaptive icons are drawn layer by layer without the system mask`() {
        val image = IconSourceRenderer.render(adaptive(), 96)

        // The full square is covered: no squircle cut-outs in the corners.
        assertEquals(red, image[0, 0])
        assertEquals(red, image[95, 95])
        assertEquals(blue, image[48, 48])
    }

    @Test
    fun `the visible 72 dp of an adaptive icon fill the output`() {
        val image = IconSourceRenderer.render(adaptive(), 96)

        // The blue middle third of 108 dp is 36 dp = half of the visible 72 dp: pixels 24..71.
        assertEquals(red, image[22, 48])
        assertEquals(blue, image[26, 48])
        assertEquals(blue, image[69, 48])
        assertEquals(red, image[73, 48])
    }

    @Test
    fun `foreground layer and free-standing shape leave out the background`() {
        listOf(
            IconSourceRenderer.render(adaptive(), 96, IconLayer.Foreground),
            IconSourceRenderer.render(adaptive(), 96, IconLayer.Full, IconShape.FreeStanding),
        ).forEach { image ->
            assertEquals(0, image[0, 0])
            assertEquals(blue, image[48, 48])
        }
    }

    @Test
    fun `monochrome layer is drawn in white and falls back to the foreground`() {
        val mono = IconSourceRenderer.render(adaptive(monochrome = true), 96, IconLayer.Monochrome)
        val fallback = IconSourceRenderer.render(adaptive(), 96, IconLayer.Monochrome)

        assertEquals(IconSourceRenderer.MONOCHROME_TINT, mono[48, 48])
        assertEquals(0, mono[0, 0])
        assertEquals(blue, fallback[48, 48])
    }

    @Test
    fun `neon icons are traced from the monochrome layer with the full icon's colours`() {
        val (shapes, colours) = IconSourceRenderer.neonSources(adaptive(monochrome = true), 96, IconLayer.Full)

        assertTrue(IconSourceRenderer.hasMonochrome(adaptive(monochrome = true)))
        assertEquals(IconSourceRenderer.MONOCHROME_TINT, shapes[48, 48])
        assertEquals(0, shapes[0, 0])
        assertEquals(red, colours!![0, 0])
        assertEquals(blue, colours[48, 48])
    }

    @Test
    fun `neon icons without a monochrome layer are traced from the foreground`() {
        val (shapes, colours) = IconSourceRenderer.neonSources(adaptive(), 96, IconLayer.Monochrome)

        assertFalse(IconSourceRenderer.hasMonochrome(adaptive()))
        assertEquals(0, shapes[0, 0])
        assertEquals(blue, shapes[48, 48])
        assertEquals(red, colours!![0, 0])
    }

    @Test
    fun `neon icons with an empty foreground are traced whole`() {
        val plateOnly = AdaptiveIconDrawable(ColorDrawable(red), ColorDrawable(Color.TRANSPARENT))
        val (shapes, colours) = IconSourceRenderer.neonSources(plateOnly, 96, IconLayer.Full)

        assertEquals(red, shapes[0, 0])
        assertNull(colours)
    }

    @Test
    fun `neon icons can be traced from the foreground, and legacy icons as they are`() {
        val (shapes, colours) = IconSourceRenderer.neonSources(adaptive(monochrome = true), 96, IconLayer.Foreground)
        assertEquals(0, shapes[0, 0])
        assertEquals(blue, shapes[48, 48])
        assertEquals(red, colours!![0, 0])

        val legacy = InsetDrawable(ColorDrawable(blue), 0.25f)
        val (image, none) = IconSourceRenderer.neonSources(legacy, 96, IconLayer.Full)
        assertEquals(IconSourceRenderer.render(legacy, 96), image)
        assertNull(none)
        assertFalse(IconSourceRenderer.hasMonochrome(legacy))
    }

    @Test
    fun `legacy icons are drawn as they are and never masked`() {
        val legacy = InsetDrawable(ColorDrawable(blue), 0.25f)

        val image = IconSourceRenderer.render(legacy, 96)

        assertEquals(0, image[0, 0])
        assertEquals(blue, image[48, 48])
        assertEquals(IconShape.FreeStanding, IconSourceRenderer.shapeFor(legacy, IconShape.Masked))
        assertEquals(IconShape.Masked, IconSourceRenderer.shapeFor(adaptive(), IconShape.Masked))
    }

    @Test
    fun `transparent icons stay transparent`() {
        val image = IconSourceRenderer.render(ColorDrawable(Color.TRANSPARENT), 64)

        assertTrue(image.pixels.all { it == 0 })
    }

    @Test
    fun `fingerprint follows the look of the icon`() {
        assertEquals(IconSourceRenderer.fingerprint(adaptive()), IconSourceRenderer.fingerprint(adaptive()))
        assertNotEquals(
            IconSourceRenderer.fingerprint(adaptive()),
            IconSourceRenderer.fingerprint(
                AdaptiveIconDrawable(
                    ColorDrawable(Color.GREEN),
                    InsetDrawable(
                        ColorDrawable(blue),
                        1f / 3,
                    ),
                ),
            ),
        )
    }
}
