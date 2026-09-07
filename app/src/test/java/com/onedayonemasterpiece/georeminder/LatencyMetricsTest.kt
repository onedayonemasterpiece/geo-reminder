package com.onedayonemasterpiece.georeminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatencyMetricsTest {
    @Test
    fun returnsNonNegativeDelta() {
        assertEquals(250L, LatencyMetrics.nonNegativeDelta(1_000L, 1_250L))
        assertEquals(0L, LatencyMetrics.nonNegativeDelta(1_000L, 1_000L))
    }

    @Test
    fun rejectsMissingInvalidOrClockReversedValues() {
        assertNull(LatencyMetrics.nonNegativeDelta(null, 1_000L))
        assertNull(LatencyMetrics.nonNegativeDelta(1_000L, null))
        assertNull(LatencyMetrics.nonNegativeDelta(0L, 1_000L))
        assertNull(LatencyMetrics.nonNegativeDelta(2_000L, 1_000L))
    }
}
