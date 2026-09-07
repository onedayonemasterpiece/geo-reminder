package com.onedayonemasterpiece.georeminder

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import java.io.OutputStreamWriter

class AdbCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return

        when (intent.action) {
            ACTION_IMPORT_RULES -> importRules(context)
            ACTION_TEST_NOTIFICATION -> testNotification(context, intent)
            ACTION_DIAGNOSE -> diagnose(context)
            ACTION_REREGISTER -> reregister(context)
            ACTION_EXPORT_JOURNAL -> exportJournal(context)
            ACTION_RESET_RULE_STATE -> resetRuleState(context, intent)
            ACTION_MARK_OBSERVATION -> markObservation(context, intent)
        }
    }

    private fun importRules(context: Context) {
        val pending = goAsync()
        AppExecutors.io.execute {
            val imported = RuleStore(context).importStaged(source = "adb")
            EventLog.record(
                context,
                type = if (imported.success) "ADB_IMPORT_SUCCEEDED" else "ADB_IMPORT_FAILED",
                message = imported.message,
                severity = if (imported.success) "INFO" else "ERROR",
                status = if (imported.success) "SUCCEEDED" else "FAILED",
            )
            if (!imported.success) {
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData = imported.message
                pending.finish()
                return@execute
            }

            GeofenceRegistrar.registerAll(context, "adb-import") { registration ->
                pending.resultCode = if (registration.success) {
                    Activity.RESULT_OK
                } else {
                    Activity.RESULT_CANCELED
                }
                pending.resultData = "${imported.message}; ${registration.message}"
                pending.finish()
            }
        }
    }

    private fun testNotification(context: Context, intent: Intent) {
        val requestedRuleId = intent.getStringExtra(EXTRA_RULE_ID)
        val rule = runCatching { RuleStore(context).load() }
            .getOrNull()
            ?.rules
            ?.firstOrNull { requestedRuleId == null || it.id == requestedRuleId }
        if (rule == null || rule.zones.isEmpty()) {
            resultCode = Activity.RESULT_CANCELED
            resultData = "No matching rule with at least one zone"
            return
        }

        val outcome = NotificationHelper.show(context, rule, rule.zones.first(), test = true)
        resultCode = if (outcome.posted) Activity.RESULT_OK else Activity.RESULT_CANCELED
        resultData = "notification_id=${outcome.notificationId} state=${outcome.state} ${outcome.message}"
    }

    private fun diagnose(context: Context) {
        EventLog.record(
            context,
            type = "ADB_DIAGNOSTICS_REQUESTED",
            message = "ADB requested diagnostics summary",
            status = "REQUESTED",
        )
        resultCode = Activity.RESULT_OK
        resultData = Diagnostics.summary(context)
    }

    private fun reregister(context: Context) {
        val pending = goAsync()
        GeofenceRegistrar.registerAll(context, "adb-reregister") { registration ->
            pending.resultCode = if (registration.success) {
                Activity.RESULT_OK
            } else {
                Activity.RESULT_CANCELED
            }
            pending.resultData = registration.message
            pending.finish()
        }
    }

    private fun exportJournal(context: Context) {
        val pending = goAsync()
        AppExecutors.io.execute {
            try {
                val destination = File(context.filesDir, AuditExporter.ADB_EXPORT_FILE)
                destination.outputStream().use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                        AuditExporter.write(context, writer)
                    }
                }
                EventLog.record(
                    context,
                    type = "ADB_JOURNAL_EXPORT_CREATED",
                    message = "Exported ${destination.length()} bytes to ${destination.name}",
                    status = "SUCCEEDED",
                )
                pending.resultCode = Activity.RESULT_OK
                pending.resultData = "${destination.name} ${destination.length()} bytes"
            } catch (error: Exception) {
                val message = "${error.javaClass.simpleName}: ${error.message}"
                EventLog.record(
                    context,
                    type = "ADB_JOURNAL_EXPORT_FAILED",
                    message = message,
                    severity = "ERROR",
                    status = "FAILED",
                )
                pending.resultCode = Activity.RESULT_CANCELED
                pending.resultData = message
            } finally {
                pending.finish()
            }
        }
    }


    private fun markObservation(context: Context, intent: Intent) {
        val note = intent.getStringExtra(EXTRA_NOTE)?.trim().orEmpty()
            .ifEmpty { "ADB observation marker" }
        EventLog.record(
            context,
            type = "USER_OBSERVATION_MARK",
            message = note,
            status = "MARKED",
        )
        resultCode = Activity.RESULT_OK
        resultData = note
    }

    private fun resetRuleState(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(EXTRA_RULE_ID)
        val deleted = AuditRepository(context).resetRuleState(ruleId)
        EventLog.record(
            context,
            type = "ADB_RULE_STATE_RESET",
            message = if (ruleId == null) {
                "Reset all rule state rows: $deleted"
            } else {
                "Reset state for rule=$ruleId rows=$deleted"
            },
            status = "SUCCEEDED",
            ruleId = ruleId,
        )
        resultCode = Activity.RESULT_OK
        resultData = "deleted=$deleted"
    }

    companion object {
        const val ACTION_IMPORT_RULES =
            "com.onedayonemasterpiece.georeminder.debug.action.IMPORT_RULES"
        const val ACTION_TEST_NOTIFICATION =
            "com.onedayonemasterpiece.georeminder.debug.action.TEST_NOTIFICATION"
        const val ACTION_DIAGNOSE =
            "com.onedayonemasterpiece.georeminder.debug.action.DIAGNOSE"
        const val ACTION_REREGISTER =
            "com.onedayonemasterpiece.georeminder.debug.action.REREGISTER"
        const val ACTION_EXPORT_JOURNAL =
            "com.onedayonemasterpiece.georeminder.debug.action.EXPORT_JOURNAL"
        const val ACTION_RESET_RULE_STATE =
            "com.onedayonemasterpiece.georeminder.debug.action.RESET_RULE_STATE"
        const val ACTION_MARK_OBSERVATION =
            "com.onedayonemasterpiece.georeminder.debug.action.MARK_OBSERVATION"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_NOTE = "note"
    }
}
