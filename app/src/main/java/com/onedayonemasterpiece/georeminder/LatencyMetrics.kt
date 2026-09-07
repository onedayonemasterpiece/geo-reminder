package com.onedayonemasterpiece.georeminder

object LatencyMetrics {
    fun nonNegativeDelta(fromEpochMs: Long?, toEpochMs: Long?): Long? {
        if (fromEpochMs == null || toEpochMs == null) return null
        if (fromEpochMs <= 0L || toEpochMs <= 0L) return null
        val delta = toEpochMs - fromEpochMs
        return delta.takeIf { it >= 0L }
    }
}
