package com.onedayonemasterpiece.georeminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONObject

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
            return
        }
        val action = intent.action ?: "unknown"
        EventLog.record(
            context,
            type = "SYSTEM_RESTORE_EVENT_RECEIVED",
            message = "Received $action; geofences will be registered again",
            status = "RECEIVED",
            details = JSONObject().put("action", action),
        )
        val pending = goAsync()
        GeofenceRegistrar.registerAll(
            context = context.applicationContext,
            reason = action,
        ) {
            pending.finish()
        }
    }
}
