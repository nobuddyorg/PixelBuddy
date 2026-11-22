package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CustomIconStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val store by lazy { CustomIconStore(folder.root) }

    private fun png(
        width: Int,
        height: Int,
        color: Int = Color.RED,
    ): InputStream {
        val bitmap = createBitmap(width, height).apply { eraseColor(color) }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        return ByteArrayInputStream(bytes)
    }

    private fun imported(input: InputStream): String =
        assertIs<CustomIconStore.ImportResult.Imported>(store.import(input)).name

    @Test
    fun `a picture is scaled to fit 256 px, centred on transparency`() {
        val name = imported(png(1200, 600))

        val stored = BitmapFactory.decodeFile(store.file(name).path)
        assertEquals(256 to 256, stored.width to stored.height)
        // 2:1 fits as 256 × 128 in the middle.
        assertEquals(0, stored.getPixel(128, 10))
        assertEquals(Color.RED, stored.getPixel(128, 128))
        assertTrue(CustomIconStore.isValidName(name))
    }

    @Test
    fun `the same picture is stored once under the same name`() {
        val first = imported(png(300, 300))
        val second = imported(png(300, 300))

        assertEquals(first, second)
        assertEquals(1, folder.root.listFiles()!!.size)
    }

    @Test
    fun `transparent parts stay transparent`() {
        val bitmap =
            createBitmap(64, 64).apply {
                eraseColor(Color.TRANSPARENT)
                for (x in 16 until 48) for (y in 16 until 48) setPixel(x, y, Color.BLUE)
            }
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

        val pixels = store.load(imported(ByteArrayInputStream(bytes)), 64)!!

        assertEquals(0, pixels.pixels[0])
        assertEquals(Color.BLUE, pixels.pixels[32 * 64 + 32])
    }

    @Test
    fun `a completely transparent picture is refused`() {
        assertEquals(CustomIconStore.ImportResult.Empty, store.import(png(50, 50, Color.TRANSPARENT)))
        assertEquals(
            0,
            folder.root
                .listFiles()
                .orEmpty()
                .size,
        )
    }

    @Test
    fun `corrupt files are refused`() {
        val corrupt = ByteArrayInputStream(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4))

        assertEquals(CustomIconStore.ImportResult.Unreadable, store.import(corrupt))
        assertEquals(CustomIconStore.ImportResult.Unreadable, store.import(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `oversized files and pictures are refused before decoding them`() {
        val huge =
            object : InputStream() {
                var left = CustomIconStore.MAX_BYTES + 1L

                override fun read(): Int = if (left-- > 0) 0 else -1
            }
        assertEquals(CustomIconStore.ImportResult.TooLarge, store.import(huge))
        assertEquals(CustomIconStore.ImportResult.TooLarge, store.import(png(CustomIconStore.MAX_SIDE + 1, 2)))
    }

    @Test
    fun `stored pictures load at any size, unknown names don't`() {
        val name = imported(png(400, 400))

        assertEquals(96, store.load(name, 96)!!.width)
        assertNull(store.load("f".repeat(64), 96))
        assertNull(store.load("../settings", 96))
    }

    @Test
    fun `pictures no override uses are deleted`() {
        val kept = imported(png(100, 100, Color.GREEN))
        val dropped = imported(png(100, 100, Color.BLUE))
        folder.newFile("leftover.tmp")

        store.retain(setOf(kept))

        assertEquals(listOf("$kept.png"), folder.root.list()!!.toList())
        assertNull(store.load(dropped, 32))
    }
}
