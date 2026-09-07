package com.onedayonemasterpiece.georeminder

import android.app.Application
import android.os.Process
import org.json.JSONObject

class GeoReminderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        EventLog.record(
            context = this,
            type = "APP_PROCESS_STARTED",
            message = "Application process started",
            details = JSONObject()
                .put("pid", Process.myPid())
                .put("package", BuildConfig.APPLICATION_ID)
                .put("version", BuildConfig.VERSION_NAME),
        )
    }
}
