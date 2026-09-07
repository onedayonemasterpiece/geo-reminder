package com.onedayonemasterpiece.georeminder

import android.content.ContentValues

fun AuditRepository.nextNotificationId(): Int {
    val db = database.writableDatabase
    db.beginTransaction()
    return try {
        db.execSQL(
            "INSERT OR IGNORE INTO counters(name, value) VALUES('notification_id', 1000)",
        )
        val current = db.rawQuery(
            "SELECT value FROM counters WHERE name = 'notification_id'",
            null,
        ).use { cursor ->
            check(cursor.moveToFirst()) { "notification_id counter is missing" }
            cursor.getLong(0)
        }
        val next = if (current >= Int.MAX_VALUE - 1L) 1001 else current + 1L
        val values = ContentValues().apply { put("value", next) }
        check(
            db.update("counters", values, "name = ?", arrayOf("notification_id")) == 1,
        ) { "notification_id counter update failed" }
        db.setTransactionSuccessful()
        next.toInt()
    } finally {
        db.endTransaction()
    }
}

fun AuditRepository.createNotificationAttempt(
    notificationId: Int,
    rule: ReminderRule,
    zone: CircleZone,
    isTest: Boolean,
    channelId: String,
    createdAtEpochMs: Long,
) {
    val values = ContentValues().apply {
        put("notification_id", notificationId)
        put("rule_id", rule.id)
        put("zone_id", zone.id)
        put("zone_label", zone.label)
        put("created_at", createdAtEpochMs)
        put("is_test", if (isTest) 1 else 0)
        put("channel_id", channelId)
        put("state", "ATTEMPTED")
    }
    database.writableDatabase.insertOrThrow("notification_deliveries", null, values)
}

fun AuditRepository.markNotificationPosted(
    notificationId: Int,
    postedAtEpochMs: Long,
    activeConfirmed: Boolean,
) {
    val values = ContentValues().apply {
        put("posted_at", postedAtEpochMs)
        put("last_active_check_at", postedAtEpochMs)
        put("state", if (activeConfirmed) "ACTIVE_CONFIRMED" else "POSTED_UNCONFIRMED")
        if (activeConfirmed) put("verified_active_at", postedAtEpochMs)
        putNull("failure_reason")
    }
    database.writableDatabase.update(
        "notification_deliveries",
        values,
        "notification_id = ?",
        arrayOf(notificationId.toString()),
    )
}

fun AuditRepository.markNotificationFailed(notificationId: Int, reason: String) {
    val values = ContentValues().apply {
        put("state", "FAILED")
        put("failure_reason", reason)
    }
    database.writableDatabase.update(
        "notification_deliveries",
        values,
        "notification_id = ?",
        arrayOf(notificationId.toString()),
    )
}

fun AuditRepository.markNotificationTapped(notificationId: Int, atEpochMs: Long = System.currentTimeMillis()) {
    val values = ContentValues().apply {
        put("tapped_at", atEpochMs)
        put("last_active_check_at", atEpochMs)
        put("state", "TAPPED")
    }
    database.writableDatabase.update(
        "notification_deliveries",
        values,
        "notification_id = ?",
        arrayOf(notificationId.toString()),
    )
}

fun AuditRepository.markNotificationDismissed(
    notificationId: Int,
    atEpochMs: Long = System.currentTimeMillis(),
) {
    val values = ContentValues().apply {
        put("dismissed_at", atEpochMs)
        put("last_active_check_at", atEpochMs)
        put("state", "DISMISSED")
    }
    database.writableDatabase.update(
        "notification_deliveries",
        values,
        "notification_id = ?",
        arrayOf(notificationId.toString()),
    )
}

fun AuditRepository.reconcileActiveNotifications(
    activeNotificationIds: Set<Int>,
    atEpochMs: Long = System.currentTimeMillis(),
): NotificationReconciliation {
    val candidates = database.readableDatabase.query(
        "notification_deliveries",
        arrayOf("notification_id", "state"),
        "posted_at IS NOT NULL AND tapped_at IS NULL AND dismissed_at IS NULL " +
            "AND state != 'FAILED'",
        null,
        null,
        null,
        null,
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getInt(0) to cursor.getString(1))
        }
    }

    var confirmed = 0
    var inactive = 0
    val db = database.writableDatabase
    db.beginTransaction()
    try {
        candidates.forEach { (notificationId, oldState) ->
            val values = ContentValues().apply {
                put("last_active_check_at", atEpochMs)
                if (notificationId in activeNotificationIds) {
                    put("verified_active_at", atEpochMs)
                    put("state", "ACTIVE_CONFIRMED")
                    putNull("inactive_observed_at")
                } else if (oldState == "ACTIVE_CONFIRMED") {
                    put("inactive_observed_at", atEpochMs)
                    put("state", "NO_LONGER_ACTIVE_UNKNOWN")
                }
            }
            if (notificationId in activeNotificationIds) confirmed += 1
            else if (oldState == "ACTIVE_CONFIRMED") inactive += 1
            db.update(
                "notification_deliveries",
                values,
                "notification_id = ?",
                arrayOf(notificationId.toString()),
            )
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
    return NotificationReconciliation(candidates.size, confirmed, inactive)
}

fun AuditRepository.listNotifications(limit: Int = 500): List<NotificationDelivery> {
    val safeLimit = limit.coerceIn(1, 10_000)
    return database.readableDatabase.query(
        "notification_deliveries",
        NOTIFICATION_COLUMNS,
        null,
        null,
        null,
        null,
        "created_at DESC, notification_id DESC",
        safeLimit.toString(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toNotificationDelivery())
        }
    }
}

fun AuditRepository.forEachNotificationAscending(block: (NotificationDelivery) -> Unit) {
    database.readableDatabase.query(
        "notification_deliveries",
        NOTIFICATION_COLUMNS,
        null,
        null,
        null,
        null,
        "created_at ASC, notification_id ASC",
    ).use { cursor ->
        while (cursor.moveToNext()) block(cursor.toNotificationDelivery())
    }
}

