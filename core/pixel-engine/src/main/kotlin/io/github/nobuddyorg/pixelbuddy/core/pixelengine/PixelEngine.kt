package io.github.nobuddyorg.pixelbuddy.core.pixelengine

import kotlin.math.round

/**
 * Turns an icon rendered at 4 × grid into a pixel-art icon. Deterministic and side-effect free: the same input
 * and style always give the same output.
 */
public object PixelEngine {
    /** Renders [source] (any size that is a multiple of [IconStyle.grid] in both directions) with [style]. */
    public fun render(
        source: PixelImage,
        style: IconStyle,
    ): PixelImage {
        val small = downscale(source, style.grid)
        val crisp = applyAlphaThreshold(small, style.alphaThreshold)
        val solid =
            if (style.shape ==
                IconShape.Masked
            ) {
                applyMask(crisp, inset = if (style.outline) 1 else 0)
            } else {
                crisp
            }
        val colored = applyPalette(solid, style)
        return if (style.outline) addOutline(colored, style.outlineColor) else colored
    }

    /** Box filter with premultiplied alpha, so transparent pixels don't darken the edges. */
    internal fun downscale(
        source: PixelImage,
        grid: Int,
    ): PixelImage {
        require(source.width % grid == 0 && source.height % grid == 0) {
            "source ${source.width}x${source.height} is not a multiple of the grid $grid"
        }
        val fx = source.width / grid
        val fy = source.height / grid
        val area = fx * fy
        val out = IntArray(grid * grid)
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                var a = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (y in gy * fy until (gy + 1) * fy) {
                    for (x in gx * fx until (gx + 1) * fx) {
                        val c = source[x, y]
                        val alpha = Argb.alpha(c).toLong()
                        a += alpha
                        r += Argb.red(c) * alpha
                        g += Argb.green(c) * alpha
                        b += Argb.blue(c) * alpha
                    }
                }
                out[gy * grid + gx] =
                    if (a == 0L) {
                        0
                    } else {
                        Argb.of(
                            round(a.toDouble() / area).toInt(),
                            round(r.toDouble() / a).toInt(),
                            round(g.toDouble() / a).toInt(),
                            round(b.toDouble() / a).toInt(),
                        )
                    }
            }
        }
        return PixelImage(grid, grid, out)
    }

    /** Crisp edges: opaque at or above [threshold], fully transparent (0) below. */
    internal fun applyAlphaThreshold(
        image: PixelImage,
        threshold: Int,
    ): PixelImage = image.map { c -> if (Argb.alpha(c) >= threshold) Argb.opaque(c) else 0 }

    /** Clips to a pixel-rounded square [inset] pixels inside the image edge. */
    internal fun applyMask(
        image: PixelImage,
        inset: Int,
    ): PixelImage {
        val mask = RoundedMask.of(image.width, inset)
        return PixelImage(
            image.width,
            image.height,
            IntArray(image.pixels.size) { if (mask[it]) image.pixels[it] else 0 },
        )
    }

    internal fun applyPalette(
        image: PixelImage,
        style: IconStyle,
    ): PixelImage {
        val palette = style.palette
        if (palette is Palette.Original) return image
        val opaque = image.pixels.filter { Argb.alpha(it) == 255 }
        if (opaque.isEmpty()) return image
        val out = IntArray(image.pixels.size)
        val shades = (palette as? Palette.MonochromeTint)?.let(::shadesOf)
        val colors =
            when (palette) {
                is Palette.Adaptive -> MedianCut.palette(opaque, palette.colors)
                is Palette.Fixed -> palette.colors.map(Argb::opaque)
                is Palette.MonochromeTint -> shades!!
                Palette.Original -> error("handled above")
            }
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val i = y * image.width + x
                val c = image.pixels[i]
                if (Argb.alpha(c) != 255) continue
                val offset = if (style.dithering) bayerOffset(x, y) else 0f
                val target =
                    if (shades != null) {
                        val position = Argb.luma(c) / 255f * (shades.size - 1) + offset
                        shades[round(position).toInt().coerceIn(0, shades.lastIndex)]
                    } else {
                        nearest(colors, nudge(c, offset * DITHER_SPREAD))
                    }
                out[i] = if (style.intensity >= 1f) target else Argb.lerp(c, target, style.intensity)
            }
        }
        return PixelImage(image.width, image.height, out)
    }

    /** Transparent pixels next to the shape (4-neighbourhood) become [color]. */
    internal fun addOutline(
        image: PixelImage,
        color: Int,
    ): PixelImage {
        val w = image.width
        val h = image.height

        fun solid(
            x: Int,
            y: Int,
        ) = x in 0 until w && y in 0 until h && Argb.alpha(image[x, y]) == 255
        val out = image.pixels.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!solid(x, y) && (solid(x - 1, y) || solid(x + 1, y) || solid(x, y - 1) || solid(x, y + 1))) {
                    out[y * w + x] = Argb.opaque(color)
                }
            }
        }
        return PixelImage(w, h, out)
    }

    internal fun shadesOf(palette: Palette.MonochromeTint): List<Int> =
        List(palette.shades) { i ->
            val t = i.toFloat() / (palette.shades - 1)
            Argb.lerp(darken(palette.tint), palette.tint, t)
        }

    private fun darken(c: Int): Int =
        Argb.of(
            255,
            Argb.red(c) * 15 / 100,
            Argb.green(c) * 15 / 100,
            Argb.blue(c) * 15 / 100,
        )

    internal fun nearest(
        colors: List<Int>,
        c: Int,
    ): Int {
        var best = colors[0]
        var bestDistance = Int.MAX_VALUE
        for (candidate in colors) {
            val d = Argb.distance(candidate, c)
            if (d < bestDistance) {
                best = candidate
                bestDistance = d
            }
        }
        return best
    }

    private fun nudge(
        c: Int,
        amount: Float,
    ): Int {
        val d = round(amount).toInt()
        return Argb.of(255, Argb.red(c) + d, Argb.green(c) + d, Argb.blue(c) + d)
    }

    /** Ordered-dither offset in -0.5..0.5 from a 4 × 4 Bayer matrix. */
    internal fun bayerOffset(
        x: Int,
        y: Int,
    ): Float = (BAYER_4[y and 3][x and 3] + 0.5f) / 16f - 0.5f

    private const val DITHER_SPREAD = 48f
    private val BAYER_4 =
        arrayOf(
            intArrayOf(0, 8, 2, 10),
            intArrayOf(12, 4, 14, 6),
            intArrayOf(3, 11, 1, 9),
            intArrayOf(15, 7, 13, 5),
        )

    private inline fun PixelImage.map(transform: (Int) -> Int): PixelImage =
        PixelImage(width, height, IntArray(pixels.size) { transform(pixels[it]) })
}

/** A square with pixel-rounded corners, radius a quarter of the side. */
internal object RoundedMask {
    fun of(
        size: Int,
        inset: Int,
    ): BooleanArray {
        val side = size - 2 * inset
        val radius = side / 4f
        return BooleanArray(size * size) { i ->
            val x = i % size - inset
            val y = i / size - inset
            if (x !in 0 until side || y !in 0 until side) return@BooleanArray false
            // Distance from the pixel centre to the nearest corner circle centre.
            val cx = (x + 0.5f).coerceIn(radius, side - radius)
            val cy = (y + 0.5f).coerceIn(radius, side - radius)
            val dx = x + 0.5f - cx
            val dy = y + 0.5f - cy
            dx * dx + dy * dy <= radius * radius
        }
    }
}

/** Deterministic median-cut colour quantization on primitive arrays. */
internal object MedianCut {
    fun palette(
        colors: List<Int>,
        size: Int,
    ): List<Int> {
        val all = IntArray(colors.size) { colors[it] or (0xFF shl 24) }
        all.sort()
        val unique = all.distinctSorted()
        if (unique.size <= size) return unique.toList()
        val boxes = mutableListOf(Box(all))
        while (boxes.size < size) {
            var best = -1
            var bestScore = -1L
            for (i in boxes.indices) {
                val box = boxes[i]
                if (box.range == 0) continue
                val score = box.range.toLong() * box.colors.size
                if (score > bestScore) {
                    best = i
                    bestScore = score
                }
            }
            if (best < 0) break
            val (low, high) = boxes.removeAt(best).split()
            boxes += low
            boxes += high
        }
        return boxes.map(Box::average).distinct().sorted()
    }

    private fun IntArray.distinctSorted(): IntArray {
        if (isEmpty()) return this
        var n = 1
        for (i in 1 until size) if (this[i] != this[i - 1]) n++
        val out = IntArray(n)
        var j = 0
        for (i in indices) if (i == 0 || this[i] != this[i - 1]) out[j++] = this[i]
        return out
    }

    private class Box(
        val colors: IntArray,
    ) {
        /** Channel with the widest spread (0 red, 1 green, 2 blue) and that spread. */
        val channel: Int
        val range: Int

        init {
            var best = 0
            var bestRange = -1
            for (c in 0..2) {
                var min = 255
                var max = 0
                for (color in colors) {
                    val v = channelOf(color, c)
                    if (v < min) min = v
                    if (v > max) max = v
                }
                if (max - min > bestRange) {
                    best = c
                    bestRange = max - min
                }
            }
            channel = best
            range = bestRange
        }

        /** Splits at the median of the widest channel; ties ordered by the full colour value. */
        fun split(): Pair<Box, Box> {
            val keys =
                LongArray(colors.size) {
                    (channelOf(colors[it], channel).toLong() shl 32) or
                        (colors[it].toLong() and 0xFFFFFFFFL)
                }
            keys.sort()
            val sorted = IntArray(keys.size) { keys[it].toInt() }
            val mid = sorted.size / 2
            return Box(sorted.copyOfRange(0, mid)) to Box(sorted.copyOfRange(mid, sorted.size))
        }

        fun average(): Int {
            var r = 0
            var g = 0
            var b = 0
            for (c in colors) {
                r += Argb.red(c)
                g += Argb.green(c)
                b += Argb.blue(c)
            }
            return Argb.of(255, r / colors.size, g / colors.size, b / colors.size)
        }
    }

    private fun channelOf(
        c: Int,
        channel: Int,
    ): Int =
        when (channel) {
            0 -> Argb.red(c)
            1 -> Argb.green(c)
            else -> Argb.blue(c)
        }
}
