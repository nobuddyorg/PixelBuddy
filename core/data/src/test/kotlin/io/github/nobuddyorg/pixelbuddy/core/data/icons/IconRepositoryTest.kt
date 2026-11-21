package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
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
