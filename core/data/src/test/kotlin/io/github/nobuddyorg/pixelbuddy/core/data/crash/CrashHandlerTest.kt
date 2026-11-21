package io.github.nobuddyorg.pixelbuddy.core.data.crash

import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CrashHandlerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val originalDefault = Thread.getDefaultUncaughtExceptionHandler()
    private val device = DeviceInfo("1.0", 1, "17", 37, null, "Google", "Pixel")
    private val clock = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC)

    @After
    fun restoreDefaultHandler() = Thread.setDefaultUncaughtExceptionHandler(originalDefault)

    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        val received = mutableListOf<Throwable>()

        override fun uncaughtException(
            t: Thread,
            e: Throwable,
        ) {
            received += e
        }
    }

    @Test
    fun `crash is stored and then passed to the previous handler`() {
        val store = CrashReportStore(folder.root)
        val previous = RecordingHandler()
        val crash = IllegalStateException("boom")

        CrashHandler(store, device, previous, clock).uncaughtException(Thread.currentThread(), crash)

        val report = store.list().single()
        assertEquals("java.lang.IllegalStateException: boom", report.summary)
        assertContains(store.read(report.id)!!, "Time: 2026-11-21T10:00:00Z")
        assertSame(crash, previous.received.single())
    }

    @Test
    fun `previous handler still runs when the report can't be written`() {
        val notADirectory = File(folder.root, "file").apply { writeText("x") }
        val previous = RecordingHandler()

        CrashHandler(CrashReportStore(notADirectory), device, previous, clock)
            .uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertEquals(1, previous.received.size)
    }

    @Test
    fun `install chains the existing default handler once`() {
        val previous = RecordingHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)
        val store = CrashReportStore(folder.root)

        CrashHandler.install(store, device)
        val second = CrashHandler.install(store, device)

        assertSame(second, Thread.getDefaultUncaughtExceptionHandler())
        assertSame(previous, second.previous)
    }
}
