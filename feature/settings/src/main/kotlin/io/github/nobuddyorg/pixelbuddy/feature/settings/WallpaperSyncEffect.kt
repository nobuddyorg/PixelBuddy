package io.github.nobuddyorg.pixelbuddy.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WallpaperSync
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the system wallpaper equal to the sky while [sync] is on: when the sky, its dithering or the scanlines
 * change, also a sky that follows the sun moving on to the next phase, the wallpaper is set again. Call it on home,
 * inside the theme; it works while home is shown, never in the background.
 */
@Composable
fun KeepWallpaperInSync(sync: WallpaperSync) {
    val target = sync.target ?: return
    val context = LocalContext.current
    val style = PixelBuddyTheme.style
    LaunchedEffect(target, style.sky, style.dithering, style.scanlines) {
        withContext(Dispatchers.IO) { SkyWallpaper.syncIfChanged(context, style, target) }
    }
}

internal val WallpaperSync.target: WallpaperTarget?
    get() =
        when (this) {
            WallpaperSync.Off -> null
            WallpaperSync.Home -> WallpaperTarget.Home
            WallpaperSync.Lock -> WallpaperTarget.Lock
            WallpaperSync.Both -> WallpaperTarget.Both
        }
