package com.onedayonemasterpiece.georeminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeofenceIdsTest {
    @Test
    fun roundTrip() {
        val requestId = GeofenceIds.compose("museum-1", "entrance-a")
        assertEquals("museum-1::entrance-a", requestId)
        assertEquals("museum-1" to "entrance-a", GeofenceIds.split(requestId))
    }

    @Test
    fun rejectsMalformedIds() {
        assertNull(GeofenceIds.split("missing-separator"))
        assertNull(GeofenceIds.split("::zone"))
        assertNull(GeofenceIds.split("rule::"))
    }
}
