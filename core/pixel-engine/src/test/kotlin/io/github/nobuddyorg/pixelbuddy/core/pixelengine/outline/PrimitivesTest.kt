package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PrimitivesTest {
    private fun assertNear(
        expected: Double,
        actual: Double,
        tolerance: Double = 1e-6,
    ) = assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")

    @Test
    fun `lab of white, black and red`() {
        val out = DoubleArray(3)
        Primitives.lab(1.0, 1.0, 1.0, out)
        assertNear(100.0, out[0], 0.01)
        assertNear(0.0, out[1], 0.05)
        Primitives.lab(0.0, 0.0, 0.0, out)
        assertNear(0.0, out[0], 0.01)
        Primitives.lab(1.0, 0.0, 0.0, out)
        assertNear(53.24, out[0], 0.05)
        assertNear(80.09, out[1], 0.1)
        assertNear(67.2, out[2], 0.1)
    }

    @Test
    fun `blur keeps constants and spreads a dot symmetrically`() {
        val size = 9
        Primitives.blur(DoubleArray(size * size) { 0.5 }, size, 1.5).forEach { assertNear(0.5, it) }
        val dot = DoubleArray(size * size).also { it[4 * size + 4] = 1.0 }
        val blurred = Primitives.blur(dot, size, 1.0)
        assertNear(1.0, blurred.sum(), 1e-3)
        assertNear(blurred[4 * size + 3], blurred[4 * size + 5])
        assertNear(blurred[3 * size + 4], blurred[4 * size + 3])
        assertTrue(blurred[4 * size + 4] > blurred[4 * size + 3])
        assertContentEquals(dot, Primitives.blur(dot, size, 0.0))
    }

    @Test
    fun `fast blur is close to the exact Gaussian`() {
        val size = 64
        val square = DoubleArray(size * size) { i -> if (i % size in 20..40 && i / size in 20..40) 1.0 else 0.0 }
        val exact = Primitives.blur(square, size, 5.0)
        val fast = Primitives.fastBlur(square, size, 5.0)
        assertTrue(
            exact.indices.all { abs(exact[it] - fast[it]) < 0.03 },
            "max ${exact.indices.maxOf {
                abs(exact[it] - fast[it])
            }}",
        )
        assertContentEquals(square, Primitives.fastBlur(square, size, 0.0))
    }

    @Test
    fun `sobel is zero on flat areas and strong on a step`() {
        val size = 8
        val step = DoubleArray(size * size) { if (it % size < 4) 0.0 else 1.0 }
        val g = Primitives.sobel(step, size)
        assertNear(0.0, g[3 * size + 1])
        assertNear(4.0, g[3 * size + 3])
        assertNear(4.0, g[3 * size + 4])
        assertNear(0.0, g[3 * size + 7])
    }

    @Test
    fun `components are 4-connected and skip the background`() {
        // two diagonal 1s are separate, the 2s are one component
        val values =
            intArrayOf(
                1,
                0,
                2,
                0,
                1,
                2,
                2,
                2,
                2,
            )
        val c = Primitives.components(values, 3)
        assertEquals(0, c[1])
        assertTrue(c[0] != c[4])
        assertEquals(setOf(c[2]), setOf(c[5], c[6], c[7], c[8]))
        assertEquals(3, c.max())
    }

    @Test
    fun `distance to the nearest false pixel or the border`() {
        val size = 7
        val d = Primitives.distance(BooleanArray(size * size) { true }, size)
        assertNear(1.0, d[0])
        assertNear(4.0, d[3 * size + 3])
        val hole = BooleanArray(size * size) { it != 3 * size + 3 }
        val h = Primitives.distance(hole, size)
        assertNear(0.0, h[3 * size + 3])
        assertNear(1.0, h[3 * size + 4])
        assertNear(4.0 / 3, h[2 * size + 2])
    }

    @Test
    fun `k-means separates two groups deterministically`() {
        val points = arrayOf(DoubleArray(100) { if (it < 60) 0.0 + it % 3 else 50.0 + it % 3 }, DoubleArray(100))
        val a = Primitives.kmeans(points, 2)
        val b = Primitives.kmeans(points, 2)
        assertContentEquals(a.assignment, b.assignment)
        assertEquals(
            1,
            a.assignment
                .take(60)
                .toSet()
                .size,
        )
        assertEquals(
            1,
            a.assignment
                .drop(60)
                .toSet()
                .size,
        )
        assertTrue(a.assignment[0] != a.assignment[99])
        // more clusters than distinct points: duplicates are never chosen as centres
        assertTrue(Primitives.kmeans(arrayOf(DoubleArray(10) { 1.0 }), 4).centres.size == 1)
        // weights pull the centre
        val weighted = Primitives.kmeans(arrayOf(doubleArrayOf(0.0, 10.0)), 1, doubleArrayOf(3.0, 1.0))
        assertNear(2.5, weighted.centres[0][0])
        // sampling for large inputs still assigns every point
        assertEquals(10_000, Primitives.kmeans(arrayOf(DoubleArray(10_000) { it % 7.0 }), 3).assignment.size)
    }

    @Test
    fun `median and indices`() {
        assertNear(2.0, Primitives.median(doubleArrayOf(3.0, 1.0, 2.0)))
        assertNear(2.5, Primitives.median(doubleArrayOf(4.0, 1.0, 2.0, 3.0)))
        assertContentEquals(intArrayOf(1, 3), Primitives.indices(booleanArrayOf(false, true, false, true)))
    }

    @Test
    fun `work image pads to a square and keeps colours of translucent pixels`() {
        val red = 0x80FF0000.toInt()
        val wide = PixelImage(4, 2, IntArray(8) { red })
        val image = WorkImage.of(wide, 4)
        // rows 0 and 3 are padding
        assertNear(0.0, image.alpha[0])
        assertNear(128 / 255.0, image.alpha[4 + 1], 1e-9)
        assertNear(1.0, image.rgb[0][4 + 1])
        assertNear(0.0, image.rgb[1][4 + 1])
        assertNear(0.0, image.alpha[12])
        // upscaling interpolates
        val up = WorkImage.of(PixelImage(2, 2, intArrayOf(-1, -1, 0, 0)), 8)
        assertTrue(up.alpha[0] == 1.0 && up.alpha[63] == 0.0 && up.alpha[3 * 8] in 0.01..0.99)
    }

    @Test
    fun `hsv conversion matches colorsys`() {
        val hsv = DoubleArray(3)
        Colouring.toHsv(1.0, 0.5, 0.0, hsv)
        assertNear(30 / 360.0, hsv[0])
        assertNear(1.0, hsv[1])
        assertNear(1.0, hsv[2])
        Colouring.toHsv(0.2, 0.2, 0.2, hsv)
        assertNear(0.0, hsv[1])
        Colouring.toHsv(1.0, 0.0, 0.5, hsv)
        assertNear(330 / 360.0, hsv[0])
        listOf(0.05, 0.2, 0.4, 0.55, 0.75, 0.9).forEach { h ->
            val rgb = Colouring.toRgb(h, 0.6, 0.8)
            Colouring.toHsv(rgb[0], rgb[1], rgb[2], hsv)
            assertNear(h, hsv[0])
            assertNear(0.6, hsv[1])
            assertNear(0.8, hsv[2])
        }
        assertContentEquals(doubleArrayOf(0.3, 0.3, 0.3), Colouring.toRgb(0.5, 0.0, 0.3))
    }

    @Test
    fun `boost saturates and brightens`() {
        val p = OutlineParams()
        val hsv = DoubleArray(3)
        val dark = Colouring.boost(doubleArrayOf(0.2, 0.1, 0.0), p)
        Colouring.toHsv(dark[0], dark[1], dark[2], hsv)
        assertNear(p.minValue, hsv[2])
        val pale = Colouring.boost(doubleArrayOf(1.0, 0.6, 0.6), p)
        Colouring.toHsv(pale[0], pale[1], pale[2], hsv)
        assertNear(0.4 * p.saturationBoost, hsv[1])
    }

    @Test
    fun `dominant colour of a grey icon is its mean, of nothing white`() {
        val grey = WorkImage.of(PixelImage(4, 4, IntArray(16) { 0xFF808080.toInt() }), 4)
        val all = BooleanArray(16) { true }
        Colouring.dominantColour(grey, all).forEach { assertNear(128 / 255.0, it, 1e-9) }
        assertContentEquals(doubleArrayOf(1.0, 1.0, 1.0), Colouring.dominantColour(grey, BooleanArray(16)))
    }
}
