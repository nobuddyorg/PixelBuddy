package io.github.nobuddyorg.pixelbuddy

import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconSourceRenderer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconRenderer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.NeonStyle
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Neon outline icons (#29) from the real icons of every app installed on the device: none may fail, and rendering
 * must not be far off. The emulators in CI run a debug build, so the limit here only catches big regressions; the
 * ≈ 20 ms budget is read on the S24 under Settings → About. Times per icon are logged (tag `NeonIcons`). Brand
 * icons are only used on the device, never stored.
 */
@RunWith(AndroidJUnit4::class)
class NeonIconsOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyInstalledAppBecomesANeonIcon() {
        val density = context.resources.displayMetrics.densityDpi
        val sizePx = IconRepository.iconPx(density)
        val activities =
            context.getSystemService(LauncherApps::class.java).getActivityList(null, Process.myUserHandle())
        assertTrue(activities.isNotEmpty(), "no apps found")
        val styles =
            listOf(
                IconPreset.NeonLines.style,
                IconPreset.NeonSingle.style,
                IconPreset.NeonLines.style.copy(grid = 16, neon = NeonStyle(pixelated = true)),
            )
        val times = mutableListOf<Double>()
        for (activity in activities) {
            val drawable = activity.getIcon(density)
            for (style in styles) {
                val start = System.nanoTime()
                val (shapes, colours) = IconSourceRenderer.neonSources(drawable, style.sourceSize, IconLayer.Full)
                val image = IconRenderer.render(shapes, style, sizePx, colours)
                val ms = (System.nanoTime() - start) / NANOS_PER_MS
                assertEquals(IconRenderer.outputSize(style, sizePx), image.width, "${activity.label}")
                if (style.smooth) {
                    times += ms
                    Log.i(TAG, "%s: %.1f ms, %d visible pixels".format(activity.label, ms, image.visible()))
                }
            }
        }
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        Log.i(TAG, "%d icons at %d px: median %.1f ms, max %.1f ms".format(sorted.size, sizePx, median, sorted.last()))
        assertTrue(median < MEDIAN_LIMIT_MS, "median $median ms per neon icon")
    }

    private fun PixelImage.visible() = pixels.count { (it ushr 24) > 0 }

    private companion object {
        const val TAG = "NeonIcons"
        const val NANOS_PER_MS = 1e6

        /** Generous for a debug build on an emulator; the phone's release build is far faster. */
        const val MEDIAN_LIMIT_MS = 500.0
    }
}
