package io.github.nobuddyorg.pixelbuddy.core.data.settings

import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconStyle
import kotlinx.serialization.Serializable

/**
 * Everything the user sets, stored as one versioned JSON file (see [SettingsSerializer]).
 *
 * Compatibility rules: every property has a default, so a field added later reads from older files without a
 * migration. Renaming or removing a property, or changing its meaning, needs a [SettingsSchema] step and a new
 * [SettingsSchema.CURRENT_VERSION].
 */
@Serializable
data class Settings(
    val version: Int = SettingsSchema.CURRENT_VERSION,
    /** Favorites on home, in the user's order. */
    val favorites: List<AppKey> = emptyList(),
    val hidden: Set<AppKey> = emptySet(),
    val appearance: Appearance = Appearance(),
    /** Style for all icons. */
    val iconStyle: IconStyleSetting = IconStyleSetting(),
    /** Per-app styles that replace [iconStyle] (#14). */
    val iconOverrides: List<IconOverride> = emptyList(),
    val weather: WeatherSettings = WeatherSettings(),
    val gestures: Gestures = Gestures(),
) {
    /** Every app this file refers to, e.g. to clean up after uninstalls. */
    val appKeys: Set<AppKey>
        get() = favorites.toSet() + hidden + iconOverrides.map { it.app } + listOfNotNull(weather.tapApp)

    /** Adds [app] at the end of the favorites; unchanged if it is one already. */
    fun withFavorite(app: AppKey): Settings = if (app in favorites) this else copy(favorites = favorites + app)

    fun withoutFavorite(app: AppKey): Settings = copy(favorites = favorites - app)

    /** Moves favorite [app] by [offset] places (negative: up), clamped to the list. */
    fun withFavoriteMoved(
        app: AppKey,
        offset: Int,
    ): Settings {
        val from = favorites.indexOf(app)
        if (from < 0) return this
        val to = (from + offset).coerceIn(0, favorites.lastIndex)
        return copy(favorites = favorites.toMutableList().apply { add(to, removeAt(from)) })
    }

    /** Hides [app] from home; a hidden app is no favorite. */
    fun withHidden(app: AppKey): Settings = copy(hidden = hidden + app, favorites = favorites - app)

    fun withShown(app: AppKey): Settings = copy(hidden = hidden - app)
}

/** Home look (#12). Colours are ARGB values, so this module needs no UI library. */
@Serializable
data class Appearance(
    val clockFormat: ClockFormat = ClockFormat.System,
    /** 1-pixel drop shadow under the clock. */
    val clockShadow: Boolean = true,
    val clockSize: SizeStep = SizeStep.Medium,
    /** Size of the text on home (app names, info line), on top of the system font size. */
    val textSize: SizeStep = SizeStep.Medium,
    val accent: Accent = Accent.Orange,
    /** Faint CRT scanlines over the sky. */
    val scanlines: Boolean = false,
    val sky: Sky = Sky.Dusk,
    /** Dithered bands (pixel look) or a smooth gradient. */
    val dithering: Boolean = true,
)

@Serializable
enum class ClockFormat { System, TwelveHour, TwentyFourHour }

@Serializable
enum class SizeStep(
    /** Factor for text sizes. */
    val scale: Float,
    /** Height of the big clock in dp. */
    val clockHeightDp: Int,
) {
    Small(0.85f, 56),
    Medium(1f, 72),
    Large(1.2f, 96),
}

/** Accent colours: section letters, highlights, buttons. All readable on the dark sky. */
@Serializable
enum class Accent(
    val argb: Int,
) {
    Orange(0xFFFF8A3D.toInt()),
    Pink(0xFFFF6FAE.toInt()),
    Yellow(0xFFFFD23F.toInt()),
    Green(0xFF7CE38B.toInt()),
    Cyan(0xFF4FD6E8.toInt()),
    Lavender(0xFFB69CFF.toInt()),
}

/**
 * Sky gradients, top to bottom. The top stays dark for the clock; the bottom is no brighter than the default's
 * orange, so the light text stays readable.
 */
@Serializable
enum class Sky(
    vararg val stops: Int,
) {
    Dusk(0xFF141833.toInt(), 0xFF3D2A6B.toInt(), 0xFFB4506A.toInt(), 0xFFFF8A3D.toInt()),
    Night(0xFF0B0E24.toInt(), 0xFF1B2150.toInt(), 0xFF2E3A7A.toInt(), 0xFF4A5BA8.toInt()),
    Dawn(0xFF1E2A5A.toInt(), 0xFF5B4B8A.toInt(), 0xFFC46A8A.toInt(), 0xFFE8865A.toInt()),
    Forest(0xFF0E1A14.toInt(), 0xFF1F3B2A.toInt(), 0xFF3E6B3F.toInt(), 0xFF6E9A4A.toInt()),
    Ocean(0xFF08142B.toInt(), 0xFF0F3057.toInt(), 0xFF1F6F8B.toInt(), 0xFF3FA0A0.toInt()),
    Mono(0xFF101014.toInt(), 0xFF26262E.toInt(), 0xFF44444F.toInt(), 0xFF6A6A78.toInt()),
}

/** What the gestures on home do (#12). */
@Serializable
data class Gestures(
    val swipeUp: GestureAction = GestureAction.Search,
    val swipeDown: GestureAction = GestureAction.Notifications,
    val longPress: GestureAction = GestureAction.Settings,
) {
    /**
     * The long press on empty space, as used: it opens the settings whenever no swipe does, so the settings can
     * always be reached.
     */
    val effectiveLongPress: GestureAction
        get() =
            if (swipeUp != GestureAction.Settings && swipeDown != GestureAction.Settings) {
                GestureAction.Settings
            } else {
                longPress
            }
}

@Serializable
enum class GestureAction { Nothing, Search, Notifications, Settings }

/** An icon style; for now a preset, the editor (#13) adds its own parameters. */
@Serializable
data class IconStyleSetting(
    /** [IconPreset] name. Unknown names (from a newer version) fall back to the default. */
    val preset: String = IconPreset.Default.name,
) {
    val style: IconStyle
        get() = (IconPreset.entries.firstOrNull { it.name == preset } ?: IconPreset.Default).style

    companion object {
        fun of(preset: IconPreset): IconStyleSetting = IconStyleSetting(preset.name)
    }
}

@Serializable
data class IconOverride(
    val app: AppKey,
    val style: IconStyleSetting,
)

/** Weather on home (#11). Off until the user turns it on: no network requests before that (PRIVACY.md). */
@Serializable
data class WeatherSettings(
    val enabled: Boolean = false,
    val unit: TemperatureUnit = TemperatureUnit.System,
    /** A place chosen by name; used unless [approximateLocation] is on. */
    val place: WeatherPlace? = null,
    /** Use the phone's approximate location instead of [place] (needs the coarse location permission). */
    val approximateLocation: Boolean = false,
    /** App opened by tapping the weather; null refreshes it. */
    val tapApp: AppKey? = null,
) {
    /** True once there is somewhere to get the weather for. */
    val hasLocation: Boolean get() = approximateLocation || place != null
}

/** A place picked from the name search. */
@Serializable
data class WeatherPlace(
    val name: String,
    val region: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
enum class TemperatureUnit { System, Celsius, Fahrenheit }

/** Icon styles to draw: the global one and per-app overrides. */
data class IconStyles(
    val default: IconStyle = IconPreset.Default.style,
    val overrides: Map<AppKey, IconStyle> = emptyMap(),
) {
    fun styleFor(app: AppKey): IconStyle = overrides[app] ?: default
}
