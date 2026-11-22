package io.github.nobuddyorg.pixelbuddy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.nobuddyorg.pixelbuddy.core.data.settings.Appearance
import io.github.nobuddyorg.pixelbuddy.core.data.settings.SettingsRepository
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyColors
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyStyle
import io.github.nobuddyorg.pixelbuddy.core.designsystem.PixelBuddyTheme

/** The design-system style for the user's [Appearance] settings. */
internal fun Appearance.toStyle(): PixelBuddyStyle =
    PixelBuddyStyle(
        accent = Color(accent.argb),
        scanlines = scanlines,
        sky = sky.stops.map { Color(it) },
        dithering = dithering,
        textScale = textSize.scale,
    )

/**
 * [PixelBuddyTheme] with the stored appearance, so every change applies at once. Until the settings are read only
 * the plain background shows, so the default look never flashes up.
 */
@Composable
internal fun StoredAppearanceTheme(
    settings: SettingsRepository,
    content: @Composable (Appearance) -> Unit,
) {
    val appearance by settings.appearance.collectAsStateWithLifecycle(initialValue = null)
    val current = appearance
    if (current == null) {
        Box(Modifier.fillMaxSize().background(PixelBuddyColors.Navy))
        return
    }
    PixelBuddyTheme(current.toStyle()) { content(current) }
}
