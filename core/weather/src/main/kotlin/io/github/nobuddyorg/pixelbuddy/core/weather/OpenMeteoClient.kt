package io.github.nobuddyorg.pixelbuddy.core.weather

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToLong

/** A point on Earth in degrees. */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    /** Rounded to 0.1° (about 11 km), the only precision ever sent over the network. */
    fun rounded(): Coordinates = Coordinates(roundTenth(latitude), roundTenth(longitude))

    private fun roundTenth(value: Double): Double = (value * 10).roundToLong() / 10.0
}

/** A place found by name. */
data class Place(
    val name: String,
    val region: String?,
    val country: String?,
    val coordinates: Coordinates,
)

/** Current conditions as Open-Meteo reports them. */
data class CurrentWeather(
    val temperatureCelsius: Double,
    val weatherCode: Int,
    val isDay: Boolean,
)

/**
 * The Open-Meteo forecast and geocoding APIs (no key, no account). Requests carry only what they need: rounded
 * coordinates, or the place name the user typed. Blocking; call off the main thread.
 */
class OpenMeteoClient(
    private val http: HttpGet,
    private val forecastUrl: String = "https://api.open-meteo.com/v1/forecast",
    private val geocodingUrl: String = "https://geocoding-api.open-meteo.com/v1/search",
) {
    fun current(at: Coordinates): CurrentWeather {
        val point = at.rounded()
        val url =
            URL(
                "$forecastUrl?latitude=${point.latitude}&longitude=${point.longitude}" +
                    "&current=temperature_2m,weather_code,is_day",
            )
        val current = decode(ForecastBody.serializer(), http.get(url)).current
        if (!current.temperature.isFinite() ||
            current.isDay !in 0..1
        ) {
            throw WeatherError.Malformed("values out of range")
        }
        return CurrentWeather(current.temperature, current.weatherCode, current.isDay == 1)
    }

    /** Places named [name]; names shorter than two characters find nothing (and send nothing). */
    fun searchPlaces(
        name: String,
        language: String,
        count: Int = 10,
    ): List<Place> {
        val query = name.trim()
        if (query.length < 2) return emptyList()
        val url =
            URL(
                "$geocodingUrl?name=${encode(query)}&count=$count&language=${encode(language.lowercase())}&format=json",
            )
        // Open-Meteo leaves "results" out when nothing matches.
        return decode(GeocodingBody.serializer(), http.get(url)).results.map { result ->
            Place(result.name, result.admin1, result.country, Coordinates(result.latitude, result.longitude))
        }
    }

    private fun <T> decode(
        serializer: KSerializer<T>,
        body: String,
    ): T =
        try {
            OpenMeteoJson.decodeFromString(serializer, body)
        } catch (e: SerializationException) {
            throw WeatherError.Malformed(e.message ?: "invalid JSON", e)
        } catch (e: IllegalArgumentException) {
            throw WeatherError.Malformed(e.message ?: "invalid JSON", e)
        }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}

/** Strict: types must match and required fields must be there; only unknown fields are ignored. */
internal val OpenMeteoJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }

@Serializable
internal class ErrorBody(
    val reason: String? = null,
)

@Serializable
private class ForecastBody(
    val current: Current,
) {
    @Serializable
    class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int,
    )
}

@Serializable
private class GeocodingBody(
    val results: List<Result> = emptyList(),
) {
    @Serializable
    class Result(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
    )
}
