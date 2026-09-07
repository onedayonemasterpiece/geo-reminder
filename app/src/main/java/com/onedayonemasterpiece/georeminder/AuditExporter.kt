package com.onedayonemasterpiece.georeminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.Writer
import java.time.Instant

object AuditExporter {
    const val ADB_EXPORT_FILE = "adb-journal.jsonl"

    fun write(context: Context, writer: Writer) {
        val appContext = context.applicationContext
        val audit = AuditRepository(appContext)
        val ruleStore = RuleStore(appContext)
        NotificationHelper.reconcileWithSystem(appContext, recordEvent = false)

        writeLine(
            writer,
            JSONObject()
                .put("record_type", "export_metadata")
                .put("schema_version", 1)
                .put("generated_at", Instant.now().toString())
                .put("package", BuildConfig.APPLICATION_ID)
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("diagnostics", Diagnostics.summary(appContext)),
        )

        val rawRules = ruleStore.loadRaw()
        writeLine(
            writer,
            JSONObject()
                .put("record_type", "rules_snapshot")
                .put("sha256", ruleStore.currentSha256() ?: JSONObject.NULL)
                .put("rules", rawRules?.let(::JSONObject) ?: JSONObject.NULL),
        )

        val registration = audit.registrationSnapshot()
        writeLine(
            writer,
            JSONObject()
                .put("record_type", "registration_snapshot")
                .put("attempted_at_ms", registration.attemptedAtEpochMs ?: JSONObject.NULL)
                .put("completed_at_ms", registration.completedAtEpochMs ?: JSONObject.NULL)
                .put("succeeded_at_ms", registration.succeededAtEpochMs ?: JSONObject.NULL)
                .put("reason", registration.reason ?: JSONObject.NULL)
                .put("rule_count", registration.ruleCount)
                .put("zone_count", registration.zoneCount)
                .put("success", registration.success ?: JSONObject.NULL)
                .put("message", registration.message ?: JSONObject.NULL)
                .put("rules_sha256", registration.rulesSha256 ?: JSONObject.NULL)
                .put("request_ids", JSONArray(registration.requestIds)),
        )

        audit.forEachImportAscending { item ->
            writeLine(
                writer,
                JSONObject()
                    .put("record_type", "rule_import")
                    .put("id", item.id)
                    .put("imported_at_ms", item.importedAtEpochMs)
                    .put("source", item.source)
                    .put("sha256", item.sha256 ?: JSONObject.NULL)
                    .put("rule_count", item.ruleCount)
                    .put("zone_count", item.zoneCount)
                    .put("success", item.success)
                    .put("message", item.message),
            )
        }

        audit.forEachNotificationAscending { item ->
            writeLine(
                writer,
                JSONObject()
                    .put("record_type", "notification_delivery")
                    .put("notification_id", item.notificationId)
                    .put("rule_id", item.ruleId)
                    .put("zone_id", item.zoneId ?: JSONObject.NULL)
                    .put("zone_label", item.zoneLabel ?: JSONObject.NULL)
                    .put("created_at_ms", item.createdAtEpochMs)
                    .put("posted_at_ms", item.postedAtEpochMs ?: JSONObject.NULL)
                    .put("verified_active_at_ms", item.verifiedActiveAtEpochMs ?: JSONObject.NULL)
                    .put("last_active_check_at_ms", item.lastActiveCheckAtEpochMs ?: JSONObject.NULL)
                    .put("inactive_observed_at_ms", item.inactiveObservedAtEpochMs ?: JSONObject.NULL)
                    .put("tapped_at_ms", item.tappedAtEpochMs ?: JSONObject.NULL)
                    .put("dismissed_at_ms", item.dismissedAtEpochMs ?: JSONObject.NULL)
                    .put("test", item.isTest)
                    .put("channel_id", item.channelId)
                    .put("state", item.state)
                    .put("failure_reason", item.failureReason ?: JSONObject.NULL),
            )
        }

        audit.forEachEventAscending { event ->
            writeLine(
                writer,
                JSONObject()
                    .put("record_type", "audit_event")
                    .put("id", event.id)
                    .put("occurred_at_ms", event.occurredAtEpochMs)
                    .put("event_type", event.eventType)
                    .put("severity", event.severity)
                    .put("status", event.status)
                    .put("rule_id", event.ruleId ?: JSONObject.NULL)
                    .put("zone_id", event.zoneId ?: JSONObject.NULL)
                    .put("transition", event.transition ?: JSONObject.NULL)
                    .put("notification_id", event.notificationId ?: JSONObject.NULL)
                    .put("message", event.message)
                    .put(
                        "details",
                        event.detailsJson?.let { runCatching { JSONObject(it) }.getOrNull() }
                            ?: event.detailsJson
                            ?: JSONObject.NULL,
                    ),
            )
        }
        writer.flush()
    }

    private fun writeLine(writer: Writer, value: JSONObject) {
        writer.append(value.toString()).append('\n')
    }
}
