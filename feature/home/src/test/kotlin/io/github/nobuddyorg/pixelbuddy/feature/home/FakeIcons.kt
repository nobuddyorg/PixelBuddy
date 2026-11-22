package io.github.nobuddyorg.pixelbuddy.feature.home

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import io.github.nobuddyorg.pixelbuddy.core.data.apps.LauncherApp
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconCache
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLoader
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconRepository
import kotlinx.coroutines.Dispatchers
import java.io.File

/** Synthetic adaptive icons (no brand icons in tests): a coloured plate with a light square glyph. */
internal object FakeIcons : IconLoader {
    private val colors =
        listOf(
            0xFFE0413A,
            0xFF3FA7D6,
            0xFF6CC24A,
            0xFFF29541,
            0xFF7D4BA8,
            0xFF1D4E89,
        ).map { it.toInt() }

    override fun load(
        app: LauncherApp,
        densityDpi: Int,
    ): Drawable {
        val color = colors[Math.floorMod(app.label.hashCode(), colors.size)]
        return AdaptiveIconDrawable(ColorDrawable(color), InsetDrawable(ColorDrawable(Color.WHITE), 0.38f))
    }

    override fun version(app: LauncherApp) = 1L

    fun repository(cacheDir: File) = IconRepository(this, IconCache(cacheDir), Dispatchers.Unconfined)
}
