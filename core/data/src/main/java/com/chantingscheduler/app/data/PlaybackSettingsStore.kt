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

    private val _alarmVolumeEnabled = MutableStateFlow(readAlarmVolumeEnabled())

    /**
     * When on (default), alarm-sourced sessions render on the alarm audio
     * stream so they sound even at media volume 0 / DND.
     */
    val alarmVolumeEnabled: StateFlow<Boolean> = _alarmVolumeEnabled.asStateFlow()

    /** Current value, readable synchronously from the service. */
    val currentAlarmVolumeEnabled: Boolean get() = _alarmVolumeEnabled.value

    fun setAlarmVolumeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALARM_VOLUME_ENABLED, enabled).apply()
        _alarmVolumeEnabled.value = enabled
    }

    private val _chantVolumePercent = MutableStateFlow(readChantVolumePercent())

    /**
     * Chant loudness ceiling in percent (25–100). Scheduled chants play on the
     * alarm stream by default, whose volume is often high — this caps playback
     * volume to keep them gentle. Read synchronously by the service.
     */
    val chantVolumePercent: StateFlow<Int> = _chantVolumePercent.asStateFlow()

    /** Current chant volume ceiling, readable synchronously from the service. */
    val currentChantVolumePercent: Int get() = _chantVolumePercent.value

    fun setChantVolumePercent(percent: Int) {
        val clamped = percent.coerceIn(MIN_CHANT_VOLUME_PERCENT, MAX_CHANT_VOLUME_PERCENT)
        prefs.edit().putInt(KEY_CHANT_VOLUME_PERCENT, clamped).apply()
        _chantVolumePercent.value = clamped
    }

    // Pending snoozes survive reboots / force-stops: the alarm itself never
    // does. Stored as individual "snooze_<scheduleId>" = triggerAt entries.

    /** Records a snooze so it can be re-armed after reboot or re-arm. */
    fun setPendingSnooze(scheduleId: Long, triggerAtMillis: Long) {
        prefs.edit().putLong(snoozeKey(scheduleId), triggerAtMillis).apply()
    }

    /** Drops the stored snooze (fired, cancelled, or superseded). */
    fun clearPendingSnooze(scheduleId: Long) {
        prefs.edit().remove(snoozeKey(scheduleId)).apply()
    }

    /** All stored snoozes: scheduleId → triggerAt epoch millis. */
    fun pendingSnoozes(): Map<Long, Long> = prefs.all
        .mapNotNull { (key, value) ->
            val id = key.removePrefix(SNOOZE_KEY_PREFIX).toLongOrNull()
            if (id != null && value is Long) id to value else null
        }
        .toMap()

    private fun snoozeKey(scheduleId: Long) = "$SNOOZE_KEY_PREFIX$scheduleId"

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

    private fun readAlarmVolumeEnabled(): Boolean =
        prefs.getBoolean(KEY_ALARM_VOLUME_ENABLED, DEFAULT_ALARM_VOLUME_ENABLED)

    private fun readChantVolumePercent(): Int =
        prefs.getInt(KEY_CHANT_VOLUME_PERCENT, DEFAULT_CHANT_VOLUME_PERCENT)
            .coerceIn(MIN_CHANT_VOLUME_PERCENT, MAX_CHANT_VOLUME_PERCENT)

    companion object {
        private const val PREFS_NAME = "playback_settings"
        private const val KEY_FADE_IN_SECONDS = "fade_in_seconds"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_ALARM_VOLUME_ENABLED = "alarm_volume_enabled"
        private const val KEY_CHANT_VOLUME_PERCENT = "chant_volume_percent"
        private const val SNOOZE_KEY_PREFIX = "snooze_"

        /** Selectable fade-in durations in seconds; 0 = Off. */
        val FADE_IN_OPTIONS = listOf(0, 30, 60, 120, 300)

        /** Selectable snooze lengths in minutes. */
        val SNOOZE_OPTIONS = listOf(5, 10, 15)

        const val DEFAULT_FADE_IN_SECONDS = 0
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_ALARM_VOLUME_ENABLED = true
        const val DEFAULT_CHANT_VOLUME_PERCENT = 70

        /** Chant volume slider bounds. */
        const val MIN_CHANT_VOLUME_PERCENT = 25
        const val MAX_CHANT_VOLUME_PERCENT = 100

        @Volatile
        private var instance: PlaybackSettingsStore? = null

        fun get(context: Context): PlaybackSettingsStore =
            instance ?: synchronized(this) {
                instance ?: PlaybackSettingsStore(context).also { instance = it }
            }
    }
}
