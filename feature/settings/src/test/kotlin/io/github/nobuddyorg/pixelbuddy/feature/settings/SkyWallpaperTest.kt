package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.app.Application
import android.app.WallpaperManager
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WallpaperSync
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyStyle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SkyWallpaperTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dusk = PixelBuddyStyle()
    private val night = PixelBuddyStyle(sky = listOf(Color(0xFF05061A), Color(0xFF141833)))

    @Test
    fun `syncing sets the wallpaper only when the sky changed`() {
        assertTrue(SkyWallpaper.syncIfChanged(app, dusk, WallpaperTarget.Both))
        assertNotNull(shadowOf(WallpaperManager.getInstance(app)).getBitmap(WallpaperManager.FLAG_SYSTEM))

        // Same sky (a restart, a recomposition): nothing to do.
        assertFalse(SkyWallpaper.syncIfChanged(app, dusk, WallpaperTarget.Both))

        // The sky moved on (a new phase of the day), scanlines or another target: set again.
        assertTrue(SkyWallpaper.syncIfChanged(app, night, WallpaperTarget.Both))
        assertTrue(SkyWallpaper.syncIfChanged(app, night.copy(scanlines = !night.scanlines), WallpaperTarget.Both))
        assertTrue(SkyWallpaper.syncIfChanged(app, night, WallpaperTarget.Home))
    }

    @Test
    fun `a wallpaper that couldn't be set is tried again`() {
        shadowOf(WallpaperManager.getInstance(app)).setIsSetWallpaperAllowed(false)
        assertFalse(SkyWallpaper.syncIfChanged(app, dusk, WallpaperTarget.Home))

        shadowOf(WallpaperManager.getInstance(app)).setIsSetWallpaperAllowed(true)
        assertTrue(SkyWallpaper.syncIfChanged(app, dusk, WallpaperTarget.Home))
    }

    @Test
    fun `sync choices map to wallpaper targets`() {
        assertNull(WallpaperSync.Off.target)
        assertEquals(WallpaperTarget.Home, WallpaperSync.Home.target)
        assertEquals(WallpaperTarget.Lock, WallpaperSync.Lock.target)
        assertEquals(WallpaperTarget.Both, WallpaperSync.Both.target)
    }
}
