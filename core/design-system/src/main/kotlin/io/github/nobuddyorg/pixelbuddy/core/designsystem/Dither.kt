package io.github.nobuddyorg.pixelbuddy.core.designsystem

/**
 * Ordered (Bayer) dithering for the retro sky gradient. Pure functions on ARGB ints, so they're fast to test
 * and run off the main thread.
 */
object Dither {
    /** Bayer threshold matrix of size [n] × [n] ([n] a power of two), values 0 until n². */
    fun bayerMatrix(n: Int): Array<IntArray> {
        require(n >= 1 && n and (n - 1) == 0) { "n must be a power of two, was $n" }
        if (n == 1) return arrayOf(intArrayOf(0))
        val half = bayerMatrix(n / 2)
        val h = n / 2
        return Array(n) { y ->
            IntArray(n) { x ->
                val base = 4 * half[y % h][x % h]
                base +
                    when {
                        y < h && x < h -> 0
                        y >= h && x >= h -> 1
                        y < h -> 2
                        else -> 3
                    }
            }
        }
    }

    /**
     * Renders a vertical gradient through [stops] (ARGB, top to bottom) into a [width] × [height] image.
     * The gradient is reduced to [bands] colours sampled along it; between two bands, pixels are dithered with a
     * [matrixSize] Bayer matrix. Scaled up without filtering, this gives the banded pixel-art sky.
     */
    fun verticalGradient(
        width: Int,
        height: Int,
        stops: IntArray,
        bands: Int = 12,
        matrixSize: Int = 4,
    ): IntArray {
        require(width > 0 && height > 0) { "size must be positive" }
        require(stops.size >= 2) { "need at least two colours" }
        require(bands >= 2) { "need at least two bands" }
        val palette = IntArray(bands) { interpolate(stops, it.toFloat() / (bands - 1)) }
        val matrix = bayerMatrix(matrixSize)
        val cells = (matrixSize * matrixSize).toFloat()
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val position = if (height == 1) 0f else y.toFloat() / (height - 1) * (bands - 1)
            for (x in 0 until width) {
                val threshold = (matrix[y % matrixSize][x % matrixSize] + 0.5f) / cells - 0.5f
                out[y * width + x] = palette[bandIndex(position, threshold, bands)]
            }
        }
        return out
    }

    /** The band for a fractional [position], pushed up or down by the dither [threshold] (-0.5..0.5). */
    internal fun bandIndex(
        position: Float,
        threshold: Float,
        bands: Int,
    ): Int =
        kotlin.math
            .round(position + threshold)
            .toInt()
            .coerceIn(0, bands - 1)

    /** Linear interpolation through [stops] at [t] (0..1); returns an opaque ARGB colour. */
    internal fun interpolate(
        stops: IntArray,
        t: Float,
    ): Int {
        val position = t.coerceIn(0f, 1f) * (stops.size - 1)
        val index = position.toInt().coerceAtMost(stops.size - 2)
        val f = position - index
        val a = stops[index]
        val b = stops[index + 1]

        fun channel(shift: Int) =
            kotlin.math.round((a shr shift and 0xFF) * (1 - f) + (b shr shift and 0xFF) * f).toInt()
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
