package io.github.nobuddyorg.pixelbuddy.core.weather

/** What the weather glyph shows. */
enum class WeatherCondition {
    ClearDay,
    ClearNight,
    PartlyCloudy,
    Cloudy,
    Fog,
    Rain,
    Snow,
    Thunder,
    ;

    companion object {
        /** Maps a WMO weather code (as Open-Meteo reports it) and day/night to a glyph. */
        fun fromWmo(
            code: Int,
            isDay: Boolean,
        ): WeatherCondition =
            when (code) {
                0, 1 -> if (isDay) ClearDay else ClearNight

                2 -> PartlyCloudy

                45, 48 -> Fog

                in 51..67, in 80..82 -> Rain

                in 71..77, 85, 86 -> Snow

                in 95..99 -> Thunder

                // 3 (overcast) and anything unknown.
                else -> Cloudy
            }
    }
}
