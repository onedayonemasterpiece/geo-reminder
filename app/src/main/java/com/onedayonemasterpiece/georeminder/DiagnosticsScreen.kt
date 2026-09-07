package com.onedayonemasterpiece.georeminder

import android.content.Intent
import android.provider.Settings
import android.view.View

internal fun MainActivity.buildDiagnosticsScreen(): View {
    val column = screenColumn()
    column.addView(sectionTitle("Диагностика устройства"))
    column.addView(bodyText(
        "Фактический список зарегистрированных геозон Android публично не отдаёт. " +
            "Приложение поэтому хранит последнюю подтверждённую операцию регистрации и полный журнал последующих событий.",
    ))
    column.addView(actionButton("Обновить диагностику") { showCurrentScreen() })
    column.addView(actionButton("Перерегистрировать геозоны") { reregister("diagnostics-ui") })
    column.addView(actionButton("Настроить звук геонапоминаний") {
        startActivity(NotificationHelper.channelSettingsIntent(this))
    })
    column.addView(actionButton("Открыть настройки приложения") { openAppSettings() })
    column.addView(actionButton("Открыть настройки геолокации") {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    })
    column.addView(actionButton("Открыть настройки батареи") {
        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    })
    column.addView(actionButton("Экспортировать журнал JSONL") { startExport() })
    column.addView(monoText(Diagnostics.summary(this)).apply {
        setTextIsSelectable(true)
        setPadding(dp(12), dp(12), dp(12), dp(24))
    })
    return wrapScroll(column)
}
