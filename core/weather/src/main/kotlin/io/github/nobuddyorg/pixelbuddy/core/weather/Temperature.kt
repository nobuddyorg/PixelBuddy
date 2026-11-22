package io.github.nobuddyorg.pixelbuddy.core.weather

import androidx.core.text.util.LocalePreferences
import io.github.nobuddyorg.pixelbuddy.core.data.settings.TemperatureUnit
import java.util.Locale
import kotlin.math.roundToInt

/** True if temperatures show in °F: chosen, or by the user's regional preference ([TemperatureUnit.System]). */
fun TemperatureUnit.isFahrenheit(locale: Locale = Locale.getDefault()): Boolean =
    when (this) {
        TemperatureUnit.Celsius -> {
            false
        }

        TemperatureUnit.Fahrenheit -> {
            true
        }

        TemperatureUnit.System -> {
            LocalePreferences.getTemperatureUnit(locale) ==
                LocalePreferences.TemperatureUnit.FAHRENHEIT
        }
    }

/** Whole degrees for the header. */
fun displayDegrees(
    celsius: Double,
    fahrenheit: Boolean,
): Int = (if (fahrenheit) celsius * 9 / 5 + 32 else celsius).roundToInt()
