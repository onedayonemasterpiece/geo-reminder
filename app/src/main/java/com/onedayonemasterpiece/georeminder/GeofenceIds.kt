package com.onedayonemasterpiece.georeminder

object GeofenceIds {
    private const val SEPARATOR = "::"

    fun compose(ruleId: String, zoneId: String): String = "$ruleId$SEPARATOR$zoneId"

    fun split(requestId: String): Pair<String, String>? {
        val parts = requestId.split(SEPARATOR, limit = 2)
        if (parts.size != 2 || parts.any { it.isBlank() }) return null
        return parts[0] to parts[1]
    }
}
