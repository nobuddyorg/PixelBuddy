package io.github.nobuddyorg.pixelbuddy.core.data.crash

import java.time.Clock

/**
 * Writes a crash report for every uncaught exception, then hands the crash to the previous handler so Android
 * still shows its dialog and ends the process. Nothing is sent anywhere.
 */
class CrashHandler(
    private val store: CrashReportStore,
    private val device: DeviceInfo,
    internal val previous: Thread.UncaughtExceptionHandler?,
    private val clock: Clock = Clock.systemUTC(),
    private val formatter: CrashReportFormatter = CrashReportFormatter(),
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            val time = clock.instant()
            store.save(time, formatter.format(time, device, thread.name, throwable))
        } catch (_: Throwable) {
            // Reporting must never hide the original crash.
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        /** Installs the handler as the default one. Installing again replaces the earlier PixelBuddy handler. */
        fun install(
            store: CrashReportStore,
            device: DeviceInfo,
        ): CrashHandler {
            val current = Thread.getDefaultUncaughtExceptionHandler()
            val previous = if (current is CrashHandler) current.previous else current
            return CrashHandler(store, device, previous).also(Thread::setDefaultUncaughtExceptionHandler)
        }
    }
}
