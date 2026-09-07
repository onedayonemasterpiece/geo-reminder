package com.onedayonemasterpiece.georeminder

import org.junit.Assert.assertThrows
import org.junit.Test

class RuleJsonLimitTest {
    @Test
    fun rejectsMoreThanOneHundredGeofences() {
        val zones = (0..100).joinToString(",") { index ->
            """{"id":"z$index","latitude":54.7,"longitude":20.5,"radius_m":150}"""
        }
        val json =
            """{"schema_version":1,"rules":[{"id":"rule","title":"T","message":"M","zones":[$zones]}]}"""
        assertThrows(IllegalArgumentException::class.java) { RuleJson.parse(json) }
    }
}
