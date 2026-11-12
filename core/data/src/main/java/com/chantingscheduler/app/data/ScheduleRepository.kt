package com.chantingscheduler.app.data

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ScheduleRepository @Inject constructor(private val db: AppDatabase) {

    private val dao = db.scheduleDao()
    private val sessionLogDao = db.sessionLogDao()

    fun observeAll(): Flow<List<ScheduleWithSounds>> = dao.observeAll()

    suspend fun getById(id: Long): ScheduleWithSounds? = dao.getById(id)

    suspend fun getEnabled(): List<ScheduleWithSounds> = dao.getEnabled()

    /** Inserts or updates the schedule and replaces its sound list. Returns the schedule id. */
    suspend fun save(
        schedule: ScheduleEntity,
        sounds: List<SoundItemEntity>,
        steps: List<RoutineStepEntity> = emptyList(),
    ): Long =
        db.withTransaction {
            val id = if (schedule.id == 0L) {
                dao.insertSchedule(schedule)
            } else {
                dao.updateSchedule(schedule)
                schedule.id
            }
            dao.deleteSoundsFor(id)
            dao.insertSounds(
                sounds.mapIndexed { index, sound ->
                    sound.copy(id = 0, scheduleId = id, position = index)
                },
            )
            dao.deleteStepsFor(id)
            dao.insertSteps(
                steps.mapIndexed { index, step ->
                    step.copy(id = 0, scheduleId = id, position = index)
                },
            )
            id
        }

    suspend fun setEnabled(schedule: ScheduleEntity, enabled: Boolean) {
        dao.updateSchedule(schedule.copy(enabled = enabled))
    }

    /** Cascade removes the sound items. */
    suspend fun delete(item: ScheduleWithSounds) {
        dao.deleteSchedule(item.schedule)
    }

    suspend fun logSession(log: SessionLogEntity): Long = sessionLogDao.insert(log)

    /** Adds, edits, or clears (null) the practice-journal note of one row. */
    suspend fun updateSessionNote(id: Long, note: String?) = sessionLogDao.updateNote(id, note)

    fun observeSessionLogs(): Flow<List<SessionLogEntity>> = sessionLogDao.observeAll()

    suspend fun sessionLogsInRange(fromMs: Long, toMs: Long): List<SessionLogEntity> =
        sessionLogDao.inRange(fromMs, toMs)

    /** One-shot snapshot of everything, for backup export. */
    suspend fun snapshot(): BackupCodec.Backup = BackupCodec.Backup(
        schedules = dao.getAllOnce(),
        sessions = sessionLogDao.getAllOnce(),
    )

    /**
     * Inserts imported schedules (with sounds/steps) and session history as
     * fresh rows; existing data is left untouched. Schedules identical to one
     * already present (same label, trigger, time, days, sounds) are skipped
     * as re-import duplicates, as are session rows identical to an existing
     * one — re-importing a backup must not double-count streaks, minutes, or
     * the calendar. Phone-file sound URIs are expected to arrive already
     * marked by [BackupCodec].
     */
    suspend fun importBackup(backup: BackupCodec.Backup): ImportResult =
        db.withTransaction {
            val existing = dao.getAllOnce()
            val accepted = mutableListOf<ScheduleWithSounds>()
            var imported = 0
            var skipped = 0
            backup.schedules.forEach { item ->
                // Skip when an identical schedule exists already or was
                // accepted earlier in this same import.
                if (BackupCodec.isDuplicate(item, existing + accepted)) {
                    skipped++
                } else {
                    val id = dao.insertSchedule(item.schedule.copy(id = 0))
                    dao.insertSounds(
                        item.sounds.map { it.copy(id = 0, scheduleId = id) },
                    )
                    dao.insertSteps(
                        item.steps.map { it.copy(id = 0, scheduleId = id) },
                    )
                    imported++
                    accepted.add(item)
                }
            }
            val sessionKeys = sessionLogDao.getAllOnce()
                .map { sessionKeyOf(it) }
                .toMutableSet()
            var sessionsImported = 0
            var sessionsSkipped = 0
            backup.sessions.forEach { session ->
                val key = sessionKeyOf(session)
                if (sessionKeys.add(key)) {
                    sessionLogDao.insert(session.copy(id = 0))
                    sessionsImported++
                } else {
                    sessionsSkipped++
                }
            }
            ImportResult(
                imported = imported,
                skipped = skipped,
                sessionsImported = sessionsImported,
                sessionsSkipped = sessionsSkipped,
            )
        }

    /** Identity of a session row for duplicate detection on import. */
    private data class SessionKey(
        val startedAtEpochMs: Long,
        val durationSeconds: Int,
        val endReason: SessionEndReason,
        val source: SessionSource,
    )

    private fun sessionKeyOf(s: SessionLogEntity) = SessionKey(
        s.startedAtEpochMs,
        s.durationSeconds,
        s.endReason,
        s.source,
    )

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val sessionsImported: Int,
        val sessionsSkipped: Int,
    )
}
