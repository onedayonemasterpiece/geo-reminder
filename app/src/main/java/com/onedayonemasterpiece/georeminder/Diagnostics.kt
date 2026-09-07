package com.onedayonemasterpiece.georeminder

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Diagnostics {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        .withZone(ZoneId.systemDefault())

    fun registrationBlocker(context: Context): String? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "Не выдано разрешение на точную геолокацию"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "Не выдан доступ к геолокации «Всегда»"
        }
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (!locationManager.isLocationEnabled) return "Геолокация Android выключена"

        val playServicesCode = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        if (playServicesCode != ConnectionResult.SUCCESS) {
            return "Google Play services недоступны: code=$playServicesCode"
        }
        return null
    }

    fun summary(context: Context): String {
        val appContext = context.applicationContext
        val audit = AuditRepository(appContext)
        val ruleStore = RuleStore(appContext)
        val ruleSetResult = runCatching { ruleStore.load() }
        val rules = ruleSetResult.getOrNull()
        val locationManager = appContext.getSystemService(LocationManager::class.java)
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val activityManager = appContext.getSystemService(ActivityManager::class.java)
        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val registration = audit.registrationSnapshot()
        NotificationHelper.ensureChannel(appContext)
        val reconciliation = NotificationHelper.reconcileWithSystem(appContext, recordEvent = false)
        val counts = audit.counts()
        val channel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        val legacyChannel = notificationManager.getNotificationChannel(NotificationHelper.LEGACY_CHANNEL_ID)
        val activeNotificationIds = runCatching {
            notificationManager.activeNotifications.map { it.id }.sorted()
        }.getOrDefault(emptyList())
        val latestImport = audit.listImports(1).firstOrNull()
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageInfo(
                appContext.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }

        return buildString {
            appendLine("ПРИЛОЖЕНИЕ")
            appendLine("package=${BuildConfig.APPLICATION_ID}")
            appendLine("version=${packageInfo.versionName} (${packageInfo.longVersionCode})")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT}")
            appendLine()

            appendLine("РАЗРЕШЕНИЯ И ОГРАНИЧЕНИЯ")
            appendLine("location_enabled=${locationManager.isLocationEnabled}")
            appendLine("fine_location=${permission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)}")
            appendLine(
                "background_location=" +
                    permission(appContext, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            )
            appendLine(
                "post_notifications=" + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission(appContext, Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    "NOT_REQUIRED"
                },
            )
            appendLine("notifications_enabled=${notificationManager.areNotificationsEnabled()}")
            appendLine("notification_channel_id=${NotificationHelper.CHANNEL_ID}")
            appendLine("notification_channel=${channel?.importance?.let(::importanceName) ?: "MISSING"}")
            appendLine("notification_channel_sound=${channel?.sound?.toString() ?: "SILENT_OR_MISSING"}")
            appendLine("notification_channel_vibration=${channel?.shouldVibrate() ?: false}")
            appendLine("notification_channel_bypass_dnd=${channel?.canBypassDnd() ?: false}")
            appendLine("notification_channel_audio_usage=${channel?.audioAttributes?.usage ?: "NONE"}")
            appendLine(
                "legacy_notification_channel=" +
                    (legacyChannel?.importance?.let(::importanceName) ?: "ABSENT"),
            )
            appendLine("background_restricted=${activityManager.isBackgroundRestricted}")
            appendLine(
                "ignoring_battery_optimizations=" +
                    powerManager.isIgnoringBatteryOptimizations(appContext.packageName),
            )
            appendLine("registration_blocker=${registrationBlocker(appContext) ?: "NONE"}")
            appendLine()

            appendLine("ПРАВИЛА")
            if (rules == null) {
                appendLine("rules=INVALID: ${ruleSetResult.exceptionOrNull()?.message}")
            } else {
                appendLine("rules=${rules.rules.size}")
                appendLine("zones=${rules.zoneCount}")
                appendLine("completed_once_rules=${audit.completedCount(rules)}")
                appendLine("rules_sha256=${ruleStore.currentSha256() ?: "NONE"}")
                appendLine(
                    "responsiveness_ms=" + rules.rules.joinToString(",") {
                        "${it.id}:${it.responsivenessMs}"
                    },
                )
            }
            appendLine("latest_import_at=${format(latestImport?.importedAtEpochMs)}")
            appendLine("latest_import_success=${latestImport?.success ?: "UNKNOWN"}")
            appendLine("latest_import_sha256=${latestImport?.sha256 ?: "NONE"}")
            appendLine("latest_import_message=${latestImport?.message ?: "NONE"}")
            appendLine()

            appendLine("ПОСЛЕДНЯЯ РЕГИСТРАЦИЯ")
            appendLine("attempted_at=${format(registration.attemptedAtEpochMs)}")
            appendLine("completed_at=${format(registration.completedAtEpochMs)}")
            appendLine("succeeded_at=${format(registration.succeededAtEpochMs)}")
            appendLine("success=${registration.success ?: "UNKNOWN"}")
            appendLine("reason=${registration.reason ?: "NONE"}")
            appendLine("registered_rules=${registration.ruleCount}")
            appendLine("registered_zones=${registration.zoneCount}")
            appendLine("registered_rules_sha256=${registration.rulesSha256 ?: "NONE"}")
            appendLine("registered_request_ids=${registration.requestIds.joinToString(",")}")
            appendLine("message=${registration.message ?: "NONE"}")
            appendLine("actual_android_registry=NOT_QUERYABLE_BY_PUBLIC_API")
            appendLine()

            appendLine("ЖУРНАЛ И УВЕДОМЛЕНИЯ")
            appendLine("events=${counts.events}")
            appendLine("notifications=${counts.notifications}")
            appendLine("imports=${counts.imports}")
            appendLine("active_own_notification_ids=${activeNotificationIds.joinToString(",")}")
            appendLine("reconciliation_checked=${reconciliation.checked}")
            appendLine("reconciliation_active_confirmed=${reconciliation.activeConfirmed}")
            appendLine("reconciliation_no_longer_active=${reconciliation.noLongerActive}")
            appendLine("latency_fields=triggering_location_to_receiver_ms,geofence_receiver_to_notification_attempt_ms,notification_attempt_to_posted_ms,geofence_receiver_to_notification_posted_ms")
            val latest = audit.latestEvent()
            appendLine(
                "latest_event=" + if (latest == null) {
                    "NONE"
                } else {
                    "${format(latest.occurredAtEpochMs)} ${latest.eventType} ${latest.status}"
                },
            )
        }.trim()
    }

    fun format(epochMs: Long?): String {
        return epochMs?.let { timeFormatter.format(Instant.ofEpochMilli(it)) } ?: "NONE"
    }

    private fun permission(context: Context, permission: String): String {
        return if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            "GRANTED"
        } else {
            "DENIED"
        }
    }

    private fun importanceName(importance: Int): String = when (importance) {
        NotificationManager.IMPORTANCE_NONE -> "BLOCKED"
        NotificationManager.IMPORTANCE_MIN -> "MIN"
        NotificationManager.IMPORTANCE_LOW -> "LOW"
        NotificationManager.IMPORTANCE_DEFAULT -> "DEFAULT"
        NotificationManager.IMPORTANCE_HIGH -> "HIGH"
        NotificationManager.IMPORTANCE_MAX -> "MAX"
        else -> importance.toString()
    }
}
