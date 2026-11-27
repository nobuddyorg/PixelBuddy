package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Image primitives on square planes (`size * size` values, row by row): Lab conversion, Gaussian blur, Sobel,
 * connected components, distance transform and k-means. Ported from `tools/icon-outline/primitives.py`.
 */
internal object Primitives {
    /** sRGB 0..1 to CIE Lab (D65), written to [out]. */
    fun lab(
        r: Double,
        g: Double,
        b: Double,
        out: DoubleArray,
    ) {
        val lr = linear(r)
        val lg = linear(g)
        val lb = linear(b)
        val fx = labF((0.4124 * lr + 0.3576 * lg + 0.1805 * lb) / 0.95047)
        val fy = labF(0.2126 * lr + 0.7152 * lg + 0.0722 * lb)
        val fz = labF((0.0193 * lr + 0.1192 * lg + 0.9505 * lb) / 1.08883)
        out[0] = 116 * fy - 16
        out[1] = 500 * (fx - fy)
        out[2] = 200 * (fy - fz)
    }

    /** Lab planes of three RGB planes. */
    fun lab(rgb: Array<DoubleArray>): Array<DoubleArray> {
        val n = rgb[0].size
        val out = Array(3) { DoubleArray(n) }
        val tmp = DoubleArray(3)
        for (i in 0 until n) {
            lab(rgb[0][i], rgb[1][i], rgb[2][i], tmp)
            out[0][i] = tmp[0]
            out[1][i] = tmp[1]
            out[2][i] = tmp[2]
        }
        return out
    }

    /** sRGB to linear, from a table with linear interpolation (`pow` per pixel is slow on phones). */
    private fun linear(c: Double): Double {
        val t = c.coerceIn(0.0, 1.0) * LINEAR_STEPS
        val i = t.toInt().coerceAtMost(LINEAR_STEPS - 1)
        return LINEAR[i] + (t - i) * (LINEAR[i + 1] - LINEAR[i])
    }

    private const val LINEAR_STEPS = 1024
    private val LINEAR =
        DoubleArray(LINEAR_STEPS + 1) {
            val c = it.toDouble() / LINEAR_STEPS
            if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }

    private fun labF(t: Double): Double = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116

    /** Normalised Gaussian weights for offsets `-radius..radius`. */
    fun kernel(
        sigma: Double,
        radius: Int = max(1, (3 * sigma).roundToInt()),
    ): DoubleArray {
        val k = DoubleArray(2 * radius + 1) { i -> (i - radius).toDouble().let { exp(-it * it / (2 * sigma * sigma)) } }
        val sum = k.sum()
        for (i in k.indices) k[i] /= sum
        return k
    }

    /** Separable Gaussian blur, edges clamped. */
    fun blur(
        plane: DoubleArray,
        size: Int,
        sigma: Double,
    ): DoubleArray {
        if (sigma <= 0) return plane.copyOf()
        val k = kernel(sigma)
        val r = k.size / 2
        val line = DoubleArray(size + 2 * r)
        val tmp = DoubleArray(plane.size)
        for (y in 0 until size) {
            for (i in line.indices) line[i] = plane[y * size + (i - r).coerceIn(0, size - 1)]
            convolve(line, k, tmp, y * size, 1, size)
        }
        val out = DoubleArray(plane.size)
        for (x in 0 until size) {
            for (i in line.indices) line[i] = tmp[(i - r).coerceIn(0, size - 1) * size + x]
            convolve(line, k, out, x, size, size)
        }
        return out
    }

    /**
     * Gaussian approximated by three box blurs (running sums, so the cost doesn't grow with [sigma]); edges clamped.
     * Used for the wide glow, where the exact kernel would be slow and the difference is invisible.
     */
    fun fastBlur(
        plane: DoubleArray,
        size: Int,
        sigma: Double,
    ): DoubleArray {
        if (sigma <= 0) return plane.copyOf()
        // box widths for three passes (Kovesi): the variances add up to sigma^2
        val ideal = sqrt(12 * sigma * sigma / BOXES + 1)
        var lower = ideal.toInt()
        if (lower % 2 == 0) lower--
        val upper = lower + 2
        val m =
            ((12 * sigma * sigma - BOXES * lower * lower - 4 * BOXES * lower - 3 * BOXES) / (-4 * lower - 4))
                .roundToInt()
        var current = plane
        for (pass in 0 until BOXES) {
            val radius = ((if (pass < m) lower else upper) - 1) / 2
            current = box(box(current, size, radius, horizontal = true), size, radius, horizontal = false)
        }
        return current
    }

    private const val BOXES = 3

    private fun box(
        plane: DoubleArray,
        size: Int,
        radius: Int,
        horizontal: Boolean,
    ): DoubleArray {
        if (radius <= 0) return plane
        val out = DoubleArray(plane.size)
        val step = if (horizontal) 1 else size
        val lineStep = if (horizontal) size else 1
        val scale = 1.0 / (2 * radius + 1)
        for (line in 0 until size) {
            val base = line * lineStep

            fun at(i: Int) = plane[base + i.coerceIn(0, size - 1) * step]
            var sum = 0.0
            for (i in -radius..radius) sum += at(i)
            for (i in 0 until size) {
                out[base + i * step] = sum * scale
                sum += at(i + radius + 1) - at(i - radius)
            }
        }
        return out
    }

    /** `out[start + i * step] = sum(k[j] * line[i + j])` for `i < count`. */
    private fun convolve(
        line: DoubleArray,
        k: DoubleArray,
        out: DoubleArray,
        start: Int,
        step: Int,
        count: Int,
    ) {
        for (i in 0 until count) {
            var acc = 0.0
            for (j in k.indices) acc += k[j] * line[i + j]
            out[start + i * step] = acc
        }
    }

    /** Sobel gradient magnitude, edges clamped. */
    fun sobel(
        plane: DoubleArray,
        size: Int,
    ): DoubleArray {
        val w = size + 2
        val p =
            DoubleArray(w * w) { i ->
                plane[
                    (i / w - 1).coerceIn(0, size - 1) * size +
                        (i % w - 1).coerceIn(0, size - 1),
                ]
            }
        val out = DoubleArray(plane.size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val c = (y + 1) * w + x + 1
                val gx = p[c - w + 1] + 2 * p[c + 1] + p[c + w + 1] - (p[c - w - 1] + 2 * p[c - 1] + p[c + w - 1])
                val gy = p[c + w - 1] + 2 * p[c + w] + p[c + w + 1] - (p[c - w - 1] + 2 * p[c - w] + p[c - w + 1])
                out[y * size + x] = sqrt(gx * gx + gy * gy)
            }
        }
        return out
    }

    /** 4-connected components of equal values; 0 stays 0 (background), components are numbered from 1. */
    fun components(
        values: IntArray,
        size: Int,
    ): IntArray {
        val out = IntArray(values.size)
        val queue = IntArray(values.size)
        var next = 1
        for (start in values.indices) {
            if (values[start] == 0 || out[start] != 0) continue
            val value = values[start]
            out[start] = next
            var head = 0
            var tail = 0
            queue[tail++] = start
            while (head < tail) {
                val i = queue[head++]
                val x = i % size
                val y = i / size
                for (d in 0 until 4) {
                    val j =
                        when (d) {
                            0 -> if (y > 0) i - size else -1
                            1 -> if (y < size - 1) i + size else -1
                            2 -> if (x > 0) i - 1 else -1
                            else -> if (x < size - 1) i + 1 else -1
                        }
                    if (j >= 0 && out[j] == 0 && values[j] == value) {
                        out[j] = next
                        queue[tail++] = j
                    }
                }
            }
            next++
        }
        return out
    }

    /** Chamfer (3-4) distance in pixels from each `true` pixel to the nearest `false` pixel or the border. */
    fun distance(
        mask: BooleanArray,
        size: Int,
    ): DoubleArray {
        val d = IntArray(mask.size) { if (mask[it]) FAR else 0 }

        fun at(
            x: Int,
            y: Int,
        ): Int = if (x < 0 || y < 0 || x >= size || y >= size) 0 else d[y * size + x]
        for (y in 0 until size) {
            for (x in 0 until size) {
                val i = y * size + x
                if (d[i] == 0) continue
                d[i] = minOf(d[i], at(x, y - 1) + 3, at(x - 1, y) + 3, min(at(x - 1, y - 1), at(x + 1, y - 1)) + 4)
            }
        }
        for (y in size - 1 downTo 0) {
            for (x in size - 1 downTo 0) {
                val i = y * size + x
                if (d[i] == 0) continue
                d[i] = minOf(d[i], at(x, y + 1) + 3, at(x + 1, y) + 3, min(at(x + 1, y + 1), at(x - 1, y + 1)) + 4)
            }
        }
        return DoubleArray(d.size) { d[it] / 3.0 }
    }

    private const val FAR = 1_000_000_000

    class Clusters(
        val centres: Array<DoubleArray>,
        val assignment: IntArray,
    )

    /**
     * Deterministic weighted k-means with k-means++ initialisation and a fixed seed. [points] holds one array per
     * dimension. Centres are fitted on at most [sample] evenly spaced points, then every point is assigned.
     */
    fun kmeans(
        points: Array<DoubleArray>,
        k: Int,
        weights: DoubleArray? = null,
        iterations: Int = 12,
        seed: Int = 7,
        sample: Int = 4096,
    ): Clusters {
        val all = points[0].size
        val stride = max(1, all / sample)
        val n = (all + stride - 1) / stride
        val dims = points.size
        val fit = Array(dims) { d -> DoubleArray(n) { points[d][it * stride] } }
        val w = DoubleArray(n) { weights?.get(it * stride) ?: 1.0 }

        fun distance(
            data: Array<DoubleArray>,
            i: Int,
            c: DoubleArray,
        ): Double {
            var d = 0.0
            for (j in 0 until dims) {
                val v = data[j][i] - c[j]
                d += v * v
            }
            return d
        }
        val random = Random(seed)
        val centres = mutableListOf(DoubleArray(dims) { fit[it][w.indices.maxBy { i -> w[i] }] })
        val nearest = DoubleArray(n) { distance(fit, it, centres[0]) }
        while (centres.size < min(k, n)) {
            var total = 0.0
            for (i in 0 until n) total += nearest[i] * w[i]
            if (total == 0.0) break
            var target = random.nextDouble() * total
            var chosen = -1
            for (i in 0 until n) {
                val p = nearest[i] * w[i]
                if (p <= 0) continue
                chosen = i
                target -= p
                if (target < 0) break
            }
            val centre = DoubleArray(dims) { fit[it][chosen] }
            centres += centre
            for (i in 0 until n) nearest[i] = min(nearest[i], distance(fit, i, centre))
        }

        fun assign(data: Array<DoubleArray>): IntArray =
            IntArray(data[0].size) { i ->
                var best = 0
                var bestDistance = Double.MAX_VALUE
                for (c in centres.indices) {
                    val d = distance(data, i, centres[c])
                    if (d < bestDistance) {
                        bestDistance = d
                        best = c
                    }
                }
                best
            }
        repeat(iterations) {
            val assignment = assign(fit)
            val sums = Array(centres.size) { DoubleArray(dims) }
            val totals = DoubleArray(centres.size)
            for (i in 0 until n) {
                val c = assignment[i]
                totals[c] += w[i]
                for (j in 0 until dims) sums[c][j] += fit[j][i] * w[i]
            }
            for (c in centres.indices) {
                if (totals[c] > 0) centres[c] = DoubleArray(dims) { sums[c][it] / totals[c] }
            }
        }
        return Clusters(centres.toTypedArray(), assign(points))
    }

    /** Indices of the `true` entries. */
    fun indices(mask: BooleanArray): IntArray {
        val out = IntArray(mask.count { it })
        var next = 0
        for (i in mask.indices) if (mask[i]) out[next++] = i
        return out
    }

    /** Median of [values] (mean of the two middle ones for an even count). */
    fun median(values: DoubleArray): Double {
        val sorted = values.sortedArray()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
