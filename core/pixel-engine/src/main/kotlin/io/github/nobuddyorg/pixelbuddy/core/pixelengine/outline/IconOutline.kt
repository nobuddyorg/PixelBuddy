package io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline

import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Automatic neon outline icons (#29): an app icon becomes smooth, consistently thick strokes with a soft tinted
 * glow. Geometry ([trace]) and rendering ([render]) are separate, so one traced outline can be rendered in every
 * colour mode and size. Deterministic; ported from the prototype in `tools/icon-outline`.
 */
public object IconOutline {
    /**
     * Finds the silhouette and the important internal borders of [source] (any size, transparent or opaque). The
     * stroke colours come from [colours] if given: another picture of the same icon, aligned with [source], such as
     * the full icon when [source] is its monochrome layer. A light glyph on a plate there still gives the plate
     * colour.
     */
    public fun trace(
        source: PixelImage,
        params: OutlineParams = OutlineParams(),
        colours: PixelImage? = null,
    ): Outline {
        val image = WorkImage.of(source, params.workSize)
        val lab = Primitives.lab(image.rgb)
        var mask = Segmentation.foreground(image, lab)
        val plate = Segmentation.plateGlyph(image, lab, mask)
        val labels: IntArray
        if (plate != null) {
            // outline the glyph, colour from the plate
            mask = plate.glyph
            labels = IntArray(mask.size) { if (mask[it]) 1 else 0 }
        } else {
            labels = Segmentation.segment(image, mask, params)
        }
        val sigma = params.smooth * params.workSize
        val contours =
            Contours.cleanup(
                Contours.trace(labels, image.size).map {
                    Contours.smooth(it, sigma)
                },
                image.size,
                params,
            )
        if (colours == null) return Outline(params, image, mask, labels, plate?.colour, contours)
        val colourImage = WorkImage.of(colours, params.workSize)
        val colourLab = Primitives.lab(colourImage.rgb)
        val colourPlate =
            Segmentation.plateGlyph(
                colourImage,
                colourLab,
                Segmentation.foreground(colourImage, colourLab),
            )
        // sample only where the colour picture has paint, unless that leaves nothing
        val painted = BooleanArray(mask.size) { mask[it] && colourImage.alpha[it] > 0.5 }
        val colourMask = if (painted.any { it }) painted else mask
        return Outline(params, colourImage, colourMask, labels, plate?.colour ?: colourPlate?.colour, contours)
    }

    /**
     * Renders [outline] as a transparent [size] px image: anti-aliased strokes with round joins and caps, a bright
     * core and, with [glow], tinted halo layers.
     */
    public fun render(
        outline: Outline,
        size: Int,
        colours: OutlineColours = OutlineColours.Sampled,
        glow: Boolean = true,
    ): PixelImage {
        require(size > 0) { "size must be positive, was $size" }
        val p = outline.params
        val strokeColours = Colouring.colours(outline, colours, p)
        val scale = size * (1 - 2 * p.padding) / outline.image.size
        val offset = size * p.padding
        val stroke = Layer(size)
        val core = Layer(size)
        outline.contours.forEachIndexed { k, c ->
            val base = (if (c.outer) p.outerWidth else p.innerWidth) * size
            val xs = DoubleArray(c.size) { c.xs[it] * scale + offset }
            val ys = DoubleArray(c.size) { c.ys[it] * scale + offset }
            stroke.draw(xs, ys, c.closed, strokeColours[k], max(1.0, base), 0.0)
            core.draw(xs, ys, c.closed, strokeColours[k], max(0.5, base * p.core), p.coreWhiten)
        }
        val out = Canvas(size)
        if (glow) {
            val premultiplied = Array(3) { ch -> DoubleArray(size * size) { stroke.coverage[it] * stroke.rgb[ch][it] } }
            for (layer in p.glow) {
                val sigma = p.outerWidth * size * layer.radius / 2
                val alpha = Primitives.fastBlur(stroke.coverage, size, sigma)
                val blurred = Array(3) { Primitives.fastBlur(premultiplied[it], size, sigma) }
                val colour = DoubleArray(3)
                for (i in alpha.indices) {
                    val a = alpha[i]
                    if (a <= 0) continue
                    for (c in 0 until 3) colour[c] = (blurred[c][i] / max(a, 1e-6)).coerceIn(0.0, 1.0)
                    out.over(i, (a * GLOW_GAIN).coerceAtMost(1.0) * layer.opacity, colour)
                }
            }
        }
        out.over(stroke)
        out.over(core)
        return out.toImage()
    }

    /** Glow alpha gain: a blurred thin stroke is faint, this keeps the halo visible near the stroke. */
    private const val GLOW_GAIN = 1.8
}

/** A traced icon: the contours plus what colouring needs. */
public class Outline internal constructor(
    internal val params: OutlineParams,
    internal val image: WorkImage,
    internal val mask: BooleanArray,
    internal val labels: IntArray,
    internal val plateColour: DoubleArray?,
    /** Strokes, longest first; points are in work pixels (0..[OutlineParams.workSize]). */
    public val contours: List<OutlineContour>,
) {
    /** The icon was a light glyph on a uniform plate: the glyph is outlined in the plate colour. */
    public val onPlate: Boolean get() = plateColour != null
}

/** Coverage and colour of one stroke layer; overlapping strokes keep the higher coverage. */
private class Layer(
    private val size: Int,
) {
    val coverage = DoubleArray(size * size)
    val rgb = Array(3) { DoubleArray(size * size) }

    fun draw(
        xs: DoubleArray,
        ys: DoubleArray,
        closed: Boolean,
        colours: StrokeColours,
        width: Double,
        whiten: Double,
    ) {
        val n = xs.size
        val segments =
            if (n == 1) {
                1
            } else if (closed) {
                n
            } else {
                n - 1
            }
        for (i in 0 until segments) {
            val j = if (n == 1) 0 else (i + 1) % n
            capsule(xs[i], ys[i], xs[j], ys[j], width / 2, colours, i, j, whiten)
        }
    }

    /** An anti-aliased segment with round caps; consecutive capsules give round joins. */
    private fun capsule(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        half: Double,
        colours: StrokeColours,
        i: Int,
        j: Int,
        whiten: Double,
    ) {
        val dx = x1 - x0
        val dy = y1 - y0
        val length2 = dx * dx + dy * dy
        val left = max(0, floor(min(x0, x1) - half - 1).toInt())
        val right = min(size - 1, ceil(max(x0, x1) + half + 1).toInt())
        val top = max(0, floor(min(y0, y1) - half - 1).toInt())
        val bottom = min(size - 1, ceil(max(y0, y1) + half + 1).toInt())
        for (py in top..bottom) {
            val cy = py + 0.5
            for (px in left..right) {
                val cx = px + 0.5
                val t = if (length2 > 0) (((cx - x0) * dx + (cy - y0) * dy) / length2).coerceIn(0.0, 1.0) else 0.0
                val ex = cx - (x0 + t * dx)
                val ey = cy - (y0 + t * dy)
                val distance = sqrt(ex * ex + ey * ey)
                val cover = (half - distance + 0.5).coerceIn(0.0, 1.0)
                val index = py * size + px
                if (cover <= coverage[index]) continue
                coverage[index] = cover
                rgb[0][index] = mix(colours.r[i], colours.r[j], t, whiten)
                rgb[1][index] = mix(colours.g[i], colours.g[j], t, whiten)
                rgb[2][index] = mix(colours.b[i], colours.b[j], t, whiten)
            }
        }
    }

    private fun mix(
        a: Double,
        b: Double,
        t: Double,
        whiten: Double,
    ): Double = (a + t * (b - a)) * (1 - whiten) + whiten
}

/** Premultiplied source-over compositing. */
private class Canvas(
    private val size: Int,
) {
    private val alpha = DoubleArray(size * size)
    private val rgb = Array(3) { DoubleArray(size * size) }

    fun over(
        i: Int,
        a: Double,
        colour: DoubleArray,
    ) {
        for (c in 0 until 3) rgb[c][i] = colour[c] * a + rgb[c][i] * (1 - a)
        alpha[i] = a + alpha[i] * (1 - a)
    }

    fun over(layer: Layer) {
        val colour = DoubleArray(3)
        for (i in alpha.indices) {
            val a = layer.coverage[i]
            if (a <= 0) continue
            for (c in 0 until 3) colour[c] = layer.rgb[c][i]
            over(i, a, colour)
        }
    }

    fun toImage(): PixelImage =
        PixelImage(
            size,
            size,
            IntArray(size * size) { i ->
                val a = (alpha[i] * 255).roundToInt()
                if (a == 0) {
                    0
                } else {
                    val channel = { c: Int -> (rgb[c][i] / alpha[i] * 255).roundToInt().coerceIn(0, 255) }
                    (a shl 24) or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
                }
            },
        )
}
