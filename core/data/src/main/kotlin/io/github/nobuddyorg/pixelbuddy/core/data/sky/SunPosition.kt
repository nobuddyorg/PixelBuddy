package io.github.nobuddyorg.pixelbuddy.core.data.sky

import java.time.Instant
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** A place on Earth in degrees: north and east are positive. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** Where the sun is: [elevation] above the horizon in degrees, and whether it is still rising ([morning]). */
data class SunPosition(
    val elevation: Double,
    val morning: Boolean,
) {
    companion object {
        /**
         * The sun's position at [time] seen from [place], with NOAA's solar equations (accurate to about a minute
         * for sunrise and sunset between ±72° latitude). Refraction is ignored: sunrise is at about -0.83°.
         */
        fun at(
            time: Instant,
            place: GeoPoint,
        ): SunPosition {
            val julianDay = time.toEpochMilli() / MILLIS_PER_DAY + UNIX_EPOCH_JULIAN_DAY
            val t = (julianDay - J2000) / DAYS_PER_CENTURY
            val meanLongitude = (280.46646 + t * (36000.76983 + t * 0.0003032)).mod(360.0)
            val meanAnomaly = 357.52911 + t * (35999.05029 - 0.0001537 * t)
            val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
            val m = rad(meanAnomaly)
            val center =
                sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
                    sin(2 * m) * (0.019993 - 0.000101 * t) +
                    sin(3 * m) * 0.000289
            val omega = rad(125.04 - 1934.136 * t)
            val apparentLongitude = meanLongitude + center - 0.00569 - 0.00478 * sin(omega)
            val meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
            val obliquity = meanObliquity + 0.00256 * cos(omega)
            val declination = asin(sin(rad(obliquity)) * sin(rad(apparentLongitude)))

            val y = tan(rad(obliquity / 2)).pow(2)
            val l0 = rad(meanLongitude)
            val equationOfTimeMinutes =
                4 *
                    deg(
                        y * sin(2 * l0) - 2 * eccentricity * sin(m) +
                            4 * eccentricity * y * sin(m) * cos(2 * l0) -
                            0.5 * y * y * sin(4 * l0) - 1.25 * eccentricity * eccentricity * sin(2 * m),
                    )
            val minutesOfDay = time.toEpochMilli().mod(MILLIS_PER_DAY.toLong()) / MILLIS_PER_MINUTE
            val trueSolarMinutes = (minutesOfDay + equationOfTimeMinutes + 4 * place.longitude).mod(MINUTES_PER_DAY)
            val hourAngle = trueSolarMinutes / 4 - 180
            val latitude = rad(place.latitude)
            val cosZenith =
                (sin(latitude) * sin(declination) + cos(latitude) * cos(declination) * cos(rad(hourAngle)))
                    .coerceIn(-1.0, 1.0)
            return SunPosition(elevation = 90 - deg(acos(cosZenith)), morning = hourAngle < 0)
        }

        private fun rad(degrees: Double) = Math.toRadians(degrees)

        private fun deg(radians: Double) = Math.toDegrees(radians)

        private const val MILLIS_PER_DAY = 86_400_000.0
        private const val MILLIS_PER_MINUTE = 60_000.0
        private const val MINUTES_PER_DAY = 1440.0
        private const val UNIX_EPOCH_JULIAN_DAY = 2440587.5
        private const val J2000 = 2451545.0
        private const val DAYS_PER_CENTURY = 36525.0
    }
}
