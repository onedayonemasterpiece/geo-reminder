package com.onedayonemasterpiece.georeminder

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray

internal fun SQLiteDatabase.countRows(table: String): Long {
    return rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }
}

internal fun parseStringArray(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) add(array.getString(index))
        }
    }.getOrDefault(emptyList())
}

internal fun Cursor.toAuditEvent(): AuditEvent = AuditEvent(
    id = getLong(getColumnIndexOrThrow("id")),
    occurredAtEpochMs = getLong(getColumnIndexOrThrow("occurred_at")),
    eventType = getString(getColumnIndexOrThrow("event_type")),
    severity = getString(getColumnIndexOrThrow("severity")),
    status = getString(getColumnIndexOrThrow("status")),
    ruleId = nullableString(getColumnIndexOrThrow("rule_id")),
    zoneId = nullableString(getColumnIndexOrThrow("zone_id")),
    transition = nullableString(getColumnIndexOrThrow("transition_name")),
    notificationId = nullableInt(getColumnIndexOrThrow("notification_id")),
    message = getString(getColumnIndexOrThrow("message")),
    detailsJson = nullableString(getColumnIndexOrThrow("details_json")),
)

internal fun Cursor.toNotificationDelivery(): NotificationDelivery = NotificationDelivery(
    notificationId = getInt(getColumnIndexOrThrow("notification_id")),
    ruleId = getString(getColumnIndexOrThrow("rule_id")),
    zoneId = nullableString(getColumnIndexOrThrow("zone_id")),
    zoneLabel = nullableString(getColumnIndexOrThrow("zone_label")),
    createdAtEpochMs = getLong(getColumnIndexOrThrow("created_at")),
    postedAtEpochMs = nullableLong(getColumnIndexOrThrow("posted_at")),
    verifiedActiveAtEpochMs = nullableLong(getColumnIndexOrThrow("verified_active_at")),
    lastActiveCheckAtEpochMs = nullableLong(getColumnIndexOrThrow("last_active_check_at")),
    inactiveObservedAtEpochMs = nullableLong(getColumnIndexOrThrow("inactive_observed_at")),
    tappedAtEpochMs = nullableLong(getColumnIndexOrThrow("tapped_at")),
    dismissedAtEpochMs = nullableLong(getColumnIndexOrThrow("dismissed_at")),
    isTest = getInt(getColumnIndexOrThrow("is_test")) == 1,
    channelId = getString(getColumnIndexOrThrow("channel_id")),
    state = getString(getColumnIndexOrThrow("state")),
    failureReason = nullableString(getColumnIndexOrThrow("failure_reason")),
)

internal fun Cursor.toRuleImportRecord(): RuleImportRecord = RuleImportRecord(
    id = getLong(getColumnIndexOrThrow("id")),
    importedAtEpochMs = getLong(getColumnIndexOrThrow("imported_at")),
    source = getString(getColumnIndexOrThrow("source")),
    sha256 = nullableString(getColumnIndexOrThrow("sha256")),
    ruleCount = getInt(getColumnIndexOrThrow("rule_count")),
    zoneCount = getInt(getColumnIndexOrThrow("zone_count")),
    success = getInt(getColumnIndexOrThrow("success")) == 1,
    message = getString(getColumnIndexOrThrow("message")),
)

internal fun Cursor.nullableString(index: Int): String? = if (isNull(index)) null else getString(index)
internal fun Cursor.nullableLong(index: Int): Long? = if (isNull(index)) null else getLong(index)
internal fun Cursor.nullableInt(index: Int): Int? = if (isNull(index)) null else getInt(index)

internal val EVENT_COLUMNS = arrayOf(
    "id",
    "occurred_at",
    "event_type",
    "severity",
    "status",
    "rule_id",
    "zone_id",
    "transition_name",
    "notification_id",
    "message",
    "details_json",
)

internal val NOTIFICATION_COLUMNS = arrayOf(
    "notification_id",
    "rule_id",
    "zone_id",
    "zone_label",
    "created_at",
    "posted_at",
    "verified_active_at",
    "last_active_check_at",
    "inactive_observed_at",
    "tapped_at",
    "dismissed_at",
    "is_test",
    "channel_id",
    "state",
    "failure_reason",
)

internal val IMPORT_COLUMNS = arrayOf(
    "id",
    "imported_at",
    "source",
    "sha256",
    "rule_count",
    "zone_count",
    "success",
    "message",
)
