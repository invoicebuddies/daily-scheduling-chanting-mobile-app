package com.chantingscheduler.app.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises every Room migration against a real (Robolectric-shadowed) SQLite
 * database: each step is applied and inspected individually, then the database
 * is opened through Room, which validates the migrated schema against the
 * current entities and would throw on any drift.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createV1Database(name: String): SupportSQLiteDatabase {
        // Robolectric's sandbox filesystem can persist between runs; never
        // reuse a stale database file.
        context.deleteDatabase(name)
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onConfigure(db: SupportSQLiteDatabase) = Unit

            override fun onCreate(db: SupportSQLiteDatabase) {
                // Original v1 schema: no sun-trigger columns, no session history,
                // no routine steps.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS schedules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "hour INTEGER NOT NULL, " +
                        "minute INTEGER NOT NULL, " +
                        "daysOfWeek INTEGER NOT NULL, " +
                        "gapSeconds INTEGER NOT NULL DEFAULT 0, " +
                        "enabled INTEGER NOT NULL DEFAULT 1)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sound_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "scheduleId INTEGER NOT NULL, " +
                        "uri TEXT NOT NULL, " +
                        "displayName TEXT NOT NULL, " +
                        "position INTEGER NOT NULL, " +
                        "FOREIGN KEY(scheduleId) REFERENCES schedules(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sound_items_scheduleId " +
                        "ON sound_items(scheduleId)",
                )
                db.execSQL(
                    "INSERT INTO schedules (label, hour, minute, daysOfWeek, gapSeconds, enabled) " +
                        "VALUES ('Morning chant', 6, 30, 127, 5, 1)",
                )
                db.execSQL(
                    "INSERT INTO sound_items (scheduleId, uri, displayName, position) " +
                        "VALUES (1, 'content:// chanting.mp3', 'Chant', 0)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(callback)
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `migrations 1 to 5 preserve data and build every table`() {
        val db = createV1Database("migrate-stepwise.db")

        AppDatabase.MIGRATIONS[0].migrate(db) // 1 -> 2
        assertColumnExists(db, "schedules", "triggerType")
        assertColumnExists(db, "schedules", "offsetMinutes")

        AppDatabase.MIGRATIONS[1].migrate(db) // 2 -> 3
        assertTableExists(db, "session_logs")

        AppDatabase.MIGRATIONS[2].migrate(db) // 3 -> 4
        assertTableExists(db, "routine_steps")

        AppDatabase.MIGRATIONS[3].migrate(db) // 4 -> 5
        assertColumnExists(db, "schedules", "maxDurationMinutes")
        assertColumnExists(db, "schedules", "repeatCount")
        assertColumnExists(db, "session_logs", "audioSeconds")

        AppDatabase.MIGRATIONS[4].migrate(db) // 5 -> 6
        assertColumnExists(db, "schedules", "reminderMinutes")
        // The migrations were applied manually above (which leaves the user
        // version untouched): mark the file current so Room only validates.
        db.version = 6
        db.close()

        // Reopen via Room: full schema validation against the v5 entities,
        // plus proof the original rows survived every migration.
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, "migrate-stepwise.db")
            .addMigrations(*AppDatabase.MIGRATIONS.toTypedArray())
            .allowMainThreadQueries()
            .build()
        try {
            val item = runBlocking { roomDb.scheduleDao().getById(1L) }
            assertNotNull(item)
            assertEquals("Morning chant", item!!.schedule.label)
            assertEquals(6, item.schedule.hour)
            assertEquals(30, item.schedule.minute)
            assertEquals(127, item.schedule.daysOfWeek)
            assertEquals(5, item.schedule.gapSeconds)
            assertEquals(TriggerType.FIXED, item.schedule.triggerType)
            assertEquals(0, item.schedule.offsetMinutes)
            assertEquals(1, item.schedule.repeatCount)
            assertEquals(null, item.schedule.maxDurationMinutes)
            assertEquals(1, item.sounds.size)
            assertEquals("Chant", item.sounds[0].displayName)
            assertTrue(item.steps.isEmpty())
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun `room migrates a v1 file straight to latest on open`() {
        createV1Database("migrate-full-path.db").close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, "migrate-full-path.db")
            .addMigrations(*AppDatabase.MIGRATIONS.toTypedArray())
            .allowMainThreadQueries()
            .build()
        try {
            val enabled = runBlocking { db.scheduleDao().getEnabled() }
            assertEquals(1, enabled.size)
            assertEquals("Morning chant", enabled[0].schedule.label)
            assertEquals(1, enabled[0].schedule.repeatCount)
        } finally {
            db.close()
        }
    }

    private fun assertColumnExists(db: SupportSQLiteDatabase, table: String, column: String) {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val names = generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }
                .toList()
            assertTrue("column $table.$column missing (have $names)", column in names)
        }
    }

    private fun assertTableExists(db: SupportSQLiteDatabase, table: String) {
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { cursor ->
            assertTrue("table $table missing", cursor.moveToFirst())
        }
    }
}
