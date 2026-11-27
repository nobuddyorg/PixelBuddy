package io.github.nobuddyorg.pixelbuddy.core.data.icons

/**
 * How long icons took to render on this phone, from drawing the app's icon to the finished bitmap: the numbers
 * behind the #29 budget (about 20 ms per neon icon on the S24), shown under Settings → About. Only in memory, the
 * last [capacity] renders of each kind.
 */
class RenderTimings(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    /** [count] renders: half took at most [medianMs], nine in ten at most [p90Ms]. */
    data class Summary(
        val count: Int,
        val medianMs: Int,
        val p90Ms: Int,
    )

    private val neon = LongArray(capacity)
    private val pixel = LongArray(capacity)
    private var neonCount = 0
    private var pixelCount = 0

    @Synchronized
    fun record(
        neonStyle: Boolean,
        nanos: Long,
    ) {
        if (neonStyle) {
            neon[neonCount++ % capacity] = nanos
        } else {
            pixel[pixelCount++ % capacity] = nanos
        }
    }

    /** The neon or pixel renders so far, or null if there were none. */
    @Synchronized
    fun summary(neonStyle: Boolean): Summary? {
        val count = if (neonStyle) neonCount else pixelCount
        if (count == 0) return null
        val all = if (neonStyle) neon else pixel
        val sorted = all.copyOf(minOf(count, capacity)).also { it.sort() }

        fun percentile(p: Int) = ((sorted[(sorted.size - 1) * p / 100] + NANOS_PER_MS / 2) / NANOS_PER_MS).toInt()
        return Summary(count, percentile(MEDIAN), percentile(P90))
    }

    private companion object {
        const val DEFAULT_CAPACITY = 256
        const val NANOS_PER_MS = 1_000_000L
        const val MEDIAN = 50
        const val P90 = 90
    }
}
