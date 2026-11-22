package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Process
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconLook
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyles
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class FakeLoader : IconLoader {
        var drawable: Drawable? = icon(Color.RED)
        var version = 1L
        var loads = 0

        override fun load(
            app: LauncherApp,
            densityDpi: Int,
        ): Drawable? {
            loads++
            return drawable
        }

        override fun version(app: LauncherApp) = version
    }

    private val loader = FakeLoader()
    private val app =
        LauncherApp(
            key = AppKey("org.example", "org.example.Main", 0),
            label = "Example",
            component = ComponentName("org.example", "org.example.Main"),
            user = Process.myUserHandle(),
            isMainProfile = true,
        )
    private val style = IconPreset.SixteenBit.style

    private fun repository() = IconRepository(loader, IconCache(folder.root), Dispatchers.Unconfined)

    @Test
    fun `icon is rendered at the grid size`() =
        runTest {
            val bitmap = repository().icon(app, style, densityDpi = 420)!!

            assertEquals(style.grid, bitmap.width)
            assertEquals(style.grid, bitmap.height)
            // Masked: the corner is cut off, the middle has the colour.
            assertEquals(0, bitmap.getPixel(0, 0))
        }

    @Test
    fun `repeated requests are served from memory without loading the icon again`() =
        runTest {
            val repository = repository()
            repository.icon(app, style, 420)
            repository.icon(app, style, 420)

            assertEquals(1, repository.renderCount)
            assertEquals(1, loader.loads)
        }

    @Test
    fun `after a re-check an unchanged icon comes from the cache`() =
        runTest {
            val repository = repository()
            repository.icon(app, style, 420)

            repository.recheck()
            repository.icon(app, style, 420)

            assertEquals(1, repository.renderCount)
            assertEquals(2, loader.loads)
        }

    @Test
    fun `an app update renders again`() =
        runTest {
            val repository = repository()
            repository.icon(app, style, 420)

            loader.version = 2
            repository.recheck()
            repository.icon(app, style, 420)

            assertEquals(2, repository.renderCount)
        }

    @Test
    fun `an icon theme change is caught by the fingerprint`() =
        runTest {
            val repository = repository()
            val before = repository.icon(app, style, 420)!!

            loader.drawable = icon(Color.GREEN)
            repository.onConfigurationChanged()
            val after = repository.icon(app, style, 420)!!

            assertEquals(2, repository.renderCount)
            assertEquals(false, before.sameAs(after))
        }

    @Test
    fun `other styles and densities are rendered separately`() =
        runTest {
            val repository = repository()
            repository.icon(app, style, 420)
            repository.icon(app, IconPreset.Pocket.style, 420)
            repository.icon(app, style, 560)

            assertEquals(3, repository.renderCount)
        }

    @Test
    fun `rendered icons survive a restart on disk`() =
        runTest {
            repository().icon(app, style, 420)

            val restarted = repository()
            restarted.icon(app, style, 420)

            assertEquals(0, restarted.renderCount)
        }

    @Test
    fun `apps without an icon give null`() =
        runTest {
            loader.drawable = null

            assertNull(repository().icon(app, style, 420))
        }

    @Test
    fun `previews draw the source once per session and write nothing to disk`() =
        runTest {
            val repository = repository()

            val first = repository.preview(app, style, 420)!!
            repository.preview(app, style.copy(dithering = true, palette = Palette.Original), 420)
            assertEquals(1, loader.loads)
            assertEquals(style.grid, first.width)
            assertEquals(
                emptyList(),
                folder.root
                    .walk()
                    .filter { it.isFile }
                    .toList(),
            )

            // Another grid needs a source of another size.
            repository.preview(app, IconPreset.EightBit.style, 420)
            assertEquals(2, loader.loads)
            repository.endPreview()
            repository.preview(app, style, 420)
            assertEquals(3, loader.loads)
        }

    @Test
    fun `the original icon is drawn at the asked size`() =
        runTest {
            val original = repository().original(app, sizePx = 100, densityDpi = 420)!!

            assertEquals(100, original.width)
            // The plate at the edge, the white glyph in the middle.
            assertEquals(Color.RED, original.getPixel(2, 50))
            assertEquals(Color.WHITE, original.getPixel(50, 50))
            loader.drawable = null
            assertNull(repository().original(app, 100, 420))
            assertNull(repository().preview(app, style, 420))
        }

    @Test
    fun `prewarming renders every app and drops other styles from the cache`() =
        runTest {
            val repository = repository()
            val old = IconPreset.Neon.style
            repository.icon(app, old, 420)
            val other = app.copy(key = AppKey("org.other", "org.other.Main", 0), label = "Other")

            repository.prewarm(listOf(app, other), IconStyles(style), 420)

            assertEquals(3, repository.renderCount)
            val files =
                folder.root
                    .walk()
                    .filter { it.isFile }
                    .toList()
            assertEquals(2, files.size)
            // The kept style is served without rendering again.
            repository.icon(other, style, 420)
            assertEquals(3, repository.renderCount)
        }

    @Test
    fun `an app's own picture is drawn instead of its icon, and the icon comes back without it`() =
        runTest {
            val images = CustomIconStore(folder.newFolder("images"))
            val png =
                ByteArrayOutputStream().also {
                    createBitmap(64, 64).apply { eraseColor(Color.BLUE) }.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            val name =
                (
                    images.import(
                        ByteArrayInputStream(png.toByteArray()),
                    ) as CustomIconStore.ImportResult.Imported
                ).name
            val repository =
                IconRepository(loader, IconCache(folder.newFolder("cache")), Dispatchers.Unconfined, images = images)
            val look = IconLook(IconPreset.EightBit.style.copy(palette = Palette.Original), image = name)

            val picture = repository.icon(app, look, 420)!!
            assertEquals(Color.BLUE, picture.getPixel(6, 6))
            assertEquals(0, loader.loads)
            assertEquals(Color.BLUE, repository.preview(app, look, 420)!!.getPixel(6, 6))

            images.retain(emptySet())
            val fallback = repository.icon(app, look.copy(image = "e".repeat(64)), 420)!!
            assertEquals(Color.RED, fallback.getPixel(2, 6))
        }

    @Test
    fun `another layer of the icon is another icon`() =
        runTest {
            val repository = repository()

            val full = repository.icon(app, IconLook(style), 420)!!
            val foreground = repository.icon(app, IconLook(style, IconLayer.Foreground), 420)!!

            assertEquals(2, repository.renderCount)
            // Without the red plate, only the white glyph is left.
            assertEquals(0, foreground.getPixel(4, 12))
            assertEquals(true, full.getPixel(4, 12) != 0)
        }

    @Test
    fun `generation increases on every re-check`() {
        val repository = repository()
        val start = repository.generation.value

        repository.recheck()
        repository.onConfigurationChanged()

        assertEquals(start + 2, repository.generation.value)
    }

    private companion object {
        fun icon(color: Int): Drawable =
            AdaptiveIconDrawable(
                ColorDrawable(color),
                InsetDrawable(
                    ColorDrawable(Color.WHITE),
                    1f / 3,
                ),
            )
    }
}
