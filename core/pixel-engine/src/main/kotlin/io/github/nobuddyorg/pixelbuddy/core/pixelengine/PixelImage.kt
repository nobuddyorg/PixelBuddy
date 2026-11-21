package io.github.nobuddyorg.pixelbuddy.core.pixelengine

/** An image as ARGB pixels (not premultiplied), row by row. */
public class PixelImage(
    public val width: Int,
    public val height: Int,
    public val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0) { "size must be positive, was ${width}x$height" }
        require(pixels.size == width * height) { "expected ${width * height} pixels, got ${pixels.size}" }
    }

    public operator fun get(
        x: Int,
        y: Int,
    ): Int = pixels[y * width + x]

    override fun equals(other: Any?): Boolean =
        other is PixelImage && other.width == width && other.height == height && other.pixels.contentEquals(pixels)

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()

    override fun toString(): String = "PixelImage(${width}x$height)"
}
