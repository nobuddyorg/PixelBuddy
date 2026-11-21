package io.github.nobuddyorg.pixelbuddy.core.pixelengine

/** Built-in looks. Outline styles (neon outlines) are a separate renderer, see #29. */
public enum class IconPreset(
    public val style: IconStyle,
) {
    /** Chunky 12 px icons in a fixed 16-colour retro palette (no dithering: too noisy at 12 px). */
    EightBit(IconStyle(grid = 12, palette = Palettes.Retro16)),

    /** Default: 24 px, 16 colours picked per icon, 1 px outline. */
    SixteenBit(IconStyle(grid = 24, palette = Palette.Adaptive(16), outline = true)),

    /** Four greens, like an old handheld screen. */
    Pocket(IconStyle(grid = 16, palette = Palettes.Pocket, dithering = true, outlineColor = 0xFF1B3A1B.toInt())),

    /** Bright neon colours with a dark outline. */
    Neon(IconStyle(grid = 16, palette = Palettes.Neon, outline = true)),

    /** Shades of the light text colour. */
    Mono(IconStyle(grid = 16, palette = Palette.MonochromeTint(0xFFF2EEFF.toInt(), shades = 4))),
    ;

    public companion object {
        public val Default: IconPreset = SixteenBit
    }
}

/** Fixed palettes used by the presets (own colour picks). */
public object Palettes {
    public val Retro16: Palette.Fixed =
        Palette.Fixed(
            "Retro 16",
            listOf(
                0xFF0B0B14,
                0xFF2B2D42,
                0xFF5C5F7A,
                0xFFB8BCD0,
                0xFFF4F1EA,
                0xFF7A1E2C,
                0xFFE0413A,
                0xFFF29541,
                0xFFF7D154,
                0xFF2E6B3A,
                0xFF6CC24A,
                0xFF1D4E89,
                0xFF3FA7D6,
                0xFF7D4BA8,
                0xFFE58DB5,
                0xFF8A5A3B,
            ).map { it.toInt() },
        )

    public val Pocket: Palette.Fixed =
        Palette.Fixed("Pocket", listOf(0xFF1B3A1B, 0xFF3A6B2E, 0xFF86A83A, 0xFFC4D86A).map { it.toInt() })

    public val Neon: Palette.Fixed =
        Palette.Fixed(
            "Neon",
            listOf(
                0xFF12081F,
                0xFFFF2E88,
                0xFF00E5FF,
                0xFFFFE600,
                0xFF39FF14,
                0xFFFF8A3D,
                0xFFB14DFF,
                0xFFF2EEFF,
            ).map { it.toInt() },
        )
}
