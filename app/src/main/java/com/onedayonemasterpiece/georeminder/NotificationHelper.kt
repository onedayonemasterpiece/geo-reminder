package com.onedayonemasterpiece.georeminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject

object NotificationHelper {
    const val CHANNEL_ID = "geo-reminders-v2"
    const val LEGACY_CHANNEL_ID = "geo-reminders"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_RULE_ID = "rule_id"
    const val EXTRA_ZONE_ID = "zone_id"
    const val ACTION_OPEN_NOTIFICATION =
        "com.onedayonemasterpiece.georeminder.action.OPEN_NOTIFICATION"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name_v2),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_channel_description_v2)
            enableVibration(true)
            setShowBadge(true)
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(channel)
    }

    fun channelSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
        }

    @Synchronized
    fun show(
        context: Context,
        rule: ReminderRule,
        zone: CircleZone,
        test: Boolean = false,
        triggerReceivedAtEpochMs: Long? = null,
    ): NotificationOutcome {
        val appContext = context.applicationContext
        val audit = AuditRepository(appContext)
        val manager = appContext.getSystemService(NotificationManager::class.java)
        ensureChannel(appContext)

        val notificationId = audit.nextNotificationId()
        val attemptAt = System.currentTimeMillis()
        audit.createNotificationAttempt(
            notificationId = notificationId,
            rule = rule,
            zone = zone,
            isTest = test,
            channelId = CHANNEL_ID,
            createdAtEpochMs = attemptAt,
        )
        val attemptDetails = JSONObject()
            .put("channel_id", CHANNEL_ID)
            .put("test", test)
            .put("notifications_enabled", manager.areNotificationsEnabled())
        LatencyMetrics.nonNegativeDelta(triggerReceivedAtEpochMs, attemptAt)?.let {
            attemptDetails.put("geofence_receiver_to_notification_attempt_ms", it)
        }
        EventLog.record(
            appContext,
            type = "NOTIFICATION_ATTEMPT",
            message = if (test) "Test notification requested" else "Reminder notification requested",
            status = "ATTEMPTED",
            ruleId = rule.id,
            zoneId = zone.id,
            notificationId = notificationId,
            details = attemptDetails,
        )

        val blocker = notificationBlocker(appContext, manager)
        if (blocker != null) {
            audit.markNotificationFailed(notificationId, blocker)
            EventLog.record(
                appContext,
                type = "NOTIFICATION_BLOCKED",
                message = blocker,
                severity = "ERROR",
                status = "FAILED",
                ruleId = rule.id,
                zoneId = zone.id,
                notificationId = notificationId,
            )
            return NotificationOutcome(false, notificationId, "FAILED", blocker)
        }

        val contentIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            Intent(appContext, MainActivity::class.java)
                .setAction(ACTION_OPEN_NOTIFICATION)
                .setData(Uri.parse("georeminder://notification/$notificationId/open"))
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_RULE_ID, rule.id)
                .putExtra(EXTRA_ZONE_ID, zone.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deleteIntent = PendingIntent.getBroadcast(
            appContext,
            notificationId,
            Intent(appContext, NotificationInteractionReceiver::class.java)
                .setAction(NotificationInteractionReceiver.ACTION_DISMISSED)
                .setData(Uri.parse("georeminder://notification/$notificationId/dismiss"))
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_RULE_ID, rule.id)
                .putExtra(EXTRA_ZONE_ID, zone.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (test) "Тест: ${rule.title}" else rule.title
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(rule.message)
            .setStyle(Notification.BigTextStyle().bigText(rule.message))
            .setSubText(zone.label)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setWhen(attemptAt)
            .setShowWhen(true)
            .setLocalOnly(true)
            .build()

        return try {
            manager.notify(notificationId, notification)
            val activeConfirmed = runCatching {
                manager.activeNotifications.any { it.id == notificationId }
            }.getOrDefault(false)
            val postedAt = System.currentTimeMillis()
            audit.markNotificationPosted(notificationId, postedAt, activeConfirmed)

            val state = if (activeConfirmed) "ACTIVE_CONFIRMED" else "POSTED_UNCONFIRMED"
            val postedDetails = JSONObject()
                .put("test", test)
                .put("channel_id", CHANNEL_ID)
                .put("channel_importance", manager.getNotificationChannel(CHANNEL_ID)?.importance)
            LatencyMetrics.nonNegativeDelta(attemptAt, postedAt)?.let {
                postedDetails.put("notification_attempt_to_posted_ms", it)
            }
            LatencyMetrics.nonNegativeDelta(triggerReceivedAtEpochMs, postedAt)?.let {
                postedDetails.put("geofence_receiver_to_notification_posted_ms", it)
            }
            EventLog.record(
                appContext,
                type = if (activeConfirmed) {
                    "NOTIFICATION_ACTIVE_CONFIRMED"
                } else {
                    "NOTIFICATION_POSTED_UNCONFIRMED"
                },
                message = if (activeConfirmed) {
                    "notify() returned and the notification is present in activeNotifications"
                } else {
                    "notify() returned, but immediate activeNotifications verification did not confirm it"
                },
                severity = if (activeConfirmed) "INFO" else "WARNING",
                status = state,
                ruleId = rule.id,
                zoneId = zone.id,
                notificationId = notificationId,
                details = postedDetails,
            )
            NotificationOutcome(true, notificationId, state, "Notification posted")
        } catch (error: Exception) {
            val reason = "${error.javaClass.simpleName}: ${error.message ?: "unknown error"}"
            audit.markNotificationFailed(notificationId, reason)
            EventLog.record(
                appContext,
                type = "NOTIFICATION_POST_FAILED",
                message = reason,
                severity = "ERROR",
                status = "FAILED",
                ruleId = rule.id,
                zoneId = zone.id,
                notificationId = notificationId,
            )
            NotificationOutcome(false, notificationId, "FAILED", reason)
        }
    }

    fun reconcileWithSystem(
        context: Context,
        recordEvent: Boolean = true,
    ): NotificationReconciliation {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val activeIds = runCatching {
            manager.activeNotifications.mapTo(mutableSetOf()) { it.id }
        }.getOrDefault(emptySet())
        val result = AuditRepository(appContext).reconcileActiveNotifications(activeIds)
        if (recordEvent && (result.activeConfirmed > 0 || result.noLongerActive > 0)) {
            EventLog.record(
                appContext,
                type = "NOTIFICATION_SYSTEM_RECONCILED",
                message = "Compared journal with NotificationManager.activeNotifications",
                severity = if (result.noLongerActive > 0) "WARNING" else "INFO",
                status = "RECONCILED",
                details = JSONObject()
                    .put("checked", result.checked)
                    .put("active_confirmed", result.activeConfirmed)
                    .put("no_longer_active_unknown", result.noLongerActive)
                    .put("active_ids", activeIds.sorted()),
            )
        }
        return result
    }

    private fun notificationBlocker(
        context: Context,
        manager: NotificationManager,
    ): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "POST_NOTIFICATIONS permission is missing"
        }
        if (!manager.areNotificationsEnabled()) return "App notifications are disabled"
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        if (channel == null) return "Notification channel is missing"
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return "Geo Reminder notification channel is blocked"
        }
        return null
    }
}
