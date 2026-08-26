package com.onedayonemasterpiece.georeminder

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import java.util.Locale

internal fun MainActivity.buildRemindersScreen(): View {
    val column = screenColumn()
    column.addView(sectionTitle("Заведённые напоминания"))
    column.addView(bodyText(
        "Все правила читаются из локального rules.json. Здесь ничего не редактируется: " +
            "OpenCode формирует проверенный конечный список координат и импортирует его по ADB.",
    ))

    column.addView(actionButton("Запросить точную геолокацию и уведомления") {
        requestBasePermissions()
    })
    column.addView(actionButton("Открыть настройки приложения для доступа «Всегда»") {
        openAppSettings()
    })
    column.addView(actionButton("Перерегистрировать все геозоны") {
        reregister("manual-ui")
    })
    column.addView(actionButton("Экспортировать журнал JSONL") { startExport() })

    val rules = runCatching { RuleStore(this).load() }
    val loaded = rules.getOrNull()
    if (loaded == null) {
        column.addView(errorCard("rules.json не читается: ${rules.exceptionOrNull()?.message}"))
    } else if (loaded.rules.isEmpty()) {
        column.addView(infoCard(
            "Правил пока нет. После установки debug APK выполните scripts/import-rules.sh config/rules.local.json.",
        ))
    } else {
        val audit = AuditRepository(this)
        column.addView(bodyText("Правил: ${loaded.rules.size}; геозон: ${loaded.zoneCount}"))
        loaded.rules.forEach { rule ->
            column.addView(ruleCard(rule, audit.ruleSummary(rule.id)))
        }
    }
    return wrapScroll(column)
}

internal fun MainActivity.ruleCard(rule: ReminderRule, runtime: RuleRuntimeSummary): View {
    val card = cardColumn()
    card.addView(TextView(this).apply {
        text = (if (rule.enabled) "● " else "○ ") + rule.title
        textSize = 18f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (rule.enabled) Color.rgb(33, 74, 60) else Color.GRAY)
    })
    card.addView(bodyText(rule.message))
    card.addView(monoText(buildString {
        appendLine("id=${rule.id}")
        appendLine("transition=${rule.transition}")
        appendLine("repeat=${rule.repeatMode}")
        appendLine("cooldown_minutes=${rule.cooldownMinutes}")
        appendLine("responsiveness_ms=${rule.responsivenessMs}")
        if (rule.transition == TransitionType.DWELL) appendLine("dwell_ms=${rule.dwellMs}")
        appendLine("valid_from=${rule.validFrom ?: "NONE"}")
        appendLine("valid_until=${rule.validUntil ?: "NONE"}")
        appendLine("completed=${runtime.completed}")
        appendLine("last_triggered=${Diagnostics.format(runtime.lastTriggeredAtEpochMs)}")
        appendLine("notification_count=${runtime.notificationCount}")
        appendLine("last_notification_state=${runtime.lastNotificationState ?: "NONE"}")
        append("last_notification_at=${Diagnostics.format(runtime.lastNotificationAtEpochMs)}")
    }))

    card.addView(TextView(this).apply {
        text = "Геозоны (${rule.zones.size})"
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(8), 0, dp(4))
    })
    rule.zones.forEach { zone ->
        card.addView(bodyText(
            "• ${zone.label}\n  ${zone.id}: " +
                String.format(
                    Locale.US,
                    "%.6f, %.6f; radius=%.0f m",
                    zone.latitude,
                    zone.longitude,
                    zone.radiusMeters,
                ),
        ))
    }
    card.addView(actionButton("Показать тестовое уведомление") {
        val outcome = NotificationHelper.show(this, rule, rule.zones.first(), test = true)
        toast("${outcome.state}: ${outcome.message}")
        currentScreen = Screen.JOURNAL
        showCurrentScreen()
    })
    return card
}
