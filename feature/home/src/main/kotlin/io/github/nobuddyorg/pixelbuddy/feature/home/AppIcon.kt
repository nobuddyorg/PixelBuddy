package io.github.nobuddyorg.pixelbuddy.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppShortcut
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import io.github.nobuddyorg.pixelbuddy.core.data.settings.IconLook
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconStyle

/**
 * The largest multiple of [grid] that fits into [targetPx], so every icon pixel is the same whole number of
 * physical pixels (the S24's density is not an integer).
 */
fun snappedIconPx(
    targetPx: Float,
    grid: Int,
): Int = maxOf(1, (targetPx / grid).toInt()) * grid

/** A pixelated app icon in [style], loaded off the main thread and drawn without filtering. */
@Composable
fun AppIcon(
    app: LauncherApp,
    repository: IconRepository,
    style: IconStyle,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) = AppIcon(app, repository, IconLook(style), modifier, size)

/** A shortcut's icon (#17), pixelated in [style] like its app; empty while loading or if it has none. */
@Composable
fun ShortcutIcon(
    shortcut: AppShortcut,
    repository: IconRepository,
    style: IconStyle,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val density = LocalDensity.current
    val densityDpi = LocalConfiguration.current.densityDpi
    val bitmap by produceState<ImageBitmap?>(null, shortcut, style, densityDpi) {
        value = repository.shortcutIcon(shortcut, style, densityDpi)?.asImageBitmap()
    }
    PixelBitmap(bitmap, with(density) { snappedIconPx(size.toPx(), style.grid).toDp() }, modifier)
}

@Composable
private fun PixelBitmap(
    bitmap: ImageBitmap?,
    side: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(side)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A pixelated app icon as [look] says (the app's own icon, #14, or the style of all). */
@Composable
fun AppIcon(
    app: LauncherApp,
    repository: IconRepository,
    look: IconLook,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val style = look.style
    val density = LocalDensity.current
    val densityDpi = LocalConfiguration.current.densityDpi
    val generation by repository.generation.collectAsStateWithLifecycle()
    val bitmap by produceState<ImageBitmap?>(null, app.key, look, densityDpi, generation) {
        value = repository.icon(app, look, densityDpi)?.asImageBitmap()
    }
    val sidePx = snappedIconPx(with(density) { size.toPx() }, style.grid)
    PixelBitmap(bitmap, with(density) { sidePx.toDp() }, modifier)
}
