package io.github.nobuddyorg.pixelbuddy.core.data.icons

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RenderTimingsTest {
    private fun ms(value: Int) = value * 1_000_000L

    @Test
    fun `nothing rendered, no summary`() {
        val timings = RenderTimings()
        assertNull(timings.summary(neonStyle = true))
        assertNull(timings.summary(neonStyle = false))
    }

    @Test
    fun `median and 90th percentile per kind`() {
        val timings = RenderTimings()
        (1..10).forEach { timings.record(neonStyle = true, ms(it * 10)) }
        timings.record(neonStyle = false, 1_400_000L)

        assertEquals(RenderTimings.Summary(10, medianMs = 50, p90Ms = 90), timings.summary(neonStyle = true))
        // rounded to whole milliseconds
        assertEquals(RenderTimings.Summary(1, medianMs = 1, p90Ms = 1), timings.summary(neonStyle = false))
    }

    @Test
    fun `only the latest renders count`() {
        val timings = RenderTimings(capacity = 4)
        repeat(4) { timings.record(neonStyle = true, ms(100)) }
        repeat(4) { timings.record(neonStyle = true, ms(20)) }

        assertEquals(RenderTimings.Summary(8, medianMs = 20, p90Ms = 20), timings.summary(neonStyle = true))
    }
}
