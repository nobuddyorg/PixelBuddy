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
) {
    /** Every app this file refers to, e.g. to clean up after uninstalls. */
    val appKeys: Set<AppKey>
        get() = favorites.toSet() + hidden + iconOverrides.map { it.app } + listOfNotNull(weather.tapApp)
}

/** Home look (#12). */
@Serializable
data class Appearance(
    val clockFormat: ClockFormat = ClockFormat.System,
)

@Serializable
enum class ClockFormat { System, TwelveHour, TwentyFourHour }

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
