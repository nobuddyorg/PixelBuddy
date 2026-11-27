package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** One stroke of an outline: a polyline in work pixels between two regions. */
public class OutlineContour internal constructor(
    internal val xs: DoubleArray,
    internal val ys: DoubleArray,
    /** The two regions it separates, smaller id first; 0 is the background. */
    internal val regionA: Int,
    internal val regionB: Int,
    /** Closed loop (no end points). */
    public val closed: Boolean,
) {
    /** On the silhouette (borders the background). */
    public val outer: Boolean get() = regionA == 0

    public val size: Int get() = xs.size

    /** Arc length in work pixels. */
    public val length: Double by lazy {
        var total = 0.0
        for (i in 1 until xs.size) total += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        if (closed && xs.size > 1) total += hypot(xs[0] - xs.last(), ys[0] - ys.last())
        total
    }

    public fun x(index: Int): Double = xs[index]

    public fun y(index: Int): Double = ys[index]

    override fun toString(): String = "OutlineContour(${if (outer) "outer" else "inner"}, $size points, closed=$closed)"
}

/** Steps 3 and 4 of the prototype: contour tracing, smoothing and cleanup. */
internal object Contours {
    /**
     * Crack edges between differently labelled pixels; every border between two regions is traced exactly once,
     * so a border is one stroke. Chains end at junctions of three or more regions.
     */
    fun trace(
        labels: IntArray,
        size: Int,
    ): List<OutlineContour> {
        val padded = size + 2
        val stride = padded + 1

        fun label(
            y: Int,
            x: Int,
        ): Int = if (y in 1..size && x in 1..size) labels[(y - 1) * size + x - 1] else 0
        val from = ArrayList<Int>()
        val to = ArrayList<Int>()
        val pairs = ArrayList<Long>()

        fun edge(
            a: Int,
            b: Int,
            v0: Int,
            v1: Int,
        ) {
            if (a == b) return
            from += v0
            to += v1
            pairs += Regions.pair(a, b)
        }
        for (y in 1 until padded) {
            for (x in 0 until padded) {
                edge(
                    label(y - 1, x),
                    label(y, x),
                    y * stride + x,
                    y * stride + x + 1,
                )
            }
        }
        for (y in 0 until padded) {
            for (x in 1 until padded) {
                edge(
                    label(y, x - 1),
                    label(y, x),
                    y * stride + x,
                    (y + 1) * stride + x,
                )
            }
        }
        val vertices = stride * stride
        val count = IntArray(vertices)
        val incident = IntArray(vertices * 4)
        for (e in from.indices) {
            incident[from[e] * 4 + count[from[e]]++] = e
            incident[to[e] * 4 + count[to[e]]++] = e
        }

        fun isNode(v: Int): Boolean = count[v] != 2 || pairs[incident[v * 4]] != pairs[incident[v * 4 + 1]]
        val seen = BooleanArray(from.size)
        val contours = ArrayList<OutlineContour>()

        fun walk(
            start: Int,
            first: Int,
        ) {
            val points = arrayListOf(start)
            var v = start
            var edge = first
            var closed = false
            while (true) {
                seen[edge] = true
                val u = if (from[edge] == v) to[edge] else from[edge]
                if (u == start) {
                    closed = !isNode(u)
                    if (!closed) points += u
                    break
                }
                points += u
                if (isNode(u)) break
                var next = -1
                for (j in 0 until count[u]) {
                    val e = incident[u * 4 + j]
                    if (e != edge && !seen[e]) {
                        next = e
                        break
                    }
                }
                if (next < 0) break
                v = u
                edge = next
            }
            val xs = DoubleArray(points.size) { (points[it] % stride - 1).toDouble() }
            val ys = DoubleArray(points.size) { (points[it] / stride - 1).toDouble() }
            val pair = pairs[first]
            contours += OutlineContour(xs, ys, Regions.first(pair), Regions.second(pair), closed)
        }
        for (v in 0 until vertices) {
            if (count[v] == 0 || !isNode(v)) continue
            for (j in 0 until count[v]) {
                val e = incident[v * 4 + j]
                if (!seen[e]) walk(v, e)
            }
        }
        for (e in from.indices) if (!seen[e]) walk(from[e], e)
        return contours
    }

    /** Evenly spaced points [step] apart along the polyline. */
    fun resample(
        c: OutlineContour,
        step: Double = 1.0,
    ): OutlineContour {
        val m = c.size + if (c.closed) 1 else 0
        val px = DoubleArray(m) { c.xs[it % c.size] }
        val py = DoubleArray(m) { c.ys[it % c.size] }
        val distance = DoubleArray(m)
        for (i in 1 until m) distance[i] = distance[i - 1] + hypot(px[i] - px[i - 1], py[i] - py[i - 1])
        val total = distance.last()
        if (total ==
            0.0
        ) {
            return OutlineContour(doubleArrayOf(c.xs[0]), doubleArrayOf(c.ys[0]), c.regionA, c.regionB, c.closed)
        }
        val n = max(2, (total / step).roundToInt())
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        var segment = 1
        for (i in 0 until n) {
            val t = if (c.closed) total * i / n else total * i / (n - 1)
            while (segment < m - 1 && distance[segment] < t) segment++
            val span = distance[segment] - distance[segment - 1]
            val f = if (span > 0) ((t - distance[segment - 1]) / span).coerceIn(0.0, 1.0) else 0.0
            xs[i] = px[segment - 1] + f * (px[segment] - px[segment - 1])
            ys[i] = py[segment - 1] + f * (py[segment] - py[segment - 1])
        }
        return OutlineContour(xs, ys, c.regionA, c.regionB, c.closed)
    }

    /** Gaussian along the arc length; ends of open chains stay pinned, so junctions stay joined. */
    fun smooth(
        contour: OutlineContour,
        sigma: Double,
    ): OutlineContour {
        val c = resample(contour)
        val n = c.size
        if (n < 3 || sigma <= 0) return c
        val r = (3 * sigma).toInt()
        val k = DoubleArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }
        val sum = k.sum()
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        for (i in 0 until n) {
            for (j in k.indices) {
                val at = if (c.closed) Math.floorMod(i + j - r, n) else (i + j - r).coerceIn(0, n - 1)
                xs[i] += k[j] * c.xs[at] / sum
                ys[i] += k[j] * c.ys[at] / sum
            }
        }
        if (!c.closed) {
            xs[0] = c.xs[0]
            ys[0] = c.ys[0]
            xs[n - 1] = c.xs[n - 1]
            ys[n - 1] = c.ys[n - 1]
        }
        return OutlineContour(xs, ys, c.regionA, c.regionB, c.closed)
    }

    /** Drops short contours and internal ones that mostly run along a longer contour (near-parallel duplicates). */
    fun cleanup(
        contours: List<OutlineContour>,
        size: Int,
        p: OutlineParams,
    ): List<OutlineContour> {
        val minLength = p.minContour * size
        val limit = p.duplicate * size
        val result = ArrayList<OutlineContour>()
        contours
            .filter { it.length >= if (it.outer) minLength * 0.5 else minLength }
            .sortedByDescending { it.length }
            .forEach { c ->
                if (c.outer || !runsAlong(c, result, limit * limit)) result += c
            }
        return result
    }

    private fun runsAlong(
        c: OutlineContour,
        others: List<OutlineContour>,
        limitSquared: Double,
    ): Boolean {
        var near = 0
        for (i in 0 until c.size) {
            val close =
                others.any { o ->
                    (0 until o.size).any { j ->
                        val dx = c.xs[i] - o.xs[j]
                        val dy = c.ys[i] - o.ys[j]
                        dx * dx + dy * dy < limitSquared
                    }
                }
            if (close) near++
        }
        return near > 0.7 * c.size
    }
}
