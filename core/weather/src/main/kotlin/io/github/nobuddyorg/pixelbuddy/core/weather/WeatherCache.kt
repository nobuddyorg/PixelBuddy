package io.github.nobuddyorg.pixelbuddy.core.weather

import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.time.Instant

/** The last weather shown on home, with where and when it was fetched. */
data class WeatherReport(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val coordinates: Coordinates,
    val fetchedAt: Instant,
) {
    val condition: WeatherCondition get() = WeatherCondition.fromWmo(weatherCode, isDay)
}

/**
 * Keeps the last [WeatherReport] in one small file, so it survives restarts and offline periods. Use a directory
 * that isn't backed up (no-backup files): it's a cache.
 */
class WeatherCache(
    private val file: File,
) {
    fun read(): WeatherReport? =
        try {
            if (!file.exists()) {
                null
            } else {
                OpenMeteoJson.decodeFromString(Stored.serializer(), file.readText()).toReport()
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            // Unreadable (e.g. from an older version): as if there were none.
            null
        }

    fun write(report: WeatherReport) {
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.path + ".tmp")
            temp.writeText(OpenMeteoJson.encodeToString(Stored.serializer(), Stored.of(report)))
            if (!temp.renameTo(file)) temp.delete()
        } catch (_: IOException) {
            // A cache: losing it only costs one request.
        }
    }

    fun clear() {
        file.delete()
    }

    @Serializable
    private class Stored(
        val temperatureCelsius: Double,
        val weatherCode: Int,
        val isDay: Boolean,
        val latitude: Double,
        val longitude: Double,
        val fetchedAtMillis: Long,
    ) {
        fun toReport() =
            WeatherReport(
                temperatureCelsius,
                weatherCode,
                isDay,
                Coordinates(latitude, longitude),
                Instant.ofEpochMilli(fetchedAtMillis),
            )

        companion object {
            fun of(report: WeatherReport) =
                Stored(
                    report.temperatureCelsius,
                    report.weatherCode,
                    report.isDay,
                    report.coordinates.latitude,
                    report.coordinates.longitude,
                    report.fetchedAt.toEpochMilli(),
                )
        }
    }
}
