package com.chantingscheduler.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chantingscheduler.app.data.ScheduleRepository
import com.chantingscheduler.app.data.SessionEndReason
import com.chantingscheduler.app.data.SessionLogEntity
import com.chantingscheduler.app.data.SessionPoint
import com.chantingscheduler.app.data.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One row of the recent-session history list on the stats screen. */
data class SessionHistoryItem(
    val logId: Long,
    val startedAtEpochMs: Long,
    val label: String?,
    val minutes: Int,
    val endReason: SessionEndReason,
    /** Optional practice-journal note; null when untouched. */
    val note: String? = null,
)

data class StatsUiState(
    val sessionCount: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalMinutes: Int = 0,
    val weekMinutes: Int = 0,
    val minutesByDay: Map<LocalDate, Int> = emptyMap(),
    val history: List<SessionHistoryItem> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: ScheduleRepository,
) : ViewModel() {

    val stats: StateFlow<StatsUiState> = combine(
        repository.observeSessionLogs(),
        repository.observeAll(),
    ) { logs, schedules ->
        val labels = schedules.associate { it.schedule.id to it.schedule.label }
        val points = logs.map {
            SessionPoint(it.startedAtEpochMs, it.durationSeconds, it.audioSeconds)
        }
        StatsUiState(
            sessionCount = points.size,
            currentStreak = StreakCalculator.currentStreak(points),
            longestStreak = StreakCalculator.longestStreak(points),
            totalMinutes = StreakCalculator.totalMinutes(points),
            weekMinutes = StreakCalculator.minutesThisWeek(points),
            minutesByDay = StreakCalculator.minutesByDay(points),
            history = logs.take(HISTORY_LIMIT).map { log -> log.toHistoryItem(labels) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private fun SessionLogEntity.toHistoryItem(
        labels: Map<Long, String>,
    ): SessionHistoryItem = SessionHistoryItem(
        logId = id,
        startedAtEpochMs = startedAtEpochMs,
        label = scheduleId?.let { labels[it]?.ifBlank { null } },
        minutes = ((audioSeconds ?: durationSeconds) + 30) / 60,
        endReason = endReason,
        note = note,
    )

    /** Adds, edits, or clears (null/blank) the journal note of a history row. */
    fun updateSessionNote(logId: Long, note: String?) {
        viewModelScope.launch {
            repository.updateSessionNote(logId, note?.ifBlank { null })
        }
    }

    private companion object {
        const val HISTORY_LIMIT = 20
    }
}
