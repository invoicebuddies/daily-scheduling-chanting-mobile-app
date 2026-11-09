package com.chantingscheduler.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Bitmask for [ScheduleEntity.daysOfWeek]: bit 0 = Monday ... bit 6 = Sunday. */
const val ALL_DAYS: Int = 0b1111111

/** How a schedule's fire time is determined each day. */
enum class TriggerType { FIXED, SUNRISE, SUNSET, FULL_MOON, NEW_MOON }

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "",
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Int = ALL_DAYS,
    val gapSeconds: Int = 0,
    val enabled: Boolean = true,
    val triggerType: TriggerType = TriggerType.FIXED,
    /** Minutes added to the sun time for SUNRISE/SUNSET triggers (negative = before). */
    val offsetMinutes: Int = 0,
    /** Optional auto-stop: playlist loops until this many minutes elapse, then fades out. */
    val maxDurationMinutes: Int? = null,
    /** How many times the playlist repeats per session (1 = play once through). */
    val repeatCount: Int = 1,
    /** Optional pre-session reminder: minutes before the trigger to post a quiet notification. */
    val reminderMinutes: Int? = null,
)

@Entity(
    tableName = "sound_items",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduleId")],
)
data class SoundItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val uri: String,
    val displayName: String,
    val position: Int,
)

data class ScheduleWithSounds(
    @Embedded val schedule: ScheduleEntity,
    @Relation(parentColumn = "id", entityColumn = "scheduleId")
    val sounds: List<SoundItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "scheduleId")
    val steps: List<RoutineStepEntity> = emptyList(),
) {
    val sortedSounds: List<SoundItemEntity> get() = sounds.sortedBy { it.position }
    val sortedSteps: List<RoutineStepEntity> get() = steps.sortedBy { it.position }

    /** Routine mode when at least one step exists; otherwise playlist mode. */
    val isRoutine: Boolean get() = steps.isNotEmpty()
}

/** How a logged chanting session ended. */
enum class SessionEndReason { COMPLETED, STOPPED, SNOOZED }

/** What started a logged chanting session. */
enum class SessionSource { ALARM, MANUAL }

/**
 * One finished chanting session, for history/stats. No foreign key to
 * schedules: history must survive schedule deletion. [scheduleId] is null
 * when the session had no schedule context.
 */
@Entity(tableName = "session_logs")
data class SessionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long?,
    val startedAtEpochMs: Long,
    val durationSeconds: Int,
    val endReason: SessionEndReason,
    val source: SessionSource,
    /** Actual audio playback time, excluding inter-track gaps; null for old rows. */
    val audioSeconds: Int? = null,
)

/**
 * One step of a routine schedule. Steps play in [position] order, each for
 * [durationMinutes] minutes; a null [soundUri] is a silence step. Unlike
 * session history, steps cascade-delete with their schedule.
 */
@Entity(
    tableName = "routine_steps",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scheduleId")],
)
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val position: Int,
    /** Sound to loop for the step; null = silence. */
    val soundUri: String?,
    /** Display name of the sound (edit UI); null for silence. */
    val soundName: String?,
    val durationMinutes: Int,
)
