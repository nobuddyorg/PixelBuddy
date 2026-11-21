package io.github.nobuddyorg.pixelbuddy.core.pixelengine

/** Colours an icon may use after pixelation. */
public sealed interface Palette {
    /** Keep the averaged original colours. */
    public data object Original : Palette

    /** The [colors] most representative colours of each icon (median cut). */
    public data class Adaptive(
        val colors: Int,
    ) : Palette {
        init {
            require(colors in 2..256) { "colors must be 2..256, was $colors" }
        }
    }

    /** A fixed list of opaque ARGB colours. */
    public data class Fixed(
        val name: String,
        val colors: List<Int>,
    ) : Palette {
        init {
            require(colors.isNotEmpty()) { "palette $name has no colours" }
        }
    }

    /** [shades] shades of [tint] from dark to light, chosen by brightness. */
    public data class MonochromeTint(
        val tint: Int,
        val shades: Int,
    ) : Palette {
        init {
            require(shades in 2..16) { "shades must be 2..16, was $shades" }
        }
    }
}

/** Outer shape of the icon. */
public enum class IconShape {
    /** Background and foreground clipped to a pixel-rounded square (no system icon frames). */
    Masked,

    /** Only the drawn shape, no background plate. */
    FreeStanding,
}

/**
 * How an icon is pixelated.
 *
 * @property grid output size in pixels: 12, 16, 24 or 32.
 * @property intensity palette blend: 0 keeps the averaged original colour, 1 uses the nearest palette colour.
 * @property dithering ordered (Bayer) dithering before choosing palette colours.
 * @property outline 1 px outline around the shape in [outlineColor].
 * @property alphaThreshold pixels with at least this alpha (0..255) become opaque, the rest transparent.
 * @property shape [IconShape.Masked] clips to a pixel-rounded square, inset by 1 px when there is an outline.
 */
public data class IconStyle(
    val grid: Int = 24,
    val palette: Palette = Palette.Adaptive(16),
    val intensity: Float = 1f,
    val dithering: Boolean = false,
    val outline: Boolean = false,
    val outlineColor: Int = DEFAULT_OUTLINE,
    val alphaThreshold: Int = 128,
    val shape: IconShape = IconShape.Masked,
) {
    init {
        require(grid in GRIDS) { "grid must be one of $GRIDS, was $grid" }
        require(intensity in 0f..1f) { "intensity must be 0..1, was $intensity" }
        require(alphaThreshold in 1..255) { "alphaThreshold must be 1..255, was $alphaThreshold" }
    }

    /** Source size the icon pipeline renders at: 4 × [grid]. */
    val sourceSize: Int get() = grid * SOURCE_SCALE

    public companion object {
        public val GRIDS: List<Int> = listOf(12, 16, 24, 32)
        public const val SOURCE_SCALE: Int = 4
        public const val DEFAULT_OUTLINE: Int = 0xFF07081A.toInt()
    }
}
