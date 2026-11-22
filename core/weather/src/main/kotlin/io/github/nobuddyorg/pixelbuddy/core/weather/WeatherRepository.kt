package io.github.nobuddyorg.pixelbuddy.core.weather

import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** How current a report is: shown, dimmed after 3 h, hidden after 12 h. */
enum class Freshness {
    Fresh,
    Dimmed,
    Hidden,
    ;

    companion object {
        val DIM_AFTER: Duration = Duration.ofHours(3)
        val HIDE_AFTER: Duration = Duration.ofHours(12)

        fun of(
            report: WeatherReport,
            now: Instant,
        ): Freshness {
            val age = Duration.between(report.fetchedAt, now)
            return when {
                age >= HIDE_AFTER -> Hidden
                age >= DIM_AFTER -> Dimmed
                else -> Fresh
            }
        }
    }
}

/** What a refresh did. */
sealed interface RefreshResult {
    /** Weather is off: nothing was sent. */
    data object Off : RefreshResult

    /** No place chosen, or the approximate location isn't available (no permission, no fix). */
    data object NoLocation : RefreshResult

    /** The last report is recent enough. */
    data object UpToDate : RefreshResult

    data class Updated(
        val report: WeatherReport,
    ) : RefreshResult

    /** The request failed; the last report stays. */
    data class Failed(
        val error: WeatherError,
    ) : RefreshResult
}

/**
 * Weather for the home header. Nothing happens in the background: home calls [refreshIfStale] when it becomes
 * visible, and only then, if weather is on and the last report is older than 30 minutes (or for another place), is
 * a request sent. The last report is kept across restarts and while offline.
 */
class WeatherRepository(
    private val settings: Flow<WeatherSettings>,
    private val client: OpenMeteoClient,
    private val cache: WeatherCache,
    private val location: LocationSource,
    private val clock: Clock = Clock.systemUTC(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private var loaded = false
    private val current = MutableStateFlow<WeatherReport?>(null)

    /** The last report, or null before the first one. */
    val report: StateFlow<WeatherReport?> = current.asStateFlow()

    suspend fun refreshIfStale(force: Boolean = false): RefreshResult =
        mutex.withLock {
            if (!loaded) {
                current.value = withContext(io) { cache.read() }
                loaded = true
            }
            val weather = settings.first()
            if (!weather.enabled) return RefreshResult.Off
            val target = coordinates(weather)?.rounded() ?: return RefreshResult.NoLocation
            val last = current.value
            val now = clock.instant()
            if (!force && last != null && last.coordinates == target &&
                Duration.between(last.fetchedAt, now) < REFRESH_AFTER
            ) {
                return RefreshResult.UpToDate
            }
            // A request that went out is finished and kept even if home closes meanwhile (bounded by timeouts).
            withContext(NonCancellable + io) {
                try {
                    val weatherNow = client.current(target)
                    val report =
                        WeatherReport(
                            weatherNow.temperatureCelsius,
                            weatherNow.weatherCode,
                            weatherNow.isDay,
                            target,
                            now,
                        )
                    cache.write(report)
                    current.value = report
                    RefreshResult.Updated(report)
                } catch (e: WeatherError) {
                    RefreshResult.Failed(e)
                }
            }
        }

    /** Places for the settings' name search: only called when the user searches. */
    suspend fun searchPlaces(
        name: String,
        language: String,
    ): List<Place> = withContext(io) { client.searchPlaces(name, language) }

    private suspend fun coordinates(weather: WeatherSettings): Coordinates? =
        if (weather.approximateLocation) {
            location.approximate()
        } else {
            weather.place?.let { Coordinates(it.latitude, it.longitude) }
        }

    companion object {
        val REFRESH_AFTER: Duration = Duration.ofMinutes(30)
    }
}
