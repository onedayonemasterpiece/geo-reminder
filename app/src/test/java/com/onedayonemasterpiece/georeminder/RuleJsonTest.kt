package com.onedayonemasterpiece.georeminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RuleJsonTest {
    @Test
    fun parsesFiniteRuleSet() {
        val parsed = RuleJson.parse(validJson())
        assertEquals(1, parsed.schemaVersion)
        assertEquals(1, parsed.rules.size)
        assertEquals(1, parsed.zoneCount)
        val rule = parsed.rules.single()
        assertEquals(TransitionType.ENTER, rule.transition)
        assertEquals(RepeatMode.ONCE, rule.repeatMode)
        assertEquals("Museum", rule.zones.single().label)
    }

    @Test
    fun rejectsUnknownFields() {
        val invalid = validJson().replace(
            "\"message\": \"Visit\",",
            "\"message\": \"Visit\", \"unexpected\": true,",
        )
        assertThrows(IllegalArgumentException::class.java) { RuleJson.parse(invalid) }
    }

    @Test
    fun rejectsDuplicateRuleIds() {
        val rule = """{"id":"same","title":"T","message":"M","zones":[{"id":"z","latitude":54.7,"longitude":20.5,"radius_m":150}]}"""
        val invalid = """{"schema_version":1,"rules":[$rule,$rule]}"""
        assertThrows(IllegalArgumentException::class.java) { RuleJson.parse(invalid) }
    }


    @Test
    fun validatesTimeWindow() {
        val rule = RuleJson.parse(validJson()).rules.single()
        assertFalse(rule.isInsideTimeWindow(Instant.parse("2026-08-26T11:59:59Z")))
        assertTrue(rule.isInsideTimeWindow(Instant.parse("2026-08-26T12:30:00Z")))
        assertFalse(rule.isInsideTimeWindow(Instant.parse("2026-08-26T14:00:01Z")))
    }

    private fun validJson(): String =
        """
        {
          "schema_version": 1,
          "generated_at": "2026-08-26T12:00:00Z",
          "rules": [
            {
              "id": "museum-1",
              "title": "Visit museum",
              "message": "Visit",
              "enabled": true,
              "transition": "enter",
              "repeat": "once",
              "cooldown_minutes": 0,
              "responsiveness_ms": 120000,
              "valid_from": "2026-08-26T12:00:00Z",
              "valid_until": "2026-08-26T14:00:00Z",
              "zones": [
                {
                  "id": "museum-a",
                  "label": "Museum",
                  "latitude": 54.71,
                  "longitude": 20.45,
                  "radius_m": 150
                }
              ]
            }
          ]
        }
        """.trimIndent()
}
