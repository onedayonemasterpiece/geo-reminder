package com.onedayonemasterpiece.georeminder

import java.time.Instant

enum class TransitionType {
    ENTER,
    EXIT,
    DWELL,
}

enum class RepeatMode {
    ONCE,
    ALWAYS,
}

data class CircleZone(
    val id: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
)

data class ReminderRule(
    val id: String,
    val title: String,
    val message: String,
    val enabled: Boolean,
    val transition: TransitionType,
    val repeatMode: RepeatMode,
    val cooldownMinutes: Long,
    val responsivenessMs: Int,
    val dwellMs: Int,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val sourceText: String?,
    val zones: List<CircleZone>,
) {
    fun isInsideTimeWindow(now: Instant = Instant.now()): Boolean {
        if (!enabled) return false
        if (validFrom != null && now.isBefore(validFrom)) return false
        if (validUntil != null && now.isAfter(validUntil)) return false
        return true
    }
}

data class RuleSet(
    val schemaVersion: Int,
    val generatedAt: Instant?,
    val rules: List<ReminderRule>,
) {
    val zoneCount: Int
        get() = rules.sumOf { it.zones.size }

    fun resolve(requestId: String): ResolvedZone? {
        val (ruleId, zoneId) = GeofenceIds.split(requestId) ?: return null
        val rule = rules.firstOrNull { it.id == ruleId } ?: return null
        val zone = rule.zones.firstOrNull { it.id == zoneId } ?: return null
        return ResolvedZone(rule, zone)
    }
}

data class ResolvedZone(
    val rule: ReminderRule,
    val zone: CircleZone,
)

data class ImportResult(
    val success: Boolean,
    val message: String,
    val ruleSet: RuleSet? = null,
    val sha256: String? = null,
)

data class RegistrationResult(
    val success: Boolean,
    val message: String,
    val ruleCount: Int,
    val zoneCount: Int,
)

data class NotificationOutcome(
    val posted: Boolean,
    val notificationId: Int?,
    val state: String,
    val message: String,
)

data class RuleEligibility(
    val allowed: Boolean,
    val reason: String,
)
