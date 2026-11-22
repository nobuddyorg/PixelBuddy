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
    Box(modifier.size(with(density) { sidePx.toDp() })) {
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
