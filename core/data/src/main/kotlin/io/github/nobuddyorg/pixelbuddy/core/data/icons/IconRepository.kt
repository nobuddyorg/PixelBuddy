package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppShortcut
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconLook
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyles
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconShape
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconStyle
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelEngine
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
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

    /** A shortcut's icon (#17), or null if it has none or is gone. */
    fun loadShortcut(
        shortcut: AppShortcut,
        densityDpi: Int,
    ): Drawable? = null
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

    override fun loadShortcut(
        shortcut: AppShortcut,
        densityDpi: Int,
    ): Drawable? =
        try {
            val query =
                LauncherApps
                    .ShortcutQuery()
                    .setPackage(shortcut.app.component.packageName)
                    .setShortcutIds(listOf(shortcut.id))
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                    )
            launcherApps
                .getShortcuts(query, shortcut.app.user)
                ?.firstOrNull()
                ?.let { launcherApps.getShortcutIconDrawable(it, densityDpi) }
        } catch (_: SecurityException) {
            // No longer the default home app.
            null
        } catch (_: IllegalStateException) {
            // The profile got locked.
            null
        }

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
    /** For [prewarm]: one at a time, so icons on screen keep [dispatcher] to themselves. */
    private val background: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    /** Pictures imported as app icons (#14). */
    private val images: CustomIconStore? = null,
) {
    /** Drawn sources for [preview], by app, density, size and shape: drawn once per editor session. */
    private val previewSources = ConcurrentHashMap<String, Pair<IconShape, PixelImage>>()

    /** Shortcut icons (#17), only in memory: few, and only while a menu is open. */
    private val shortcutIcons = ConcurrentHashMap<String, Bitmap>()

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
    ): Bitmap? = icon(app, IconLook(style, layer), densityDpi)

    /** The pixelated icon of [app] as [look] says; an imported picture that is gone falls back to the app's icon. */
    suspend fun icon(
        app: LauncherApp,
        look: IconLook,
        densityDpi: Int,
    ): Bitmap? = withContext(dispatcher) { cachedIcon(app, look, densityDpi) }

    private fun cachedIcon(
        app: LauncherApp,
        look: IconLook,
        densityDpi: Int,
    ): Bitmap? {
        val image = look.image
        if (image != null) imageIcon(app, look.style, image, densityDpi)?.let { return it }
        val style = look.style
        val layer = look.layer
        val id = app.key.id
        val session = "$id|$densityDpi"
        val styleKey = styleKey(IconLook(style, layer))
        verified[session]?.let { base -> cache.get(id, base, styleKey)?.let { return it } }

        val drawable = loader.load(app, densityDpi) ?: return null
        val base = "${loader.version(app)}|${IconSourceRenderer.fingerprint(drawable)}|$densityDpi"
        verified[session] = base
        cache.get(id, base, styleKey)?.let { return it }

        val shape = IconSourceRenderer.shapeFor(drawable, style.shape)
        val effective = style.copy(shape = shape)
        val source = IconSourceRenderer.render(drawable, effective.sourceSize, layer, shape)
        val bitmap = PixelEngine.render(source, effective).toBitmap()
        renderCount++
        cache.put(id, base, styleKey, bitmap)
        return bitmap
    }

    /** [app] drawn from the imported picture [image], or null if the picture is gone. */
    private fun imageIcon(
        app: LauncherApp,
        style: IconStyle,
        image: String,
        densityDpi: Int,
    ): Bitmap? {
        val base = "image|$image|$densityDpi"
        val styleKey = styleKey(IconLook(style, image = image))
        cache.get(app.key.id, base, styleKey)?.let { return it }
        val source = images?.load(image, style.sourceSize) ?: return null
        val bitmap = PixelEngine.render(source, style).toBitmap()
        renderCount++
        cache.put(app.key.id, base, styleKey, bitmap)
        return bitmap
    }

    /**
     * [app] in [style] for the icon editor's live preview. The source is drawn once per session ([endPreview]
     * forgets it) and nothing is cached on disk, so trying styles leaves no files behind. A cancelled preview stops
     * before rendering, so only the latest style costs time.
     */
    suspend fun preview(
        app: LauncherApp,
        style: IconStyle,
        densityDpi: Int,
    ): Bitmap? = preview(app, IconLook(style), densityDpi)

    /** Like [preview] for a whole [look]: another layer of the app's icon or an imported picture. */
    suspend fun preview(
        app: LauncherApp,
        look: IconLook,
        densityDpi: Int,
    ): Bitmap? =
        withContext(dispatcher) {
            val style = look.style
            val key = "${app.key.id}|$densityDpi|${style.sourceSize}|${style.shape}|${look.layer}|${look.image}"
            val (shape, source) =
                previewSources[key] ?: run {
                    val image = look.image?.let { images?.load(it, style.sourceSize) }
                    if (image != null) {
                        style.shape to image
                    } else {
                        val drawable = loader.load(app, densityDpi) ?: return@withContext null
                        val shape = IconSourceRenderer.shapeFor(drawable, style.shape)
                        shape to IconSourceRenderer.render(drawable, style.sourceSize, look.layer, shape)
                    }.also { previewSources[key] = it }
                }
            ensureActive()
            PixelEngine.render(source, style.copy(shape = shape)).toBitmap()
        }

    /** [shortcut]'s icon pixelated in [style] (#17), or null if it has none. */
    suspend fun shortcutIcon(
        shortcut: AppShortcut,
        style: IconStyle,
        densityDpi: Int,
    ): Bitmap? =
        withContext(dispatcher) {
            val key = "${shortcut.key}|$style|$densityDpi"
            shortcutIcons[key] ?: run {
                val drawable = loader.loadShortcut(shortcut, densityDpi) ?: return@withContext null
                val shape = IconSourceRenderer.shapeFor(drawable, style.shape)
                val effective = style.copy(shape = shape)
                val source = IconSourceRenderer.render(drawable, effective.sourceSize, IconLayer.Full, shape)
                ensureActive()
                PixelEngine.render(source, effective).toBitmap().also {
                    renderCount++
                    shortcutIcons[key] = it
                }
            }
        }

    /** The app's own icon at [sizePx], to compare with the pixelated one. */
    suspend fun original(
        app: LauncherApp,
        sizePx: Int,
        densityDpi: Int,
    ): Bitmap? =
        withContext(dispatcher) {
            val drawable = loader.load(app, densityDpi) ?: return@withContext null
            IconSourceRenderer.render(drawable, sizePx).toBitmap()
        }

    /** The editor closed: forget its sources. */
    fun endPreview() = previewSources.clear()

    /**
     * After a style change: renders every app in [styles] ahead, one at a time in the background, so icons further
     * down the list are ready too; icons on screen are requested by home itself and come first. Then removes cached
     * icons of styles no longer in use.
     */
    suspend fun prewarm(
        apps: List<LauncherApp>,
        styles: IconStyles,
        densityDpi: Int,
    ) {
        val inUse = HashSet<String>()
        for (app in apps) {
            val look = styles.lookFor(app.key)
            inUse += styleKey(look)
            // The app's own icon stays too, in case the picture is gone.
            if (look.image != null) inUse += styleKey(look.copy(image = null))
            withContext(background) { cachedIcon(app, look, densityDpi) }
        }
        withContext(background) { cache.retainStyles(inUse) }
    }

    /** The cache key of a look: style and either the layer or the imported picture (#14). */
    private fun styleKey(look: IconLook) =
        if (look.image != null) "${look.style}|image|${look.image}" else "${look.style}|${look.layer}"

    private fun PixelImage.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

    /**
     * Apps may have changed (installed, updated, icon theme): re-check fingerprints on the next request. Cheap: an
     * unchanged icon is served from the cache again.
     */
    fun recheck() {
        verified.clear()
        shortcutIcons.clear()
        _generation.value++
    }

    /** The apps now installed: icons of all others are deleted from the cache (#56). */
    fun retainApps(apps: List<LauncherApp>) = cache.retainApps(apps.mapTo(HashSet()) { it.key.id })

    /** Android asks for memory back (home is in the background): most decoded icons go, the files stay. */
    fun trimMemory() = cache.trimMemory()

    /** Configuration changed (density, theme): also drop decoded icons from memory. */
    fun onConfigurationChanged() {
        cache.clearMemory()
        recheck()
    }
}
