package com.onedayonemasterpiece.georeminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationInteractionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISSED) return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId < 0) return
        val ruleId = intent.getStringExtra(NotificationHelper.EXTRA_RULE_ID)
        val zoneId = intent.getStringExtra(NotificationHelper.EXTRA_ZONE_ID)
        AuditRepository(context).markNotificationDismissed(notificationId)
        EventLog.record(
            context,
            type = "NOTIFICATION_DISMISSED",
            message = "Notification was dismissed by the user or system UI",
            status = "DISMISSED",
            ruleId = ruleId,
            zoneId = zoneId,
            notificationId = notificationId,
        )
    }

    companion object {
        const val ACTION_DISMISSED =
            "com.onedayonemasterpiece.georeminder.action.NOTIFICATION_DISMISSED"
    }
}
