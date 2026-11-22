package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconStyle
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Loads an app's original icon; a seam for tests. */
interface IconLoader {
    fun load(
        app: LauncherApp,
        densityDpi: Int,
    ): Drawable?

    /** Changes when the app is updated. */
    fun version(app: LauncherApp): Long
}

class LauncherIconLoader(
    private val context: Context,
) : IconLoader {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    override fun load(
        app: LauncherApp,
        densityDpi: Int,
    ): Drawable? =
        launcherApps
            .getActivityList(app.component.packageName, app.user)
            .firstOrNull { it.componentName == app.component }
            ?.getIcon(densityDpi)

    /**
     * The package's last update time for the main profile. Other profiles' package info isn't visible, so their
     * install time is used; the icon fingerprint still catches icon changes there.
     */
    override fun version(app: LauncherApp): Long =
        if (app.isMainProfile) {
            runCatching {
                context.packageManager
                    .getPackageInfo(
                        app.component.packageName,
                        0,
                    ).lastUpdateTime
            }.getOrDefault(0L)
        } else {
            launcherApps
                .getActivityList(app.component.packageName, app.user)
                .firstOrNull { it.componentName == app.component }
                ?.firstInstallTime ?: 0L
        }
}

/**
 * Pixelated app icons. Renders the source at 4 × grid, runs the [PixelEngine] and caches the result in memory and
 * on disk. Work runs on [dispatcher] with limited parallelism; the list asks for visible icons first, so they come
 * first.
 */
class IconRepository(
    private val loader: IconLoader,
    private val cache: IconCache,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2),
) {
    /** App id and density → verified base (version, icon fingerprint, density) for this session. */
    private val verified = ConcurrentHashMap<String, String>()

    private val _generation = MutableStateFlow(0)

    /** Increases whenever cached icons may be outdated; icon UIs reload when it changes. */
    val generation: StateFlow<Int> = _generation

    /** Number of icons rendered, for tests and diagnostics. */
    @Volatile
    var renderCount: Int = 0
        private set

    /** The pixelated icon of [app] in [style], or null if the app has no icon (anymore). */
    suspend fun icon(
        app: LauncherApp,
        style: IconStyle,
        densityDpi: Int,
        layer: IconLayer = IconLayer.Full,
    ): Bitmap? =
        withContext(dispatcher) {
            val id = app.key.id
            val session = "$id|$densityDpi"
            val styleKey = "$style|$layer"
            verified[session]?.let { base -> cache.get(id, base, styleKey)?.let { return@withContext it } }

            val drawable = loader.load(app, densityDpi) ?: return@withContext null
            val base = "${loader.version(app)}|${IconSourceRenderer.fingerprint(drawable)}|$densityDpi"
            verified[session] = base
            cache.get(id, base, styleKey)?.let { return@withContext it }

            val shape = IconSourceRenderer.shapeFor(drawable, style.shape)
            val effective = style.copy(shape = shape)
            val source = IconSourceRenderer.render(drawable, effective.sourceSize, layer, shape)
            val pixels = PixelEngine.render(source, effective)
            val bitmap = Bitmap.createBitmap(pixels.pixels, pixels.width, pixels.height, Bitmap.Config.ARGB_8888)
            renderCount++
            cache.put(id, base, styleKey, bitmap)
            bitmap
        }

    /**
     * Apps may have changed (installed, updated, icon theme): re-check fingerprints on the next request. Cheap: an
     * unchanged icon is served from the cache again.
     */
    fun recheck() {
        verified.clear()
        _generation.value++
    }

    /** Configuration changed (density, theme): also drop decoded icons from memory. */
    fun onConfigurationChanged() {
        cache.clearMemory()
        recheck()
    }
}
