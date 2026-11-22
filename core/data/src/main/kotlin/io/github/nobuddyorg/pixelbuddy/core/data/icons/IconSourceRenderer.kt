package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconShape
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import kotlinx.serialization.Serializable

/** Which part of an adaptive icon is the source. Stored in the settings (#14): don't rename. */
@Serializable
enum class IconLayer {
    /** Background and foreground. */
    Full,

    /** Foreground only. */
    Foreground,

    /** The themed-icon monochrome layer (Android 13+), drawn in [MONOCHROME_TINT]; falls back to the foreground. */
    Monochrome,
}

/**
 * Draws an app's icon into an ARGB buffer for the pixel engine. Adaptive icons are drawn layer by layer, without
 * the system mask, so Samsung's frames and squircles never appear; the engine applies PixelBuddy's own mask.
 */
object IconSourceRenderer {
    /** White, so palettes and tints can colour it. */
    const val MONOCHROME_TINT: Int = 0xFFFFFFFF.toInt()

    /**
     * Adaptive layers are 108 dp with the visible icon in the middle 72 dp; this scale makes those 72 dp fill the
     * output.
     */
    private const val ADAPTIVE_SCALE = 108f / 72f

    fun isAdaptive(drawable: Drawable): Boolean = drawable is AdaptiveIconDrawable

    /** The shape to use for [drawable]: legacy icons have their own outline, so they are never masked. */
    fun shapeFor(
        drawable: Drawable,
        preferred: IconShape,
    ): IconShape = if (isAdaptive(drawable)) preferred else IconShape.FreeStanding

    fun render(
        drawable: Drawable,
        size: Int,
        layer: IconLayer = IconLayer.Full,
        shape: IconShape = IconShape.Masked,
    ): PixelImage {
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            drawAdaptive(canvas, drawable, size, layer, shape)
        } else {
            draw(canvas, drawable, 0, size)
        }
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        bitmap.recycle()
        return PixelImage(size, size, pixels)
    }

    private fun drawAdaptive(
        canvas: Canvas,
        icon: AdaptiveIconDrawable,
        size: Int,
        layer: IconLayer,
        shape: IconShape,
    ) {
        val full = (size * ADAPTIVE_SCALE).toInt()
        val offset = (size - full) / 2
        val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) icon.monochrome else null
        when {
            layer == IconLayer.Monochrome && monochrome != null -> {
                val tinted =
                    monochrome.mutate().apply {
                        colorFilter =
                            PorterDuffColorFilter(MONOCHROME_TINT, PorterDuff.Mode.SRC_IN)
                    }
                draw(canvas, tinted, offset, full)
            }

            layer == IconLayer.Full && shape == IconShape.Masked -> {
                icon.background?.let { draw(canvas, it, offset, full) }
                icon.foreground?.let { draw(canvas, it, offset, full) }
            }

            else -> {
                icon.foreground?.let { draw(canvas, it, offset, full) }
            }
        }
    }

    private fun draw(
        canvas: Canvas,
        drawable: Drawable,
        offset: Int,
        size: Int,
    ) {
        val old = drawable.copyBounds()
        drawable.setBounds(offset, offset, offset + size, offset + size)
        drawable.draw(canvas)
        drawable.bounds = old
    }

    /**
     * A cheap fingerprint of how [drawable] looks (16 × 16 render), to notice icon changes without a package update,
     * e.g. from Galaxy Themes.
     */
    fun fingerprint(drawable: Drawable): Long {
        val pixels = render(drawable, FINGERPRINT_SIZE, IconLayer.Full, IconShape.Masked).pixels
        var hash = FNV_OFFSET
        for (p in pixels) {
            hash = (hash xor p.toLong()) * FNV_PRIME
        }
        return hash
    }

    private const val FINGERPRINT_SIZE = 16
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L
}
