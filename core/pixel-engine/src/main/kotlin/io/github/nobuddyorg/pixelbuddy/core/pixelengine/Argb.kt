package io.github.nobuddyorg.pixelbuddy.core.pixelengine

/** Small helpers for packed ARGB ints. */
internal object Argb {
    fun alpha(c: Int): Int = c ushr 24

    fun red(c: Int): Int = c shr 16 and 0xFF

    fun green(c: Int): Int = c shr 8 and 0xFF

    fun blue(c: Int): Int = c and 0xFF

    fun of(
        a: Int,
        r: Int,
        g: Int,
        b: Int,
    ): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun opaque(c: Int): Int = c or (0xFF shl 24)

    /** Perceived brightness 0..255 (Rec. 601 weights). */
    fun luma(c: Int): Int = (299 * red(c) + 587 * green(c) + 114 * blue(c)) / 1000

    /** Squared distance with weights close to human perception (red 2, green 4, blue 3). */
    fun distance(
        a: Int,
        b: Int,
    ): Int {
        val dr = red(a) - red(b)
        val dg = green(a) - green(b)
        val db = blue(a) - blue(b)
        return 2 * dr * dr + 4 * dg * dg + 3 * db * db
    }

    fun lerp(
        from: Int,
        to: Int,
        t: Float,
    ): Int {
        fun mix(
            a: Int,
            b: Int,
        ) = kotlin.math.round(a + (b - a) * t).toInt()
        return of(255, mix(red(from), red(to)), mix(green(from), green(to)), mix(blue(from), blue(to)))
    }
}
