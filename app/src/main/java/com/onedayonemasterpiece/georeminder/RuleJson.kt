package com.onedayonemasterpiece.georeminder

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object RuleJson {
    private val safeId = Regex("^[a-z0-9][a-z0-9._-]{0,63}$")
    private val rootKeys = setOf("schema_version", "generated_at", "rules")
    private val ruleKeys = setOf(
        "id",
        "title",
        "message",
        "enabled",
        "transition",
        "repeat",
        "cooldown_minutes",
        "responsiveness_ms",
        "dwell_ms",
        "valid_from",
        "valid_until",
        "source_text",
        "zones",
    )
    private val zoneKeys = setOf("id", "label", "latitude", "longitude", "radius_m")

    fun parse(raw: String): RuleSet {
        val root = JSONObject(raw)
        root.rejectUnknown(rootKeys, "root")
        val schemaVersion = root.requireInt("schema_version")
        require(schemaVersion == 1) { "Unsupported schema_version=$schemaVersion" }

        val generatedAt = root.optNullableString("generated_at")?.let(::parseInstant)
        val rulesArray = root.requireArray("rules")
        require(rulesArray.length() <= 100) { "At most 100 rules are allowed" }

        val ruleIds = mutableSetOf<String>()
        val requestIds = mutableSetOf<String>()
        val rules = buildList {
            for (index in 0 until rulesArray.length()) {
                val item = rulesArray.requireObject(index)
                val prefix = "rules[$index]"
                item.rejectUnknown(ruleKeys, prefix)
                val id = item.requireString("id", maxLength = 64)
                validateId(id, "rule id")
                require(ruleIds.add(id)) { "Duplicate rule id: $id" }

                val transition = parseTransition(item.optStrictString("transition", "enter"), id)
                val repeatMode = parseRepeat(item.optStrictString("repeat", "once"), id)
                val cooldownMinutes = item.optStrictLong("cooldown_minutes", 0L)
                require(cooldownMinutes in 0L..10_080L) {
                    "cooldown_minutes must be between 0 and 10080 for rule $id"
                }
                val responsivenessMs = item.optStrictInt("responsiveness_ms", 120_000)
                require(responsivenessMs in 60_000..900_000) {
                    "responsiveness_ms must be between 60000 and 900000 for rule $id"
                }
                val dwellMs = item.optStrictInt("dwell_ms", 120_000)
                require(dwellMs in 60_000..3_600_000) {
                    "dwell_ms must be between 60000 and 3600000 for rule $id"
                }

                val validFrom = item.optNullableString("valid_from")?.let(::parseInstant)
                val validUntil = item.optNullableString("valid_until")?.let(::parseInstant)
                if (validFrom != null && validUntil != null) {
                    require(!validUntil.isBefore(validFrom)) {
                        "valid_until is before valid_from for rule $id"
                    }
                }

                val zonesArray = item.requireArray("zones")
                require(zonesArray.length() in 1..100) {
                    "Rule $id must contain 1..100 zones"
                }
                val zoneIds = mutableSetOf<String>()
                val zones = buildList {
                    for (zoneIndex in 0 until zonesArray.length()) {
                        val zone = zonesArray.requireObject(zoneIndex)
                        val zonePrefix = "$prefix.zones[$zoneIndex]"
                        zone.rejectUnknown(zoneKeys, zonePrefix)
                        val zoneId = zone.requireString("id", maxLength = 64)
                        validateId(zoneId, "zone id")
                        require(zoneIds.add(zoneId)) {
                            "Duplicate zone id $zoneId inside rule $id"
                        }
                        require(requestIds.add(GeofenceIds.compose(id, zoneId))) {
                            "Duplicate geofence request id for $id/$zoneId"
                        }

                        val latitude = zone.requireFiniteDouble("latitude")
                        val longitude = zone.requireFiniteDouble("longitude")
                        val radius = zone.requireFiniteDouble("radius_m").toFloat()
                        require(latitude in -90.0..90.0) { "Invalid latitude for $id/$zoneId" }
                        require(longitude in -180.0..180.0) { "Invalid longitude for $id/$zoneId" }
                        require(radius in 75f..2_000f) {
                            "radius_m must be between 75 and 2000 for $id/$zoneId"
                        }

                        val label = if (zone.has("label")) {
                            zone.requireString("label", maxLength = 160)
                        } else {
                            zoneId
                        }
                        add(
                            CircleZone(
                                id = zoneId,
                                label = label,
                                latitude = latitude,
                                longitude = longitude,
                                radiusMeters = radius,
                            ),
                        )
                    }
                }

                add(
                    ReminderRule(
                        id = id,
                        title = item.requireString("title", maxLength = 120),
                        message = item.requireString("message", maxLength = 1_000),
                        enabled = item.optStrictBoolean("enabled", true),
                        transition = transition,
                        repeatMode = repeatMode,
                        cooldownMinutes = cooldownMinutes,
                        responsivenessMs = responsivenessMs,
                        dwellMs = dwellMs,
                        validFrom = validFrom,
                        validUntil = validUntil,
                        sourceText = item.optNullableString("source_text")?.also {
                            require(it.length <= 4_000) { "source_text is too long for rule $id" }
                        },
                        zones = zones,
                    ),
                )
            }
        }

        val zoneCount = rules.sumOf { it.zones.size }
        require(zoneCount <= 100) {
            "Android allows at most 100 active geofences per app user; found $zoneCount"
        }

        return RuleSet(
            schemaVersion = schemaVersion,
            generatedAt = generatedAt,
            rules = rules,
        )
    }

    private fun parseTransition(value: String, ruleId: String): TransitionType = when (value) {
        "enter" -> TransitionType.ENTER
        "exit" -> TransitionType.EXIT
        "dwell" -> TransitionType.DWELL
        else -> throw IllegalArgumentException(
            "transition must be enter, exit, or dwell for rule $ruleId",
        )
    }

    private fun parseRepeat(value: String, ruleId: String): RepeatMode = when (value) {
        "once" -> RepeatMode.ONCE
        "always" -> RepeatMode.ALWAYS
        else -> throw IllegalArgumentException("repeat must be once or always for rule $ruleId")
    }

    private fun validateId(value: String, label: String) {
        require(safeId.matches(value)) {
            "$label must match ${safeId.pattern}; got '$value'"
        }
    }

    private fun parseInstant(value: String): Instant = try {
        Instant.parse(value)
    } catch (error: Exception) {
        throw IllegalArgumentException("Invalid ISO-8601 timestamp: $value", error)
    }

    private fun JSONObject.rejectUnknown(allowed: Set<String>, prefix: String) {
        val unknown = keys().asSequence().filterNot(allowed::contains).sorted().toList()
        require(unknown.isEmpty()) {
            "$prefix contains unsupported fields: ${unknown.joinToString()}"
        }
    }

    private fun JSONObject.requireString(name: String, maxLength: Int): String {
        require(has(name) && !isNull(name)) { "Missing '$name'" }
        require(get(name) is String) { "'$name' must be a string" }
        val value = getString(name).trim()
        require(value.isNotEmpty()) { "'$name' must not be blank" }
        require(value.length <= maxLength) { "'$name' exceeds $maxLength characters" }
        return value
    }

    private fun JSONObject.requireInt(name: String): Int {
        require(has(name) && !isNull(name)) { "Missing '$name'" }
        val value = get(name)
        require(value is Number) { "'$name' must be an integer" }
        val asLong = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == asLong.toDouble()) {
            "'$name' must be an integer"
        }
        require(asLong in Int.MIN_VALUE..Int.MAX_VALUE) { "'$name' is outside Int range" }
        return asLong.toInt()
    }

    private fun JSONObject.requireFiniteDouble(name: String): Double {
        require(has(name) && !isNull(name)) { "Missing '$name'" }
        val value = get(name)
        require(value is Number) { "'$name' must be a number" }
        val result = value.toDouble()
        require(result.isFinite()) { "'$name' must be finite" }
        return result
    }

    private fun JSONObject.requireArray(name: String): JSONArray {
        require(has(name) && !isNull(name)) { "Missing '$name'" }
        require(get(name) is JSONArray) { "'$name' must be an array" }
        return getJSONArray(name)
    }

    private fun JSONArray.requireObject(index: Int): JSONObject {
        require(get(index) is JSONObject) { "array item $index must be an object" }
        return getJSONObject(index)
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        require(get(name) is String) { "'$name' must be a string or null" }
        return getString(name).trim().takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optStrictString(name: String, default: String): String {
        if (!has(name)) return default
        require(!isNull(name) && get(name) is String) { "'$name' must be a string" }
        return getString(name)
    }

    private fun JSONObject.optStrictBoolean(name: String, default: Boolean): Boolean {
        if (!has(name)) return default
        require(!isNull(name) && get(name) is Boolean) { "'$name' must be boolean" }
        return getBoolean(name)
    }

    private fun JSONObject.optStrictLong(name: String, default: Long): Long {
        if (!has(name)) return default
        require(!isNull(name)) { "'$name' must be an integer" }
        val value = get(name)
        require(value is Number) { "'$name' must be an integer" }
        val result = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == result.toDouble()) {
            "'$name' must be an integer"
        }
        return result
    }

    private fun JSONObject.optStrictInt(name: String, default: Int): Int {
        val value = optStrictLong(name, default.toLong())
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "'$name' is outside Int range" }
        return value.toInt()
    }
}
