package io.github.nobuddyorg.pixelbuddy.core.pixelengine

import io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline.IconOutline
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline.OutlineColours
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline.Synth
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconRendererTest {
    private fun PixelImage.map(transform: (Int) -> Int) =
        PixelImage(width, height, IntArray(pixels.size) { transform(pixels[it]) })

    private val neon = IconStyle(neon = NeonStyle())
    private val pixelatedNeon = IconStyle(grid = 16, neon = NeonStyle(pixelated = true))

    @Test
    fun `pixel styles go through the pixel engine at grid size`() {
        val style = IconPreset.SixteenBit.style
        val source = Fixtures.roundIcon(style.sourceSize)
        assertEquals(PixelEngine.render(source, style), IconRenderer.render(source, style, sizePx = 144))
        assertEquals(24, IconRenderer.outputSize(style, 144))
        assertFalse(style.smooth)
    }

    @Test
    fun `smooth neon icons are drawn at the size on screen`() {
        assertTrue(neon.smooth)
        assertEquals(IconStyle.NEON_SOURCE_SIZE, neon.sourceSize)
        assertEquals(130, IconRenderer.outputSize(neon, 130))
        val image = IconRenderer.render(Synth.circleInSquare(neon.sourceSize), neon, 130)
        assertEquals(130, image.width)
        assertEquals(0, image[0, 0] ushr 24)
        // soft edges and glow: partly transparent pixels
        assertTrue(image.pixels.any { (it ushr 24) in 1..254 })
    }

    @Test
    fun `pixelated neon icons are crisp at grid size`() {
        assertFalse(pixelatedNeon.smooth)
        assertEquals(16, IconRenderer.outputSize(pixelatedNeon, 130))
        val image = IconRenderer.render(Synth.circleInSquare(pixelatedNeon.sourceSize), pixelatedNeon, 130)
        assertEquals(16, image.width)
        assertTrue(image.pixels.all { (it ushr 24) == 0 || (it ushr 24) == 255 })
        assertTrue(image.pixels.count { (it ushr 24) == 255 } > 16, "strokes are drawn")
    }

    @Test
    fun `strokes can take their colours from another picture of the icon`() {
        val size = neon.sourceSize
        // the "monochrome layer": white disc; the full icon: the same disc in blue
        val shapes = Synth.circleInSquare(size).map { c -> if (c ushr 24 == 0) 0 else 0xFFFFFFFF.toInt() }
        val coloured = Synth.circleInSquare(size).map { c -> if (c ushr 24 == 0) 0 else 0xFF2050E0.toInt() }
        val image = IconRenderer.render(shapes, neon.copy(neon = NeonStyle(OutlineColours.Single)), 120, coloured)
        val strokes = image.pixels.filter { (it ushr 24) == 255 }
        assertTrue(strokes.isNotEmpty())
        assertTrue(strokes.count { (it and 0xFF) > (it shr 16 and 0xFF) } > strokes.size / 2, "blue, not white")
    }

    @Test
    fun `a glyph on a plate in the colour picture still gives the plate colour`() {
        val glyph = Synth.plateWithGlyph(192)
        // only the light glyph, as a monochrome layer would have it
        val shapes = glyph.map { c -> if ((c and 0xFF) > 200 && (c shr 16 and 0xFF) > 200) c else 0 }
        assertNull(IconOutline.trace(shapes).plateColour)
        val outline = IconOutline.trace(shapes, colours = glyph)
        assertTrue(outline.onPlate)
        assertTrue(outline.contours.isNotEmpty())
    }

    @Test
    fun `colours from a picture without paint under the shapes fall back to the shapes`() {
        val shapes = Synth.circleInSquare(128)
        val outline = IconOutline.trace(shapes, colours = Synth.transparent(128))
        assertEquals(IconOutline.trace(shapes).contours.size, outline.contours.size)
        assertTrue(IconOutline.render(outline, 64, OutlineColours.Single).pixels.any { (it ushr 24) == 255 })
    }

    @Test
    fun `neon presets`() {
        assertEquals(
            OutlineColours.Sampled,
            IconPreset.NeonLines.style.neon
                ?.colours,
        )
        assertEquals(
            OutlineColours.Single,
            IconPreset.NeonSingle.style.neon
                ?.colours,
        )
        assertTrue(IconPreset.NeonLines.style.smooth)
    }
}
