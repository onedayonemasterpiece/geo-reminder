package com.onedayonemasterpiece.georeminder

import android.content.ContentValues

fun AuditRepository.recordEvent(
    occurredAtEpochMs: Long = System.currentTimeMillis(),
    eventType: String,
    severity: String,
    status: String,
    message: String,
    ruleId: String? = null,
    zoneId: String? = null,
    transition: String? = null,
    notificationId: Int? = null,
    detailsJson: String? = null,
): Long {
    val values = ContentValues().apply {
        put("occurred_at", occurredAtEpochMs)
        put("event_type", eventType)
        put("severity", severity)
        put("status", status)
        put("rule_id", ruleId)
        put("zone_id", zoneId)
        put("transition_name", transition)
        put("notification_id", notificationId)
        put("message", message)
        put("details_json", detailsJson)
    }
    return database.writableDatabase.insertOrThrow("audit_events", null, values)
}

fun AuditRepository.listEvents(limit: Int = 500): List<AuditEvent> {
    val safeLimit = limit.coerceIn(1, 10_000)
    return database.readableDatabase.query(
        "audit_events",
        EVENT_COLUMNS,
        null,
        null,
        null,
        null,
        "occurred_at DESC, id DESC",
        safeLimit.toString(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toAuditEvent())
        }
    }
}

fun AuditRepository.latestEvent(): AuditEvent? = listEvents(1).firstOrNull()

fun AuditRepository.forEachEventAscending(block: (AuditEvent) -> Unit) {
    database.readableDatabase.query(
        "audit_events",
        EVENT_COLUMNS,
        null,
        null,
        null,
        null,
        "occurred_at ASC, id ASC",
    ).use { cursor ->
        while (cursor.moveToNext()) block(cursor.toAuditEvent())
    }
}


fun AuditRepository.counts(): AuditCounts {
    val db = database.readableDatabase
    return AuditCounts(
        events = db.countRows("audit_events"),
        notifications = db.countRows("notification_deliveries"),
        imports = db.countRows("rule_imports"),
    )
}
