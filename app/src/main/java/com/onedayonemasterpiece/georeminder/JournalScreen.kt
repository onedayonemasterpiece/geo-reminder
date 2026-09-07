package com.onedayonemasterpiece.georeminder

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView

internal fun MainActivity.buildJournalScreen(): View {
    val column = screenColumn()
    column.addView(sectionTitle("Журнал и доставка"))
    column.addView(bodyText(
        "Журнал различает получение геособытия, принятие правила, вызов notify(), " +
            "наличие уведомления среди activeNotifications, нажатие и смахивание. " +
            "Android не может подтвердить, что человек физически увидел баннер.",
    ))

    val spinner = Spinner(this)
    val labels = JournalFilter.entries.map { it.label }
    spinner.adapter = ArrayAdapter(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        labels,
    )
    spinner.setSelection(JournalFilter.entries.indexOf(journalFilter), false)
    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            val selected = JournalFilter.entries[position]
            if (selected != journalFilter) {
                journalFilter = selected
                showCurrentScreen()
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
    column.addView(spinner)
    column.addView(actionButton("Обновить и сверить с системой") {
        NotificationHelper.reconcileWithSystem(this)
        showCurrentScreen()
    })
    column.addView(actionButton("Экспортировать весь журнал JSONL") { startExport() })

    val observation = EditText(this).apply {
        hint = "Контрольная отметка, например: вышел из зоны в 18:40"
        setSingleLine(false)
        minLines = 2
    }
    column.addView(observation)
    column.addView(actionButton("Записать контрольную отметку") {
        val note = observation.text.toString().trim().ifEmpty { "Ручная контрольная отметка" }
        EventLog.record(
            this,
            type = "USER_OBSERVATION_MARK",
            message = note,
            status = "MARKED",
        )
        toast("Отметка записана")
        showCurrentScreen()
    })

    val audit = AuditRepository(this)
    val imports = audit.listImports(20)
    column.addView(sectionTitle("Импорты правил"))
    if (imports.isEmpty()) {
        column.addView(infoCard("Импортов пока нет."))
    } else {
        imports.forEach { item -> column.addView(importCard(item)) }
    }

    val deliveries = audit.listNotifications(100)
    column.addView(sectionTitle("Доставки уведомлений"))
    if (deliveries.isEmpty()) {
        column.addView(infoCard("Попыток доставки пока нет."))
    } else {
        deliveries.forEach { delivery -> column.addView(notificationCard(delivery)) }
    }

    column.addView(sectionTitle("События"))
    val events = audit.listEvents(500).filter(journalFilter::accepts)
    if (events.isEmpty()) {
        column.addView(infoCard("Для выбранного фильтра событий нет."))
    } else {
        events.forEach { event -> column.addView(eventCard(event)) }
    }
    return wrapScroll(column)
}

internal fun MainActivity.importCard(item: RuleImportRecord): View {
    val card = cardColumn(
        borderColor = if (item.success) Color.rgb(55, 116, 83) else Color.rgb(176, 60, 60),
    )
    card.addView(TextView(this).apply {
        text = "Импорт #${item.id} · ${if (item.success) "SUCCEEDED" else "FAILED"}"
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
    })
    card.addView(monoText(buildString {
        appendLine("at=${Diagnostics.format(item.importedAtEpochMs)}")
        appendLine("source=${item.source}")
        appendLine("sha256=${item.sha256 ?: "NONE"}")
        appendLine("rules=${item.ruleCount} zones=${item.zoneCount}")
        append("message=${item.message}")
    }))
    return card
}

internal fun MainActivity.notificationCard(item: NotificationDelivery): View {
    val card = cardColumn(
        borderColor = when (item.state) {
            "FAILED" -> Color.rgb(176, 60, 60)
            "ACTIVE_CONFIRMED", "TAPPED", "DISMISSED" -> Color.rgb(55, 116, 83)
            "NO_LONGER_ACTIVE_UNKNOWN" -> Color.rgb(176, 96, 45)
            else -> Color.rgb(160, 125, 45)
        },
    )
    card.addView(TextView(this).apply {
        text = "#${item.notificationId}  ${item.state}" + if (item.isTest) "  [TEST]" else ""
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
    })
    card.addView(monoText(buildString {
        appendLine("rule=${item.ruleId}")
        appendLine("zone=${item.zoneId ?: "NONE"} (${item.zoneLabel ?: "NONE"})")
        appendLine("created=${Diagnostics.format(item.createdAtEpochMs)}")
        appendLine("posted=${Diagnostics.format(item.postedAtEpochMs)}")
        appendLine("active_confirmed=${Diagnostics.format(item.verifiedActiveAtEpochMs)}")
        appendLine("last_active_check=${Diagnostics.format(item.lastActiveCheckAtEpochMs)}")
        appendLine("inactive_observed=${Diagnostics.format(item.inactiveObservedAtEpochMs)}")
        appendLine("tapped=${Diagnostics.format(item.tappedAtEpochMs)}")
        appendLine("dismissed=${Diagnostics.format(item.dismissedAtEpochMs)}")
        append("failure=${item.failureReason ?: "NONE"}")
    }))
    return card
}

internal fun MainActivity.eventCard(event: AuditEvent): View {
    val border = when (event.severity) {
        "ERROR" -> Color.rgb(176, 60, 60)
        "WARNING" -> Color.rgb(170, 126, 40)
        else -> Color.rgb(105, 116, 121)
    }
    val card = cardColumn(border)
    card.addView(TextView(this).apply {
        text = "${event.eventType} · ${event.status}"
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
    })
    card.addView(bodyText(Diagnostics.format(event.occurredAtEpochMs)))
    card.addView(bodyText(event.message))
    val contextLine = buildList {
        event.ruleId?.let { add("rule=$it") }
        event.zoneId?.let { add("zone=$it") }
        event.transition?.let { add("transition=$it") }
        event.notificationId?.let { add("notification=$it") }
    }.joinToString("  ")
    if (contextLine.isNotEmpty()) card.addView(monoText(contextLine))
    event.detailsJson?.let { card.addView(monoText(it)) }
    return card
}
