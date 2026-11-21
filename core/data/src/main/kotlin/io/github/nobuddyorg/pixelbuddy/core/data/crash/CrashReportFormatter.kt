package io.github.nobuddyorg.pixelbuddy.core.data.crash

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Turns a crash into a readable plain-text report of at most [maxChars] characters. */
class CrashReportFormatter(
    private val maxChars: Int = DEFAULT_MAX_CHARS,
) {
    fun format(
        time: Instant,
        device: DeviceInfo,
        threadName: String,
        throwable: Throwable,
    ): String {
        val header =
            buildString {
                appendLine("PixelBuddy crash report")
                appendLine("Time: ${TIME_FORMAT.format(time)}")
                appendLine("App: ${device.appVersionName} (${device.appVersionCode})")
                appendLine("Android: ${device.androidRelease} (API ${device.sdkInt})")
                device.oneUiVersion?.let { appendLine("One UI: $it") }
                appendLine("Device: ${device.manufacturer} ${device.model}")
                appendLine("Thread: $threadName")
                appendLine()
            }
        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val available = maxChars - header.length
        val body =
            if (stackTrace.length <= available) {
                stackTrace
            } else {
                stackTrace.take((available - TRUNCATED.length).coerceAtLeast(0)) + TRUNCATED
            }
        return (header + body).take(maxChars)
    }

    companion object {
        const val DEFAULT_MAX_CHARS = 32 * 1024
        internal const val TRUNCATED = "\n… (truncated)\n"
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
    }
}
