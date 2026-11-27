package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Per-point stroke colours (RGB 0..1) of one contour. */
internal class StrokeColours(
    val r: DoubleArray,
    val g: DoubleArray,
    val b: DoubleArray,
) {
    companion object {
        fun constant(
            n: Int,
            rgb: DoubleArray,
        ) = StrokeColours(DoubleArray(n) { rgb[0] }, DoubleArray(n) { rgb[1] }, DoubleArray(n) { rgb[2] })
    }
}

/** Step 5 of the prototype: colour, separate from geometry. */
internal object Colouring {
    fun colours(
        outline: Outline,
        mode: OutlineColours,
        p: OutlineParams,
    ): List<StrokeColours> {
        val contours = outline.contours
        if (contours.isEmpty()) return emptyList()
        // glyph on a plate: the plate is the brand colour, whatever the mode
        val plate = outline.plateColour
        if (plate != null) return contours.map { StrokeColours.constant(it.size, boost(plate, p)) }
        return when (mode) {
            OutlineColours.Single -> {
                val c = boost(dominantColour(outline.image, outline.mask), p)
                contours.map { StrokeColours.constant(it.size, c) }
            }

            OutlineColours.Sampled -> {
                sampled(outline, p)
            }

            OutlineColours.Sweep -> {
                sweep(outline, p)
            }
        }
    }

    /**
     * Outer contours take the icon's colour near them. A border between two regions takes the colour of the smaller
     * region (usually the detail, e.g. a disc on a plate), so the stroke never washes out by blending.
     */
    private fun sampled(
        outline: Outline,
        p: OutlineParams,
    ): List<StrokeColours> {
        val image = outline.image
        val size = image.size
        val labels = outline.labels
        val areas = IntArray(labels.max() + 1)
        for (l in labels) areas[l]++
        val kernel = Primitives.kernel(0.025 * p.workSize)
        val r = kernel.size / 2
        val rgb = DoubleArray(3)
        return outline.contours.map { c ->
            val a = c.regionA
            val b = c.regionB
            val source =
                if (a == 0) {
                    0
                } else if (areas[a] <= areas[b]) {
                    a
                } else {
                    b
                }

            fun inSource(i: Int) = if (source == 0) outline.mask[i] else labels[i] == source
            val out = StrokeColours(DoubleArray(c.size), DoubleArray(c.size), DoubleArray(c.size))
            for (i in 0 until c.size) {
                // Gaussian-weighted average of the source region around the point (edges clamped)
                val px = c.xs[i].roundToInt().coerceIn(0, size - 1)
                val py = c.ys[i].roundToInt().coerceIn(0, size - 1)
                var weight = 0.0
                rgb.fill(0.0)
                for (dy in -r..r) {
                    val row = (py + dy).coerceIn(0, size - 1) * size
                    for (dx in -r..r) {
                        val j = row + (px + dx).coerceIn(0, size - 1)
                        if (!inSource(j)) continue
                        val w = kernel[dy + r] * kernel[dx + r]
                        weight += w
                        for (ch in 0 until 3) rgb[ch] += w * image.rgb[ch][j]
                    }
                }
                val boosted = boost(DoubleArray(3) { rgb[it] / max(weight, 1e-6) }, p)
                out.r[i] = boosted[0]
                out.g[i] = boosted[1]
                out.b[i] = boosted[2]
            }
            out
        }
    }

    /** A hue sweep through the icon's main colours along the direction where they sit apart the most. */
    private fun sweep(
        outline: Outline,
        p: OutlineParams,
    ): List<StrokeColours> {
        val image = outline.image
        val size = image.size
        val mask = outline.mask
        // interior pixels only: anti-aliased edges mix in the background
        val depth = Primitives.distance(mask, size)
        val inside = Primitives.indices(mask)
        val interior = inside.filter { depth[it] > 2 }.toIntArray()
        val pixels = if (inside.size > 50 && interior.isNotEmpty()) interior else inside
        val lab = Array(3) { DoubleArray(pixels.size) }
        val tmp = DoubleArray(3)
        val weights =
            DoubleArray(pixels.size) { i ->
                val j = pixels[i]
                Primitives.lab(image.rgb[0][j], image.rgb[1][j], image.rgb[2][j], tmp)
                for (c in 0 until 3) lab[c][i] = tmp[c]
                (hypot(tmp[1], tmp[2]) + 3).let { it * it }
            }
        val clusters = Primitives.kmeans(lab, 3, weights)
        val k = clusters.centres.size
        val share = DoubleArray(k)
        for (i in pixels.indices) share[clusters.assignment[i]] += weights[i]
        val total = share.sum()
        val keep =
            (0 until k).filter { share[it] / total >= 0.08 }.ifEmpty {
                listOf(
                    share.indices.maxBy { share[it] },
                )
            }
        val stops =
            keep.map { c ->
                val members = pixels.indices.filter { clusters.assignment[it] == c }
                val x = members.sumOf { pixels[it] % size.toDouble() } / members.size
                val y = members.sumOf { pixels[it] / size.toDouble() } / members.size
                val colour = DoubleArray(3) { ch -> members.sumOf { image.rgb[ch][pixels[it]] } / members.size }
                Stop(x, y, colour, members.size.toDouble())
            }
        val count = stops.sumOf { it.weight }
        val meanX = stops.sumOf { it.x * it.weight } / count
        val meanY = stops.sumOf { it.y * it.weight } / count
        val (dx, dy) = direction(stops, meanX, meanY, minSpread = SWEEP_MIN_SPREAD * size)
        val ordered = stops.sortedBy { (it.x - meanX) * dx + (it.y - meanY) * dy }
        // The stops keep their order but are spread evenly over the icon, so the sweep is always gradual (colours
        // that sit close together, e.g. a disc on a plate, would otherwise switch abruptly).
        val extent = pixels.map { (it % size - meanX) * dx + (it / size - meanY) * dy }
        val from = extent.min() + SWEEP_INSET * (extent.max() - extent.min())
        val to = extent.max() - SWEEP_INSET * (extent.max() - extent.min())
        val positions =
            ordered.indices.map {
                if (ordered.size ==
                    1
                ) {
                    from
                } else {
                    from + (to - from) * it / (ordered.size - 1)
                }
            }
        val colours = ordered.map { boost(it.colour, p) }
        return outline.contours.map { c ->
            val out = StrokeColours(DoubleArray(c.size), DoubleArray(c.size), DoubleArray(c.size))
            for (i in 0 until c.size) {
                val t = (c.xs[i] - meanX) * dx + (c.ys[i] - meanY) * dy
                val colour = interpolate(t, positions, colours)
                out.r[i] = colour[0]
                out.g[i] = colour[1]
                out.b[i] = colour[2]
            }
            out
        }
    }

    /** Colour centres closer than this (of the size) give no direction: the sweep runs left to right. */
    private const val SWEEP_MIN_SPREAD = 0.05

    /** The first and last colour sit this far (of the extent) inside the icon. */
    private const val SWEEP_INSET = 0.15

    private class Stop(
        val x: Double,
        val y: Double,
        val colour: DoubleArray,
        val weight: Double,
    )

    /** Main axis of the weighted stop positions, pointing right (or down when vertical). */
    private fun direction(
        stops: List<Stop>,
        meanX: Double,
        meanY: Double,
        minSpread: Double,
    ): Pair<Double, Double> {
        val spread = max(stops.maxOf { it.x } - stops.minOf { it.x }, stops.maxOf { it.y } - stops.minOf { it.y })
        if (spread <= minSpread) return 1.0 to 0.0
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (s in stops) {
            sxx += s.weight * (s.x - meanX) * (s.x - meanX)
            sxy += s.weight * (s.x - meanX) * (s.y - meanY)
            syy += s.weight * (s.y - meanY) * (s.y - meanY)
        }
        val half = (sxx - syy) / 2
        val largest = (sxx + syy) / 2 + sqrt(half * half + sxy * sxy)
        var (x, y) =
            when {
                sxy != 0.0 -> (largest - syy) to sxy
                sxx >= syy -> 1.0 to 0.0
                else -> 0.0 to 1.0
            }
        val length = hypot(x, y)
        x /= length
        y /= length
        return if (x < 0 || (x == 0.0 && y < 0)) -x to -y else x to y
    }

    /** Piecewise linear colour at [t], clamped to the end colours. */
    private fun interpolate(
        t: Double,
        positions: List<Double>,
        colours: List<DoubleArray>,
    ): DoubleArray {
        if (t <= positions.first()) return colours.first()
        if (t >= positions.last()) return colours.last()
        val i = positions.indexOfFirst { it >= t }
        val span = positions[i] - positions[i - 1]
        val f = if (span > 0) (t - positions[i - 1]) / span else 1.0
        return DoubleArray(3) { colours[i - 1][it] + f * (colours[i][it] - colours[i - 1][it]) }
    }

    /** Hue histogram weighted by saturation, brightness and coverage; mean colour of the winning hue bin. */
    fun dominantColour(
        image: WorkImage,
        mask: BooleanArray,
    ): DoubleArray {
        val pixels = Primitives.indices(mask)
        if (pixels.isEmpty()) return doubleArrayOf(1.0, 1.0, 1.0)
        val hsv = DoubleArray(3)
        val bins = IntArray(pixels.size)
        val weights =
            DoubleArray(pixels.size) { i ->
                val j = pixels[i]
                toHsv(image.rgb[0][j], image.rgb[1][j], image.rgb[2][j], hsv)
                bins[i] = (hsv[0] * HUE_BINS).toInt() % HUE_BINS
                hsv[1] * hsv[2]
            }
        if (weights.sum() < 1e-6) return DoubleArray(3) { c -> pixels.sumOf { image.rgb[c][it] } / pixels.size }
        val histogram = DoubleArray(HUE_BINS)
        for (i in pixels.indices) histogram[bins[i]] += weights[i]
        // neighbouring hues help
        val smoothed =
            DoubleArray(HUE_BINS) {
                histogram[it] + 0.5 * (histogram[(it + HUE_BINS - 1) % HUE_BINS] + histogram[(it + 1) % HUE_BINS])
            }
        val best = smoothed.indices.maxBy { smoothed[it] }
        val members = pixels.indices.filter { bins[it] == best }
        val total = members.sumOf { weights[it] + 1e-9 }
        return DoubleArray(3) { c -> members.sumOf { (weights[it] + 1e-9) * image.rgb[c][pixels[it]] } / total }
    }

    private const val HUE_BINS = 36

    /** More saturated and at least [OutlineParams.minValue] bright. */
    fun boost(
        rgb: DoubleArray,
        p: OutlineParams,
    ): DoubleArray {
        val hsv = DoubleArray(3)
        toHsv(rgb[0].coerceIn(0.0, 1.0), rgb[1].coerceIn(0.0, 1.0), rgb[2].coerceIn(0.0, 1.0), hsv)
        return toRgb(hsv[0], min(1.0, hsv[1] * p.saturationBoost), max(hsv[2], p.minValue))
    }

    /** Like Python's `colorsys.rgb_to_hsv`. */
    fun toHsv(
        r: Double,
        g: Double,
        b: Double,
        out: DoubleArray,
    ) {
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        out[2] = maxC
        if (maxC == minC) {
            out[0] = 0.0
            out[1] = 0.0
            return
        }
        val range = maxC - minC
        out[1] = range / maxC
        val rc = (maxC - r) / range
        val gc = (maxC - g) / range
        val bc = (maxC - b) / range
        val h =
            when (maxC) {
                r -> bc - gc
                g -> 2 + rc - bc
                else -> 4 + gc - rc
            }
        out[0] = (h / 6 % 1.0 + 1.0) % 1.0
    }

    /** Like Python's `colorsys.hsv_to_rgb`. */
    fun toRgb(
        h: Double,
        s: Double,
        v: Double,
    ): DoubleArray {
        if (s == 0.0) return doubleArrayOf(v, v, v)
        val i = (h * 6).toInt()
        val f = h * 6 - i
        val p = v * (1 - s)
        val q = v * (1 - s * f)
        val t = v * (1 - s * (1 - f))
        return when (i % 6) {
            0 -> doubleArrayOf(v, t, p)
            1 -> doubleArrayOf(q, v, p)
            2 -> doubleArrayOf(p, v, t)
            3 -> doubleArrayOf(p, q, v)
            4 -> doubleArrayOf(t, p, v)
            else -> doubleArrayOf(v, p, q)
        }
    }
}
