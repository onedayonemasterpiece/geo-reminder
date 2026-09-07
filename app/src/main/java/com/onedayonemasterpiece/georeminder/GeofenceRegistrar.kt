package com.onedayonemasterpiece.georeminder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

object GeofenceRegistrar {
    private const val PENDING_INTENT_REQUEST_CODE = 4101

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction(GeofenceBroadcastReceiver.ACTION_TRANSITION)
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            PENDING_INTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    @SuppressLint("MissingPermission")
    fun registerAll(
        context: Context,
        reason: String,
        callback: (RegistrationResult) -> Unit,
    ) {
        val appContext = context.applicationContext
        val audit = AuditRepository(appContext)
        val ruleStore = RuleStore(appContext)
        val ruleSet = try {
            ruleStore.load()
        } catch (error: Exception) {
            val result = RegistrationResult(
                false,
                "Rules cannot be loaded: ${error.message}",
                0,
                0,
            )
            audit.recordRegistrationAttempt(reason, 0, 0, emptyList(), null)
            finishRegistration(
                appContext,
                audit,
                reason,
                result,
                emptyList(),
                null,
                callback,
            )
            return
        }

        val now = Instant.now()
        val activeRules = ruleSet.rules.filter { rule ->
            rule.enabled &&
                (rule.validFrom == null || !now.isBefore(rule.validFrom)) &&
                (rule.validUntil == null || now.isBefore(rule.validUntil)) &&
                !(rule.repeatMode == RepeatMode.ONCE && audit.ruleSummary(rule.id).completed)
        }
        val geofences = activeRules.flatMap { rule ->
            rule.zones.map { zone -> buildGeofence(rule, zone, now) }
        }
        val requestIds = activeRules.flatMap { rule ->
            rule.zones.map { zone -> GeofenceIds.compose(rule.id, zone.id) }
        }
        val rulesSha256 = runCatching { ruleStore.currentSha256() }.getOrNull()

        audit.recordRegistrationAttempt(
            reason = reason,
            ruleCount = activeRules.size,
            zoneCount = geofences.size,
            requestIds = requestIds,
            rulesSha256 = rulesSha256,
        )
        EventLog.record(
            appContext,
            type = "GEOFENCE_REGISTRATION_STARTED",
            message = "Preparing ${geofences.size} geofences",
            status = "STARTED",
            details = JSONObject()
                .put("reason", reason)
                .put("rule_count", activeRules.size)
                .put("zone_count", geofences.size)
                .put("rules_sha256", rulesSha256 ?: JSONObject.NULL)
                .put("request_ids", JSONArray(requestIds)),
        )

        val blocker = Diagnostics.registrationBlocker(appContext)
        if (blocker != null) {
            val result = RegistrationResult(false, blocker, activeRules.size, geofences.size)
            finishRegistration(
                appContext,
                audit,
                reason,
                result,
                requestIds,
                rulesSha256,
                callback,
            )
            return
        }

        val client = LocationServices.getGeofencingClient(appContext)
        val operationIntent = pendingIntent(appContext)
        client.removeGeofences(operationIntent).addOnCompleteListener { removeTask ->
            EventLog.record(
                appContext,
                type = "GEOFENCE_PREVIOUS_SET_REMOVED",
                message = if (removeTask.isSuccessful) {
                    "Previous PendingIntent geofence set removed"
                } else {
                    "Previous geofence removal returned: ${removeTask.exception?.message}"
                },
                severity = if (removeTask.isSuccessful) "INFO" else "WARNING",
                status = if (removeTask.isSuccessful) "REMOVED" else "REMOVE_UNCONFIRMED",
            )

            if (geofences.isEmpty()) {
                val result = RegistrationResult(
                    true,
                    "No active geofences; previous registrations were cleared",
                    activeRules.size,
                    0,
                )
                finishRegistration(
                    appContext,
                    audit,
                    reason,
                    result,
                    requestIds,
                    rulesSha256,
                    callback,
                )
                return@addOnCompleteListener
            }

            val request = GeofencingRequest.Builder()
                // The Google Play services default is ENTER + DWELL. The MVP needs a
                // real boundary crossing, not an immediate alert during provisioning.
                .setInitialTrigger(0)
                .addGeofences(geofences)
                .build()
            try {
                client.addGeofences(request, operationIntent)
                    .addOnSuccessListener {
                        val result = RegistrationResult(
                            true,
                            "Registered ${geofences.size} active geofences",
                            activeRules.size,
                            geofences.size,
                        )
                        finishRegistration(
                            appContext,
                            audit,
                            reason,
                            result,
                            requestIds,
                            rulesSha256,
                            callback,
                        )
                    }
                    .addOnFailureListener { error ->
                        val result = RegistrationResult(
                            false,
                            "Geofence registration failed: " +
                                "${error.javaClass.simpleName}: ${error.message}",
                            activeRules.size,
                            geofences.size,
                        )
                        finishRegistration(
                            appContext,
                            audit,
                            reason,
                            result,
                            requestIds,
                            rulesSha256,
                            callback,
                        )
                    }
            } catch (error: SecurityException) {
                val result = RegistrationResult(
                    false,
                    "Location permission changed during registration",
                    activeRules.size,
                    geofences.size,
                )
                finishRegistration(
                    appContext,
                    audit,
                    reason,
                    result,
                    requestIds,
                    rulesSha256,
                    callback,
                )
            }
        }
    }

    fun removeRequestIds(
        context: Context,
        requestIds: List<String>,
        callback: (Boolean, String) -> Unit,
    ) {
        if (requestIds.isEmpty()) {
            callback(true, "No geofences to remove")
            return
        }
        LocationServices.getGeofencingClient(context.applicationContext)
            .removeGeofences(requestIds.distinct())
            .addOnCompleteListener { task ->
                callback(
                    task.isSuccessful,
                    if (task.isSuccessful) {
                        "Removed ${requestIds.distinct().size} geofences"
                    } else {
                        "Removal failed: ${task.exception?.message}"
                    },
                )
            }
    }

    private fun buildGeofence(
        rule: ReminderRule,
        zone: CircleZone,
        now: Instant,
    ): Geofence {
        val expiration = rule.validUntil?.let { until ->
            Duration.between(now, until).toMillis().coerceAtLeast(1L)
        } ?: Geofence.NEVER_EXPIRE

        return Geofence.Builder()
            .setRequestId(GeofenceIds.compose(rule.id, zone.id))
            .setCircularRegion(zone.latitude, zone.longitude, zone.radiusMeters)
            .setExpirationDuration(expiration)
            .setTransitionTypes(rule.transition.toGeofenceTransition())
            .setNotificationResponsiveness(rule.responsivenessMs)
            .apply {
                if (rule.transition == TransitionType.DWELL) {
                    setLoiteringDelay(rule.dwellMs)
                }
            }
            .build()
    }

    private fun finishRegistration(
        context: Context,
        audit: AuditRepository,
        reason: String,
        result: RegistrationResult,
        requestIds: List<String>,
        rulesSha256: String?,
        callback: (RegistrationResult) -> Unit,
    ) {
        audit.recordRegistrationResult(
            success = result.success,
            reason = reason,
            ruleCount = result.ruleCount,
            zoneCount = result.zoneCount,
            requestIds = requestIds,
            rulesSha256 = rulesSha256,
            message = result.message,
        )
        EventLog.record(
            context,
            type = if (result.success) {
                "GEOFENCE_REGISTRATION_SUCCEEDED"
            } else {
                "GEOFENCE_REGISTRATION_FAILED"
            },
            message = result.message,
            severity = if (result.success) "INFO" else "ERROR",
            status = if (result.success) "SUCCEEDED" else "FAILED",
            details = JSONObject()
                .put("reason", reason)
                .put("rule_count", result.ruleCount)
                .put("zone_count", result.zoneCount)
                .put("rules_sha256", rulesSha256 ?: JSONObject.NULL)
                .put("request_ids", JSONArray(requestIds)),
        )
        callback(result)
    }

    private fun TransitionType.toGeofenceTransition(): Int = when (this) {
        TransitionType.ENTER -> Geofence.GEOFENCE_TRANSITION_ENTER
        TransitionType.EXIT -> Geofence.GEOFENCE_TRANSITION_EXIT
        TransitionType.DWELL -> Geofence.GEOFENCE_TRANSITION_DWELL
    }
}
