package com.chantingscheduler.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide playback preferences (volume fade-in duration, snooze length).
 * SharedPreferences (same pattern as [ThemeStore]) so values are readable
 * synchronously from the service and receivers; no Room schema change needed.
 */
class PlaybackSettingsStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _fadeInSeconds = MutableStateFlow(readFadeInSeconds())

    /** Alarm-triggered volume fade-in duration in seconds; 0 = off. */
    val fadeInSeconds: StateFlow<Int> = _fadeInSeconds.asStateFlow()

    /** Current fade-in duration, readable synchronously. */
    val currentFadeInSeconds: Int get() = _fadeInSeconds.value

    private val _snoozeMinutes = MutableStateFlow(readSnoozeMinutes())

    /** Snooze length in minutes for the ringing page / notification action. */
    val snoozeMinutes: StateFlow<Int> = _snoozeMinutes.asStateFlow()

    /** Current snooze length, readable synchronously. */
    val currentSnoozeMinutes: Int get() = _snoozeMinutes.value

    fun setFadeInSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_FADE_IN_SECONDS, seconds).apply()
        _fadeInSeconds.value = seconds
    }

    fun setSnoozeMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_SNOOZE_MINUTES, minutes).apply()
        _snoozeMinutes.value = minutes
    }

    private fun readFadeInSeconds(): Int =
        prefs.getInt(KEY_FADE_IN_SECONDS, DEFAULT_FADE_IN_SECONDS)
            .takeIf { it in FADE_IN_OPTIONS } ?: DEFAULT_FADE_IN_SECONDS

    private fun readSnoozeMinutes(): Int =
        prefs.getInt(KEY_SNOOZE_MINUTES, DEFAULT_SNOOZE_MINUTES)
            .takeIf { it in SNOOZE_OPTIONS } ?: DEFAULT_SNOOZE_MINUTES

    companion object {
        private const val PREFS_NAME = "playback_settings"
        private const val KEY_FADE_IN_SECONDS = "fade_in_seconds"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"

        /** Selectable fade-in durations in seconds; 0 = Off. */
        val FADE_IN_OPTIONS = listOf(0, 30, 60, 120, 300)

        /** Selectable snooze lengths in minutes. */
        val SNOOZE_OPTIONS = listOf(5, 10, 15)

        const val DEFAULT_FADE_IN_SECONDS = 0
        const val DEFAULT_SNOOZE_MINUTES = 10

        @Volatile
        private var instance: PlaybackSettingsStore? = null

        fun get(context: Context): PlaybackSettingsStore =
            instance ?: synchronized(this) {
                instance ?: PlaybackSettingsStore(context).also { instance = it }
            }
    }
}
