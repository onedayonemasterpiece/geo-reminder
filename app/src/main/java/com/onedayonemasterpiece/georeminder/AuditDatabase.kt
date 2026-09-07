package com.onedayonemasterpiece.georeminder

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AuditDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE audit_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                occurred_at INTEGER NOT NULL,
                event_type TEXT NOT NULL,
                severity TEXT NOT NULL,
                status TEXT NOT NULL,
                rule_id TEXT,
                zone_id TEXT,
                transition_name TEXT,
                notification_id INTEGER,
                message TEXT NOT NULL,
                details_json TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_audit_events_time ON audit_events(occurred_at DESC)")
        db.execSQL("CREATE INDEX idx_audit_events_rule ON audit_events(rule_id, occurred_at DESC)")
        db.execSQL("CREATE INDEX idx_audit_events_type ON audit_events(event_type, occurred_at DESC)")

        db.execSQL(
            """
            CREATE TABLE notification_deliveries (
                notification_id INTEGER PRIMARY KEY,
                rule_id TEXT NOT NULL,
                zone_id TEXT,
                zone_label TEXT,
                created_at INTEGER NOT NULL,
                posted_at INTEGER,
                verified_active_at INTEGER,
                last_active_check_at INTEGER,
                inactive_observed_at INTEGER,
                tapped_at INTEGER,
                dismissed_at INTEGER,
                is_test INTEGER NOT NULL,
                channel_id TEXT NOT NULL,
                state TEXT NOT NULL,
                failure_reason TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_notification_rule ON notification_deliveries(rule_id, created_at DESC)",
        )
        db.execSQL(
            "CREATE INDEX idx_notification_state ON notification_deliveries(state, created_at DESC)",
        )

        db.execSQL(
            """
            CREATE TABLE rule_state (
                rule_id TEXT PRIMARY KEY,
                completed INTEGER NOT NULL DEFAULT 0,
                last_triggered_at INTEGER,
                last_notification_id INTEGER,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE registration_state (
                singleton_id INTEGER PRIMARY KEY CHECK(singleton_id = 1),
                attempted_at INTEGER,
                completed_at INTEGER,
                succeeded_at INTEGER,
                reason TEXT,
                rule_count INTEGER NOT NULL DEFAULT 0,
                zone_count INTEGER NOT NULL DEFAULT 0,
                success INTEGER,
                message TEXT,
                rules_sha256 TEXT,
                request_ids_json TEXT NOT NULL DEFAULT '[]'
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE rule_imports (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                imported_at INTEGER NOT NULL,
                source TEXT NOT NULL,
                sha256 TEXT,
                rule_count INTEGER NOT NULL,
                zone_count INTEGER NOT NULL,
                success INTEGER NOT NULL,
                message TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_rule_imports_time ON rule_imports(imported_at DESC)")

        db.execSQL(
            """
            CREATE TABLE counters (
                name TEXT PRIMARY KEY,
                value INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO counters(name, value) VALUES('notification_id', 1000)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException(
            "Unsupported audit database upgrade from $oldVersion to $newVersion; " +
                "the MVP schema must be migrated explicitly",
        )
    }

    companion object {
        private const val DATABASE_NAME = "geo-reminder-audit.db"
        private const val DATABASE_VERSION = 1

        @Volatile
        private var instance: AuditDatabase? = null

        fun get(context: Context): AuditDatabase {
            return instance ?: synchronized(this) {
                instance ?: AuditDatabase(context.applicationContext).also { instance = it }
            }
        }
    }
}
