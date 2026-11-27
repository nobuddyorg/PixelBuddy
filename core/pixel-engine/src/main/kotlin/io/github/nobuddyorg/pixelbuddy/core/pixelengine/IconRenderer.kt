package io.github.nobuddyorg.pixelbuddy.core.pixelengine

import io.github.nobuddyorg.pixelbuddy.core.pixelengine.outline.IconOutline

/** One entry for every icon style: pixel art with the [PixelEngine], neon outlines with [IconOutline] (#29). */
public object IconRenderer {
    /**
     * Renders [source], drawn at [IconStyle.sourceSize], in [style]. Smooth neon icons are drawn at [sizePx] (the
     * size on screen), everything else at [IconStyle.grid] pixels. For neon, [colours] can be another picture of the
     * same icon to take the colours from (e.g. the full icon when [source] is its monochrome layer).
     */
    public fun render(
        source: PixelImage,
        style: IconStyle,
        sizePx: Int,
        colours: PixelImage? = null,
    ): PixelImage {
        val neon = style.neon ?: return PixelEngine.render(source, style)
        val outline = IconOutline.trace(source, colours = colours)
        return if (neon.pixelated) {
            val small = IconOutline.render(outline, style.grid, neon.colours, glow = false)
            PixelEngine.applyAlphaThreshold(small, style.alphaThreshold)
        } else {
            IconOutline.render(outline, sizePx, neon.colours, neon.glow)
        }
    }

    /** The side in pixels [render] gives for [style] at [sizePx] on screen. */
    public fun outputSize(
        style: IconStyle,
        sizePx: Int,
    ): Int = if (style.smooth) sizePx else style.grid
}
