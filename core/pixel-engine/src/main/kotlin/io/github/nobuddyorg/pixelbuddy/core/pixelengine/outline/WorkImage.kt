package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The source centred on a transparent square of [size] pixels: RGB and alpha planes, 0..1, not premultiplied. */
internal class WorkImage(
    val size: Int,
    val rgb: Array<DoubleArray>,
    val alpha: DoubleArray,
) {
    companion object {
        /** Pads [source] to a square and resamples it to [size] (area average down, bilinear up). */
        fun of(
            source: PixelImage,
            size: Int,
        ): WorkImage {
            val side = max(source.width, source.height)
            val width = max(1, (source.width.toDouble() * size / side).roundToInt())
            val height = max(1, (source.height.toDouble() * size / side).roundToInt())
            val columns = weights(source.width, width)
            val rows = weights(source.height, height)
            // premultiplied a, r, g, b
            val planes = Array(4) { DoubleArray(source.width * source.height) }
            for (i in source.pixels.indices) {
                val c = source.pixels[i]
                val a = (c ushr 24) / 255.0
                planes[0][i] = a
                planes[1][i] = a * (c shr 16 and 0xFF) / 255.0
                planes[2][i] = a * (c shr 8 and 0xFF) / 255.0
                planes[3][i] = a * (c and 0xFF) / 255.0
            }
            val left = (size - width) / 2
            val top = (size - height) / 2
            val alpha = DoubleArray(size * size)
            val rgb = Array(3) { DoubleArray(size * size) }
            val horizontal = Array(4) { DoubleArray(width * source.height) }
            for (p in 0 until 4) {
                for (y in 0 until source.height) {
                    for (x in 0 until width) {
                        val (index, weight) = columns[x]
                        var acc = 0.0
                        for (j in index.indices) acc += weight[j] * planes[p][y * source.width + index[j]]
                        horizontal[p][y * width + x] = acc
                    }
                }
            }
            val pixel = DoubleArray(4)
            for (y in 0 until height) {
                val (index, weight) = rows[y]
                for (x in 0 until width) {
                    for (p in 0 until 4) {
                        var acc = 0.0
                        for (j in index.indices) acc += weight[j] * horizontal[p][index[j] * width + x]
                        pixel[p] = acc
                    }
                    val i = (top + y) * size + left + x
                    val a = pixel[0].coerceIn(0.0, 1.0)
                    alpha[i] = a
                    if (a > 0) for (c in 0 until 3) rgb[c][i] = (pixel[c + 1] / a).coerceIn(0.0, 1.0)
                }
            }
            return WorkImage(size, rgb, alpha)
        }

        /** Per target index: source indices and weights summing to 1. */
        private fun weights(
            from: Int,
            to: Int,
        ): List<Pair<IntArray, DoubleArray>> {
            val scale = from.toDouble() / to
            return List(to) { t ->
                if (scale >= 1) {
                    val start = t * scale
                    val end = (t + 1) * scale
                    val first = floor(start).toInt()
                    val last = min(from - 1, kotlin.math.ceil(end).toInt() - 1)
                    val index = IntArray(last - first + 1) { first + it }
                    val weight =
                        DoubleArray(index.size) { min(end, index[it] + 1.0) - max(start, index[it].toDouble()) }
                    val sum = weight.sum()
                    index to DoubleArray(weight.size) { weight[it] / sum }
                } else {
                    val s = ((t + 0.5) * scale - 0.5).coerceIn(0.0, from - 1.0)
                    val i0 = floor(s).toInt()
                    val i1 = min(from - 1, i0 + 1)
                    val f = s - i0
                    intArrayOf(i0, i1) to doubleArrayOf(1 - f, f)
                }
            }
        }
    }
}
