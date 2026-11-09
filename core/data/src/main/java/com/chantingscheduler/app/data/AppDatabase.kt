package com.chantingscheduler.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun triggerTypeToString(triggerType: TriggerType): String = triggerType.name

    @TypeConverter
    fun stringToTriggerType(value: String): TriggerType = TriggerType.valueOf(value)

    @TypeConverter
    fun endReasonToString(endReason: SessionEndReason): String = endReason.name

    @TypeConverter
    fun stringToEndReason(value: String): SessionEndReason = SessionEndReason.valueOf(value)

    @TypeConverter
    fun sessionSourceToString(source: SessionSource): String = source.name

    @TypeConverter
    fun stringToSessionSource(value: String): SessionSource = SessionSource.valueOf(value)
}

@Database(
    entities = [
        ScheduleEntity::class,
        SoundItemEntity::class,
        SessionLogEntity::class,
        RoutineStepEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao

    abstract fun sessionLogDao(): SessionLogDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2: adds sun-trigger columns, preserving existing schedules. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE schedules ADD COLUMN triggerType TEXT NOT NULL DEFAULT 'FIXED'",
                )
                db.execSQL(
                    "ALTER TABLE schedules ADD COLUMN offsetMinutes INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** v2 -> v3: adds the session history table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS session_logs (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "scheduleId INTEGER, " +
                        "startedAtEpochMs INTEGER NOT NULL, " +
                        "durationSeconds INTEGER NOT NULL, " +
                        "endReason TEXT NOT NULL, " +
                        "source TEXT NOT NULL)",
                )
            }
        }

        /** v3 -> v4: adds the routine step table (cascade-deletes with its schedule). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS routine_steps (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "scheduleId INTEGER NOT NULL, " +
                        "position INTEGER NOT NULL, " +
                        "soundUri TEXT, " +
                        "soundName TEXT, " +
                        "durationMinutes INTEGER NOT NULL, " +
                        "FOREIGN KEY(scheduleId) REFERENCES schedules(id) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_steps_scheduleId " +
                        "ON routine_steps(scheduleId)",
                )
            }
        }

        /** v4 -> v5: auto-stop duration + playlist repeat count per schedule;
         * actual-audio-seconds per session log (null for rows logged before this). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE schedules ADD COLUMN maxDurationMinutes INTEGER",
                )
                db.execSQL(
                    "ALTER TABLE schedules ADD COLUMN repeatCount INTEGER NOT NULL DEFAULT 1",
                )
                db.execSQL(
                    "ALTER TABLE session_logs ADD COLUMN audioSeconds INTEGER",
                )
            }
        }

        /** v5 -> v6: optional pre-session reminder per schedule. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE schedules ADD COLUMN reminderMinutes INTEGER",
                )
            }
        }

        /** All migrations, oldest first — also used by the migration tests. */
        val MIGRATIONS: List<Migration> =
            listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chanting.db",
                ).addMigrations(*MIGRATIONS.toTypedArray()).build().also { instance = it }
            }
    }
}
