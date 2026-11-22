package io.github.nobuddyorg.pixelbuddy.feature.settings

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.unit.IntSize
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyStyle
import io.github.nobuddyorg.pixelbuddy.core.designsystem.SkyImage
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/** Where "Set as wallpaper" puts the sky. */
enum class WallpaperTarget(
    internal val flags: Int,
) {
    Home(WallpaperManager.FLAG_SYSTEM),
    Lock(WallpaperManager.FLAG_LOCK),
    Both(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
}

/** How the last "Set as wallpaper" went. */
enum class WallpaperStatus { Idle, Working, Done, Failed, NotAllowed }

/** Sets the launcher's sky as the system wallpaper. */
internal object SkyWallpaper {
    /**
     * Renders the sky in [style] at the exact screen size (portrait) and sets it for [target]. Blocking: call it
     * off the main thread. [context] should be an activity, which knows the screen it is on.
     */
    fun set(
        context: Context,
        style: PixelBuddyStyle,
        target: WallpaperTarget,
    ): WallpaperStatus {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) return WallpaperStatus.NotAllowed
        val size = context.screenSize()
        val bitmap =
            SkyImage.render(
                width = size.width,
                height = size.height,
                density = context.resources.displayMetrics.density,
                colors = style.sky,
                dithering = style.dithering,
                scanlines = style.scanlines,
            )
        return try {
            if (manager.setBitmap(bitmap, null, true, target.flags) !=
                0
            ) {
                WallpaperStatus.Done
            } else {
                WallpaperStatus.Failed
            }
        } catch (_: IOException) {
            WallpaperStatus.Failed
        } catch (_: SecurityException) {
            WallpaperStatus.Failed
        } finally {
            bitmap.recycle()
        }
    }

    /** The whole screen in pixels, system bars included, as portrait (width ≤ height). */
    internal fun Context.screenSize(): IntSize {
        val windows = getSystemService(WindowManager::class.java)
        val (width, height) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windows.maximumWindowMetrics.bounds.let { it.width() to it.height() }
            } else {
                // The way to read the full screen size before WindowMetrics (Android 11).
                @Suppress("DEPRECATION")
                val point = Point().also { windows.defaultDisplay.getRealSize(it) }
                point.x to point.y
            }
        return IntSize(max(1, min(width, height)), max(1, max(width, height)))
    }
}
