package io.github.nobuddyorg.pixelbuddy.core.data.settings

import io.github.nobuddyorg.pixelbuddy.core.data.apps.AppKey
import io.github.nobuddyorg.pixelbuddy.core.data.icons.CustomIconStore
import io.github.nobuddyorg.pixelbuddy.core.data.icons.IconLayer
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconPreset
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconShape
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.IconStyle
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.Palette
import io.github.nobuddyorg.pixelbuddy.core.pixelengine.Palettes
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
    /** Where the first-start setup is (#20); [SetupStep.Done] once it was finished or skipped. */
    val setup: SetupStep = SetupStep.HomeApp,
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

    fun iconOverrideFor(app: AppKey): IconOverride? = iconOverrides.firstOrNull { it.app == app }

    /** Gives [app] its own icon, replacing an earlier one. */
    fun withIconOverride(
        app: AppKey,
        choice: IconChoice,
    ): Settings = copy(iconOverrides = iconOverrides.filterNot { it.app == app } + IconOverride.of(app, choice))

    /** [app] goes back to the style of all icons. */
    fun withoutIconOverride(app: AppKey): Settings = copy(iconOverrides = iconOverrides.filterNot { it.app == app })

    /** Imported images the overrides use, so others can be deleted. */
    val iconImages: Set<String> get() = iconOverrides.mapNotNullTo(HashSet()) { it.image }
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

/**
 * Steps of the first-start setup, in order. Stored, so the setup goes on where it was after the process was
 * killed (e.g. while the system's home-app dialog was open).
 */
@Serializable
enum class SetupStep {
    HomeApp,
    Favorites,
    Weather,
    Done,
    ;

    /** The step after this one. */
    val next: SetupStep get() = entries[minOf(ordinal + 1, entries.lastIndex)]

    /** The step before this one, or this one if it is the first. */
    val previous: SetupStep get() = entries[maxOf(ordinal - 1, 0)]
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

/**
 * An icon style: a [preset], or the user's own [custom] style made from one in the editor (#13), shown as
 * "Custom (based on [preset])".
 */
@Serializable
data class IconStyleSetting(
    /** [IconPreset] name. Unknown names (from a newer version) fall back to the default. */
    val preset: String = IconPreset.Default.name,
    val custom: CustomIconStyle? = null,
) {
    /** The preset, or the one the custom style is based on. */
    val basePreset: IconPreset
        get() = IconPreset.entries.firstOrNull { it.name == preset } ?: IconPreset.Default

    val style: IconStyle
        get() = custom?.toStyle() ?: basePreset.style

    /** This style changed by [change]; a preset becomes a custom style based on it. */
    fun edited(change: (CustomIconStyle) -> CustomIconStyle): IconStyleSetting =
        IconStyleSetting(basePreset.name, change(custom ?: CustomIconStyle.from(style)))

    companion object {
        fun of(preset: IconPreset): IconStyleSetting = IconStyleSetting(preset.name)
    }
}

/**
 * Every parameter the icon editor offers. Values from a damaged or newer file are brought into range by
 * [toStyle], so they can never stop icons from rendering.
 */
@Serializable
data class CustomIconStyle(
    val grid: Int = 24,
    val palette: PaletteChoice = PaletteChoice.Adaptive,
    /** Colours of an adaptive palette, shades of the mono one; see [PaletteChoice.colorRange]. */
    val colors: Int = 16,
    /** 0 keeps the averaged original colours, 1 uses the palette's. */
    val intensity: Float = 1f,
    val dithering: Boolean = false,
    val outline: Boolean = false,
    val outlineColor: Int = IconStyle.DEFAULT_OUTLINE,
    /** On a rounded square plate (masked) or free-standing. */
    val masked: Boolean = true,
) {
    fun toStyle(): IconStyle =
        IconStyle(
            grid = grid.takeIf { it in IconStyle.GRIDS } ?: IconStyle().grid,
            palette = palette.toPalette(colors),
            intensity = intensity.takeUnless { it.isNaN() }?.coerceIn(0f, 1f) ?: 1f,
            dithering = dithering,
            outline = outline,
            outlineColor = outlineColor or OPAQUE,
            shape = if (masked) IconShape.Masked else IconShape.FreeStanding,
        )

    companion object {
        private const val OPAQUE = 0xFF shl 24

        /** The editor's view of [style]; palettes the editor doesn't offer become adaptive ones. */
        fun from(style: IconStyle): CustomIconStyle {
            val (choice, colors) =
                when (val palette = style.palette) {
                    is Palette.Adaptive -> {
                        PaletteChoice.Adaptive to palette.colors
                    }

                    Palette.Original -> {
                        PaletteChoice.Original to 16
                    }

                    is Palette.MonochromeTint -> {
                        PaletteChoice.Mono to palette.shades
                    }

                    is Palette.Fixed -> {
                        (
                            PaletteChoice.entries.firstOrNull { it.fixed == palette }
                                ?: PaletteChoice.Adaptive
                        ) to
                            16
                    }
                }
            return CustomIconStyle(
                grid = style.grid,
                palette = choice,
                colors = colors,
                intensity = style.intensity,
                dithering = style.dithering,
                outline = style.outline,
                outlineColor = style.outlineColor,
                masked = style.shape == IconShape.Masked,
            )
        }
    }
}

/** The palettes the icon editor offers. */
@Serializable
enum class PaletteChoice(
    internal val fixed: Palette.Fixed? = null,
    /** How many colours can be chosen, or null if the palette has fixed colours. */
    val colorRange: IntRange? = null,
) {
    Adaptive(colorRange = 2..32),
    Original,
    Retro16(Palettes.Retro16),
    Pocket(Palettes.Pocket),
    Neon(Palettes.Neon),
    Mono(colorRange = 2..16),
    ;

    fun toPalette(colors: Int): Palette =
        when (this) {
            Adaptive -> Palette.Adaptive(colors.coerceIn(colorRange!!))
            Original -> Palette.Original
            Mono -> Palette.MonochromeTint(MONO_TINT, colors.coerceIn(colorRange!!))
            else -> fixed!!
        }

    private companion object {
        /** The light text colour, like the Mono preset. */
        const val MONO_TINT = 0xFFF2EEFF.toInt()
    }
}

/**
 * What an icon looks like, apart from the app: a style and where the picture comes from. For all icons (#13) only
 * the [style] counts; an app's own icon (#14) can also use another [layer] or an imported [image].
 */
@Serializable
data class IconChoice(
    val style: IconStyleSetting = IconStyleSetting(),
    /** Part of the app's icon to draw; not used with an [image]. */
    val layer: IconLayer = IconLayer.Full,
    /** An imported picture drawn instead of the app's icon, by its SHA-256 (see [CustomIconStore]). */
    val image: String? = null,
)

/** One app's own icon (#14); everything else uses [Settings.iconStyle]. */
@Serializable
data class IconOverride(
    val app: AppKey,
    val style: IconStyleSetting = IconStyleSetting(),
    val layer: IconLayer = IconLayer.Full,
    val image: String? = null,
) {
    val choice: IconChoice get() = IconChoice(style, layer, image)

    /** How the icon is drawn; an image that isn't a valid file name is ignored. */
    val look: IconLook get() = IconLook(style.style, layer, image?.takeIf(CustomIconStore::isValidName))

    companion object {
        fun of(
            app: AppKey,
            choice: IconChoice,
        ): IconOverride = IconOverride(app, choice.style, choice.layer, choice.image)
    }
}

/** How one icon is drawn: the pixel [style], from the app icon's [layer] or an imported [image]. */
data class IconLook(
    val style: IconStyle,
    val layer: IconLayer = IconLayer.Full,
    val image: String? = null,
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

/** Icons to draw: the global style and per-app overrides, which win. */
data class IconStyles(
    val default: IconStyle = IconPreset.Default.style,
    val overrides: Map<AppKey, IconLook> = emptyMap(),
) {
    fun lookFor(app: AppKey): IconLook = overrides[app] ?: IconLook(default)

    fun styleFor(app: AppKey): IconStyle = lookFor(app).style
}
