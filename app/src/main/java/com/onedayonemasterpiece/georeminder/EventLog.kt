package com.onedayonemasterpiece.georeminder

import android.content.Context
import android.util.Log
import org.json.JSONObject

object EventLog {
    private const val TAG = "GeoReminder"

    fun record(
        context: Context,
        type: String,
        message: String,
        severity: String = "INFO",
        status: String = "RECORDED",
        ruleId: String? = null,
        zoneId: String? = null,
        transition: String? = null,
        notificationId: Int? = null,
        details: JSONObject? = null,
    ): Long? {
        val sanitized = message.replace('\n', ' ').replace('\r', ' ')
        val logLine = buildString {
            append(type)
            append(" status=").append(status)
            if (ruleId != null) append(" rule=").append(ruleId)
            if (zoneId != null) append(" zone=").append(zoneId)
            if (notificationId != null) append(" notification=").append(notificationId)
            append(" ").append(sanitized)
        }
        when (severity) {
            "ERROR" -> Log.e(TAG, logLine)
            "WARNING" -> Log.w(TAG, logLine)
            "DEBUG" -> Log.d(TAG, logLine)
            else -> Log.i(TAG, logLine)
        }

        return try {
            AuditRepository(context.applicationContext).recordEvent(
                eventType = type,
                severity = severity,
                status = status,
                ruleId = ruleId,
                zoneId = zoneId,
                transition = transition,
                notificationId = notificationId,
                message = sanitized,
                detailsJson = details?.toString(),
            )
        } catch (error: Exception) {
            Log.e(TAG, "AUDIT_DB_WRITE_FAILED type=$type", error)
            null
        }
    }
}
