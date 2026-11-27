package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import io.github.nobuddyorg.pixelbuddy.core.pixelengine.Fixtures
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The prototype's tests (`tools/icon-outline/test_outline.py`) on the same synthetic icons. */
class IconOutlineTest {
    private fun Outline.outer() = contours.filter { it.outer }

    private fun Outline.inner() = contours.filterNot { it.outer }

    private fun PixelImage.maxAlpha() = pixels.maxOf { it ushr 24 }

    @Test
    fun `same input gives identical output`() {
        val icon = Synth.logo()
        assertEquals(IconOutline.render(IconOutline.trace(icon), 160), IconOutline.render(IconOutline.trace(icon), 160))
    }

    @Test
    fun `transparent input gives transparent output`() {
        val outline = IconOutline.trace(Synth.transparent())
        assertEquals(emptyList(), outline.contours)
        assertEquals(0, IconOutline.render(outline, 120).maxAlpha())
    }

    @Test
    fun `alpha only where strokes and glow are`() {
        val image = IconOutline.render(IconOutline.trace(Synth.circleInSquare()), 200, OutlineColours.Single)
        for (y in 0 until 6) for (x in 0 until 6) assertEquals(0, image[x, y] ushr 24, "corner pixel $x,$y")
        assertTrue(image.maxAlpha() > 200)
    }

    @Test
    fun `circle in a square has the outer and one internal contour`() {
        val outline = IconOutline.trace(Synth.circleInSquare())
        assertEquals(1, outline.outer().size)
        assertEquals(1, outline.inner().size)
        assertTrue(outline.inner().single().closed)
    }

    @Test
    fun `a smooth gradient has no internal contour`() {
        val outline = IconOutline.trace(Synth.gradientDisc())
        assertEquals(1, outline.outer().size)
        assertEquals(emptyList(), outline.inner())
    }

    @Test
    fun `two nearby parallel edges become one stroke`() {
        assertEquals(1, IconOutline.trace(Synth.parallelEdges()).inner().size)
    }

    @Test
    fun `a white glyph on a plate is outlined in the plate colour`() {
        val outline = IconOutline.trace(Synth.plateWithGlyph())
        assertTrue(outline.onPlate)
        val plate = outline.plateColour!!
        assertTrue(plate[2] > plate[0], "blue plate")
        assertTrue(outline.contours.isNotEmpty())
    }

    @Test
    fun `opaque icons use the border as the background`() {
        assertEquals(1, IconOutline.trace(Synth.opaqueIcon()).outer().size)
    }

    @Test
    fun `non-square, small and large inputs`() {
        val square = Synth.circleInSquare(200)
        val wide = PixelImage(160, 200, IntArray(160 * 200) { square[it % 160 + 20, it / 160] })
        for (icon in listOf(wide, Synth.circleInSquare(48), Synth.circleInSquare(1024))) {
            assertEquals(1, IconOutline.trace(icon).outer().size, "$icon")
        }
        assertEquals(64, WorkImage.of(wide, 64).size)
    }

    @Test
    fun `outer strokes are at least as thick as internal ones`() {
        val p = OutlineParams()
        assertTrue(p.outerWidth >= p.innerWidth)
    }

    @Test
    fun `dominant colour`() {
        val image = WorkImage.of(Synth.circleInSquare(), 96)
        val colour = Colouring.dominantColour(image, BooleanArray(image.alpha.size) { image.alpha[it] > 0.5 })
        // the orange plate covers more than the blue disc
        assertTrue(colour[0] > 0.8, colour.contentToString())
        assertTrue(colour[2] < 0.4, colour.contentToString())
    }

    @ParameterizedTest
    @EnumSource(OutlineColours::class)
    fun `all colour modes render`(mode: OutlineColours) {
        val image = IconOutline.render(IconOutline.trace(Synth.logo()), 120, mode)
        assertEquals(120, image.width)
        assertTrue(image.maxAlpha() > 200)
    }

    @Test
    fun `the logo keeps its internal details`() {
        val outline = IconOutline.trace(Synth.logo())
        assertTrue(outline.inner().size >= 5, "${outline.contours}")
        assertTrue(outline.outer().isNotEmpty())
        assertTrue(outline.contours.zipWithNext().all { (a, b) -> a.length >= b.length }, "longest first")
    }

    @Test
    fun `contours stay inside the work area and render inside the padding`() {
        val outline = IconOutline.trace(Synth.circleInSquare())
        val size = outline.params.workSize.toDouble()
        outline.contours.forEach { c ->
            (0 until c.size).forEach { assertTrue(c.x(it) in 0.0..size && c.y(it) in 0.0..size) }
        }
        val image = IconOutline.render(outline, 100, glow = false)
        // without glow nothing reaches the outer 5 % (padding is 10 %)
        for (i in 0 until 100) {
            assertEquals(0, image[i, 0] ushr 24)
            assertEquals(0, image[0, i] ushr 24)
        }
    }

    @Test
    fun `glow adds soft alpha around the strokes`() {
        val outline = IconOutline.trace(Synth.gradientDisc())
        val plain = IconOutline.render(outline, 120, glow = false)
        val glowing = IconOutline.render(outline, 120)
        assertTrue(glowing.pixels.count { it ushr 24 > 0 } > plain.pixels.count { it ushr 24 > 0 })
        // the glow is tinted, never white: the halo outside the stroke keeps the stroke's hue
        val halo = glowing.pixels.filterIndexed { i, c -> c ushr 24 in 10..80 && plain.pixels[i] == 0 }
        assertTrue(halo.isNotEmpty())
        assertTrue(halo.all { (it shr 16 and 0xFF) > (it and 0xFF) || (it shr 8 and 0xFF) < 230 })
    }

    @Test
    fun `single colour strokes are one colour`() {
        val outline = IconOutline.trace(Synth.logo())
        val colours = Colouring.colours(outline, OutlineColours.Single, outline.params)
        val distinct = colours.flatMap { c -> c.r.indices.map { Triple(c.r[it], c.g[it], c.b[it]) } }.toSet()
        assertEquals(1, distinct.size)
    }

    @Test
    fun `sampled colours follow the icon, sweep differs`() {
        val outline = IconOutline.trace(Synth.circleInSquare())
        val sampled = Colouring.colours(outline, OutlineColours.Sampled, outline.params)
        val inner = outline.contours.indexOfFirst { !it.outer }
        // the internal border takes the colour of the smaller region: the blue disc
        assertTrue(sampled[inner].b.average() > sampled[inner].r.average())
        val outer = outline.contours.indexOfFirst { it.outer }
        assertTrue(sampled[outer].r.average() > sampled[outer].b.average(), "orange plate")
        val sweep = Colouring.colours(outline, OutlineColours.Sweep, outline.params)
        assertNotEquals(sampled[outer].r.toList(), sweep[outer].r.toList())
    }

    @Test
    fun `a plate glyph uses the plate colour in every mode`() {
        val outline = IconOutline.trace(Synth.plateWithGlyph())
        OutlineColours.entries.forEach { mode ->
            val c = Colouring.colours(outline, mode, outline.params).first()
            assertTrue(c.b[0] > c.r[0], "$mode")
        }
    }

    @Test
    fun `params are validated`() {
        assertFailsWith<IllegalArgumentException> { OutlineParams(workSize = 8) }
        assertFailsWith<IllegalArgumentException> { OutlineParams(clusters = 0) }
        assertFailsWith<IllegalArgumentException> { OutlineParams(outerWidth = 0.0) }
        assertFailsWith<IllegalArgumentException> { OutlineParams(padding = 0.5) }
        assertFailsWith<IllegalArgumentException> { IconOutline.render(IconOutline.trace(Synth.transparent()), 0) }
    }

    @Test
    fun `a smaller work size still finds the same structure`() {
        val outline = IconOutline.trace(Synth.circleInSquare(), OutlineParams(workSize = 96))
        assertEquals(1, outline.outer().size)
        assertEquals(1, outline.inner().size)
        assertEquals(
            "OutlineContour(outer, ${outline.outer()[0].size} points, closed=true)",
            outline.outer()[0].toString(),
        )
    }

    private val record = System.getProperty("pixelengine.record") == "true"

    @ParameterizedTest
    @EnumSource(OutlineColours::class)
    fun `outlines match their golden images`(mode: OutlineColours) {
        listOf("circle_in_square", "logo", "plate_with_glyph", "opaque_icon").forEach { name ->
            val output = IconOutline.render(IconOutline.trace(Synth.all.getValue(name)()), 144, mode)
            val golden = File("src/test/resources/golden/outline/${name}_${mode.name}.png")
            if (record) {
                Fixtures.writePng(output, golden)
            } else {
                assertTrue(golden.isFile, "missing golden $golden; record with -Dpixelengine.record=true")
                assertEquals(Fixtures.readPng(golden), output, "$name $mode differs")
            }
        }
    }

    // Timing test, not run under mutation testing (excludedGroups in build.gradle.kts). The real budget is on the
    // phone (#29: about 20 ms per icon); this only catches big regressions on the build machine.
    @Test
    @Tag("performance")
    fun `tracing and rendering an icon stays fast`() {
        val icons =
            listOf(Synth.logo(192), Synth.circleInSquare(192), Synth.gradientDisc(192), Synth.parallelEdges(192))
        repeat(3) { icons.forEach { IconOutline.render(IconOutline.trace(it), 144) } }
        // Each icon's fastest of several runs: a busy build machine (other modules' tests run alongside) slows single
        // runs, not the best one, so the test doesn't fail at random (it once did with the best of 3 batches).
        val perIconMs =
            icons
                .map { icon ->
                    (1..RUNS).minOf {
                        val start = System.nanoTime()
                        IconOutline.render(IconOutline.trace(icon), 144)
                        (System.nanoTime() - start) / 1e6
                    }
                }.average()
        println("Outline icons: %.1f ms per icon (trace + render at 144 px)".format(perIconMs))
        assertTrue(perIconMs < 150, "took $perIconMs ms per icon")
    }

    private companion object {
        const val RUNS = 8
    }
}
