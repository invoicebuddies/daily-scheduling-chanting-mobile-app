package com.chantingscheduler.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupCodecTest {

    @Test
    fun `export-parse round trip preserves schedules sounds and steps`() {
        val item = ScheduleWithSounds(
            schedule = ScheduleEntity(
                id = 7,
                label = "Morning",
                hour = 6,
                minute = 30,
                daysOfWeek = 0b1000001,
                gapSeconds = 12,
                enabled = true,
                triggerType = TriggerType.SUNRISE,
                offsetMinutes = -15,
                maxDurationMinutes = 20,
                repeatCount = 3,
                reminderMinutes = 5,
            ),
            sounds = listOf(
                SoundItemEntity(id = 1, scheduleId = 7, uri = "content://a.mp3", displayName = "A", position = 0),
                SoundItemEntity(
                    id = 2,
                    scheduleId = 7,
                    uri = "android.resource://pkg/raw/builtin_morning_bell",
                    displayName = "Morning bell",
                    position = 1,
                ),
            ),
            steps = listOf(
                RoutineStepEntity(id = 3, scheduleId = 7, position = 0, soundUri = null, soundName = null, durationMinutes = 2),
            ),
        )
        val sessions = listOf(
            SessionLogEntity(
                id = 9,
                scheduleId = 7,
                startedAtEpochMs = 1_700_000_000_000L,
                durationSeconds = 600,
                endReason = SessionEndReason.SNOOZED,
                source = SessionSource.ALARM,
                audioSeconds = 540,
            ),
        )

        val backup = BackupCodec.parse(BackupCodec.toJson(BackupCodec.Backup(listOf(item), sessions)))

        assertEquals(1, backup.schedules.size)
        val parsed = backup.schedules[0]
        assertEquals("Morning", parsed.schedule.label)
        assertEquals(6, parsed.schedule.hour)
        assertEquals(0b1000001, parsed.schedule.daysOfWeek)
        assertEquals(TriggerType.SUNRISE, parsed.schedule.triggerType)
        assertEquals(-15, parsed.schedule.offsetMinutes)
        assertEquals(20, parsed.schedule.maxDurationMinutes)
        assertEquals(3, parsed.schedule.repeatCount)
        assertEquals(5, parsed.schedule.reminderMinutes)
        // Imports become fresh rows.
        assertEquals(0L, parsed.schedule.id)
        assertEquals(2, parsed.sounds.size)
        assertEquals(1, parsed.steps.size)
        assertEquals(1, backup.sessions.size)
        assertEquals(540, backup.sessions[0].audioSeconds)
        assertEquals(SessionEndReason.SNOOZED, backup.sessions[0].endReason)
    }

    @Test
    fun `phone-file uris are marked for re-pick on import while built-ins pass through`() {
        val item = ScheduleWithSounds(
            schedule = ScheduleEntity(hour = 8, minute = 0),
            sounds = listOf(
                SoundItemEntity(id = 1, scheduleId = 1, uri = "content://music.mp3", displayName = "Music", position = 0),
                SoundItemEntity(
                    id = 2,
                    scheduleId = 1,
                    uri = "android.resource://pkg/raw/builtin_temple_bell",
                    displayName = "Temple bell",
                    position = 1,
                ),
            ),
            steps = listOf(
                RoutineStepEntity(
                    id = 3, scheduleId = 1, position = 0,
                    soundUri = "content://step.mp3", soundName = "Step", durationMinutes = 5,
                ),
                RoutineStepEntity(id = 4, scheduleId = 1, position = 1, soundUri = null, soundName = null, durationMinutes = 1),
            ),
        )

        val parsed = BackupCodec.parse(BackupCodec.toJson(BackupCodec.Backup(listOf(item), emptyList())))

        assertEquals(
            BackupCodec.REPICK_MARKER + "Music",
            parsed.schedules[0].sounds[0].displayName,
        )
        assertEquals("Temple bell", parsed.schedules[0].sounds[1].displayName)
        assertEquals(BackupCodec.REPICK_MARKER + "Step", parsed.schedules[0].steps[0].soundName)
        assertNull(parsed.schedules[0].steps[1].soundName)
    }

    @Test
    fun `empty backup produces empty parse`() {
        val json = BackupCodec.toJson(BackupCodec.Backup(emptyList(), emptyList()))
        val parsed = BackupCodec.parse(json)
        assertTrue(parsed.schedules.isEmpty())
        assertTrue(parsed.sessions.isEmpty())
        assertEquals(BackupCodec.VERSION, org.json.JSONObject(json).getInt("version"))
    }
}
