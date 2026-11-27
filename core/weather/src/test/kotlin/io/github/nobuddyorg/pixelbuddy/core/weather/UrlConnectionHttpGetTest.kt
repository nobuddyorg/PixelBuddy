package io.github.nobuddyorg.pixelbuddy.core.weather

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Test
import java.net.InetSocketAddress
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Against a real local HTTP server (plain HTTP is allowed for loopback in tests only). */
class UrlConnectionHttpGetTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val http =
        UrlConnectionHttpGet(
            connectTimeoutMillis = 2_000,
            // Generous: a busy CI machine can take a few hundred ms to answer even locally, and only the slow
            // response below should time out.
            readTimeoutMillis = 1_000,
            maxBytes = 1024,
            allowLoopbackHttp = true,
        )

    @After
    fun stop() = server.stop(0)

    private fun serve(
        path: String,
        status: Int,
        body: String,
        delayMillis: Long = 0,
        chunked: Boolean = false,
        headers: Map<String, String> = emptyMap(),
    ): URL {
        server.createContext(path) { exchange ->
            Thread.sleep(delayMillis)
            headers.forEach { (key, value) -> exchange.responseHeaders.add(key, value) }
            val bytes = body.encodeToByteArray()
            exchange.sendResponseHeaders(status, if (chunked) 0 else bytes.size.toLong())
            runCatching { exchange.responseBody.use { it.write(bytes) } }
        }
        return URL("http://127.0.0.1:${server.address.port}$path")
    }

    @Test
    fun `a body is returned`() {
        assertEquals("""{"ok":true}""", http.get(serve("/ok", 200, """{"ok":true}""")))
    }

    @Test
    fun `server errors carry the status and Open-Meteo's reason`() {
        val error =
            assertFailsWith<WeatherError.Http> { http.get(serve("/err", 503, """{"error":true,"reason":"Busy"}""")) }

        assertEquals(503, error.code)
        assertEquals("Busy", error.reason)
        val plain = assertFailsWith<WeatherError.Http> { http.get(serve("/plain", 500, "oops")) }
        assertEquals(null, plain.reason)
    }

    @Test
    fun `slow responses time out`() {
        assertFailsWith<WeatherError.Timeout> { http.get(serve("/slow", 200, "{}", delayMillis = 3_000)) }
    }

    @Test
    fun `oversized bodies are rejected, with or without a length`() {
        val big = "x".repeat(4_000)

        assertFailsWith<WeatherError.TooLarge> { http.get(serve("/big", 200, big)) }
        assertFailsWith<WeatherError.TooLarge> { http.get(serve("/chunked", 200, big, chunked = true)) }
    }

    @Test
    fun `redirects are not followed`() {
        val error =
            assertFailsWith<WeatherError.Http> {
                http.get(serve("/moved", 302, "", headers = mapOf("Location" to "http://example.com/")))
            }
        assertEquals(302, error.code)
    }

    @Test
    fun `a closed port is a network error`() {
        val url = serve("/gone", 200, "{}")
        server.stop(0)

        assertFailsWith<WeatherError.Network> { http.get(url) }
    }

    @Test
    fun `plain HTTP is refused outside tests and for other hosts`() {
        assertFailsWith<IllegalArgumentException> { UrlConnectionHttpGet().get(serve("/insecure", 200, "{}")) }
        assertFailsWith<IllegalArgumentException> { http.get(URL("http://example.com/")) }
        assertFailsWith<IllegalArgumentException> { http.get(URL("ftp://127.0.0.1/")) }
    }
}
