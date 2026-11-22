package io.github.nobuddyorg.pixelbuddy.core.weather

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/** Why a weather request failed. Every failure keeps the last value on screen. */
sealed class WeatherError(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    class Timeout(
        cause: Throwable,
    ) : WeatherError("timed out", cause)

    class Network(
        cause: Throwable,
    ) : WeatherError("network error: ${cause.message}", cause)

    class Http(
        val code: Int,
        val reason: String?,
    ) : WeatherError("HTTP $code${reason?.let { ": $it" } ?: ""}")

    class TooLarge(
        val limit: Int,
    ) : WeatherError("response larger than $limit bytes")

    class Malformed(
        detail: String,
        cause: Throwable? = null,
    ) : WeatherError("malformed response: $detail", cause)
}

/** A blocking HTTP GET that returns the body as text; call it off the main thread. */
fun interface HttpGet {
    @Throws(WeatherError::class)
    fun get(url: URL): String
}

/**
 * [HttpGet] on the platform's [HttpURLConnection]: HTTPS only, no redirects, timeouts and a size limit for the
 * body. [allowLoopbackHttp] lets tests talk to a local server over plain HTTP.
 */
class UrlConnectionHttpGet(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 10_000,
    private val maxBytes: Int = 64 * 1024,
    private val allowLoopbackHttp: Boolean = false,
) : HttpGet {
    override fun get(url: URL): String {
        require(url.protocol == "https" || (allowLoopbackHttp && url.protocol == "http" && isLoopback(url.host))) {
            "only HTTPS is allowed: $url"
        }
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.errorStream?.use { readLimited(it) }
                throw WeatherError.Http(code, body?.let(::errorReason))
            }
            if (connection.contentLengthLong > maxBytes) throw WeatherError.TooLarge(maxBytes)
            return connection.inputStream.use(::readLimited)
        } catch (e: SocketTimeoutException) {
            throw WeatherError.Timeout(e)
        } catch (e: WeatherError) {
            throw e
        } catch (e: IOException) {
            throw WeatherError.Network(e)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > maxBytes) throw WeatherError.TooLarge(maxBytes)
        }
        return out.toByteArray().decodeToString()
    }

    private fun isLoopback(host: String): Boolean =
        host == "localhost" || runCatching { InetAddress.getByName(host).isLoopbackAddress }.getOrDefault(false)
}

/** Open-Meteo errors look like `{"error":true,"reason":"..."}`. */
private fun errorReason(body: String): String? =
    runCatching { OpenMeteoJson.decodeFromString(ErrorBody.serializer(), body).reason }.getOrNull()
