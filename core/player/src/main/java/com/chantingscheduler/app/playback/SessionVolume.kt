package com.chantingscheduler.app.playback

/**
 * Resolves the loudness ceiling for one session: a per-schedule volume, when
 * set, replaces the global chant volume; otherwise the global value governs.
 * Percent inputs are clamped to the app's selectable range.
 */
object SessionVolume {

    const val MIN_PERCENT = 25
    const val MAX_PERCENT = 100

    /** Fraction (0.25–1.0) applied to ExoPlayer volume and fade ramps. */
    fun resolve(scheduleVolumePercent: Int?, globalPercent: Int): Float {
        val percent = scheduleVolumePercent ?: globalPercent
        return percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f
    }
}
