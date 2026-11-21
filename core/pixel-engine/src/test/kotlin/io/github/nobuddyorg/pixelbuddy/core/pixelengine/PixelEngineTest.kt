package io.github.nobuddyorg.pixelbuddy.core.pixelengine

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PixelEngineTest {
    private fun opaqueColors(image: PixelImage) = image.pixels.filter { Argb.alpha(it) == 255 }.toSet()

    @ParameterizedTest
    @ValueSource(ints = [12, 16, 24, 32])
    fun `output size equals the grid`(grid: Int) {
        val style = IconStyle(grid = grid)

        val output = PixelEngine.render(Fixtures.roundIcon(style.sourceSize), style)

        assertEquals(grid, output.width)
        assertEquals(grid, output.height)
    }

    @ParameterizedTest
    @EnumSource(IconPreset::class)
    fun `rendering is deterministic`(preset: IconPreset) {
        val source = Fixtures.roundIcon(preset.style.sourceSize)

        assertEquals(PixelEngine.render(source, preset.style), PixelEngine.render(source, preset.style))
    }

    @ParameterizedTest
    @EnumSource(IconPreset::class)
    fun `fully transparent input stays transparent`(preset: IconPreset) {
        val size = preset.style.sourceSize

        val output = PixelEngine.render(PixelImage(size, size, IntArray(size * size)), preset.style)

        assertTrue(output.pixels.all { it == 0 })
    }

    @Test
    fun `transparent areas stay transparent apart from the outline`() {
        val style = IconStyle(grid = 16, outline = false)
        val source = Fixtures.quadrants(style.sourceSize)

        val output = PixelEngine.render(source, style)

        // Border of 2 grid pixels (source border = size / 8 = 8 px = 2 grid pixels).
        for (i in 0 until 16) {
            assertEquals(0, output[i, 0])
            assertEquals(0, output[0, i])
            assertEquals(0, output[15, i])
        }
    }

    @Test
    fun `fixed palette at full intensity only uses palette colours`() {
        listOf(Palettes.Retro16, Palettes.Pocket, Palettes.Neon).forEach { palette ->
            listOf(false, true).forEach { dithering ->
                val style = IconStyle(grid = 24, palette = palette, dithering = dithering)

                val output = PixelEngine.render(Fixtures.roundIcon(style.sourceSize), style)

                assertTrue(palette.colors.containsAll(opaqueColors(output)), "${palette.name} dithering=$dithering")
            }
        }
    }

    @Test
    fun `adaptive palette uses at most the requested number of colours`() {
        listOf(2, 4, 8, 16).forEach { n ->
            val style = IconStyle(grid = 32, palette = Palette.Adaptive(n))

            val output = PixelEngine.render(Fixtures.roundIcon(style.sourceSize), style)

            assertTrue(opaqueColors(output).size <= n, "n=$n gave ${opaqueColors(output).size}")
        }
    }

    @Test
    fun `monochrome tint uses its shades only`() {
        val palette = Palette.MonochromeTint(0xFF39FF14.toInt(), shades = 4)
        val style = IconStyle(grid = 16, palette = palette)

        val output = PixelEngine.render(Fixtures.roundIcon(style.sourceSize), style)

        val shades = PixelEngine.shadesOf(palette)
        assertEquals(4, shades.size)
        assertEquals(0xFF39FF14.toInt(), shades.last())
        assertTrue(shades.containsAll(opaqueColors(output)))
        assertTrue(opaqueColors(output).size > 1)
    }

    @Test
    fun `intensity 0 keeps the averaged original colours`() {
        val source = Fixtures.roundIcon(64)
        val original = PixelEngine.render(source, IconStyle(grid = 16, palette = Palette.Original))

        val blended = PixelEngine.render(source, IconStyle(grid = 16, palette = Palettes.Neon, intensity = 0f))

        assertEquals(original, blended)
    }

    @Test
    fun `intensity between 0 and 1 blends towards the palette`() {
        val source = Fixtures.quadrants(64)
        val red = 0xFFE0413A.toInt()
        val style = IconStyle(grid = 16, palette = Palette.Fixed("black", listOf(0xFF000000.toInt())), intensity = 0.5f)

        val output = PixelEngine.render(source, style)

        // Top-left quadrant is red: halfway to black.
        assertEquals(Argb.lerp(red, 0xFF000000.toInt(), 0.5f), output[4, 4])
    }

    @Test
    fun `dithering mixes neighbouring palette colours`() {
        val grey = 0xFF808080.toInt()
        val source = PixelImage(64, 64, IntArray(64 * 64) { grey })
        val palette =
            Palette.Fixed(
                "bw",
                listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF6E6E6E.toInt(), 0xFF929292.toInt()),
            )

        val flat = PixelEngine.render(source, IconStyle(grid = 16, palette = palette))
        val dithered = PixelEngine.render(source, IconStyle(grid = 16, palette = palette, dithering = true))

        assertEquals(1, opaqueColors(flat).size)
        assertTrue(opaqueColors(dithered).size > 1)
    }

    @Test
    fun `outline surrounds the shape`() {
        val outline = 0xFF07081A.toInt()
        val style = IconStyle(grid = 16, palette = Palette.Original, outline = true, outlineColor = outline)

        val output = PixelEngine.render(Fixtures.quadrants(style.sourceSize), style)

        // Shape spans grid pixels 2..13; the outline is the ring at 1 and 14, corners stay empty.
        assertEquals(outline, output[1, 5])
        assertEquals(outline, output[14, 5])
        assertEquals(outline, output[5, 1])
        assertEquals(0, output[1, 1])
        assertEquals(0, output[0, 5])
    }

    @Test
    fun `downscale uses premultiplied alpha`() {
        // Each 2x2 block: one opaque red pixel, three transparent black ones.
        val pixels = IntArray(4 * 4) { i -> if ((i % 4) % 2 == 0 && (i / 4) % 2 == 0) 0xFFFF0000.toInt() else 0 }

        val small = PixelEngine.downscale(PixelImage(4, 4, pixels), 2)

        assertTrue(small.pixels.all { Argb.red(it) == 255 && Argb.green(it) == 0 && Argb.alpha(it) == 64 })
    }

    @Test
    fun `alpha threshold makes edges crisp`() {
        val image = PixelImage(3, 1, intArrayOf(0x7F112233, 0x80112233.toInt(), 0xFF112233.toInt()))

        val crisp = PixelEngine.applyAlphaThreshold(image, 128)

        assertEquals(listOf(0, 0xFF112233.toInt(), 0xFF112233.toInt()), crisp.pixels.toList())
    }

    @Test
    fun `invalid input is rejected`() {
        assertFailsWith<IllegalArgumentException> { PixelEngine.render(Fixtures.roundIcon(50), IconStyle(grid = 16)) }
        assertFailsWith<IllegalArgumentException> { IconStyle(grid = 20) }
        assertFailsWith<IllegalArgumentException> { IconStyle(intensity = 1.5f) }
        assertFailsWith<IllegalArgumentException> { IconStyle(alphaThreshold = 0) }
        assertFailsWith<IllegalArgumentException> { Palette.Adaptive(1) }
        assertFailsWith<IllegalArgumentException> { Palette.Fixed("empty", emptyList()) }
        assertFailsWith<IllegalArgumentException> { Palette.MonochromeTint(0, shades = 1) }
        assertFailsWith<IllegalArgumentException> { PixelImage(2, 2, IntArray(3)) }
        assertFailsWith<IllegalArgumentException> { PixelImage(0, 2, IntArray(0)) }
    }

    @Test
    fun `presets cover the planned looks and default to 16-bit`() {
        assertEquals(listOf("EightBit", "SixteenBit", "Pocket", "Neon", "Mono"), IconPreset.entries.map { it.name })
        assertEquals(IconPreset.SixteenBit, IconPreset.Default)
        assertTrue(IconPreset.SixteenBit.style.outline)
        assertEquals(4, (IconPreset.Pocket.style.palette as Palette.Fixed).colors.size)
    }

    @Test
    fun `a 24 x 24 icon renders in well under a millisecond`() {
        val style = IconPreset.SixteenBit.style
        val source = Fixtures.roundIcon(style.sourceSize)
        repeat(500) { PixelEngine.render(source, style) }

        // Best of several batches, so a busy build machine doesn't make the test flaky.
        val runs = 200
        val perIconMs =
            (1..5).minOf {
                val start = System.nanoTime()
                repeat(runs) { PixelEngine.render(source, style) }
                (System.nanoTime() - start) / 1e6 / runs
            }
        println("Pixel engine: %.3f ms per 24x24 icon".format(perIconMs))

        assertTrue(perIconMs < 1.0, "took $perIconMs ms per icon")
    }
}
