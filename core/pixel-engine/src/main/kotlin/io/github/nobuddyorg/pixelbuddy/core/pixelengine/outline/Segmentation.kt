package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Source selection and segmentation into regions whose borders become strokes (steps 1 and 2 of the prototype):
 * fine colour clusters in Lab, then regions with a weak shared border are merged, tiny and thin ones folded into
 * a neighbour. Region 0 is the background.
 */
internal object Segmentation {
    /** Alpha mask; fully opaque icons get their background estimated from the border instead. */
    fun foreground(
        image: WorkImage,
        lab: Array<DoubleArray>,
    ): BooleanArray {
        val border = borderIndices(image.size)
        if (border.sumOf { image.alpha[it] } / border.size < 0.9) {
            return BooleanArray(image.alpha.size) { image.alpha[it] > 0.5 }
        }
        val background = DoubleArray(3) { c -> Primitives.median(DoubleArray(border.size) { lab[c][border[it]] }) }
        return BooleanArray(image.alpha.size) { distance(lab, it, background) > 12 }
    }

    /** Top row, bottom row, left column, right column. */
    private fun borderIndices(size: Int): IntArray {
        val last = size - 1
        return IntArray(4 * size) { i ->
            val j = i % size
            when (i / size) {
                0 -> j
                1 -> last * size + j
                2 -> j * size
                else -> j * size + last
            }
        }
    }

    class Plate(
        val glyph: BooleanArray,
        val colour: DoubleArray,
    )

    /** For "white glyph on a uniform plate" icons: the glyph mask and the plate colour (RGB), otherwise null. */
    fun plateGlyph(
        image: WorkImage,
        lab: Array<DoubleArray>,
        mask: BooleanArray,
    ): Plate? {
        val inside = Primitives.indices(mask)
        if (inside.isEmpty()) return null
        val plate = DoubleArray(3) { c -> Primitives.median(DoubleArray(inside.size) { lab[c][inside[it]] }) }
        val nearPlate = BooleanArray(mask.size) { mask[it] && distance(lab, it, plate) < 12 }
        val glyph =
            BooleanArray(mask.size) {
                mask[it] && lab[0][it] > 82 && hypot(lab[1][it], lab[2][it]) < 14 && !nearPlate[it]
            }
        val share = glyph.count { it }.toDouble() / inside.size
        val plateShare = nearPlate.count { it }.toDouble() / inside.size
        if (plateShare <= 0.5 || share <= 0.03 || share >= 0.45 || hypot(plate[1], plate[2]) <= 10) return null
        val plated = Primitives.indices(nearPlate)
        val colour = DoubleArray(3) { c -> Primitives.median(DoubleArray(plated.size) { image.rgb[c][plated[it]] }) }
        return Plate(glyph, colour)
    }

    private fun distance(
        lab: Array<DoubleArray>,
        i: Int,
        to: DoubleArray,
    ): Double {
        val l = lab[0][i] - to[0]
        val a = lab[1][i] - to[1]
        val b = lab[2][i] - to[2]
        return sqrt(l * l + a * a + b * b)
    }

    fun segment(
        image: WorkImage,
        mask: BooleanArray,
        p: OutlineParams,
    ): IntArray {
        val size = image.size
        val inside = Primitives.indices(mask)
        if (inside.isEmpty()) return IntArray(mask.size)
        val lab = Primitives.lab(Array(3) { Primitives.blur(image.rgb[it], size, 0.8) })
        val points = Array(3) { c -> DoubleArray(inside.size) { lab[c][inside[it]] } }
        val assignment = Primitives.kmeans(points, p.clusters).assignment
        val clusters = IntArray(mask.size)
        inside.forEachIndexed { i, pixel -> clusters[pixel] = assignment[i] + 1 }
        val sobel = Array(3) { Primitives.sobel(lab[it], size) }
        val grad = DoubleArray(mask.size) { i -> sqrt(sobel.sumOf { it[i] * it[i] }) / 8 }
        val regions = Regions(size, grad, p)
        val minArea = max(4.0, p.minRegion * inside.size)
        var labels = Primitives.components(clusters, size)
        labels = regions.foldSpecks(labels, lab, minArea)
        labels = regions.mergeWeak(labels)
        labels = regions.foldSmallAndThin(labels, minArea)
        return regions.mergeWeak(labels)
    }
}

/** Region merging on a label map with the Lab gradient [grad]. */
internal class Regions(
    private val size: Int,
    private val grad: DoubleArray,
    private val p: OutlineParams,
) {
    /**
     * Tiny regions (mostly anti-aliased edge pixels) join their most similar neighbour by colour. Done before
     * comparing borders, so specks don't hide how strong the edge between two big regions is.
     */
    fun foldSpecks(
        start: IntArray,
        lab: Array<DoubleArray>,
        minArea: Double,
    ): IntArray {
        var labels = start
        repeat(8) {
            val n = labels.max() + 1
            val areas = IntArray(n)
            val sums = Array(3) { DoubleArray(n) }
            for (i in labels.indices) {
                areas[labels[i]]++
                for (c in 0 until 3) sums[c][labels[i]] += lab[c][i]
            }
            val neighbours = sortedMapOf<Int, java.util.TreeSet<Int>>()
            forEachPair(labels) { a, b, _, _ ->
                if (a != 0 && b != 0) {
                    neighbours.getOrPut(a) { java.util.TreeSet() }.add(b)
                    neighbours.getOrPut(b) { java.util.TreeSet() }.add(a)
                }
            }

            fun colourDistance(
                a: Int,
                b: Int,
            ): Double =
                (0 until 3).sumOf { c ->
                    val d = sums[c][a] / areas[a] - sums[c][b] / areas[b]
                    d * d
                }
            val parent = HashMap<Int, Int>()
            for (region in neighbours.keys.sortedBy { areas[it] }) {
                if (areas[region] >= minArea || region in parent) continue
                val target =
                    neighbours
                        .getValue(region)
                        .filter { it !in parent && areas[it] >= areas[region] }
                        .minByOrNull { colourDistance(it, region) }
                if (target != null) parent[region] = target
            }
            if (parent.isEmpty()) return labels
            labels = relabel(labels, parent)
        }
        return labels
    }

    /**
     * Agglomerative: repeatedly merges the pair whose whole shared border has the smallest share of strong edge,
     * while that share is below 30 %. Re-evaluating merged regions avoids chains through smooth gradients.
     */
    fun mergeWeak(labels: IntArray): IntArray {
        var stats = borders(labels)
        val parent = HashMap<Int, Int>()
        while (true) {
            var best = -1L
            var bestShare = WEAK_SHARE
            for ((key, s) in stats) {
                val share = strongShare(s)
                if (first(key) == 0 || share >= WEAK_SHARE) continue
                if (best < 0 || share < bestShare || (share == bestShare && key < best)) {
                    best = key
                    bestShare = share
                }
            }
            if (best < 0) break
            val a = first(best)
            val b = second(best)
            parent[b] = a
            val merged = HashMap<Long, DoubleArray>()
            for ((key, s) in stats) {
                if (key == best) continue
                val x = first(key).let { if (it == b) a else it }
                val y = second(key).let { if (it == b) a else it }
                val m = merged.getOrPut(pair(x, y)) { DoubleArray(3) }
                for (i in 0 until 3) m[i] += s[i]
            }
            stats = merged
        }
        return if (parent.isEmpty()) labels else relabel(labels, parent)
    }

    /** Tiny regions and thin slivers join the neighbour they share the longest border with. */
    fun foldSmallAndThin(
        start: IntArray,
        minArea: Double,
    ): IntArray {
        var labels = start
        val thin = p.thin * size
        repeat(6) {
            val stats = borders(labels)
            val n = labels.max() + 1
            val areas = IntArray(n)
            for (l in labels) areas[l]++
            val boundary = BooleanArray(labels.size)
            forEachPair(labels) { _, _, i, j ->
                boundary[i] = true
                boundary[j] = true
            }
            val depth = Primitives.distance(BooleanArray(labels.size) { !boundary[it] }, size)
            val thickness = DoubleArray(n)
            for (i in labels.indices) thickness[labels[i]] = max(thickness[labels[i]], depth[i])
            val neighbours = HashMap<Int, MutableList<Pair<Int, Double>>>()
            for ((key, s) in stats) {
                neighbours.getOrPut(first(key)) { mutableListOf() } += second(key) to s[0]
                neighbours.getOrPut(second(key)) { mutableListOf() } += first(key) to s[0]
            }
            val parent = HashMap<Int, Int>()
            for (region in (1 until n).sortedBy { areas[it] }) {
                if (areas[region] == 0 || region in parent) continue
                if (areas[region] >= minArea && 2 * thickness[region] + 1 >= thin) continue
                val target =
                    neighbours[region]
                        .orEmpty()
                        .filter { (other, _) -> other != 0 && other !in parent }
                        .maxWithOrNull(compareBy<Pair<Int, Double>> { it.second }.thenBy { it.first })
                if (target != null) parent[region] = target.first
            }
            if (parent.isEmpty()) return labels
            labels = relabel(labels, parent)
        }
        return labels
    }

    /**
     * Per pair of neighbouring regions (background included): border length, strong-edge length, judged length.
     * Border pixels near a junction with a third region are not judged: other edges meet there.
     */
    fun borders(labels: IntArray): Map<Long, DoubleArray> {
        // 0 unknown, 1 no, 2 yes: only pixels on a border are ever asked
        val junction = ByteArray(labels.size)

        fun nearJunction(i: Int): Boolean {
            if (junction[i] == 0.toByte()) junction[i] = if (nearJunction(labels, i)) 2 else 1
            return junction[i] == 2.toByte()
        }
        val stats = HashMap<Long, DoubleArray>()
        forEachPair(labels) { a, b, i, j ->
            val s = stats.getOrPut(pair(a, b)) { DoubleArray(3) }
            s[0]++
            if (!nearJunction(i) && !nearJunction(j)) {
                s[2]++
                if (max(grad[i], grad[j]) > p.weakBorder) s[1]++
            }
        }
        return stats
    }

    /** At least three different labels (background included) within [radius] of pixel [i]. */
    fun nearJunction(
        labels: IntArray,
        i: Int,
        radius: Int = 2,
    ): Boolean {
        val x = i % size
        val y = i / size
        val first = labels[i]
        var second = first
        for (dy in -radius..radius) {
            val row = (y + dy).coerceIn(0, size - 1) * size
            for (dx in -radius..radius) {
                val v = labels[row + (x + dx).coerceIn(0, size - 1)]
                if (v == first || v == second) continue
                if (second != first) return true
                second = v
            }
        }
        return false
    }

    /** Calls [action] for every horizontally or vertically adjacent pixel pair with different labels. */
    private inline fun forEachPair(
        labels: IntArray,
        action: (a: Int, b: Int, i: Int, j: Int) -> Unit,
    ) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                val i = y * size + x
                if (x + 1 < size && labels[i] != labels[i + 1]) action(labels[i], labels[i + 1], i, i + 1)
                if (y + 1 < size && labels[i] != labels[i + size]) action(labels[i], labels[i + size], i, i + size)
            }
        }
    }

    companion object {
        private const val WEAK_SHARE = 0.3

        fun pair(
            a: Int,
            b: Int,
        ): Long = (minOf(a, b).toLong() shl 32) or maxOf(a, b).toLong()

        fun first(key: Long): Int = (key ushr 32).toInt()

        fun second(key: Long): Int = key.toInt()

        fun strongShare(s: DoubleArray): Double = if (s[2] >= 3) s[1] / s[2] else 1.0

        /** Applies the merges in [parent] and numbers the regions 1.. again; the background stays 0. */
        fun relabel(
            labels: IntArray,
            parent: Map<Int, Int>,
        ): IntArray {
            fun find(start: Int): Int {
                var a = start
                while (true) a = parent[a] ?: return a
            }
            val n = labels.max() + 1
            val root = IntArray(n) { find(it) }
            val used = BooleanArray(n)
            for (l in labels) used[root[l]] = true
            val compact = IntArray(n)
            var next = 1
            for (r in 1 until n) if (used[r]) compact[r] = next++
            return IntArray(labels.size) { compact[root[labels[it]]] }
        }
    }
}
