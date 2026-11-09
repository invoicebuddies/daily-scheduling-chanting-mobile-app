package com.chantingscheduler.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Duplicate handling of [ScheduleRepository.importBackup]: re-importing the
 * same backup must not duplicate schedules (streaks/minutes/calendar would
 * double-count) nor session history rows.
 */
@RunWith(RobolectricTestRunner::class)
class ImportBackupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScheduleRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun schedule(label: String) = ScheduleWithSounds(
        schedule = ScheduleEntity(hour = 6, minute = 30, label = label),
        sounds = listOf(
            SoundItemEntity(id = 0, scheduleId = 0, uri = "android.resource://x/raw/b", displayName = "B", position = 0),
        ),
    )

    private fun session(startedAt: Long) = SessionLogEntity(
        scheduleId = null,
        startedAtEpochMs = startedAt,
        durationSeconds = 180,
        endReason = SessionEndReason.COMPLETED,
        source = SessionSource.ALARM,
        audioSeconds = 170,
    )

    @Test
    fun `re-importing the same backup imports nothing and skips everything`() = runBlocking {
        val backup = BackupCodec.Backup(
            schedules = listOf(schedule("Morning")),
            sessions = listOf(session(1_700_000_000_000L), session(1_700_000_100_000L)),
        )

        val first = repository.importBackup(backup)
        assertEquals(1, first.imported)
        assertEquals(2, first.sessionsImported)
        assertEquals(0, first.skipped)
        assertEquals(0, first.sessionsSkipped)

        val second = repository.importBackup(backup)
        assertEquals(0, second.imported)
        assertEquals(0, second.sessionsImported)
        assertEquals(1, second.skipped)
        assertEquals(2, second.sessionsSkipped)

        // Stats inputs must be unchanged by the re-import.
        assertEquals(1, repository.snapshot().schedules.size)
        assertEquals(2, repository.snapshot().sessions.size)
    }

    @Test
    fun `importing a backup with new and overlapping sessions only adds the new ones`() = runBlocking {
        repository.importBackup(
            BackupCodec.Backup(
                schedules = emptyList(),
                sessions = listOf(session(1_700_000_000_000L)),
            ),
        )

        val result = repository.importBackup(
            BackupCodec.Backup(
                schedules = emptyList(),
                sessions = listOf(
                    session(1_700_000_000_000L), // duplicate of existing
                    session(1_700_000_200_000L), // new
                ),
            ),
        )

        assertEquals(0, result.imported)
        assertEquals(1, result.sessionsImported)
        assertEquals(1, result.sessionsSkipped)
        assertEquals(2, repository.snapshot().sessions.size)
    }

    @Test
    fun `sessions differing in duration end reason or source are not duplicates`() = runBlocking {
        val base = session(1_700_000_000_000L)
        repository.importBackup(
            BackupCodec.Backup(schedules = emptyList(), sessions = listOf(base)),
        )

        val result = repository.importBackup(
            BackupCodec.Backup(
                schedules = emptyList(),
                sessions = listOf(
                    base.copy(durationSeconds = 60),
                    base.copy(endReason = SessionEndReason.STOPPED),
                    base.copy(source = SessionSource.MANUAL),
                ),
            ),
        )

        assertEquals(3, result.sessionsImported)
        assertEquals(0, result.sessionsSkipped)
        assertEquals(4, repository.snapshot().sessions.size)
    }
}
