package io.github.nobuddyorg.pixelbuddy.core.pixelengine

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HelpersTest {
    @Test
    fun `median cut returns the unique colours when there are few`() {
        val colors = listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())

        assertEquals(listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), MedianCut.palette(colors, 4))
    }

    @Test
    fun `median cut splits along the widest channel`() {
        val reds = (0..255 step 5).map { Argb.of(255, it, 10, 10) }

        val palette = MedianCut.palette(reds, 2)

        assertEquals(2, palette.size)
        assertTrue(Argb.red(palette[0]) < 128 && Argb.red(palette[1]) >= 128)
        assertTrue(palette.all { Argb.green(it) == 10 && Argb.blue(it) == 10 })
    }

    @Test
    fun `median cut is deterministic regardless of input order`() {
        val colors = (0 until 500).map { Argb.of(255, it * 7 % 256, it * 13 % 256, it * 29 % 256) }

        assertEquals(MedianCut.palette(colors, 8), MedianCut.palette(colors.reversed(), 8))
    }

    @Test
    fun `nearest picks the closest colour`() {
        val palette = listOf(0xFF000000.toInt(), 0xFFFF0000.toInt(), 0xFFFFFFFF.toInt())

        assertEquals(0xFFFF0000.toInt(), PixelEngine.nearest(palette, 0xFFE01010.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), PixelEngine.nearest(palette, 0xFFEEEEEE.toInt()))
    }

    @Test
    fun `bayer offsets are centred and distinct`() {
        val offsets = (0 until 4).flatMap { y -> (0 until 4).map { x -> PixelEngine.bayerOffset(x, y) } }

        assertEquals(16, offsets.distinct().size)
        assertTrue(offsets.all { it > -0.5f && it < 0.5f })
        assertEquals(0f, offsets.sum(), absoluteTolerance = 1e-5f)
        assertEquals(PixelEngine.bayerOffset(1, 2), PixelEngine.bayerOffset(5, 6))
    }

    @Test
    fun `argb helpers`() {
        val c = Argb.of(300, -5, 128, 255)
        assertEquals(255, Argb.alpha(c))
        assertEquals(0, Argb.red(c))
        assertEquals(128, Argb.green(c))
        assertEquals(255, Argb.luma(0xFFFFFFFF.toInt()))
        assertEquals(0, Argb.luma(0xFF000000.toInt()))
        assertEquals(0, Argb.distance(c, c))
        assertEquals(Argb.of(255, 128, 128, 128), Argb.lerp(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
    }

    @Test
    fun `pixel image equality and access`() {
        val a = PixelImage(2, 1, intArrayOf(1, 2))
        val b = PixelImage(2, 1, intArrayOf(1, 2))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, PixelImage(1, 2, intArrayOf(1, 2)))
        assertNotEquals<Any>(a, "image")
        assertEquals(2, a[1, 0])
        assertEquals("PixelImage(2x1)", a.toString())
    }

    @Test
    fun `size checks use width times height`() {
        assertFailsWith<IllegalArgumentException> { PixelImage(4, 2, IntArray(2)) }
        assertEquals(8, PixelImage(4, 2, IntArray(8)).pixels.size)
    }

    @Test
    fun `different images have different hash codes`() {
        val base = PixelImage(2, 2, intArrayOf(1, 2, 3, 4))

        assertNotEquals(base.hashCode(), PixelImage(2, 2, intArrayOf(1, 2, 3, 5)).hashCode())
        assertNotEquals(base.hashCode(), PixelImage(4, 1, intArrayOf(1, 2, 3, 4)).hashCode())
        assertNotEquals(0, base.hashCode())
    }

    @Test
    fun `style and palette limits are inclusive`() {
        IconStyle(intensity = 0f)
        IconStyle(intensity = 1f)
        IconStyle(alphaThreshold = 1)
        IconStyle(alphaThreshold = 255)
        Palette.Adaptive(2)
        Palette.Adaptive(256)
        assertFailsWith<IllegalArgumentException> { Palette.Adaptive(257) }
        assertFailsWith<IllegalArgumentException> { IconStyle(alphaThreshold = 256) }
        assertEquals("Neon", Palettes.Neon.name)
    }

    @Test
    fun `nearest prefers the first of equally close colours`() {
        val palette = listOf(0xFF000000.toInt(), 0xFF202020.toInt())

        assertEquals(0xFF000000.toInt(), PixelEngine.nearest(palette, 0xFF101010.toInt()))
    }

    @Test
    fun `outline reaches the image edges`() {
        // A single opaque pixel in the corner: outline right of and below it.
        val image = PixelImage(3, 3, IntArray(9).also { it[0] = 0xFFFFFFFF.toInt() })

        val outlined = PixelEngine.addOutline(image, 0xFF000000.toInt())

        assertEquals(listOf(0, 1, 3), outlined.pixels.indices.filter { outlined.pixels[it] != 0 })
    }

    @Test
    fun `source size is four times the grid`() {
        assertEquals(96, IconStyle(grid = 24).sourceSize)
    }
}
