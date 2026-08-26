package com.onedayonemasterpiece.georeminder

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.time.Instant

fun AuditRepository.eligibility(rule: ReminderRule, now: Instant = Instant.now()): RuleEligibility {
    if (!rule.enabled) return RuleEligibility(false, "RULE_DISABLED")
    if (rule.validFrom != null && now.isBefore(rule.validFrom)) {
        return RuleEligibility(false, "NOT_ACTIVE_YET")
    }
    if (rule.validUntil != null && now.isAfter(rule.validUntil)) {
        return RuleEligibility(false, "EXPIRED")
    }

    val state = database.readableDatabase.query(
        "rule_state",
        arrayOf("completed", "last_triggered_at"),
        "rule_id = ?",
        arrayOf(rule.id),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) null
        else Pair(cursor.getInt(0) == 1, cursor.nullableLong(1))
    }

    if (rule.repeatMode == RepeatMode.ONCE && state?.first == true) {
        return RuleEligibility(false, "ONCE_ALREADY_COMPLETED")
    }
    val lastTriggered = state?.second
    if (lastTriggered != null && rule.cooldownMinutes > 0) {
        val nextAllowed = Instant.ofEpochMilli(lastTriggered)
            .plusSeconds(rule.cooldownMinutes * 60)
        if (now.isBefore(nextAllowed)) return RuleEligibility(false, "COOLDOWN")
    }
    return RuleEligibility(true, "ALLOWED")
}

fun AuditRepository.markRuleTriggered(
    rule: ReminderRule,
    notificationId: Int,
    atEpochMs: Long = System.currentTimeMillis(),
) {
    val existingCompleted = database.readableDatabase.query(
        "rule_state",
        arrayOf("completed"),
        "rule_id = ?",
        arrayOf(rule.id),
        null,
        null,
        null,
        "1",
    ).use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }

    val values = ContentValues().apply {
        put("rule_id", rule.id)
        put("completed", if (existingCompleted || rule.repeatMode == RepeatMode.ONCE) 1 else 0)
        put("last_triggered_at", atEpochMs)
        put("last_notification_id", notificationId)
        put("updated_at", atEpochMs)
    }
    database.writableDatabase.insertWithOnConflict(
        "rule_state",
        null,
        values,
        SQLiteDatabase.CONFLICT_REPLACE,
    )
}

fun AuditRepository.resetRuleState(ruleId: String?): Int {
    return if (ruleId.isNullOrBlank()) {
        database.writableDatabase.delete("rule_state", null, null)
    } else {
        database.writableDatabase.delete(
            "rule_state",
            "rule_id = ?",
            arrayOf(ruleId),
        )
    }
}

fun AuditRepository.completedCount(ruleSet: RuleSet): Int {
    if (ruleSet.rules.isEmpty()) return 0
    val completed = mutableSetOf<String>()
    database.readableDatabase.query(
        "rule_state",
        arrayOf("rule_id"),
        "completed = 1",
        null,
        null,
        null,
        null,
    ).use { cursor ->
        while (cursor.moveToNext()) completed += cursor.getString(0)
    }
    return ruleSet.rules.count { it.id in completed }
}

fun AuditRepository.ruleSummary(ruleId: String): RuleRuntimeSummary {
    val state = database.readableDatabase.query(
        "rule_state",
        arrayOf("completed", "last_triggered_at", "last_notification_id"),
        "rule_id = ?",
        arrayOf(ruleId),
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) Triple(false, null, null)
        else Triple(
            cursor.getInt(0) == 1,
            cursor.nullableLong(1),
            cursor.nullableInt(2),
        )
    }

    val notificationCount = database.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM notification_deliveries WHERE rule_id = ? AND is_test = 0",
        arrayOf(ruleId),
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
    val latestNotification = database.readableDatabase.rawQuery(
        "SELECT state, COALESCE(posted_at, created_at) AS effective_at " +
            "FROM notification_deliveries WHERE rule_id = ? AND is_test = 0 " +
            "ORDER BY created_at DESC LIMIT 1",
        arrayOf(ruleId),
    ).use { cursor ->
        if (!cursor.moveToFirst()) Pair<String?, Long?>(null, null)
        else Pair(cursor.nullableString(0), cursor.nullableLong(1))
    }

    return RuleRuntimeSummary(
        completed = state.first,
        lastTriggeredAtEpochMs = state.second,
        lastNotificationId = state.third,
        notificationCount = notificationCount,
        lastNotificationState = latestNotification.first,
        lastNotificationAtEpochMs = latestNotification.second,
    )
}

