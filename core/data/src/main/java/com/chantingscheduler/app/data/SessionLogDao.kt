package com.chantingscheduler.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionLogDao {

    @Insert
    suspend fun insert(log: SessionLogEntity): Long

    /** All sessions, newest first. */
    @Query("SELECT * FROM session_logs ORDER BY startedAtEpochMs DESC")
    fun observeAll(): Flow<List<SessionLogEntity>>

    /** All sessions, newest first (one-shot read for backup). */
    @Query("SELECT * FROM session_logs ORDER BY startedAtEpochMs DESC")
    suspend fun getAllOnce(): List<SessionLogEntity>

    /** Sessions started in [fromMs, toMs), newest first. */
    @Query(
        "SELECT * FROM session_logs " +
            "WHERE startedAtEpochMs >= :fromMs AND startedAtEpochMs < :toMs " +
            "ORDER BY startedAtEpochMs DESC",
    )
    suspend fun inRange(fromMs: Long, toMs: Long): List<SessionLogEntity>

    /** Adds, edits, or clears (null) the practice-journal note of one row. */
    @Query("UPDATE session_logs SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)
}
