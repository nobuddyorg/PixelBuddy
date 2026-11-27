package io.github.nobuddyorg.pixelbuddy.core.data.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppShortcut
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconLook
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconStyles
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconRenderer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconShape
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconStyle
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.PixelImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

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
 * Pixelated or neon app icons. Renders the source at [IconStyle.sourceSize], runs the [IconRenderer] and caches the
 * result in memory and on disk. Work runs on [dispatcher] with limited parallelism; the list asks for visible icons first, so they come
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
    private val previewSources = ConcurrentHashMap<String, Source>()

    /** Shortcut icons (#17), only in memory: few, and only while a menu is open. */
    private val shortcutIcons = ConcurrentHashMap<String, Bitmap>()

    /** App id and density → verified base (version, icon fingerprint, density) for this session. */
    private val verified = ConcurrentHashMap<String, String>()

    private val _generation = MutableStateFlow(0)

    /** Increases whenever cached icons may be outdated; icon UIs reload when it changes. */
    val generation: StateFlow<Int> = _generation

    /** How long renders took on this phone (#29), for Settings → About. */
    val timings: RenderTimings = RenderTimings()

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

    /**
     * The icon of [app] as [look] says; an imported picture that is gone falls back to the app's icon. Smooth neon
     * icons are drawn at [sizePx], the size on screen (home's 48 dp by default).
     */
    suspend fun icon(
        app: LauncherApp,
        look: IconLook,
        densityDpi: Int,
        sizePx: Int = iconPx(densityDpi),
    ): Bitmap? = withContext(dispatcher) { cachedIcon(app, look, densityDpi, sizePx) }

    private fun cachedIcon(
        app: LauncherApp,
        look: IconLook,
        densityDpi: Int,
        sizePx: Int,
    ): Bitmap? {
        val image = look.image
        if (image != null) imageIcon(app, look.style, image, densityDpi, sizePx)?.let { return it }
        val style = look.style
        val layer = look.layer
        val id = app.key.id
        val session = "$id|$densityDpi"
        val styleKey = styleKey(IconLook(style, layer), sizePx)
        verified[session]?.let { base -> cache.get(id, base, styleKey)?.let { return it } }

        val drawable = loader.load(app, densityDpi) ?: return null
        val base = "${loader.version(app)}|${IconSourceRenderer.fingerprint(drawable)}|$densityDpi"
        verified[session] = base
        cache.get(id, base, styleKey)?.let { return it }

        val start = System.nanoTime()
        val bitmap = Source.of(drawable, style, layer).draw(style, sizePx)
        timings.record(style.neon != null, System.nanoTime() - start)
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
        sizePx: Int,
    ): Bitmap? {
        val base = "image|$image|$densityDpi"
        val styleKey = styleKey(IconLook(style, image = image), sizePx)
        cache.get(app.key.id, base, styleKey)?.let { return it }
        val start = System.nanoTime()
        val source = images?.load(image, style.sourceSize) ?: return null
        val bitmap = Source(style.shape, source).draw(style, sizePx)
        timings.record(style.neon != null, System.nanoTime() - start)
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
        sizePx: Int = iconPx(densityDpi),
    ): Bitmap? = preview(app, IconLook(style), densityDpi, sizePx)

    /** Like [preview] for a whole [look]: another layer of the app's icon or an imported picture. */
    suspend fun preview(
        app: LauncherApp,
        look: IconLook,
        densityDpi: Int,
        sizePx: Int = iconPx(densityDpi),
    ): Bitmap? =
        withContext(dispatcher) {
            val style = look.style
            val kind = if (style.neon != null) "neon" else "${style.shape}"
            val key = "${app.key.id}|$densityDpi|${style.sourceSize}|$kind|${look.layer}|${look.image}"
            val source =
                previewSources[key] ?: run {
                    val image = look.image?.let { images?.load(it, style.sourceSize) }
                    if (image != null) {
                        Source(style.shape, image)
                    } else {
                        val drawable = loader.load(app, densityDpi) ?: return@withContext null
                        Source.of(drawable, style, look.layer)
                    }.also { previewSources[key] = it }
                }
            ensureActive()
            source.draw(style, sizePx)
        }

    /** [shortcut]'s icon pixelated in [style] (#17), or null if it has none. */
    suspend fun shortcutIcon(
        shortcut: AppShortcut,
        style: IconStyle,
        densityDpi: Int,
        sizePx: Int = iconPx(densityDpi) / 2,
    ): Bitmap? =
        withContext(dispatcher) {
            val key = "${shortcut.key}|$style|$densityDpi|$sizePx"
            shortcutIcons[key] ?: run {
                val drawable = loader.loadShortcut(shortcut, densityDpi) ?: return@withContext null
                val source = Source.of(drawable, style, IconLayer.Full)
                ensureActive()
                source.draw(style, sizePx).also {
                    renderCount++
                    shortcutIcons[key] = it
                }
            }
        }

    /**
     * [picture] (e.g. album art, #25) pixelated in [style] as it is, never masked to an icon shape. Pictures stay
     * pixel art with a neon style: photos give no useful outlines. Not cached: home asks only when the picture
     * changes.
     */
    suspend fun pixelate(
        picture: Bitmap,
        style: IconStyle,
    ): Bitmap =
        withContext(dispatcher) {
            val pixel = style.copy(neon = null)
            val size = pixel.sourceSize
            val fitted = CustomIconStore.fitted(picture, size)
            val pixels = IntArray(size * size).also { fitted.getPixels(it, 0, size, 0, 0, size, size) }
            fitted.recycle()
            ensureActive()
            IconRenderer
                .render(PixelImage(size, size, pixels), pixel.copy(shape = IconShape.FreeStanding), size)
                .toBitmap()
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
        val sizePx = iconPx(densityDpi)
        val inUse = HashSet<String>()
        for (app in apps) {
            val look = styles.lookFor(app.key)
            inUse += styleKey(look, sizePx)
            // The app's own icon stays too, in case the picture is gone.
            if (look.image != null) inUse += styleKey(look.copy(image = null), sizePx)
            withContext(background) { cachedIcon(app, look, densityDpi, sizePx) }
        }
        withContext(background) { cache.retainStyles(inUse) }
    }

    /**
     * The cache key of a look: style and either the layer or the imported picture (#14); smooth neon icons also
     * depend on the size.
     */
    private fun styleKey(
        look: IconLook,
        sizePx: Int,
    ): String {
        val key = if (look.image != null) "${look.style}|image|${look.image}" else "${look.style}|${look.layer}"
        return if (look.style.smooth) "$key|${sizePx}px" else key
    }

    /** A drawn icon, ready for the renderer: [shape] for pixel styles, [colours] for neon ones (#29). */
    private class Source(
        val shape: IconShape,
        val image: PixelImage,
        val colours: PixelImage? = null,
    ) {
        fun draw(
            style: IconStyle,
            sizePx: Int,
        ): Bitmap = IconRenderer.render(image, style.copy(shape = shape), sizePx, colours).toBitmap()

        companion object {
            fun of(
                drawable: Drawable,
                style: IconStyle,
                layer: IconLayer,
            ): Source {
                if (style.neon != null) {
                    val (shapes, colours) = IconSourceRenderer.neonSources(drawable, style.sourceSize, layer)
                    return Source(style.shape, shapes, colours)
                }
                val shape = IconSourceRenderer.shapeFor(drawable, style.shape)
                return Source(shape, IconSourceRenderer.render(drawable, style.sourceSize, layer, shape))
            }
        }
    }

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

    companion object {
        /** Icon size on home. */
        const val ICON_DP: Int = 48

        /** Home's icon size in pixels at [densityDpi]: the size smooth neon icons are drawn at. */
        fun iconPx(densityDpi: Int): Int = (ICON_DP * densityDpi / BASELINE_DPI).roundToInt()

        private const val BASELINE_DPI = 160f
    }
}

private fun PixelImage.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
