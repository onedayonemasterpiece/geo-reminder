package com.onedayonemasterpiece.georeminder

import android.content.BroadcastReceiver
import android.content.BroadcastReceiver.PendingResult
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRANSITION) return
        val receiverReceivedAtEpochMs = System.currentTimeMillis()
        val pending = goAsync()
        val appContext = context.applicationContext
        val event = GeofencingEvent.fromIntent(intent)

        if (event == null) {
            EventLog.record(
                appContext,
                type = "GEOFENCE_EVENT_INVALID",
                message = "Intent did not contain a GeofencingEvent",
                severity = "ERROR",
                status = "FAILED",
            )
            pending.finish()
            return
        }
        if (event.hasError()) {
            EventLog.record(
                appContext,
                type = "GEOFENCE_EVENT_ERROR",
                message = "Google Play services geofence errorCode=${event.errorCode}",
                severity = "ERROR",
                status = "FAILED",
                details = JSONObject().put("error_code", event.errorCode),
            )
            pending.finish()
            return
        }

        AppExecutors.io.execute {
            try {
                processEvent(appContext, event, pending, receiverReceivedAtEpochMs)
            } catch (error: Exception) {
                EventLog.record(
                    appContext,
                    type = "GEOFENCE_PROCESSING_CRASH",
                    message = "${error.javaClass.simpleName}: ${error.message}",
                    severity = "ERROR",
                    status = "FAILED",
                )
                pending.finish()
            }
        }
    }

    private fun processEvent(
        context: Context,
        event: GeofencingEvent,
        pending: PendingResult,
        receiverReceivedAtEpochMs: Long,
    ) {
        val transition = event.geofenceTransition
        val transitionName = transitionName(transition)
        if (transitionName == null) {
            EventLog.record(
                context,
                type = "GEOFENCE_TRANSITION_UNSUPPORTED",
                message = "Unsupported transition=$transition",
                severity = "ERROR",
                status = "IGNORED",
            )
            pending.finish()
            return
        }

        val triggering = event.triggeringGeofences.orEmpty()
        val location = event.triggeringLocation
        val eventDetails = JSONObject()
            .put("request_ids", JSONArray(triggering.map { it.requestId }))
            .put("transition_code", transition)
            .put("transition", transitionName)
            .put("trigger_count", triggering.size)
            .put("receiver_received_at_ms", receiverReceivedAtEpochMs)
        if (location != null) {
            eventDetails.put(
                "triggering_location",
                JSONObject()
                    .put("latitude", location.latitude)
                    .put("longitude", location.longitude)
                    .put("accuracy_m", location.accuracy)
                    .put("provider", location.provider)
                    .put("time_ms", location.time),
            )
            LatencyMetrics.nonNegativeDelta(location.time, receiverReceivedAtEpochMs)?.let {
                eventDetails.put("triggering_location_to_receiver_ms", it)
            }
        }

        EventLog.record(
            context,
            type = "GEOFENCE_EVENT_RECEIVED",
            message = "Android delivered $transitionName for ${triggering.size} geofence(s)",
            status = "RECEIVED",
            transition = transitionName,
            details = eventDetails,
        )

        if (triggering.isEmpty()) {
            EventLog.record(
                context,
                type = "GEOFENCE_EVENT_EMPTY",
                message = "Transition event contains no triggering geofences",
                severity = "ERROR",
                status = "FAILED",
                transition = transitionName,
            )
            pending.finish()
            return
        }

        val ruleSet = try {
            RuleStore(context).load()
        } catch (error: Exception) {
            EventLog.record(
                context,
                type = "RULE_SET_LOAD_FAILED",
                message = "${error.javaClass.simpleName}: ${error.message}",
                severity = "ERROR",
                status = "FAILED",
                transition = transitionName,
            )
            pending.finish()
            return
        }
        val audit = AuditRepository(context)
        val now = Instant.now()
        val processedRules = mutableSetOf<String>()
        val onceRequestIdsToRemove = mutableListOf<String>()

        triggering.forEach { geofence ->
            val resolved = ruleSet.resolve(geofence.requestId)
            if (resolved == null) {
                EventLog.record(
                    context,
                    type = "GEOFENCE_RULE_NOT_FOUND",
                    message = "No current rule matches requestId=${geofence.requestId}",
                    severity = "ERROR",
                    status = "FAILED",
                    transition = transitionName,
                    details = JSONObject().put("request_id", geofence.requestId),
                )
                return@forEach
            }

            val rule = resolved.rule
            val zone = resolved.zone
            EventLog.record(
                context,
                type = "GEOFENCE_ZONE_MATCHED",
                message = "Resolved geofence event to ${rule.id}/${zone.id}",
                status = "MATCHED",
                ruleId = rule.id,
                zoneId = zone.id,
                transition = transitionName,
                details = JSONObject()
                    .put("request_id", geofence.requestId)
                    .put("radius_m", zone.radiusMeters)
                    .put("responsiveness_ms", rule.responsivenessMs),
            )

            if (rule.transition.name != transitionName) {
                EventLog.record(
                    context,
                    type = "GEOFENCE_TRANSITION_MISMATCH",
                    message = "Rule expects ${rule.transition.name}, received $transitionName",
                    severity = "WARNING",
                    status = "SKIPPED",
                    ruleId = rule.id,
                    zoneId = zone.id,
                    transition = transitionName,
                )
                return@forEach
            }
            if (!processedRules.add(rule.id)) {
                EventLog.record(
                    context,
                    type = "GEOFENCE_RULE_DEDUPLICATED",
                    message = "Another zone for the same rule was already processed in this event",
                    status = "SKIPPED",
                    ruleId = rule.id,
                    zoneId = zone.id,
                    transition = transitionName,
                )
                return@forEach
            }

            val eligibility = audit.eligibility(rule, now)
            if (!eligibility.allowed) {
                EventLog.record(
                    context,
                    type = "REMINDER_TRIGGER_SKIPPED",
                    message = eligibility.reason,
                    status = "SKIPPED",
                    ruleId = rule.id,
                    zoneId = zone.id,
                    transition = transitionName,
                )
                return@forEach
            }

            EventLog.record(
                context,
                type = "REMINDER_TRIGGER_ACCEPTED",
                message = "Rule passed enabled, time-window, completion and cooldown checks",
                status = "ACCEPTED",
                ruleId = rule.id,
                zoneId = zone.id,
                transition = transitionName,
            )

            val outcome = NotificationHelper.show(
                context = context,
                rule = rule,
                zone = zone,
                test = false,
                triggerReceivedAtEpochMs = receiverReceivedAtEpochMs,
            )
            if (!outcome.posted || outcome.notificationId == null) {
                EventLog.record(
                    context,
                    type = "REMINDER_DELIVERY_NOT_COMMITTED",
                    message = "Rule state was not advanced because notification delivery failed",
                    severity = "WARNING",
                    status = "NOT_COMMITTED",
                    ruleId = rule.id,
                    zoneId = zone.id,
                    transition = transitionName,
                    notificationId = outcome.notificationId,
                )
                return@forEach
            }

            audit.markRuleTriggered(rule, outcome.notificationId, now.toEpochMilli())
            EventLog.record(
                context,
                type = "REMINDER_STATE_COMMITTED",
                message = "Trigger timestamp and notification id were persisted",
                status = "COMMITTED",
                ruleId = rule.id,
                zoneId = zone.id,
                transition = transitionName,
                notificationId = outcome.notificationId,
            )

            if (rule.repeatMode == RepeatMode.ONCE) {
                onceRequestIdsToRemove += rule.zones.map {
                    GeofenceIds.compose(rule.id, it.id)
                }
            }
        }

        if (onceRequestIdsToRemove.isEmpty()) {
            pending.finish()
            return
        }

        GeofenceRegistrar.removeRequestIds(context, onceRequestIdsToRemove) { success, message ->
            EventLog.record(
                context,
                type = if (success) {
                    "ONCE_GEOFENCES_REMOVED"
                } else {
                    "ONCE_GEOFENCE_REMOVAL_FAILED"
                },
                message = message,
                severity = if (success) "INFO" else "ERROR",
                status = if (success) "REMOVED" else "FAILED",
                details = JSONObject().put(
                    "request_ids",
                    JSONArray(onceRequestIdsToRemove.distinct()),
                ),
            )
            pending.finish()
        }
    }

    private fun transitionName(value: Int): String? = when (value) {
        Geofence.GEOFENCE_TRANSITION_ENTER -> TransitionType.ENTER.name
        Geofence.GEOFENCE_TRANSITION_EXIT -> TransitionType.EXIT.name
        Geofence.GEOFENCE_TRANSITION_DWELL -> TransitionType.DWELL.name
        else -> null
    }

    companion object {
        const val ACTION_TRANSITION =
            "com.onedayonemasterpiece.georeminder.action.GEOFENCE_TRANSITION"
    }
}
