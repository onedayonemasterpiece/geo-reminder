package com.onedayonemasterpiece.georeminder

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    internal lateinit var content: FrameLayout
    internal var currentScreen = Screen.REMINDERS
    internal var journalFilter = JournalFilter.ALL
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        setContentView(buildRoot())
        if (savedInstanceState == null) handleLaunchIntent(intent)
        showCurrentScreen()
        EventLog.record(
            this,
            type = "APP_UI_OPENED",
            message = "MainActivity opened",
            status = "OPENED",
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
        showCurrentScreen()
    }

    override fun onResume() {
        super.onResume()
        NotificationHelper.reconcileWithSystem(this)
        if (::content.isInitialized) showCurrentScreen()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EventLog.record(
            this,
            type = "PERMISSION_RESULT",
            message = "Runtime permission request completed",
            status = "RECEIVED",
            details = JSONObject()
                .put("request_code", requestCode)
                .put("permissions", JSONArray(permissions.toList()))
                .put("grant_results", JSONArray(grantResults.toList())),
        )
        showCurrentScreen()
    }

    @Deprecated("Uses the platform Activity result API to avoid an AndroidX dependency in the MVP")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        EventLog.record(
            this,
            type = "JOURNAL_EXPORT_STARTED",
            message = "Writing diagnostics JSONL selected by the user",
            status = "STARTED",
            details = JSONObject().put("uri", uri.toString()),
        )
        AppExecutors.io.execute {
            try {
                contentResolver.openOutputStream(uri, "w")?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                        AuditExporter.write(this, writer)
                    }
                } ?: error("Content resolver returned no output stream")
                EventLog.record(
                    this,
                    type = "JOURNAL_EXPORT_SUCCEEDED",
                    message = "Diagnostics JSONL saved",
                    status = "SUCCEEDED",
                )
                runOnUiThread { toast("Журнал сохранён") }
            } catch (error: Exception) {
                EventLog.record(
                    this,
                    type = "JOURNAL_EXPORT_FAILED",
                    message = "${error.javaClass.simpleName}: ${error.message}",
                    severity = "ERROR",
                    status = "FAILED",
                )
                runOnUiThread { toast("Не удалось сохранить журнал: ${error.message}") }
            }
        }
    }

    internal fun buildRoot(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        root.addView(
            TextView(this).apply {
                text = "Geo Reminder"
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(32, 39, 43))
                setPadding(dp(18), dp(18), dp(18), dp(8))
            },
        )
        root.addView(
            TextView(this).apply {
                text = "Локальные геонапоминания и проверяемый журнал доставки"
                textSize = 13f
                setTextColor(Color.DKGRAY)
                setPadding(dp(18), 0, dp(18), dp(12))
            },
        )

        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), 0, dp(8), dp(8))
        }
        navigation.addView(navButton("Напоминания", Screen.REMINDERS))
        navigation.addView(navButton("Журнал", Screen.JOURNAL))
        navigation.addView(navButton("Диагностика", Screen.DIAGNOSTICS))
        root.addView(navigation)

        content = FrameLayout(this)
        root.addView(
            content,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        return root
    }

    internal fun navButton(label: String, screen: Screen): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener {
                currentScreen = screen
                showCurrentScreen()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
        }
    }

    internal fun showCurrentScreen() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val view = when (currentScreen) {
            Screen.REMINDERS -> buildRemindersScreen()
            Screen.JOURNAL -> buildJournalScreen()
            Screen.DIAGNOSTICS -> buildDiagnosticsScreen()
        }
        content.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    internal fun handleLaunchIntent(launchIntent: Intent?) {
        if (launchIntent?.action != NotificationHelper.ACTION_OPEN_NOTIFICATION) return
        val notificationId = launchIntent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)
        if (notificationId < 0) return
        val ruleId = launchIntent.getStringExtra(NotificationHelper.EXTRA_RULE_ID)
        val zoneId = launchIntent.getStringExtra(NotificationHelper.EXTRA_ZONE_ID)
        AuditRepository(this).markNotificationTapped(notificationId)
        EventLog.record(
            this,
            type = "NOTIFICATION_TAPPED",
            message = "Notification content intent opened the app",
            status = "TAPPED",
            ruleId = ruleId,
            zoneId = zoneId,
            notificationId = notificationId,
        )
        currentScreen = Screen.JOURNAL
    }

    internal fun requestBasePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissions.isEmpty()) {
            toast("Базовые разрешения уже выданы. Доступ «Всегда» проверяется в настройках приложения.")
            return
        }
        requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    internal fun reregister(reason: String) {
        toast("Регистрация запущена")
        GeofenceRegistrar.registerAll(this, reason) { result ->
            runOnUiThread {
                toast(result.message)
                showCurrentScreen()
            }
        }
    }

    internal fun startExport() {
        val filename = "geo-reminder-diagnostics-${EXPORT_FILE_TIME.format(Instant.now())}.jsonl"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-ndjson"
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    internal fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    internal fun actionButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        }
    }

    internal fun screenColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(28))
    }

    internal fun wrapScroll(column: LinearLayout): ScrollView = ScrollView(this).apply {
        addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    internal fun sectionTitle(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(32, 39, 43))
        setPadding(0, dp(12), 0, dp(8))
    }

    internal fun bodyText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(Color.rgb(55, 62, 66))
        setLineSpacing(0f, 1.1f)
        setPadding(0, dp(3), 0, dp(5))
    }

    internal fun monoText(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 12.5f
        typeface = Typeface.MONOSPACE
        setTextColor(Color.rgb(49, 58, 63))
        setTextIsSelectable(true)
        setPadding(0, dp(4), 0, dp(4))
    }

    internal fun cardColumn(borderColor: Int = Color.rgb(120, 130, 135)): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(249, 250, 250))
                setStroke(dp(1), borderColor)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        }
    }

    internal fun infoCard(message: String): View = cardColumn(Color.rgb(80, 120, 145)).apply {
        addView(bodyText(message))
    }

    internal fun errorCard(message: String): View = cardColumn(Color.rgb(176, 60, 60)).apply {
        addView(bodyText(message))
    }

    internal fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    internal fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
        private const val REQUEST_EXPORT = 2001
        private val EXPORT_FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault())
    }
}

internal enum class Screen {
    REMINDERS,
    JOURNAL,
    DIAGNOSTICS,
}

internal enum class JournalFilter(val label: String) {
    ALL("Все события"),
    ERRORS("Ошибки и предупреждения"),
    GEOFENCE("Геозоны"),
    NOTIFICATIONS("Уведомления"),
    CONFIGURATION("Импорт и регистрация"),
    ;

    fun accepts(event: AuditEvent): Boolean = when (this) {
        ALL -> true
        ERRORS -> event.severity in setOf("ERROR", "WARNING")
        GEOFENCE -> event.eventType.contains("GEOFENCE") ||
            event.eventType.contains("TRIGGER") ||
            event.eventType.contains("REMINDER_STATE")
        NOTIFICATIONS -> event.eventType.contains("NOTIFICATION") ||
            event.eventType.contains("DELIVERY")
        CONFIGURATION -> event.eventType.contains("IMPORT") ||
            event.eventType.contains("REGISTRATION") ||
            event.eventType.contains("RESTORE") ||
            event.eventType.contains("OBSERVATION")
    }
}
