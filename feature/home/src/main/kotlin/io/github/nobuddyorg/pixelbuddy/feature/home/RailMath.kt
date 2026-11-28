package io.github.nobuddyorg.pixelbuddy.feature.home

import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppSorter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

/** One rail entry: [FAVORITES], `A`–`Z` or [AppSorter.OTHER_SECTION]. */
internal data class RailEntry(
    val key: String,
    val active: Boolean,
)

internal object Rail {
    /** The rail's first entry: back to the favorites. */
    const val FAVORITES: String = "★"

    val KEYS: List<String> = listOf(FAVORITES) + ('A'..'Z').map(Char::toString) + AppSorter.OTHER_SECTION

    /** All entries; letters without apps are inactive. */
    fun entries(sections: Set<String>): List<RailEntry> = KEYS.map { RailEntry(it, it == FAVORITES || it in sections) }

    /** The entry under [y] on a rail of [height] with [count] equal cells, clamped to the rail. */
    fun indexAt(
        y: Float,
        height: Float,
        count: Int,
    ): Int {
        if (count <= 0 || height <= 0f) return 0
        return floor(y / (height / count)).toInt().coerceIn(0, count - 1)
    }

    /** The active entry nearest to [index]; on a tie the one above. Null if none is active. */
    fun nearestActive(
        index: Int,
        entries: List<RailEntry>,
    ): Int? {
        if (entries.isEmpty()) return null
        for (distance in 0..entries.size) {
            val above = index - distance
            val below = index + distance
            if (above in entries.indices && entries[above].active) return above
            if (below in entries.indices && entries[below].active) return below
        }
        return null
    }

    /**
     * Magnification of an entry [distance] cells away from the finger: a stepped wave (×2, ×1.5, ×1.25) like
     * pixel sprites, flat without motion.
     */
    fun waveScale(
        distance: Float,
        reducedMotion: Boolean = false,
    ): Float =
        when {
            reducedMotion -> 1f
            abs(distance) < 1f -> 2f
            abs(distance) < 2f -> 1.5f
            abs(distance) < 3f -> 1.25f
            else -> 1f
        }

    /** How many cells above and below the finger the rail bends. */
    const val BEND_CELLS = 6f

    /**
     * How far (px, to the left) an entry [distance] cells from the finger moves while the finger is [pull] px left
     * of the rail: the whole [pull] at the finger, less further away, nothing from [BEND_CELLS] on (a smooth
     * curve), in whole [step]s like pixels.
     */
    fun bend(
        distance: Float,
        pull: Float,
        step: Float,
    ): Float {
        if (pull <= 0f || abs(distance) >= BEND_CELLS) return 0f
        val curve = (1f + cos(PI.toFloat() * distance / BEND_CELLS)) / 2f
        val px = pull * curve
        return if (step > 0f) (px / step).roundToInt() * step else px
    }
}
