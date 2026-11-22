package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.max

/**
 * Pictures the user picked as app icons (#14). Each is decoded once with size limits, scaled to fit [SIZE] × [SIZE]
 * and kept as a PNG in [directory], named after its SHA-256, so the same picture is stored once and the name can
 * be part of cache keys. Nothing refers to the original picture afterwards (no persisted URI grant).
 */
class CustomIconStore(
    private val directory: File,
) {
    /** How an import went. */
    sealed interface ImportResult {
        data class Imported(
            val name: String,
        ) : ImportResult

        /** Bigger than [MAX_BYTES] or [MAX_SIDE] pixels on a side. */
        data object TooLarge : ImportResult

        /** Not an image this phone can decode. */
        data object Unreadable : ImportResult

        /** Nothing visible: every pixel is transparent. */
        data object Empty : ImportResult
    }

    /** Imports the picture in [input]; blocking, call it off the main thread. The caller closes [input]. */
    fun import(input: InputStream): ImportResult {
        val bytes = input.readAtMost(MAX_BYTES) ?: return ImportResult.TooLarge
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return ImportResult.Unreadable
        if (width > MAX_SIDE || height > MAX_SIDE) return ImportResult.TooLarge
        // Decode at no less than twice the target size: enough for a sharp downscale, little memory.
        var sample = 1
        while (max(width, height) / (sample * 2) >= SIZE * 2) sample *= 2
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return ImportResult.Unreadable
        val icon = fitted(decoded, SIZE)
        decoded.recycle()

        val pixels = IntArray(SIZE * SIZE).also { icon.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
        if (pixels.none { it ushr 24 != 0 }) {
            icon.recycle()
            return ImportResult.Empty
        }
        val png = ByteArrayOutputStream().also { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        icon.recycle()
        val name = sha256(png)
        val file = file(name)
        if (!file.isFile) {
            directory.mkdirs()
            val temp = File(directory, "$name.tmp")
            temp.writeBytes(png)
            temp.renameTo(file)
        }
        return ImportResult.Imported(name)
    }

    /** The picture [name] scaled to [size] × [size], or null if it is gone or [name] isn't one of ours. */
    fun load(
        name: String,
        size: Int,
    ): PixelImage? {
        if (!isValidName(name)) return null
        val stored = BitmapFactory.decodeFile(file(name).path) ?: return null
        val scaled = fitted(stored, size)
        stored.recycle()
        val pixels = IntArray(size * size).also { scaled.getPixels(it, 0, size, 0, 0, size, size) }
        scaled.recycle()
        return PixelImage(size, size, pixels)
    }

    /** The stored PNG of [name], e.g. for an export, or null if it is gone or [name] isn't one of ours. */
    fun bytes(name: String): ByteArray? = if (isValidName(name)) file(name).takeIf { it.isFile }?.readBytes() else null

    /** Deletes every stored picture except [names], e.g. after an override was reset or its app uninstalled. */
    fun retain(names: Set<String>) {
        directory
            .listFiles()
            .orEmpty()
            .filter { file -> file.name.removeSuffix(".png") !in names || !file.name.endsWith(".png") }
            .forEach { it.delete() }
    }

    internal fun file(name: String): File {
        require(isValidName(name)) { "not a picture name: $name" }
        return File(directory, "$name.png")
    }

    companion object {
        /** Side of a stored picture in pixels: enough for the 32 px grid at 4× source size and more. */
        const val SIZE: Int = 256

        const val MAX_BYTES: Int = 20 * 1024 * 1024

        const val MAX_SIDE: Int = 16_384

        private val NAME = Regex("[0-9a-f]{64}")

        /** True for names [import] gives: a settings file can't point anywhere else. */
        fun isValidName(name: String): Boolean = NAME.matches(name)

        /** [source] scaled to fit a transparent [size] × [size] square, centred. */
        private fun fitted(
            source: Bitmap,
            size: Int,
        ): Bitmap {
            val out = createBitmap(size, size)
            val scale = size.toFloat() / max(source.width, source.height)
            val w = source.width * scale
            val h = source.height * scale
            val target = RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2)
            Canvas(out).drawBitmap(source, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }

        private fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** All of the stream, or null if it is longer than [limit] bytes. */
        private fun InputStream.readAtMost(limit: Int): ByteArray? {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = read(buffer)
                if (read < 0) return out.toByteArray()
                if (out.size() + read > limit) return null
                out.write(buffer, 0, read)
            }
        }
    }
}
