package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Synthetic test icons, the same as `tools/icon-outline/synth.py` (no brand icons in the repository). */
internal object Synth {
    private fun argb(
        r: Double,
        g: Double,
        b: Double,
        a: Double = 1.0,
    ): Int {
        fun c(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt()
        return (c(a) shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /** Paints on a transparent (or [background]) square; [paint] gets x, y in 0..1 and returns a colour or null. */
    private fun image(
        size: Int,
        background: Int = 0,
        paint: (x: Double, y: Double) -> Int?,
    ): PixelImage =
        PixelImage(
            size,
            size,
            IntArray(size * size) { i ->
                paint((i % size) / (size - 1.0), (i / size) / (size - 1.0)) ?: background
            },
        )

    private val ORANGE = argb(1.0, 0.54, 0.24)

    /** Orange square plate with a blue disc: one outer contour plus one internal contour. */
    fun circleInSquare(size: Int = 256): PixelImage =
        image(size) { x, y ->
            when {
                (x - 0.5) * (x - 0.5) + (y - 0.5) * (y - 0.5) < 0.04 -> argb(0.2, 0.45, 0.9)
                abs(x - 0.5) < 0.4 && abs(y - 0.5) < 0.4 -> ORANGE
                else -> null
            }
        }

    /** Disc with a smooth orange to purple gradient: no internal contour. */
    fun gradientDisc(size: Int = 256): PixelImage =
        image(size) { x, y ->
            if ((x - 0.5) * (x - 0.5) + (y - 0.5) * (y - 0.5) < 0.42 * 0.42) {
                argb(1.0 * (1 - y) + 0.42 * y, 0.54 * (1 - y) + 0.2 * y, 0.24 * (1 - y) + 0.62 * y)
            } else {
                null
            }
        }

    /** A plate split by a thin band: two borders 1 % apart should become one stroke. */
    fun parallelEdges(size: Int = 256): PixelImage =
        image(size) { x, y ->
            when {
                abs(x - 0.5) >= 0.4 || abs(y - 0.5) >= 0.4 -> null
                abs(x - 0.5) < 0.008 -> argb(0.1, 0.1, 0.2)
                x < 0.5 -> argb(0.25, 0.65, 0.42)
                else -> argb(0.95, 0.85, 0.3)
            }
        }

    /** White speech bubble on a blue rounded plate. */
    fun plateWithGlyph(size: Int = 256): PixelImage =
        image(size) { x, y ->
            val r = 0.12
            val dx = max(abs(x - 0.5) - (0.42 - r), 0.0)
            val dy = max(abs(y - 0.5) - (0.42 - r), 0.0)
            val bubble = ((x - 0.5) / 0.26).let { it * it } + ((y - 0.46) / 0.2).let { it * it } < 1
            val tail = y > 0.6 && y < 0.74 && x > 0.34 && x < 0.34 + (0.74 - y) * 0.8
            when {
                bubble || tail -> argb(1.0, 1.0, 1.0)
                dx * dx + dy * dy < r * r -> argb(0.24, 0.45, 0.85)
                else -> null
            }
        }

    /** No transparency: a purple star-ish shape on a flat white square. */
    fun opaqueIcon(size: Int = 256): PixelImage =
        image(size, background = argb(1.0, 1.0, 1.0)) { x, y ->
            val a = atan2(y - 0.5, x - 0.5)
            if (hypot(x - 0.5, y - 0.5) < 0.25 + 0.08 * cos(5 * a)) argb(0.45, 0.2, 0.7) else null
        }

    /** A multi-part logo: gradient ring, inner globe, a head with two ears, a swirl and a flame. */
    fun logo(size: Int = 256): PixelImage =
        image(size) { x, y ->
            val r = hypot(x - 0.5, y - 0.52)
            val a = atan2(y - 0.52, x - 0.5)
            val head = ((x - 0.58) / 0.16).let { it * it } + ((y - 0.5) / 0.12).let { it * it } < 1
            val ears = y > 0.28 && y < 0.42 && (abs(x - 0.52) < (y - 0.28) * 0.5 || abs(x - 0.66) < (y - 0.28) * 0.5)
            val swirl = abs(r - 0.35) < 0.035 && a > 0.6 && a < 2.6
            val flame = ((x - 0.5) / 0.08).let { it * it } + ((y - 0.11) / 0.06).let { it * it } < 1
            val t = ((x - 0.1) / 0.8).coerceIn(0.0, 1.0)
            when {
                flame -> argb(1.0, 0.85, 0.2)
                swirl -> argb(1.0, 0.85, 0.3)
                head || ears -> argb(1.0, 0.6, 0.2)
                r < 0.28 -> argb(0.42, 0.25, 0.75)
                r < 0.42 -> argb(1.0 - 0.45 * t, 0.55 - 0.3 * t, 0.25 + 0.45 * t)
                else -> null
            }
        }

    fun transparent(size: Int = 128): PixelImage = PixelImage(size, size, IntArray(size * size))

    val all: Map<String, () -> PixelImage> =
        mapOf(
            "circle_in_square" to { circleInSquare() },
            "gradient_disc" to { gradientDisc() },
            "parallel_edges" to { parallelEdges() },
            "plate_with_glyph" to { plateWithGlyph() },
            "opaque_icon" to { opaqueIcon() },
            "logo" to { logo() },
        )
}
