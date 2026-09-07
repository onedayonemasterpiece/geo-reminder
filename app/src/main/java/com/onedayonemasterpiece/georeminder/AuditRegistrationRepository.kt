package com.onedayonemasterpiece.georeminder

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray

fun AuditRepository.recordRegistrationAttempt(
    reason: String,
    ruleCount: Int,
    zoneCount: Int,
    requestIds: List<String>,
    rulesSha256: String?,
    attemptedAtEpochMs: Long = System.currentTimeMillis(),
) {
    val values = ContentValues().apply {
        put("singleton_id", 1)
        put("attempted_at", attemptedAtEpochMs)
        putNull("completed_at")
        putNull("succeeded_at")
        put("reason", reason)
        put("rule_count", ruleCount)
        put("zone_count", zoneCount)
        putNull("success")
        put("message", "Registration started")
        put("rules_sha256", rulesSha256)
        put("request_ids_json", JSONArray(requestIds).toString())
    }
    database.writableDatabase.insertWithOnConflict(
        "registration_state",
        null,
        values,
        SQLiteDatabase.CONFLICT_REPLACE,
    )
}

fun AuditRepository.recordRegistrationResult(
    success: Boolean,
    reason: String,
    ruleCount: Int,
    zoneCount: Int,
    requestIds: List<String>,
    rulesSha256: String?,
    message: String,
    atEpochMs: Long = System.currentTimeMillis(),
) {
    val currentAttempt = registrationSnapshot().attemptedAtEpochMs ?: atEpochMs
    val values = ContentValues().apply {
        put("singleton_id", 1)
        put("attempted_at", currentAttempt)
        put("completed_at", atEpochMs)
        if (success) put("succeeded_at", atEpochMs) else putNull("succeeded_at")
        put("reason", reason)
        put("rule_count", ruleCount)
        put("zone_count", zoneCount)
        put("success", if (success) 1 else 0)
        put("message", message)
        put("rules_sha256", rulesSha256)
        put("request_ids_json", JSONArray(requestIds).toString())
    }
    database.writableDatabase.insertWithOnConflict(
        "registration_state",
        null,
        values,
        SQLiteDatabase.CONFLICT_REPLACE,
    )
}

fun AuditRepository.registrationSnapshot(): RegistrationSnapshot {
    return database.readableDatabase.query(
        "registration_state",
        arrayOf(
            "attempted_at",
            "completed_at",
            "succeeded_at",
            "reason",
            "rule_count",
            "zone_count",
            "success",
            "message",
            "rules_sha256",
            "request_ids_json",
        ),
        "singleton_id = 1",
        null,
        null,
        null,
        null,
        "1",
    ).use { cursor ->
        if (!cursor.moveToFirst()) {
            RegistrationSnapshot(null, null, null, null, 0, 0, null, null, null, emptyList())
        } else {
            RegistrationSnapshot(
                attemptedAtEpochMs = cursor.nullableLong(0),
                completedAtEpochMs = cursor.nullableLong(1),
                succeededAtEpochMs = cursor.nullableLong(2),
                reason = cursor.nullableString(3),
                ruleCount = cursor.getInt(4),
                zoneCount = cursor.getInt(5),
                success = cursor.nullableInt(6)?.let { it == 1 },
                message = cursor.nullableString(7),
                rulesSha256 = cursor.nullableString(8),
                requestIds = parseStringArray(cursor.nullableString(9)),
            )
        }
    }
}

fun AuditRepository.recordImport(
    source: String,
    sha256: String?,
    ruleCount: Int,
    zoneCount: Int,
    success: Boolean,
    message: String,
    atEpochMs: Long = System.currentTimeMillis(),
) {
    val values = ContentValues().apply {
        put("imported_at", atEpochMs)
        put("source", source)
        put("sha256", sha256)
        put("rule_count", ruleCount)
        put("zone_count", zoneCount)
        put("success", if (success) 1 else 0)
        put("message", message)
    }
    database.writableDatabase.insertOrThrow("rule_imports", null, values)
}

fun AuditRepository.listImports(limit: Int = 100): List<RuleImportRecord> {
    val safeLimit = limit.coerceIn(1, 10_000)
    return database.readableDatabase.query(
        "rule_imports",
        IMPORT_COLUMNS,
        null,
        null,
        null,
        null,
        "imported_at DESC, id DESC",
        safeLimit.toString(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.toRuleImportRecord())
        }
    }
}

fun AuditRepository.forEachImportAscending(block: (RuleImportRecord) -> Unit) {
    database.readableDatabase.query(
        "rule_imports",
        IMPORT_COLUMNS,
        null,
        null,
        null,
        null,
        "imported_at ASC, id ASC",
    ).use { cursor ->
        while (cursor.moveToNext()) block(cursor.toRuleImportRecord())
    }
}

