package io.github.nobuddyorg.pixelbuddy.core.data.crash

import org.junit.Test
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashReportFormatterTest {
    private val time = Instant.parse("2026-11-21T20:15:30Z")
    private val device =
        DeviceInfo(
            appVersionName = "1.2.3",
            appVersionCode = 1_002_003,
            androidRelease = "17",
            sdkInt = 37,
            oneUiVersion = "9.0",
            manufacturer = "samsung",
            model = "SM-S921B",
        )

    @Test
    fun `report contains device details and the full stack trace with causes`() {
        val crash = IllegalStateException("boom", IllegalArgumentException("root cause"))

        val report = CrashReportFormatter().format(time, device, "main", crash)

        assertTrue(report.startsWith("PixelBuddy crash report\n"))
        assertContains(report, "Time: 2026-11-21T20:15:30Z")
        assertContains(report, "App: 1.2.3 (1002003)")
        assertContains(report, "Android: 17 (API 37)")
        assertContains(report, "One UI: 9.0")
        assertContains(report, "Device: samsung SM-S921B")
        assertContains(report, "Thread: main")
        assertContains(report, "java.lang.IllegalStateException: boom")
        assertContains(report, "Caused by: java.lang.IllegalArgumentException: root cause")
        assertContains(report, "at ${CrashReportFormatterTest::class.java.name}")
    }

    @Test
    fun `One UI line is left out on other devices`() {
        val report = CrashReportFormatter().format(time, device.copy(oneUiVersion = null), "main", Exception())

        assertFalse(report.contains("One UI: "))
    }

    @Test
    fun `long stack traces are truncated to the size limit`() {
        val deep = generateSequence(Exception("level 0")) { Exception("level", it) }.take(200).last()

        val report = CrashReportFormatter(maxChars = 2_000).format(time, device, "main", deep)

        assertEquals(2_000, report.length)
        assertTrue(report.endsWith(CrashReportFormatter.TRUNCATED))
        assertContains(report, "Thread: main")
    }

    @Test
    fun `One UI version is derived from the Samsung platform number`() {
        assertEquals("9.0", DeviceInfo.oneUiVersionFromSemPlatform(180_000))
        assertEquals("8.5", DeviceInfo.oneUiVersionFromSemPlatform(170_500))
        assertNull(DeviceInfo.oneUiVersionFromSemPlatform(0))
    }
}
