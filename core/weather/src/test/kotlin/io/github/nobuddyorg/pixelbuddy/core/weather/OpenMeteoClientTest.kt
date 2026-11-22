package io.github.nobuddyorg.pixelbuddy.core.weather

import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenMeteoClientTest {
    private class FakeHttp(
        var body: String,
    ) : HttpGet {
        val urls = mutableListOf<URL>()

        override fun get(url: URL): String {
            urls += url
            return body
        }
    }

    // Recorded from the live API (2026-11-22).
    private val forecast =
        """{"latitude":52.506966,"longitude":13.39165,"generationtime_ms":0.064,"utc_offset_seconds":0,""" +
            """"timezone":"GMT",""" +
            """"timezone_abbreviation":"GMT","elevation":43.0,""" +
            """"current_units":{"time":"iso8601","interval":"seconds",""" +
            """"temperature_2m":"°C","weather_code":"wmo code","is_day":""},"current":{"time":"2026-11-22T11:30",""" +
            """"interval":900,"temperature_2m":4.6,"weather_code":3,"is_day":1}}"""

    private val places =
        """{"results":[{"id":2950159,"name":"Berlin","latitude":52.52437,"longitude":13.41053,"elevation":74.0,""" +
            """"feature_code":"PPLC","country_code":"DE","timezone":"Europe/Berlin","population":3426354,""" +
            """"postcodes":["10967","13347"],"country":"Deutschland","admin1":"Land Berlin"},{"id":5083330,""" +
            """"name":"Berlin","latitude":44.46867,"longitude":-71.18508,"country_code":"US"}],""" +
            """"generationtime_ms":0.2}"""

    @Test
    fun `current weather is parsed from the forecast response`() {
        val http = FakeHttp(forecast)

        val weather = OpenMeteoClient(http).current(Coordinates(52.52437, 13.41053))

        assertEquals(CurrentWeather(4.6, 3, isDay = true), weather)
    }

    @Test
    fun `only rounded coordinates are sent`() {
        val http = FakeHttp(forecast)

        OpenMeteoClient(http).current(Coordinates(52.52437, -13.46053))

        val url = http.urls.single().toString()
        assertEquals(
            "https://api.open-meteo.com/v1/forecast?latitude=52.5&longitude=-13.5" +
                "&current=temperature_2m,weather_code,is_day",
            url,
        )
    }

    @Test
    fun `places are parsed with optional fields missing`() {
        val http = FakeHttp(places)

        val found = OpenMeteoClient(http).searchPlaces(" Berlin ", "DE")

        assertEquals(
            listOf(
                Place("Berlin", "Land Berlin", "Deutschland", Coordinates(52.52437, 13.41053)),
                Place("Berlin", null, null, Coordinates(44.46867, -71.18508)),
            ),
            found,
        )
        assertEquals(
            "https://geocoding-api.open-meteo.com/v1/search?name=Berlin&count=10&language=de&format=json",
            http.urls.single().toString(),
        )
    }

    @Test
    fun `names are URL-encoded`() {
        val http = FakeHttp("""{"generationtime_ms":0.2}""")

        OpenMeteoClient(http).searchPlaces("São Paulo & Co", "pt")

        assertTrue(
            http.urls
                .single()
                .toString()
                .contains("name=S%C3%A3o+Paulo+%26+Co&"),
        )
    }

    @Test
    fun `no match gives an empty list`() {
        val client = OpenMeteoClient(FakeHttp("""{"generationtime_ms":0.2}"""))

        assertEquals(emptyList(), client.searchPlaces("Xqzzyvv", "en"))
    }

    @Test
    fun `names shorter than two characters send nothing`() {
        val http = FakeHttp(places)

        assertEquals(emptyList(), OpenMeteoClient(http).searchPlaces(" B ", "en"))
        assertEquals(emptyList(), http.urls)
    }

    @Test
    fun `malformed responses are rejected`() {
        listOf(
            "",
            "not json",
            "[]",
            """{"current":{}}""",
            """{"current":{"temperature_2m":"warm","weather_code":3,"is_day":1}}""",
            """{"current":{"temperature_2m":4.6,"weather_code":3.5,"is_day":1}}""",
            """{"current":{"temperature_2m":4.6,"weather_code":3,"is_day":2}}""",
        ).forEach { body ->
            val client = OpenMeteoClient(FakeHttp(body))
            assertFailsWith<WeatherError.Malformed>(body) { client.current(Coordinates(1.0, 2.0)) }
        }
        assertFailsWith<WeatherError.Malformed> {
            OpenMeteoClient(FakeHttp("""{"results":[{"name":"X"}]}""")).searchPlaces("Xy", "en")
        }
    }

    @Test
    fun `coordinates round to a tenth of a degree`() {
        assertEquals(Coordinates(52.5, 13.4), Coordinates(52.52437, 13.41053).rounded())
        assertEquals(Coordinates(-33.9, 151.2), Coordinates(-33.86785, 151.20732).rounded())
        assertEquals(Coordinates(0.0, -0.1), Coordinates(0.04, -0.06).rounded())
        assertEquals(Coordinates(90.0, 180.0), Coordinates(89.99, 179.96).rounded())
    }
}
