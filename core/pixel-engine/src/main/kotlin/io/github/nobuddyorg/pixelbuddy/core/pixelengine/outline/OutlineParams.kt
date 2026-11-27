package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

/**
 * Tuning of the outline icons (#29). Sizes are relative to the icon size, so results look the same at any
 * resolution. The defaults come from the prototype in `tools/icon-outline`.
 */
public data class OutlineParams(
    /** Side of the square the source is analysed at, in pixels. */
    val workSize: Int = 192,
    /** Colour clusters of the first over-segmentation. */
    val clusters: Int = 10,
    /** Regions smaller than this share of the foreground are folded into a neighbour. */
    val minRegion: Double = 0.004,
    /** Mean Lab gradient along a border below which two regions are merged. */
    val weakBorder: Double = 22.0,
    /** Regions thinner than this (of the size) are folded into a neighbour. */
    val thin: Double = 0.016,
    /** Internal contours shorter than this (of the size) are dropped; outer ones below half of it. */
    val minContour: Double = 0.08,
    /** Gaussian sigma along the arc length (of the size). */
    val smooth: Double = 0.012,
    /** Internal contours running this close to a longer one are dropped as duplicates (of the size). */
    val duplicate: Double = 0.035,
    /** Width of the silhouette stroke (of the size). */
    val outerWidth: Double = 0.036,
    /** Width of internal strokes (of the size). */
    val innerWidth: Double = 0.024,
    /** Glow layers: radius in stroke widths and opacity. */
    val glow: List<GlowLayer> = listOf(GlowLayer(1.8, 0.34), GlowLayer(3.2, 0.2)),
    /** Width of the bright core relative to the stroke. */
    val core: Double = 0.42,
    /** How much the core is mixed with white. */
    val coreWhiten: Double = 0.25,
    /** Saturation multiplier for stroke colours. */
    val saturationBoost: Double = 1.25,
    /** Minimum HSV value of stroke colours, so they stay bright on the dark launcher background. */
    val minValue: Double = 0.82,
    /** Empty margin around the outline (of the size). */
    val padding: Double = 0.1,
) {
    init {
        require(workSize in 16..1024) { "workSize must be 16..1024, was $workSize" }
        require(clusters in 1..64) { "clusters must be 1..64, was $clusters" }
        require(outerWidth > 0 && innerWidth > 0) { "stroke widths must be positive" }
        require(padding in 0.0..0.4) { "padding must be 0..0.4, was $padding" }
    }
}

/** One soft halo around the strokes, tinted with the stroke colour. */
public data class GlowLayer(
    /** Radius in silhouette stroke widths. */
    val radius: Double,
    /** Opacity 0..1. */
    val opacity: Double,
)

/** How strokes are coloured. */
public enum class OutlineColours {
    /** Colours taken from the icon along each contour, slightly boosted. */
    Sampled,

    /** A smooth sweep through the icon's main colours along the direction they are laid out in. */
    Sweep,

    /** The dominant brand colour for every stroke. */
    Single,
}
