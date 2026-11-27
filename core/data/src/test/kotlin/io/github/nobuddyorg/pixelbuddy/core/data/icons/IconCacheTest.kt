package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun bitmap(color: Int) = Bitmap.createBitmap(IntArray(4) { color }, 2, 2, Bitmap.Config.ARGB_8888)

    @Test
    fun `icons are found in memory and on disk`() {
        val cache = IconCache(folder.root)
        cache.put("app", "v1", "style", bitmap(Color.RED))

        assertNotNull(cache.get("app", "v1", "style"))
        // A new instance has an empty memory cache but finds the PNG.
        val reopened = IconCache(folder.root)
        assertEquals(0, reopened.memoryCount)
        assertEquals(Color.RED, reopened.get("app", "v1", "style")!!.getPixel(0, 0))
        assertEquals(1, reopened.memoryCount)
    }

    @Test
    fun `misses for other styles, bases and apps`() {
        val cache = IconCache(folder.root)
        cache.put("app", "v1", "style", bitmap(Color.RED))

        assertNull(cache.get("app", "v1", "other style"))
        assertNull(cache.get("app", "v2", "style"))
        assertNull(cache.get("other app", "v1", "style"))
    }

    @Test
    fun `a new base replaces the app's old files but keeps other styles of the same base`() {
        val cache = IconCache(folder.root)
        cache.put("app", "v1", "a", bitmap(Color.RED))
        cache.put("app", "v1", "b", bitmap(Color.RED))
        assertTrue(cache.file("app", "v1", "a").isFile && cache.file("app", "v1", "b").isFile)

        cache.put("app", "v2", "a", bitmap(Color.BLUE))

        assertFalse(cache.file("app", "v1", "a").exists())
        assertFalse(cache.file("app", "v1", "b").exists())
        assertTrue(cache.file("app", "v2", "a").isFile)
    }

    @Test
    fun `invalidate drops an app from memory and disk`() {
        val cache = IconCache(folder.root)
        cache.put("app", "v1", "style", bitmap(Color.RED))
        cache.put("other", "v1", "style", bitmap(Color.RED))

        cache.invalidate("app")

        assertNull(cache.get("app", "v1", "style"))
        assertNotNull(cache.get("other", "v1", "style"))
    }

    @Test
    fun `corrupt files are treated as a miss and removed`() {
        val cache = IconCache(folder.root)
        val file = cache.file("app", "v1", "style")
        file.parentFile!!.mkdirs()
        file.writeText("not a png")

        assertNull(cache.get("app", "v1", "style"))
        assertFalse(file.exists())
    }

    @Test
    fun `memory is bounded and can be cleared`() {
        val cache = IconCache(folder.root, memoryBytes = 2 * 16)
        repeat(5) { cache.put("app$it", "v1", "style", bitmap(Color.RED)) }

        assertTrue(cache.memoryCount <= 2)
        cache.clearMemory()
        assertEquals(0, cache.memoryCount)
    }

    // Budgets (#56)

    private fun files() =
        folder.root
            .walk()
            .filter { it.isFile }
            .toList()

    @Test
    fun `icons of removed apps are deleted from memory and disk`() {
        val cache = IconCache(folder.root)
        listOf("kept", "gone").forEach { cache.put(it, "v1", "style", bitmap(Color.RED)) }

        cache.retainApps(setOf("kept"))

        assertEquals(1, cache.memoryCount)
        assertEquals(1, files().size)
        assertNotNull(cache.get("kept", "v1", "style"))
        assertNull(IconCache(folder.root).get("gone", "v1", "style"))
    }

    @Test
    fun `changing the style again and again keeps one style on disk`() {
        val cache = IconCache(folder.root)
        repeat(5) { round ->
            (1..20).forEach { cache.put("app$it", "v1", "style$round", bitmap(Color.RED)) }
            cache.retainStyles(setOf("style$round"))
        }

        assertEquals(20, files().size)
    }

    @Test
    fun `300 apps stay within the memory budget`() {
        val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val budget = 300 * icon.allocationByteCount / 2
        val cache = IconCache(folder.root, memoryBytes = budget)

        (1..300).forEach { cache.put("app$it", "v1", "style", icon) }

        assertTrue(cache.memoryBytes <= budget)
        // The most recent icons are the ones kept.
        assertNotNull(cache.get("app300", "v1", "style"))
    }

    @Test
    fun `when Android needs memory only a quarter stays`() {
        val icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val cache = IconCache(folder.root, memoryBytes = 40 * icon.allocationByteCount)
        (1..40).forEach { cache.put("app$it", "v1", "style", icon) }

        cache.trimMemory()

        assertEquals(10, cache.memoryCount)
        // The files are still there, so icons come back without rendering.
        assertEquals(40, files().size)
    }
}
