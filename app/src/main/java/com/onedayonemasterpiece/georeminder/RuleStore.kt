package com.onedayonemasterpiece.georeminder

import android.content.Context
import java.io.File
import java.security.MessageDigest

class RuleStore(context: Context) {
    private val appContext = context.applicationContext
    private val rulesFile = File(appContext.filesDir, RULES_FILE)
    private val previousFile = File(appContext.filesDir, PREVIOUS_FILE)
    private val stagedFile = File(appContext.filesDir, STAGED_FILE)
    private val snapshotsDirectory = File(appContext.filesDir, SNAPSHOTS_DIRECTORY)
    private val audit = AuditRepository(appContext)

    fun load(): RuleSet {
        if (!rulesFile.exists()) {
            return RuleSet(schemaVersion = 1, generatedAt = null, rules = emptyList())
        }
        return RuleJson.parse(rulesFile.readText(Charsets.UTF_8))
    }

    fun loadRaw(): String? = rulesFile.takeIf { it.exists() }?.readText(Charsets.UTF_8)

    fun currentSha256(): String? = loadRaw()?.let(::sha256)

    fun importStaged(source: String = "adb"): ImportResult {
        if (!stagedFile.exists()) {
            val message = "Staged file '$STAGED_FILE' is missing"
            audit.recordImport(source, null, 0, 0, false, message)
            return ImportResult(false, message)
        }

        val raw = try {
            stagedFile.readText(Charsets.UTF_8)
        } catch (error: Exception) {
            val message = "Import rejected while reading staged file: ${error.message}"
            audit.recordImport(source, null, 0, 0, false, message)
            return ImportResult(false, message)
        }
        val digest = sha256(raw)
        return try {
            val parsed = RuleJson.parse(raw)
            writeAtomically(raw, digest)
            val message = "Imported ${parsed.rules.size} rules and ${parsed.zoneCount} zones"
            audit.recordImport(
                source = source,
                sha256 = digest,
                ruleCount = parsed.rules.size,
                zoneCount = parsed.zoneCount,
                success = true,
                message = message,
            )
            ImportResult(true, message, parsed, digest)
        } catch (error: Exception) {
            val message = "Import rejected: ${error.message}"
            audit.recordImport(source, digest, 0, 0, false, message)
            ImportResult(false, message, sha256 = digest)
        }
    }

    private fun writeAtomically(raw: String, digest: String) {
        if (rulesFile.exists()) {
            rulesFile.copyTo(previousFile, overwrite = true)
        }

        val temporary = File(appContext.filesDir, "$RULES_FILE.tmp")
        temporary.writeText(raw, Charsets.UTF_8)
        if (!temporary.renameTo(rulesFile)) {
            temporary.copyTo(rulesFile, overwrite = true)
            temporary.delete()
        }

        snapshotsDirectory.mkdirs()
        val snapshot = File(
            snapshotsDirectory,
            "${System.currentTimeMillis()}-${digest.take(12)}.json",
        )
        snapshot.writeText(raw, Charsets.UTF_8)
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val RULES_FILE = "rules.json"
        const val PREVIOUS_FILE = "rules.previous.json"
        const val STAGED_FILE = "adb-rules.json"
        const val SNAPSHOTS_DIRECTORY = "rule-snapshots"
    }
}
