package com.onedayonemasterpiece.georeminder

data class AuditEvent(
    val id: Long,
    val occurredAtEpochMs: Long,
    val eventType: String,
    val severity: String,
    val status: String,
    val ruleId: String?,
    val zoneId: String?,
    val transition: String?,
    val notificationId: Int?,
    val message: String,
    val detailsJson: String?,
)

data class NotificationDelivery(
    val notificationId: Int,
    val ruleId: String,
    val zoneId: String?,
    val zoneLabel: String?,
    val createdAtEpochMs: Long,
    val postedAtEpochMs: Long?,
    val verifiedActiveAtEpochMs: Long?,
    val lastActiveCheckAtEpochMs: Long?,
    val inactiveObservedAtEpochMs: Long?,
    val tappedAtEpochMs: Long?,
    val dismissedAtEpochMs: Long?,
    val isTest: Boolean,
    val channelId: String,
    val state: String,
    val failureReason: String?,
)

data class RegistrationSnapshot(
    val attemptedAtEpochMs: Long?,
    val completedAtEpochMs: Long?,
    val succeededAtEpochMs: Long?,
    val reason: String?,
    val ruleCount: Int,
    val zoneCount: Int,
    val success: Boolean?,
    val message: String?,
    val rulesSha256: String?,
    val requestIds: List<String>,
)

data class RuleImportRecord(
    val id: Long,
    val importedAtEpochMs: Long,
    val source: String,
    val sha256: String?,
    val ruleCount: Int,
    val zoneCount: Int,
    val success: Boolean,
    val message: String,
)

data class RuleRuntimeSummary(
    val completed: Boolean,
    val lastTriggeredAtEpochMs: Long?,
    val lastNotificationId: Int?,
    val notificationCount: Int,
    val lastNotificationState: String?,
    val lastNotificationAtEpochMs: Long?,
)

data class AuditCounts(
    val events: Long,
    val notifications: Long,
    val imports: Long,
)

data class NotificationReconciliation(
    val checked: Int,
    val activeConfirmed: Int,
    val noLongerActive: Int,
)
