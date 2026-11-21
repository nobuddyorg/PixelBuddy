package io.github.nobuddyorg.pixelbuddy.core.pixelengine

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.math.min

/** Synthetic icons (no brand icons in the repository). */
internal object Fixtures {
    /**
     * A round icon: orange-to-purple gradient disc with a light square "glyph" and anti-aliased edge on a
     * transparent background.
     */
    fun roundIcon(size: Int): PixelImage {
        val pixels = IntArray(size * size)
        val c = (size - 1) / 2f
        val radius = size * 0.42f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val d = hypot(x - c, y - c)
                val coverage = (radius + 0.5f - d).coerceIn(0f, 1f)
                if (coverage == 0f) continue
                val t = y / (size - 1f)
                var color = Argb.lerp(0xFFFF8A3D.toInt(), 0xFF3D2A6B.toInt(), t)
                val glyph = size * 0.14f
                if (x - c in -glyph..glyph && y - c in -glyph * 2..glyph * 2) color = 0xFFF2EEFF.toInt()
                pixels[y * size + x] = (color and 0xFFFFFF) or ((coverage * 255).toInt() shl 24)
            }
        }
        return PixelImage(size, size, pixels)
    }

    /** A square split into four flat colours with a transparent border. */
    fun quadrants(size: Int): PixelImage {
        val colors = intArrayOf(0xFFE0413A.toInt(), 0xFF3FA7D6.toInt(), 0xFF6CC24A.toInt(), 0xFFF7D154.toInt())
        val border = size / 8
        val pixels =
            IntArray(size * size) { i ->
                val x = i % size
                val y = i / size
                if (min(min(x, y), min(size - 1 - x, size - 1 - y)) < border) {
                    0
                } else {
                    colors[(if (x < size / 2) 0 else 1) + (if (y < size / 2) 0 else 2)]
                }
            }
        return PixelImage(size, size, pixels)
    }

    fun readPng(file: File): PixelImage {
        val image = ImageIO.read(file)
        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        return PixelImage(image.width, image.height, pixels)
    }

    fun writePng(
        image: PixelImage,
        file: File,
    ) {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        out.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        file.parentFile.mkdirs()
        ImageIO.write(out, "png", file)
    }
}
