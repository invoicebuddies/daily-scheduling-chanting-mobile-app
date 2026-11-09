package com.chantingscheduler.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Transaction
    @Query("SELECT * FROM schedules ORDER BY hour, minute")
    fun observeAll(): Flow<List<ScheduleWithSounds>>

    @Transaction
    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun getEnabled(): List<ScheduleWithSounds>

    @Transaction
    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): ScheduleWithSounds?

    @Transaction
    @Query("SELECT * FROM schedules")
    suspend fun getAllOnce(): List<ScheduleWithSounds>

    @Insert
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: ScheduleEntity)

    @Insert
    suspend fun insertSounds(sounds: List<SoundItemEntity>)

    @Query("DELETE FROM sound_items WHERE scheduleId = :scheduleId")
    suspend fun deleteSoundsFor(scheduleId: Long)

    @Insert
    suspend fun insertSteps(steps: List<RoutineStepEntity>)

    @Query("DELETE FROM routine_steps WHERE scheduleId = :scheduleId")
    suspend fun deleteStepsFor(scheduleId: Long)
}
