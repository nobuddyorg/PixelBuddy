package io.github.nobuddyorg.pixelbuddy.core.weather

import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherPlace
import io.github.nobuddyorg.pixelbuddy.core.data.settings.WeatherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.SocketTimeoutException
import java.net.URL
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WeatherRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class MutableClock(
        var now: Instant,
    ) : Clock() {
        override fun instant(): Instant = now

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this
    }

    private class FakeHttp : HttpGet {
        val urls = mutableListOf<URL>()
        var temperature = 4.6
        var failure: WeatherError? = null

        override fun get(url: URL): String {
            urls += url
            failure?.let { throw it }
            return """{"current":{"temperature_2m":$temperature,"weather_code":61,"is_day":0}}"""
        }
    }

    private val berlin = WeatherPlace("Berlin", latitude = 52.52437, longitude = 13.41053)
    private val settings = MutableStateFlow(WeatherSettings(enabled = true, place = berlin))
    private val http = FakeHttp()
    private val clock = MutableClock(Instant.parse("2026-11-22T12:00:00Z"))
    private var approximate: Coordinates? = null
    private val cacheFile by lazy { File(folder.root, "weather.json") }

    private fun kotlinx.coroutines.test.TestScope.repository() =
        WeatherRepository(
            settings,
            OpenMeteoClient(http),
            WeatherCache(cacheFile),
            { approximate },
            clock,
            StandardTestDispatcher(testScheduler),
        )

    @Test
    fun `weather off sends nothing`() =
        runTest {
            settings.value = WeatherSettings(enabled = false, place = berlin)

            assertEquals(RefreshResult.Off, repository().refreshIfStale(force = true))
            assertEquals(emptyList(), http.urls)
        }

    @Test
    fun `without a place nothing is sent`() =
        runTest {
            settings.value = WeatherSettings(enabled = true)

            assertEquals(RefreshResult.NoLocation, repository().refreshIfStale())
            settings.value = WeatherSettings(enabled = true, approximateLocation = true, place = berlin)
            assertEquals(RefreshResult.NoLocation, repository().refreshIfStale())
            assertEquals(emptyList(), http.urls)
        }

    @Test
    fun `a refresh fetches, keeps and caches the report`() =
        runTest {
            val repository = repository()

            val result = repository.refreshIfStale()

            val report = assertIs<RefreshResult.Updated>(result).report
            assertEquals(WeatherReport(4.6, 61, false, Coordinates(52.5, 13.4), clock.now), report)
            assertEquals(WeatherCondition.Rain, report.condition)
            assertEquals(report, repository.report.value)
            assertEquals(report, WeatherCache(cacheFile).read())
        }

    @Test
    fun `refreshes only after 30 minutes`() =
        runTest {
            val repository = repository()
            repository.refreshIfStale()

            clock.now += Duration.ofMinutes(29)
            assertEquals(RefreshResult.UpToDate, repository.refreshIfStale())
            clock.now += Duration.ofMinutes(1)
            assertIs<RefreshResult.Updated>(repository.refreshIfStale())
            assertEquals(2, http.urls.size)
        }

    @Test
    fun `a forced refresh or another place fetches at once`() =
        runTest {
            val repository = repository()
            repository.refreshIfStale()

            assertIs<RefreshResult.Updated>(repository.refreshIfStale(force = true))
            settings.value = settings.value.copy(place = WeatherPlace("Paris", latitude = 48.85, longitude = 2.35))
            val paris = assertIs<RefreshResult.Updated>(repository.refreshIfStale()).report
            assertEquals(Coordinates(48.9, 2.4), paris.coordinates)
            assertEquals(3, http.urls.size)
        }

    @Test
    fun `the approximate location is used when chosen`() =
        runTest {
            approximate = Coordinates(48.137, 11.575)
            settings.value = WeatherSettings(enabled = true, approximateLocation = true, place = berlin)

            val report = assertIs<RefreshResult.Updated>(repository().refreshIfStale()).report

            assertEquals(Coordinates(48.1, 11.6), report.coordinates)
        }

    @Test
    fun `failures keep the last report`() =
        runTest {
            val repository = repository()
            val first = assertIs<RefreshResult.Updated>(repository.refreshIfStale()).report
            clock.now += Duration.ofHours(1)
            http.failure = WeatherError.Timeout(SocketTimeoutException())

            assertIs<RefreshResult.Failed>(repository.refreshIfStale())

            assertEquals(first, repository.report.value)
        }

    @Test
    fun `the cached report is shown after a restart without a request`() =
        runTest {
            repository().refreshIfStale()
            clock.now += Duration.ofMinutes(10)

            val restarted = repository()
            assertNull(restarted.report.value)
            assertEquals(RefreshResult.UpToDate, restarted.refreshIfStale())
            assertEquals(4.6, restarted.report.value?.temperatureCelsius)
            assertEquals(1, http.urls.size)
        }

    @Test
    fun `reports dim after 3 hours and hide after 12`() {
        val report = WeatherReport(1.0, 0, true, Coordinates(0.0, 0.0), Instant.parse("2026-11-22T00:00:00Z"))
        val at = { hours: Long, minutes: Long ->
            report.fetchedAt + Duration.ofHours(hours) +
                Duration.ofMinutes(minutes)
        }

        assertEquals(Freshness.Fresh, Freshness.of(report, at(2, 59)))
        assertEquals(Freshness.Dimmed, Freshness.of(report, at(3, 0)))
        assertEquals(Freshness.Dimmed, Freshness.of(report, at(11, 59)))
        assertEquals(Freshness.Hidden, Freshness.of(report, at(12, 0)))
    }

    @Test
    fun `the place search goes through the client`() =
        runTest {
            val places = repository().searchPlaces("B", "en")

            assertEquals(emptyList(), places)
        }
}
