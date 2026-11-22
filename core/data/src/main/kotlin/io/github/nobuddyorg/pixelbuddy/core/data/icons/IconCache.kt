package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File
import java.security.MessageDigest

/**
 * Two-level cache for rendered icons: memory (bounded by bytes) and PNG files in the cache directory, which Android
 * may clear at any time. Files live in one folder per app and are named after the app's [base] (update time,
 * icon fingerprint, density) and the style, so a changed base replaces all old files of that app.
 */
class IconCache(
    private val directory: File,
    memoryBytes: Int = DEFAULT_MEMORY_BYTES,
) {
    private val memory =
        object : LruCache<String, Bitmap>(memoryBytes) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.allocationByteCount
        }

    fun get(
        app: String,
        base: String,
        style: String,
    ): Bitmap? {
        val key = memoryKey(app, base, style)
        memory.get(key)?.let { return it }
        val file = file(app, base, style)
        if (!file.isFile) return null
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null.also { file.delete() }
        memory.put(key, bitmap)
        return bitmap
    }

    fun put(
        app: String,
        base: String,
        style: String,
        bitmap: Bitmap,
    ) {
        memory.put(memoryKey(app, base, style), bitmap)
        val file = file(app, base, style)
        val folder = file.parentFile ?: return
        folder.mkdirs()
        // Files of an older base (app updated, icon theme changed) are no longer needed.
        val current = hash(base) + "_"
        folder
            .listFiles()
            .orEmpty()
            .filterNot { it.name.startsWith(current) }
            .forEach { it.delete() }
        val temp = File(file.path + ".tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        temp.renameTo(file)
    }

    /** Drops every cached icon of [app], in memory and on disk. */
    fun invalidate(app: String) {
        val prefix = "$app|"
        memory
            .snapshot()
            .keys
            .filter { it.startsWith(prefix) }
            .forEach(memory::remove)
        File(directory, hash(app)).deleteRecursively()
    }

    fun clearMemory() = memory.evictAll()

    /** Number of icons in memory, for tests. */
    internal val memoryCount: Int get() = memory.snapshot().size

    internal fun file(
        app: String,
        base: String,
        style: String,
    ) = File(File(directory, hash(app)), hash(base) + "_" + hash(style) + ".png")

    private fun memoryKey(
        app: String,
        base: String,
        style: String,
    ) = "$app|$base|$style"

    companion object {
        const val DEFAULT_MEMORY_BYTES = 8 * 1024 * 1024

        internal fun hash(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray())
                .take(16)
                .joinToString("") { "%02x".format(it) }
    }
}
