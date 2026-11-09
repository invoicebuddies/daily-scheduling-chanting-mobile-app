package com.chantingscheduler.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of what [PlaybackService] is currently doing, observable from the UI. */
data class NowPlaying(
    val isPlaying: Boolean = false,
    val label: String = "",
    val currentTrack: Int = 0,
    val totalTracks: Int = 0,
    val scheduleId: Long? = null,
    val startedAtMillis: Long = 0L,
    /** 1-based routine step position; 0/0 when running in playlist mode. */
    val currentStep: Int = 0,
    val totalSteps: Int = 0,
) {
    fun isPlayingSchedule(id: Long): Boolean = isPlaying && scheduleId == id
}

/**
 * Process-wide playback state. The service and UI live in the same process, so a
 * singleton StateFlow is the simplest reliable observation channel (no binding or
 * broadcasts needed).
 */
object PlaybackState {
    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    fun update(
        label: String,
        currentTrack: Int,
        totalTracks: Int,
        scheduleId: Long?,
        currentStep: Int = 0,
        totalSteps: Int = 0,
    ) {
        val previous = _state.value
        _state.value = NowPlaying(
            isPlaying = true,
            label = label,
            currentTrack = currentTrack,
            totalTracks = totalTracks,
            scheduleId = scheduleId,
            // Keep the original start time across track advances.
            startedAtMillis = if (previous.isPlaying && previous.scheduleId == scheduleId &&
                previous.label == label
            ) {
                previous.startedAtMillis
            } else {
                System.currentTimeMillis()
            },
            currentStep = currentStep,
            totalSteps = totalSteps,
        )
    }

    fun clear() {
        _state.value = NowPlaying()
    }
}
